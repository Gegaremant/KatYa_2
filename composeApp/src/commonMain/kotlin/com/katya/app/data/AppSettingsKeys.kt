package com.katya.app.data

/**
 * Central registry of all AppSettings keys to avoid circular dependencies.
 * All constants are public and can be used from any file.
 */
object AppSettingsKeys {
    const val KEY_CURRENT_SERVICE_ID = "current_service_id"
    const val KEY_APP_OPENS = "app_opens"

    const val KEY_CONVERSATIONS = "conversations_json"
    const val KEY_CURRENT_CONVERSATION_ID = "current_conversation_id"
    const val KEY_CURRENT_INTERACTIVE_MODE = "current_interactive_mode"
    const val KEY_CURRENT_CONVERSATION_MIGRATED = "current_conversation_migrated"
    const val KEY_ENCRYPTION_KEY = "encryption_key"
    const val KEY_MIGRATION_COMPLETE = "migration_complete_v1"
    const val KEY_TOOL_PREFIX = "tool_enabled_"
    const val KEY_SOUL = "soul_text"
    const val KEY_MEMORY_ENABLED = "memory_enabled"
    const val KEY_MEMORY_INSTRUCTIONS = "memory_instructions"
    const val KEY_AGENT_MEMORIES = "agent_memories"
    const val KEY_SCHEDULED_TASKS = "scheduled_tasks"
    const val KEY_SCHEDULING_ENABLED = "scheduling_enabled"
    const val KEY_DYNAMIC_UI_ENABLED = "dynamic_ui_enabled"
    const val KEY_OLED_MODE_ENABLED = "oled_mode_enabled"
    const val KEY_THEME_MODE = "theme_mode"
    const val KEY_DAEMON_ENABLED = "daemon_enabled"
    const val KEY_HAS_REQUESTED_ROOT = "has_requested_root"
    const val KEY_HEARTBEAT_CONFIG = "heartbeat_config"
    const val KEY_HEARTBEAT_PROMPT = "heartbeat_prompt"
    const val KEY_HEARTBEAT_LOG = "heartbeat_log"

    const val KEY_EMAIL_ENABLED = "email_enabled"
    const val KEY_EMAIL_ACCOUNTS = "email_accounts"
    const val KEY_EMAIL_PASSWORD_PREFIX = "email_password_"
    const val KEY_EMAIL_SYNC_PREFIX = "email_sync_"
    const val KEY_EMAIL_POLL_INTERVAL = "email_poll_interval"
    const val KEY_EMAIL_PENDING = "email_pending"

    const val KEY_SMS_ENABLED = "sms_enabled"
    const val KEY_SMS_POLL_INTERVAL = "sms_poll_interval"
    const val KEY_SMS_PENDING = "sms_pending"
    const val KEY_SMS_SYNC_STATE = "sms_sync_state"
    const val KEY_SMS_SEND_ENABLED = "sms_send_enabled"
    const val KEY_SMS_DRAFTS = "sms_drafts"

    const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
    const val KEY_NOTIFICATIONS_PENDING = "notifications_pending"
    const val KEY_NOTIFICATIONS_STORE = "notifications_store"
    const val KEY_NOTIFICATIONS_SYNC_STATE = "notifications_sync_state"
    const val KEY_CONFIGURED_SERVICES = "configured_services"
    const val KEY_FREE_FALLBACK_ENABLED = "freeFallbackEnabled"
    const val KEY_QUICK_ACTIONS = "quickActions"
    const val KEY_FREE_MODE = "free_mode"
    const val KEY_FREE_SERVICE_PRIMARY = "free_service_primary"
    const val KEY_SERVICES_MIGRATION_COMPLETE = "services_migration_complete_v1"
    const val KEY_UI_SCALE = "ui_scale"
    const val KEY_MCP_SERVERS = "mcp_servers"
    const val KEY_INSTANCE_MIGRATION_COMPLETE = "instance_migration_complete_v1"
    const val KEY_BASE_URL_V1_MIGRATION_COMPLETE = "base_url_v1_migration_complete"

    const val KEY_SPLINTERLANDS_ENABLED = "splinterlands_enabled"
    const val KEY_SPLINTERLANDS_ACCOUNT = "splinterlands_account"
    const val KEY_SPLINTERLANDS_POSTING_KEY = "splinterlands_posting_key"
    const val KEY_SPLINTERLANDS_BATTLE_LOG = "splinterlands_battle_log"
    const val KEY_SPLINTERLANDS_INSTANCE_ID = "splinterlands_instance_id"
    const val KEY_SPLINTERLANDS_INSTANCE_IDS = "splinterlands_instance_ids"

    const val KEY_MODEL_CONTEXT_PREFIX = "model_context_"

    const val KEY_SANDBOX_ENABLED = "sandbox_enabled"
    const val KEY_GOD_MODE_ENABLED = "god_mode_enabled"
    const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    const val KEY_AGENT_VISIBILITY_ENABLED = "agent_visibility_enabled"
    const val KEY_VLESS_ENABLED = "vless_enabled"
    const val KEY_VLESS_URI = "vless_uri"

    const val KEY_SERVER_IP = "server_ip"
    const val KEY_SERVER_PORT = "server_port"
    const val KEY_SERVER_USER = "server_user"
    const val KEY_SERVER_PASSWORD = "server_password"
    const val KEY_TUNNEL_PERSISTENT_RECONNECT = "tunnel_persistent_reconnect"

    const val KEY_LOGGING_ENABLED = "logging_enabled"
    const val KEY_SKIP_COMPONENTS_PROMPT = "skip_components_prompt"
    const val DEFAULT_MEMORY_INSTRUCTIONS = ""

    const val KEY_WAKE_WORD = "wake_word"
    const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
    const val KEY_WAKE_WORD_SOUND = "wake_word_sound_enabled"
    const val KEY_VOICE_RESPONSE_ENABLED = "voice_response_enabled"
    const val KEY_VOICE_RECOGNITION_ENABLED = "voice_recognition_enabled"
    const val KEY_WATCH_INTEGRATION_ENABLED = "watch_integration_enabled"
    const val KEY_WAKE_WORD_MODEL_LANG = "wake_word_model_lang"
    const val KEY_WAKE_WORD_TRIGGER = "wake_word_trigger"
    const val KEY_WAKE_WORD_VIBRATION = "wake_word_vibration"

    const val KEY_MONITOR_OVERLAY_MODE = "monitor_overlay_mode"
}
