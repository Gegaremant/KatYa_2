package com.katya.app.data

import androidx.compose.runtime.Immutable
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.katya.app.db.KatyaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Serializable
enum class MemoryCategory {
    GENERAL,
    LEARNING,
    ERROR,
    PREFERENCE,
}

@Immutable
@Serializable
data class MemoryEntry(
    val key: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val category: MemoryCategory = MemoryCategory.GENERAL,
    val hitCount: Int = 1,
    val source: String? = null,
    /**
     * Кому принадлежит знание: `null` — общее ядро, доступное всем личностям,
     * иначе имя роли, которой оно известно одному.
     *
     * В хранилище роль записывается пустой строкой, а не NULL: в составном
     * первичном ключе NULL в SQLite допустим, и две записи с одинаковым ключом и
     * NULL считались бы разными — уникальность молча исчезла бы именно там, где
     * она держит всё.
     */
    val role: String? = null,
)

@OptIn(ExperimentalTime::class)
class MemoryStore(private val database: KatyaDatabase?, private val appSettings: AppSettings) {

    private val json = SharedJson
    private val mutex = Mutex()

    private fun loadMemoriesJson(): MutableList<MemoryEntry> {
        val raw = appSettings.getMemoriesJson()
        if (raw.isBlank()) return mutableListOf()
        return try {
            json.decodeFromString<List<MemoryEntry>>(raw).toMutableList()
        } catch (e: Exception) {
            println("MemoryStore: failed to load memories: ${e.message}")
            mutableListOf()
        }
    }

    private fun saveMemoriesJson(memories: List<MemoryEntry>) {
        appSettings.setMemoriesJson(json.encodeToString(memories))
    }

