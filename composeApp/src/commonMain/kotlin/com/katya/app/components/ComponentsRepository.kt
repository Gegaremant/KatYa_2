package com.katya.app.components

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.katya.app.db.DownloadableComponent
import com.katya.app.db.KatyaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Реестр скачиваемых компонентов. Таблица живёт в SQLite (conversations.db),
 * так что ссылки можно менять на лету — в коде остаются только seed-значения.
 * Один репозиторий разделяется между UI (вкладка «Сервер»), фоновым загрузчиком
 * и песочницей.
 */
class ComponentsRepository(
    private val database: KatyaDatabase?,
    scope: CoroutineScope,
) {
    private val queries get() = database?.componentsQueries

    /** Живой список компонентов (порядок по имени) — основа UI-списка. */
    val components: StateFlow<List<DownloadableComponent>> =
        (queries?.selectAllComponents()?.asFlow()?.mapToList(Dispatchers.Default) ?: emptyFlow())
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Мягкий маркер того, что seed уже выполнен (ид не совпадает с реальным компонентом). */
    fun seedIfNeeded() {
        val q = queries ?: return
        if (q.selectComponentById(SEED_MARKER_ID).executeAsOneOrNull() != null) return
        val now = now()
        ComponentDefaults.seeds().forEach { seed ->
            q.upsertComponent(
                id = seed.id,
                name = seed.name,
                componentType = seed.componentType.dbValue,
                url = seed.url,
                version = seed.version,
                abi = seed.abi,
                status = "missing",
                destinationPath = null,
                downloadedBytes = 0L,
                totalBytes = 0L,
                updatedAt = now,
            )
        }
        q.upsertComponent(
            id = SEED_MARKER_ID,
            name = "seed-marker",
            componentType = "seed",
            url = "",
            version = "",
            abi = null,
            status = "installed",
            destinationPath = null,
            downloadedBytes = 0L,
            totalBytes = 0L,
            updatedAt = now,
        )
    }

    fun component(id: String): DownloadableComponent? = queries?.selectComponentById(id)?.executeAsOneOrNull()

    fun updateUrl(id: String, url: String) {
        queries?.updateComponentUrl(url, now(), id)
    }

    fun updateProgress(id: String, status: String, downloadedBytes: Long, totalBytes: Long) {
        queries?.updateComponentProgress(status, downloadedBytes, totalBytes, now(), id)
    }

    fun markInstalled(id: String) {
        queries?.markComponentInstalled(now(), id)
    }

    fun markFailed(id: String) {
        queries?.markComponentFailed(now(), id)
    }

    fun isInstalled(id: String): Boolean = queries?.selectComponentById(id)?.executeAsOneOrNull()?.status == "installed"

    /** Id компонента rootfs для текущей ABI. */
    fun currentRootfsId(): String = "debian_${currentAbi()}"

    fun currentRootfsUrl(): String? = queries?.selectComponentById(currentRootfsId())?.executeAsOneOrNull()?.url

    /** Компоненты, которые нужны текущему устройству и ещё не установлены. */
    fun missingForCurrentDevice(): List<DownloadableComponent> {
        val q = queries ?: return emptyList()
        val abi = currentAbi()
        return q.selectAllComponents().executeAsList().filter { c ->
            c.id != SEED_MARKER_ID &&
                (c.abi == null || c.abi == abi) &&
                c.status != "installed" &&
                c.componentType != ComponentType.MODEL.dbValue
        }
    }

    private fun now(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

    companion object {
        const val SEED_MARKER_ID = "__seed_v1"
    }
}
