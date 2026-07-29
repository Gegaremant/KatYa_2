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

    suspend fun store(
        key: String,
        content: String,
        category: MemoryCategory = MemoryCategory.GENERAL,
        source: String? = null,
    ): MemoryEntry = if (database != null) {
        withContext(Dispatchers.IO) {
            val now = Clock.System.now().toEpochMilliseconds()
            val existing = database.memoryQueries.selectMemoryByKey(key).executeAsOneOrNull()
            if (existing != null) {
                database.memoryQueries.upsertMemory(
                    key = key,
                    content = content,
                    createdAt = existing.createdAt,
                    updatedAt = now,
                    category = category.name,
                    hitCount = existing.hitCount,
                    source = source ?: existing.source,
                )
                MemoryEntry(key, content, existing.createdAt, now, category, existing.hitCount.toInt(), source ?: existing.source)
            } else {
                database.memoryQueries.upsertMemory(
                    key = key,
                    content = content,
                    createdAt = now,
                    updatedAt = now,
                    category = category.name,
                    hitCount = 1,
                    source = source,
                )
                MemoryEntry(key, content, now, now, category, 1, source)
            }
        }
    } else {
        mutex.withLock {
            val memories = loadMemoriesJson()
            val now = Clock.System.now().toEpochMilliseconds()
            val existing = memories.indexOfFirst { it.key == key }
            val entry = if (existing >= 0) {
                val updated = memories[existing].copy(content = content, updatedAt = now, category = category, source = source ?: memories[existing].source)
                memories[existing] = updated
                updated
            } else {
                val newEntry = MemoryEntry(key = key, content = content, createdAt = now, updatedAt = now, category = category, source = source)
                memories.add(newEntry)
                newEntry
            }
            saveMemoriesJson(memories)
            entry
        }
    }

    suspend fun updateContent(key: String, content: String): MemoryEntry? = if (database != null) {
        withContext(Dispatchers.IO) {
            val now = Clock.System.now().toEpochMilliseconds()
            val existing = database.memoryQueries.selectMemoryByKey(key).executeAsOneOrNull() ?: return@withContext null
            database.memoryQueries.updateMemoryContent(content, now, key)
            MemoryEntry(key, content, existing.createdAt, now, MemoryCategory.valueOf(existing.category), existing.hitCount.toInt(), existing.source)
        }
    } else {
        mutex.withLock {
            val memories = loadMemoriesJson()
            val index = memories.indexOfFirst { it.key == key }
            if (index < 0) return@withLock null
            val now = Clock.System.now().toEpochMilliseconds()
            val updated = memories[index].copy(content = content, updatedAt = now)
            memories[index] = updated
            saveMemoriesJson(memories)
            updated
        }
    }

    suspend fun reinforceMemory(key: String): MemoryEntry? = if (database != null) {
        withContext(Dispatchers.IO) {
            val now = Clock.System.now().toEpochMilliseconds()
            val existing = database.memoryQueries.selectMemoryByKey(key).executeAsOneOrNull() ?: return@withContext null
            database.memoryQueries.reinforceMemory(now, key)
            MemoryEntry(key, existing.content, existing.createdAt, now, MemoryCategory.valueOf(existing.category), existing.hitCount.toInt() + 1, existing.source)
        }
    } else {
        mutex.withLock {
            val memories = loadMemoriesJson()
            val index = memories.indexOfFirst { it.key == key }
            if (index < 0) return@withLock null
            val now = Clock.System.now().toEpochMilliseconds()
            val updated = memories[index].copy(hitCount = memories[index].hitCount + 1, updatedAt = now)
            memories[index] = updated
            saveMemoriesJson(memories)
            updated
        }
    }

    suspend fun getPromotionCandidates(minHits: Int = 5): List<MemoryEntry> = if (database != null) {
        withContext(Dispatchers.IO) {
            database.memoryQueries.selectPromotionCandidates(minHits.toLong()).executeAsList().map {
                MemoryEntry(it.key, it.content, it.createdAt, it.updatedAt, MemoryCategory.valueOf(it.category), it.hitCount.toInt(), it.source)
            }
        }
    } else {
        loadMemoriesJson().filter { it.hitCount >= minHits }
    }

    suspend fun forget(key: String): Boolean = if (database != null) {
        withContext(Dispatchers.IO) {
            val existing = database.memoryQueries.selectMemoryByKey(key).executeAsOneOrNull()
            if (existing != null) {
                database.memoryQueries.deleteMemory(key)
                true
            } else {
                false
            }
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
            MemoryEntry(it.key, it.content, it.createdAt, it.updatedAt, MemoryCategory.valueOf(it.category), it.hitCount.toInt(), it.source)
        }
    } else {
        loadMemoriesJson()
    }
}