    /**
     * Записывает знание.
     *
     * [role] — `null` для общего ядра (имя, работа, постоянные факты), иначе имя
     * личности, которой знание принадлежит. Общее ядро и личное хранятся по
     * одному ключу и в одной таблице, но различаются ролью, поэтому одна и та же
     * формулировка может честно принадлежать и общему ядру, и личности.
     */
    suspend fun store(
        key: String,
        content: String,
        category: MemoryCategory = MemoryCategory.GENERAL,
        source: String? = null,
        role: String? = null,
    ): MemoryEntry = if (database != null) {
        withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
            val now = Clock.System.now().toEpochMilliseconds()
            val storedKey = RoleScopedMemoryKey.encode(key, role)
            val existing = database.memoryQueries.selectMemoryByKey(storedKey).executeAsOneOrNull()
            if (existing != null) {
                database.memoryQueries.upsertMemory(
                    key = storedKey,
                    content = content,
                    createdAt = existing.createdAt,
                    updatedAt = now,
                    category = category.name,
                    hitCount = existing.hitCount,
                    source = source ?: existing.source,
                )
                MemoryEntry(key, content, existing.createdAt, now, category, existing.hitCount.toInt(), source ?: existing.source, RoleScopedMemoryKey.decode(storedKey).first)
            } else {
                database.memoryQueries.upsertMemory(
                    key = storedKey,
                    content = content,
                    createdAt = now,
                    updatedAt = now,
                    category = category.name,
                    hitCount = 1,
                    source = source,
                )
                MemoryEntry(key, content, now, now, category, 1, source, role)
            }
        }
    } else {
        mutex.withLock {
            val memories = loadMemoriesJson()
            val now = Clock.System.now().toEpochMilliseconds()
            val existing = memories.indexOfFirst { it.key == key && it.role == role }
            val entry = if (existing >= 0) {
                val updated = memories[existing].copy(content = content, updatedAt = now, category = category, source = source ?: memories[existing].source)
                memories[existing] = updated
                updated
            } else {
                val newEntry = MemoryEntry(key = key, content = content, createdAt = now, updatedAt = now, category = category, source = source, role = role)
                memories.add(newEntry)
                newEntry
            }
            saveMemoriesJson(memories)
            entry
        }
    }

    suspend fun updateContent(key: String, content: String, role: String? = null): MemoryEntry? = if (database != null) {
        withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
            val now = Clock.System.now().toEpochMilliseconds()
            val storedKey = RoleScopedMemoryKey.encode(key, role)
            val existing = database.memoryQueries.selectMemoryByKey(storedKey).executeAsOneOrNull() ?: return@withContext null
            database.memoryQueries.updateMemoryContent(content, now, storedKey)
            MemoryEntry(key, content, existing.createdAt, now, MemoryCategory.valueOf(existing.category), existing.hitCount.toInt(), existing.source, RoleScopedMemoryKey.decode(storedKey).first)
        }
    } else {
        mutex.withLock {
            val memories = loadMemoriesJson()
            val index = memories.indexOfFirst { it.key == key && it.role == role }
            if (index < 0) return@withLock null
            val now = Clock.System.now().toEpochMilliseconds()
            val updated = memories[index].copy(content = content, updatedAt = now)
            memories[index] = updated
            saveMemoriesJson(memories)
            updated
        }
    }

    suspend fun reinforceMemory(key: String, role: String? = null): MemoryEntry? = if (database != null) {
        withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
            val now = Clock.System.now().toEpochMilliseconds()
            val storedKey = RoleScopedMemoryKey.encode(key, role)
            val existing = database.memoryQueries.selectMemoryByKey(storedKey).executeAsOneOrNull() ?: return@withContext null
            database.memoryQueries.reinforceMemory(now, storedKey)
            MemoryEntry(key, existing.content, existing.createdAt, now, MemoryCategory.valueOf(existing.category), existing.hitCount.toInt() + 1, existing.source, RoleScopedMemoryKey.decode(storedKey).first)
        }
    } else {
        mutex.withLock {
            val memories = loadMemoriesJson()
            val index = memories.indexOfFirst { it.key == key && it.role == role }
            if (index < 0) return@withLock null
            val now = Clock.System.now().toEpochMilliseconds()
            val updated = memories[index].copy(hitCount = memories[index].hitCount + 1, updatedAt = now)
            memories[index] = updated
            saveMemoriesJson(memories)
            updated
        }
    }

    suspend fun getPromotionCandidates(minHits: Int = 5): List<MemoryEntry> = if (database != null) {
        withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
            database.memoryQueries.selectPromotionCandidates(minHits.toLong()).executeAsList().map {
                toEntry(it.key, it.content, it.createdAt, it.updatedAt, it.category, it.hitCount.toInt(), it.source)
            }
        }
    } else {
        loadMemoriesJson().filter { it.hitCount >= minHits }
    }

    /**
     * Забывает знание целиком — во всех ролях сразу.
     *
     * Явно [role], удаляющий только запись одной личности, был бы ловушкой: «забудь,
     * что я курю» стирало бы только Подругу, а Коллега продолжала бы помнить то же
     * самое. Если действительно нужно стереть у одной роли, а не у всех, это должен
     * быть отдельный явный вызов, а не побочный эффект `forget`.
     */
    suspend fun forget(key: String): Boolean = if (database != null) {
        withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
            val removed = database.memoryQueries.selectAllMemories().executeAsList()
                .map { it.key }
                .filter { stored -> RoleScopedMemoryKey.decode(stored).second == key }
            removed.forEach { database.memoryQueries.deleteMemory(it) }
            removed.isNotEmpty()
        }
    } else {
        mutex.withLock {
            val memories = loadMemoriesJson()
            val removed = memories.removeAll { it.key == key }
            if (removed) saveMemoriesJson(memories)
            removed
        }
    }

    fun getAllMemories(): List<MemoryEntry> = if (database != null) {
        database.memoryQueries.selectAllMemories().executeAsList().map {
            toEntry(it.key, it.content, it.createdAt, it.updatedAt, it.category, it.hitCount.toInt(), it.source)
        }
    } else {
        loadMemoriesJson()
    }

    /**
     * Что знает одна личность: общее ядро плюс её собственные записи.
     *
     * Ровно этот набор попадает в системный промпт. Своё знание другой роли сюда
     * намеренно не попадает — именно поэтому в промпте есть правило «скажи прямо,
     * что знаешь это из другого режима»: личность физически не видит чужих
     * записей, но видит, что общее ядро получило факт, а значит, спросить уместно.
     */
    fun getMemoriesForRole(role: String?): List<MemoryEntry> = getAllMemories().filter { it.role == null || it.role == role }
}

/** Строка из базы в [MemoryEntry]: роль разбирается из ключа. */
private fun toEntry(
    storedKey: String,
    content: String,
    createdAt: Long,
    updatedAt: Long,
    category: String,
    hitCount: Int,
    source: String?,
): MemoryEntry {
    val (role, key) = RoleScopedMemoryKey.decode(storedKey)
    return MemoryEntry(
        key = key,
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
        category = MemoryCategory.valueOf(category),
        hitCount = hitCount,
        source = source,
        role = role,
    )
}
