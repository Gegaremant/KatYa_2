import re

def update_file(path, replacements):
    with open(path, 'r') as f:
        content = f.read()
    
    for (old, new) in replacements:
        if old in content:
            content = content.replace(old, new)
        else:
            print(f"Warning: Could not find '{old[:30]}...' in {path}")
            
    with open(path, 'w') as f:
        f.write(content)

# Update AppSettings.kt
app_settings_changes = [
    (
        'fun isShowAndVoiceThoughtsEnabled(): Boolean = settings.getBoolean("show_and_voice_thoughts_enabled", DEFAULTS.SHOW_AND_VOICE_THOUGHTS_ENABLED)',
        '''fun isShowAndVoiceThoughtsEnabled(): Boolean = settings.getBoolean("show_and_voice_thoughts_enabled", DEFAULTS.SHOW_AND_VOICE_THOUGHTS_ENABLED)

    fun isShowConnectionStatusEnabled(): Boolean = settings.getBoolean("show_connection_status_enabled", DEFAULTS.SHOW_CONNECTION_STATUS_ENABLED)
    fun setShowConnectionStatusEnabled(enabled: Boolean) = settings.putBoolean("show_connection_status_enabled", enabled)'''
    ),
    (
        'val SHOW_AND_VOICE_THOUGHTS_ENABLED = false',
        'val SHOW_AND_VOICE_THOUGHTS_ENABLED = false\n        val SHOW_CONNECTION_STATUS_ENABLED = true'
    )
]
update_file('composeApp/src/commonMain/kotlin/com/katya/app/data/AppSettings.kt', app_settings_changes)

# Update SettingsUiState.kt
ui_state_changes = [
    (
        'val isVoskReady: Boolean = false,',
        'val isVoskReady: Boolean = false,\n    val showConnectionStatus: Boolean = true,'
    )
]
update_file('composeApp/src/commonMain/kotlin/com/katya/app/ui/settings/SettingsUiState.kt', ui_state_changes)

# Update SettingsActions.kt
actions_changes = [
    (
        'val onToggleWakeWordSound: (Boolean) -> Unit,',
        'val onToggleWakeWordSound: (Boolean) -> Unit,\n    val onToggleConnectionStatus: (Boolean) -> Unit,'
    ),
    (
        'onToggleWakeWordSound = {},',
        'onToggleWakeWordSound = {},\n            onToggleConnectionStatus = {},'
    )
]
update_file('composeApp/src/commonMain/kotlin/com/katya/app/ui/settings/SettingsActions.kt', actions_changes)

# Update SettingsViewModel.kt
viewmodel_changes = [
    (
        'isVoskReady = localInferenceEngine?.voskEngine?.isModelLoaded?.value == true,',
        'isVoskReady = localInferenceEngine?.voskEngine?.isModelLoaded?.value == true,\n        showConnectionStatus = appSettings.isShowConnectionStatusEnabled(),'
    ),
    (
        'onToggleWakeWordSound = ::onToggleWakeWordSound,',
        'onToggleWakeWordSound = ::onToggleWakeWordSound,\n        onToggleConnectionStatus = ::onToggleConnectionStatus,'
    ),
    (
        'private fun onToggleWakeWordSound(enabled: Boolean) {',
        '''private fun onToggleConnectionStatus(enabled: Boolean) {
        appSettings.setShowConnectionStatusEnabled(enabled)
        _state.update { it.copy(showConnectionStatus = enabled) }
    }

    private fun onToggleWakeWordSound(enabled: Boolean) {'''
    )
]
update_file('composeApp/src/commonMain/kotlin/com/katya/app/ui/settings/SettingsViewModel.kt', viewmodel_changes)

print("Done with boilerplate fields!")
