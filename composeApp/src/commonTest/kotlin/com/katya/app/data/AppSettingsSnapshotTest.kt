package com.katya.app.data

import com.russhwolf.settings.MapSettings
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The import/export semantics in [AppSettingsSnapshot] decide what a restore
 * actually does to a phone, so they are pinned here rather than only described
 * in comments.
 */
class AppSettingsSnapshotTest {

    private fun settingsWith(vararg entries: Pair<String, Any>): AppSettings = AppSettings(MapSettings(*entries))

    /**
     * The shape a real backup has on disk: a document with the flat map nested
     * under `app_settings`, not the bare map.
     */
    private fun backupOf(store: AppSettings): JsonObject = store.exportSnapshotDocument()

    // --- Defaults are derived, not hand-listed --------------------------------

    @Test
    fun `a fresh store exports nothing`() {
        val exported = settingsWith().exportSnapshot()
        assertEquals(emptyMap(), exported.toMap(), "A default install should produce an empty non-defaults map")
    }

    @Test
    fun `only values differing from the default are exported`() {
        val store = settingsWith(
            AppSettingsKeys.KEY_UI_SCALE to 1.4f,
            AppSettingsKeys.KEY_GOD_MODE_ENABLED to true,
        )
        val exported = store.exportSnapshot()

        assertEquals(1.4f, exported[AppSettingsKeys.KEY_UI_SCALE]?.jsonPrimitive?.float)
        assertEquals(true, exported[AppSettingsKeys.KEY_GOD_MODE_ENABLED]?.jsonPrimitive?.boolean)
        assertNull(exported[AppSettingsKeys.KEY_THEME_MODE], "A default value has no business in a backup")
    }

    // --- Round trip ------------------------------------------------------------

    @Test
    fun `merge applies every non-default the backup carries`() {
        val target = settingsWith(AppSettingsKeys.KEY_UI_SCALE to 0.9f)
        val backup = settingsWith(
            AppSettingsKeys.KEY_UI_SCALE to 1.4f,
            AppSettingsKeys.KEY_GOD_MODE_ENABLED to true,
        )

        target.applySnapshot(backupOf(backup), ImportSection.entries.toSet(), ImportMode.Merge)

        assertEquals(1.4f, target.getUiScale())
        assertTrue(target.isGodModeEnabled())
    }

    @Test
    fun `merge leaves untouched settings alone`() {
        val target = settingsWith(
            AppSettingsKeys.KEY_UI_SCALE to 0.9f,
            AppSettingsKeys.KEY_WAKE_WORD to "кот",
        )
        val backup = settingsWith(AppSettingsKeys.KEY_GOD_MODE_ENABLED to true)

        target.applySnapshot(backupOf(backup), ImportSection.entries.toSet(), ImportMode.Merge)

        assertEquals(0.9f, target.getUiScale(), "Дополнить must not reset what the file says nothing about")
        assertEquals("кот", target.getWakeWord())
    }

    @Test
    fun `replace resets settings the backup says nothing about`() {
        val target = settingsWith(
            AppSettingsKeys.KEY_UI_SCALE to 0.9f,
            AppSettingsKeys.KEY_WAKE_WORD to "кот",
        )
        // The backup carries one setting. Everything else it is silent about must go
        // back to the factory default — that is what "Заменить" means.
        val backup = settingsWith(AppSettingsKeys.KEY_GOD_MODE_ENABLED to true)

        target.applySnapshot(backupOf(backup), ImportSection.entries.toSet(), ImportMode.Replace)

        assertEquals(1.0f, target.getUiScale(), "Заменить means the backup is the whole configuration")
        // Back to the factory default the getter itself supplies — not to "".
        assertEquals("привет катя", target.getWakeWord())
        assertTrue(target.isGodModeEnabled())
    }

    @Test
    fun `a default value is never written on merge`() {
        val target = settingsWith(AppSettingsKeys.KEY_UI_SCALE to 1.4f)
        // Hand-built backup carrying the factory default: a no-op under Дополнить.
        val backup = JsonObject(
            mapOf(AppSettingsKeys.KEY_UI_SCALE to JsonPrimitive(1.0f)),
        )

        target.applySnapshot(backup, ImportSection.entries.toSet(), ImportMode.Merge)

        assertEquals(1.4f, target.getUiScale(), "A default in the file means 'nothing to add'")
    }

    // --- Sections -------------------------------------------------------------

    @Test
    fun `an unselected section is left alone in both modes`() {
        val target = settingsWith(
            AppSettingsKeys.KEY_UI_SCALE to 0.9f,
            AppSettingsKeys.KEY_WAKE_WORD to "кот",
        )
        val backup = settingsWith(
            AppSettingsKeys.KEY_UI_SCALE to 1.4f,
            AppSettingsKeys.KEY_WAKE_WORD to "пёс",
            AppSettingsKeys.KEY_GOD_MODE_ENABLED to true,
        )

        // Only SERVICES is picked, and nothing in the backup belongs to it, so a
        // Replace must not reach across and reset the settings the user has here.
        target.applySnapshot(backupOf(backup), setOf(ImportSection.SERVICES), ImportMode.Replace)

        assertEquals(0.9f, target.getUiScale())
        assertEquals("кот", target.getWakeWord())
        assertFalse(target.isGodModeEnabled())
    }

    @Test
    fun `a setting is filed under its own section, not under tools`() {
        val target = settingsWith()
        val backup = settingsWith(AppSettingsKeys.KEY_UI_SCALE to 1.4f)

        val preview = target.previewSnapshot(backupOf(backup), ImportSection.entries.toSet(), ImportMode.Merge)

        assertEquals(
            setOf(ImportSection.SETTINGS),
            preview.diff.map { it.section }.toSet(),
            "A plain setting must not also show up as a tool toggle",
        )
    }

    // --- Secrets --------------------------------------------------------------

    @Test
    fun `secrets are masked in the diff but still imported`() {
        val target = settingsWith()
        val backup = settingsWith(AppSettingsKeys.KEY_SERVER_PASSWORD to "top-secret-password")

        val preview = target.previewSnapshot(backupOf(backup), ImportSection.entries.toSet(), ImportMode.Merge)
        val row = assertNotNull(preview.diff.firstOrNull { it.key == AppSettingsKeys.KEY_SERVER_PASSWORD })

        assertEquals("••••••••", row.incoming, "The review screen must never print a password")
        assertFalse(row.incoming.contains("top-secret"))

        target.applySnapshot(backupOf(backup), ImportSection.entries.toSet(), ImportMode.Merge)
        assertEquals("top-secret-password", target.getServerPassword(), "Masking is for display only")
    }

    // --- The diff tells the truth --------------------------------------------

    @Test
    fun `the diff reports no change when merge would write nothing`() {
        val target = settingsWith()
        val backup = settingsWith()

        val preview = target.previewSnapshot(backupOf(backup), ImportSection.entries.toSet(), ImportMode.Merge)

        assertTrue(preview.diff.isEmpty(), "Two default installs differ in nothing")
    }

    @Test
    fun `the diff counts only what replace would actually change`() {
        val target = settingsWith(
            AppSettingsKeys.KEY_UI_SCALE to 1.4f,
            AppSettingsKeys.KEY_WAKE_WORD to "кот",
        )
        val backup = settingsWith(AppSettingsKeys.KEY_UI_SCALE to 1.4f)

        val preview = target.previewSnapshot(backupOf(backup), ImportSection.entries.toSet(), ImportMode.Replace)
        val scaleRow = preview.diff.firstOrNull { it.key == AppSettingsKeys.KEY_UI_SCALE }

        assertNotNull(scaleRow, "The backup does carry the scale, so the row is shown")
        assertFalse(scaleRow.changed, "The scale already matches — the row is there, but nothing to do")
    }

    @Test
    fun `preview detects a version-2 snapshot`() {
        val store = settingsWith()
        val document = store.exportSnapshotDocument()

        val preview = store.previewSnapshot(document, ImportSection.entries.toSet(), ImportMode.Merge)
        assertTrue(preview.hasSnapshot)
        assertEquals(2, document["version"]?.jsonPrimitive?.int)
    }

    @Test
    fun `per-instance secrets travel per instance`() {
        val store = settingsWith()
        // An instance is only part of a backup once it exists in the services list —
        // a stray key with no instance behind it is not a configuration.
        store.setConfiguredServiceInstances(
            listOf(
                ServiceInstance(instanceId = "legacy-1", serviceId = Service.Anthropic.id),
                ServiceInstance(instanceId = "free-2", serviceId = Service.OpenAICompatible.id),
            ),
        )
        store.setInstanceApiKey("legacy-1", "key-one")
        store.setInstanceApiKey("free-2", "key-two")

        val instances = store.exportSnapshotInstances()

        assertEquals("key-one", instances["legacy-1"]?.jsonObject?.get("api_key")?.jsonPrimitive?.content)
        assertEquals("key-two", instances["free-2"]?.jsonObject?.get("api_key")?.jsonPrimitive?.content)
    }

    @Test
    fun `an instance key with no instance behind it is not exported`() {
        val store = settingsWith()
        store.setInstanceApiKey("ghost", "key-ghost")

        assertNull(store.exportSnapshotInstances()["ghost"])
    }
}
