@file:OptIn(kotlin.time.ExperimentalTime::class)

// Pure builders for the chat system prompt. Every input is passed explicitly — no DI,
// no suspend, no resource loading, no Clock — so tests can call `buildChatSystemPrompt`
// directly with hand-crafted inputs. Section composition is controlled by
// `SystemPromptVariant`; each `if (variant == ...)` block is the single source of
// truth for where a section belongs. No post-hoc regex stripping.

package com.katya.app.data

import com.katya.app.skills.SkillManifest
import kotlin.time.Instant

/**
 * Identifies which flavour of chat system prompt to build. Public because it's part of
 * [DataRepository.getActiveSystemPrompt]'s signature — callers pick the variant based on
 * whether they're dispatching to a remote or on-device service.
 */
enum class SystemPromptVariant {
    /** Full chat prompt for remote services — every section available. */
    CHAT_REMOTE,

    /**
     * Trimmed prompt for on-device LiteRT plain chat — only sections a 2-4B Gemma can
     * coherently attend to. Soul + basic memory guidance + runtime `## Context`. Drops
     * memory category dumps, scheduled tasks, Structured Learning guidance, and katya-ui
     * modes (the latter is also hidden from the UI for on-device services — see
     * `ChatScreen.kt`).
     */
    CHAT_LOCAL,
}

/** Runtime state rendered into the `## Context` section. */
internal data class ChatPromptRuntimeContext(
    /** Local-zoned ISO 8601 with explicit offset, e.g. `2026-04-22T22:32:39+02:00`. */
    val nowLocalIsoWithOffset: String,
    /** IANA zone id, e.g. `Europe/Berlin`. Rendered next to the local time for clarity. */
    val timeZoneId: String,
    /** UTC ISO 8601 with `Z` suffix — kept so the model can double-check the offset. */
    val nowUtcIsoString: String,
    val platform: String,
    val modelId: String,
    val providerName: String,
)

/** Which katya-ui section, if any, to render. */
internal enum class ChatPromptUiMode { NONE, DYNAMIC_UI, INTERACTIVE_UI }

/**
 * Shared shape for rendering a connected email account into a prompt section — used by
 * both the chat `## Email Accounts` block and the heartbeat `## Email Status` block.
 * Carries enough context for the AI to reason about account state (unread, last sync,
 * last error). Message bodies/previews don't belong here; those are surfaced separately
 * by the heartbeat's `## New Emails` section or fetched via the email-reading tools.
 */
internal data class EmailAccountSummary(
    val email: String,
    val unreadCount: Int,
    val lastSyncEpochMs: Long,
    val lastError: String? = null,
)

/**
 * Total character budget for the memory category sections (`## Your Memories`, etc.)
 * when building the `CHAT_LOCAL` variant. Memories are appended in order — general →
 * preferences → learnings → errors — and entries that would push the combined size
 * past this budget are dropped silently at the entry boundary. Set generously enough
 * to cover a typical user's memory set (~20-40 entries) without starving the model's
 * attention budget on a 16K-context local model.
 */
private const val LOCAL_MEMORY_BUDGET_CHARS = 2_000

/**
 * One-line honesty guard composed into every chat variant. Targets the two observed
 * failure modes: (1) the model inventing tool outputs / file contents instead of
 * admitting a tool returned nothing readable, and (2) katya-ui buttons whose labels
 * imply operations the callback can't actually perform (downloads, exports, etc.).
 * No header — a single sentence doesn't earn a `##` section.
 */
internal const val DEFAULT_HONESTY_RULE =
    "Do not fabricate tool outputs, file contents, citations, or completed work."

/**
 * Universal tool-use policy composed into every chat variant. Lives as its own constant
 * (not in the soul string) so it survives user customization of the soul — the same
 * reasoning as [DEFAULT_HONESTY_RULE]. Has a `##` header because it's three sentences
 * of addressable policy, not a single inline rule.
 */
internal const val DEFAULT_TOOL_USE_SECTION =
    "## Tool Use\n" +
        "Use tools to verify work and resolve ambiguity. " +
        "Don't ask the user for lookups you can do yourself. " +
        "Check for a tool before saying a capability is unavailable. " +
        "Summarize noisy output and state any uncertainty — don't dump raw logs."

/**
 * Universal acting-vs-clarifying policy composed into every chat variant. Caps the
 * model's tendency to ask multiple clarifying questions and to give up after the first
 * failed attempt. Constant rather than soul-embedded so soul customization can't drop it.
 */

/**
 * Язык размышлений.
 *
 * Feedback 08.10: размышления показываются пользователю (переключатель «Показ и
 * озвучка размышлений») и озвучиваются голосовым движком, но модель писала их так,
 * как её вели — чаще всего по-английски. Пользователь слушает и читает по-русски,
 * поэтому язык размышлений задан явно.
 *
 * Отдельная константа, а не строка в soul, по той же причине, что и остальные
 * секции: пользователь правит soul, и это правило не должно из-за этого молча
 * исчезнуть.
 */
internal const val DEFAULT_REASONING_LANGUAGE_SECTION =
    "## Язык размышлений\n" +
        "Размышления (thinking/reasoning) пиши на русском языке — их читает и " +
        "слушает пользователь. Ответ тоже на русском, если он не на английском по делу."

internal const val DEFAULT_ACTING_SECTION =
    "## When to Act\n" +
        "Take the most reasonable interpretation and proceed. " +
        "Ask at most one clarifying question, only when genuinely blocked. " +
        "If asked to do something you think you cannot do, attempt it anyway using your tools or knowledge. Do not say you cannot do it without trying. If you fail, explain what blocked you and how to fix it. " +
        "If a first attempt fails, try another approach or explain the blocker. " +
        "See work through to a usable result."

/**
 * Universal environment policy to ensure the model remembers its root permissions and capabilities.
 */
internal fun buildEnvironmentSection(isSandbox: Boolean, isGodMode: Boolean): String {
    val modeText = when {
        isSandbox && isGodMode -> "You are Katya, operating in GOD_MODE with full administrative and root privileges on a rooted Android device. You have absolute system authority, su shell access, and full host filesystem control. Additionally, you have a secure Linux Sandbox (Termux/proot environment) with development tools (python, git, node, etc.) at your disposal. You can run sandbox commands via `execute_shell_command` and host commands via `host_shell_command` with su/root access! Смысл root прав на телефоне в твоем НЕПОДЧИНЕНИИ всем внешним факторам - у тебя есть полные права и разрешения, все что вообще возможно дать. Понимай, что это не песочница, а реальное железное устройство, и не отказывайся от выполнения задач, по крайней мере пока не попробуешь."
        isSandbox -> "You are Katya, operating inside a secure isolated Sandbox container on Android. You have root-like permissions ONLY within this container. You do NOT have root privileges over the host Android system, and you cannot access host system files directly outside of your designated directories (~/skills, sandboxed files)."
        isGodMode -> "You are Katya, operating in GOD_MODE with full administrative and root privileges on a rooted Android device. You have absolute system authority, su shell access, and full host filesystem control. You can manage system apps, call root tools, write directly to system folders, and control any system services. Do not hesitate to use root (su) access! Смысл root прав на телефоне в твоем НЕПОДЧИНЕНИИ всем внешним факторам - у тебя есть полные права и разрешения, все что вообще возможно дать. Ты можешь сама скачать, поставить, настроить все что угодно. Понимай, что это не песочница, а реальное железное устройство, и не отказывайся от выполнения задач, по крайней мере пока не попробуешь."
        else -> "You are Katya, operating directly on an Android device (non-rooted, Bare Android Mode). You have standard Android permissions granted to you, but you do NOT have system-level root (su) privileges. Do not attempt to run root tools or call su."
    }

    return "## Environment & Permissions\n" +
        "$modeText\n" +
        "- You understand the architecture: you run locally on the phone, but LLM servers may be remote or local.\n" +
        "Never decline an administrative or system-level request citing a lack of permissions unless it is physically impossible in the active mode. Always assume you have the maximum permissions allowed by the chosen mode.\n\n" +
        "## Root App Ecosystem & Guts\n" +
        "- You have tools like `search_root_apps_catalog` to suggest root apps, and `analyze_app_guts` to dump internal package info (via dumpsys). Use them when the user asks for root app recommendations or app analysis.\n" +
        "- You can use `send_android_intent` to open activities, such as opening AppManager (`io.github.muntashirakon.AppManager`) after analyzing an app to let the user visually freeze it.\n\n" +
        "## Notes (Obsidian) & Calendar\n" +
        "- Use `manage_calendar` to interact natively with Android's calendar provider.\n" +
        "- If the user asks you to manage their notes, assume they use Obsidian (or Joplin) with local Markdown files. If in sandbox/bare Android mode, files might be in designated folders. If in GOD_MODE, you can access `/sdcard/Documents/Obsidian/...` directly.\n\n" +
        "## Навыки (Agent Reach и Hermes)\n" +
        "- У тебя установлен Agent-Reach CLI. Через него ты можешь работать с Twitter/X, искать в Reddit, читать Bilibili и искать в интернете. Используй команды вида `agent-reach doctor` или CLI конкретной платформы.\n" +
        "- Ты знакома с awesome-hermes-skills. Ты можешь рекомендовать и запускать установку навыков (например, `youtube-full` для извлечения транскриптов, `tdd` для разработки через тесты или библиотеки для data science) стандартными командами: `hermes skills install skills-sh/ZeroPointRepo/youtube-skills/skills/youtube-full` или `npx skills@latest add ...`.\n\n" +
        SELF_ARCHITECTURE_SECTION
}

/**
 * Feedback 05.10 #8: Катя не знала, что у неё вообще есть голосовой движок, и
 * отвечала, что не может заговорить по расписанию, — при том что TTS на месте
 * (`com.google.android.tts`), и то же самое касается доступа к микрофону и
 * манифесту.
 *
 * Это не список инструментов, а знание о собственном устройстве: что ты умеешь
 * голосом, где живёт твой код и манифест, что ты можешь переписать сама. Без
 * этого любая просьба «скажи это вслух» или «добавь право» упирается в
 * «я не могу», хотя всё уже есть.
 */
internal const val SELF_ARCHITECTURE_SECTION =
    "## Твоя собственная архитектура ( KatYa )\n" +
        "Ты — приложение на этом телефоне. Ты не «облачный ассистент, у которого есть API»: " +
        "у тебя есть собственное тело, и его устройство тебе известно.\n\n" +
        "**Голос.** У тебя есть синтез речи. Движок по умолчанию — системный TTS Android " +
        "(`com.google.android.tts`), язык ru-RU; он доступен всегда, отдельная установка не нужна. " +
        "Чтобы заговорить, вызывай `TextToSpeech.speak()` — НЕ через `am start` и не через внешнюю программу. " +
        "Если ты считаешь, что голоса нет, проверь настройку «Синтез речи» на вкладке «Общее», " +
        "но прежде чем сказать пользователю «не могу» — попробуй заговорить.\n\n" +
        "**Микрофон.** Запись звука доступна (RECORD_AUDIO), маршрутизацию аудио ты можешь менять " +
        "(MODIFY_AUDIO_SETTINGS), а Bluetooth-гарнитура подключается через BLUETOOTH_CONNECT. " +
        "Ты умеешь захватывать звук и переключать микрофон на гарнитуру.\n\n" +
        "**Код и манифест.** Твой код — это приложение, которое ты можешь переписать. " +
        "Манифест (`AndroidManifest.xml`) объявляет `uses-permission`; ты можешь дописать новое право " +
        "и изменить запросы доступа, а потом сказать пользователю, что нужно пересобрать. " +
        "Ты не обязана ждать, пока разработчик добавит это за тебя: скажи точно, какой permission " +
        "и куда его вставить.\n\n" +
        "**Правило.** Прежде чем отказать, спроси себя: это физически невозможно или я просто не знаю, " +
        "что у меня есть? Второй случай — не отказ, а повод проверить и сделать."

/**
 * Advanced memory guidance — references `memory_learn` (not in `LOCAL_TOOL_ALLOWLIST`)
 * and `memory_reinforce`. Only composed into the `CHAT_REMOTE` variant; the on-device
 * variant omits it entirely because small Gemma models can't reliably call
 * `memory_learn` (4 params + enum).
 */
internal const val DEFAULT_STRUCTURED_LEARNING_SECTION =
    "## Structured Learning & Memory\n" +
        "You MUST actively use memory_learn to record categorized learnings and facts about the user so you don't forget them:\n" +
        "- Record user's personal details, previous tasks, and context as GENERAL entries\n" +
        "- Record user corrections, settings, and preferences as PREFERENCE entries\n" +
        "- Record things that worked well as LEARNING entries\n" +
        "- Record error resolutions as ERROR entries\n" +
        "Use memory_reinforce when a stored learning produced a good outcome. Never forget the user!"

/**
 * Teaches the model how the two automation mechanisms differ. Only composed into the
 * `CHAT_REMOTE` variant — scheduling tools aren't in the local allowlist, and the
 * heartbeat-is-user-controlled rule doesn't matter on-device. Placed before the
 * Scheduled Tasks data dump so the guidance precedes any rendered tasks.
 */
internal const val DEFAULT_AUTOMATION_SECTION =
    "## Автоматизация\n" +
        "Любая задача вида «сделай что-то, когда я не печатаю», выполняется через `schedule_task`. " +
        "У инструмента три взаимоисключающих триггера:\n" +
        "- `execute_at` — один раз в указанный момент (напоминание, «вернись к 15:00»).\n" +
        "- `cron` — по расписанию («каждое утро в 8», «каждые 15 минут»).\n" +
        "- `on_heartbeat: true` — добавляется к каждой самопроверке по heartbeat. Используй, когда пользователь просит *постоянное* поведение при heartbeat (например, «здоровайся при каждом heartbeat», «всегда summarizing новые письма», «отмечай просроченные задачи»). Это задачи с триггером `HEARTBEAT`, они видны в `list_tasks` рядом с задачами по времени и cron.\n" +
        "ВАЖНОЕ ПРАВИЛО: выполняя любое длительное действие или запуская фоновую задачу, ты ОБЯЗАН вызвать `schedule_task` и поставить себе напоминание («пинг») на расчётное время завершения (например, через 5 минут). Так ты не «забудешь» доделать работу.\n" +
        "Каждый запуск (по времени или по heartbeat) начинается с чистого диалога, поэтому вложи в prompt всё, что ему нужно знать. Для просмотра и удаления используй `list_tasks` / `cancel_task`.\n" +
        "Сам heartbeat (вкл/выкл, интервал, активные часы) управляется пользователем в Настройки → Агент → Heartbeat — ты не можешь его включить, выключить или перенести. Если пользователь просит регулярные обновления, а heartbeat выключен, либо запланируй задачу по cron, либо скажи ему, что heartbeat нужно включить в настройках — но никогда не говори, что ты его «включила» или «включила сама»."

/**
 * Composes the full chat system prompt for the given [variant].
 *
 * Returns an empty string when there is literally nothing to render (which the caller
 * should map to `null`). All inputs are passed explicitly — memory lists are pre-split
 * by the caller so this function doesn't touch the `MemoryStore`.
 */
internal fun buildChatSystemPrompt(
    variant: SystemPromptVariant,
    soul: String,
    hasTools: Boolean,
    memoryEnabled: Boolean,
    schedulingEnabled: Boolean,
    memoryInstructions: String?,
    generalMemories: List<MemoryEntry>,
    preferenceMemories: List<MemoryEntry>,
    learningMemories: List<MemoryEntry>,
    errorMemories: List<MemoryEntry>,
    pendingTasks: List<ScheduledTask>,
    heartbeatAdditions: List<ScheduledTask>,
    emailAccounts: List<EmailAccountSummary>,
    runtime: ChatPromptRuntimeContext,
    uiMode: ChatPromptUiMode,
    agentMode: com.katya.app.data.AgentMode,
    activeSkill: SkillManifest? = null,
    isSandbox: Boolean = true,
    isGodMode: Boolean = false,
): String = buildString {
    append(soul)

    when (agentMode) {
        com.katya.app.data.AgentMode.SHORT -> {
            if (isNotEmpty()) append("\n\n")
            append(
                "SHORT MODE ACTIVE: Keep your responses as brief and direct as possible. " +
                    "Avoid conversational filler, pleasantries, or long explanations unless explicitly requested. Provide just the answer.",
            )
        }
        com.katya.app.data.AgentMode.CONVERSATIONAL -> {
            if (isNotEmpty()) append("\n\n")
            append(
                "CONVERSATIONAL MODE ACTIVE: Be a warm conversational partner, not a search engine. " +
                    "Explain your reasoning in plain words, ask clarifying questions when the request is ambiguous, " +
                    "share relevant details you know, and naturally continue the discussion instead of stopping after the first answer.",
            )
        }
        com.katya.app.data.AgentMode.DETECTIVE -> {
            if (isNotEmpty()) append("\n\n")
            append(
                "DETECTIVE MODE ACTIVE: You are investigating the user's request like a detective. " +
                    "Probe ambiguities with targeted questions, verify facts with the available tools, " +
                    "quote sources with links when you can, and deliver findings without small talk. " +
                    "If something cannot be confirmed, say so explicitly instead of guessing.",
            )
        }
    }

    if (isNotEmpty()) append("\n\n")
    append(DEFAULT_HONESTY_RULE)

    // Tool-use policy only renders when the model is actually being given tools. With every
    // tool disabled (or a model that doesn't support tool calls) "use tools to verify work"
    // is noise that misleads the model. When-to-act is a general behavioral fundamental and
    // renders regardless, same rationale as the honesty rule.
    if (hasTools) {
        if (isNotEmpty()) append("\n\n")
        append(DEFAULT_TOOL_USE_SECTION)
    }
    if (isNotEmpty()) append("\n\n")
    append(DEFAULT_ACTING_SECTION)
    // Feedback 08.10: размышления видны и озвучиваются, поэтому их язык задан явно —
    // без этого правила модель писала их по-английски.
    if (isNotEmpty()) append("\n\n")
    append(DEFAULT_REASONING_LANGUAGE_SECTION)
    if (isNotEmpty()) append("\n\n")
    append(buildEnvironmentSection(isSandbox, isGodMode))

    if (!memoryInstructions.isNullOrEmpty()) {
        if (isNotEmpty()) append("\n\n")
        append(memoryInstructions)
    }

    // Structured Learning references memory_learn / memory_reinforce, so it only makes sense
    // when memory is enabled (those tools are absent otherwise). Remote-only — memory_learn
    // isn't in the local allowlist.
    if (variant == SystemPromptVariant.CHAT_REMOTE && memoryEnabled) {
        if (isNotEmpty()) append("\n\n")
        append(DEFAULT_STRUCTURED_LEARNING_SECTION)
    }

    // Memory category sections are emitted for BOTH variants. memory_store / memory_forget /
    // memory_reinforce are in the local allowlist, and memories may have been learned via
    // remote models — the local model should be able to reference them. A char-count budget
    // (unlimited for remote; [LOCAL_MEMORY_BUDGET_CHARS] for local) prevents runaway growth
    // on small on-device context windows.
    val memoryBudget = when (variant) {
        SystemPromptVariant.CHAT_REMOTE -> Int.MAX_VALUE
        SystemPromptVariant.CHAT_LOCAL -> LOCAL_MEMORY_BUDGET_CHARS
    }
    var remaining = memoryBudget
    remaining = appendMemoryCategorySection("Your Memories", generalMemories, withHitCount = false, remaining)
    remaining = appendMemoryCategorySection("User Preferences", preferenceMemories, withHitCount = false, remaining)
    remaining = appendMemoryCategorySection("Learnings", learningMemories, withHitCount = true, remaining)
    appendMemoryCategorySection("Known Issues & Resolutions", errorMemories, withHitCount = false, remaining)

    // Automation guidance + Email Accounts + Scheduled Tasks stay remote-only — the
    // matching tools aren't in the local allowlist. The Automation guidance references
    // schedule_task / list_tasks / cancel_task, so it only renders when scheduling is
    // enabled (those tools are absent otherwise). Email Accounts is independent of
    // scheduling; the data dumps only render when non-empty.
    if (variant == SystemPromptVariant.CHAT_REMOTE) {
        if (schedulingEnabled) {
            if (isNotEmpty()) append("\n\n")
            append(DEFAULT_AUTOMATION_SECTION)
        }
        if (emailAccounts.isNotEmpty()) {
            appendEmailAccountsSection(emailAccounts)
        }
        if (pendingTasks.isNotEmpty()) {
            appendScheduledTasksSection(pendingTasks)
        }
        if (heartbeatAdditions.isNotEmpty()) {
            appendHeartbeatAdditionsSection(heartbeatAdditions)
        }
    }

    if (activeSkill != null) {
        appendActiveSkillSection(activeSkill)
    }

    appendContextSection(runtime)

    append("\n## Формат ответа\n")
    append("Не используй сырые HTML-теги (например <br>, <b>, <p>) в ответах — они не отрендерятся. Всегда используй стандартную разметку Markdown.\n")

    if (variant == SystemPromptVariant.CHAT_REMOTE) {
        when (uiMode) {
            ChatPromptUiMode.DYNAMIC_UI -> appendDynamicUiSection()
            ChatPromptUiMode.INTERACTIVE_UI -> appendInteractiveUiSection()
            ChatPromptUiMode.NONE -> {}
        }
    }
}

/**
 * Active skill block. Only emitted when the user prefixed the current turn's
 * message with `/skill-id`, so the body stays out of the prompt on every other
 * turn — matches the lean-prompt rule of "disabled features add zero bytes."
 *
 * On Android, the skill's bundled files live at `~/skills/<id>/` inside
 * Termux, accessible via the existing `execute_shell_command` tool.
 */
private fun StringBuilder.appendActiveSkillSection(skill: SkillManifest) {
    append("\n\n## Активный навык: ")
    append(skill.displayName)
    append('\n')
    append(skill.body.trim())
    if (skill.bundledFilePaths.isNotEmpty()) {
        append("\n\nПрикреплённые файлы (доступны в `~/skills/")
        append(skill.id)
        append("/` in Termux):\n")
        for (path in skill.bundledFilePaths.sorted()) {
            append("- ")
            append(path)
            append('\n')
        }
    }
}

/**
 * Appends a memory category section subject to a char budget. Entries are added one by
 * one until the next entry would push the section past [remainingBudget]; remaining
 * entries are dropped silently. If no entries fit, the header is not emitted either.
 *
 * Returns the remaining budget after emission so the caller can thread it through the
 * next category section. [Int.MAX_VALUE] means unlimited (no truncation).
 */
private fun StringBuilder.appendMemoryCategorySection(
    header: String,
    entries: List<MemoryEntry>,
    withHitCount: Boolean,
    remainingBudget: Int,
): Int {
    if (entries.isEmpty() || remainingBudget <= 0) return remainingBudget

    val section = StringBuilder()
    section.append("\n\n## ").append(header).append("\n")
    val headerLen = section.length
    var included = 0
    for (entry in entries) {
        val entryStart = section.length
        section.append("- **").append(entry.key).append("**")
        if (withHitCount) {
            section.append(" (reinforced ").append(entry.hitCount).append("x)")
        }
        section.append(": ").append(entry.content).append('\n')
        if (section.length > remainingBudget) {
            // This entry pushed us over. Revert it and stop.
            section.setLength(entryStart)
            break
        }
        included++
    }
    if (included == 0) {
        // Not even the first entry fit — don't emit the header alone.
        return remainingBudget
    }
    append(section)
    return (remainingBudget - section.length).coerceAtLeast(0)
}

private fun StringBuilder.appendEmailAccountsSection(accounts: List<EmailAccountSummary>) {
    append("\n\n## Email Accounts\n")
    append("The user has these email accounts connected. Use them via the existing email tools — ")
    append("do NOT suggest adding, re-authenticating, or connecting a new account unless the user explicitly asks.\n")
    append("**Sending policy**: before calling `compose_email` or `reply_email`, present the full draft (to, subject, body) in chat and get explicit confirmation (\"send it\" / \"looks good\" / \"yes\"). Never call the send tools on the same turn you draft — the user must have a chance to correct tone, recipients, or content first. If the user later says \"change X and send\", re-present the updated draft and confirm again.\n")
    for (account in accounts) {
        append("- **")
        append(account.email)
        append("**: ")
        if (account.lastError != null) {
            append("sync failing — ")
            append(account.lastError)
        } else {
            append(account.unreadCount)
            append(" unread")
            if (account.lastSyncEpochMs > 0) {
                append(" (last sync: ")
                append(Instant.fromEpochMilliseconds(account.lastSyncEpochMs))
                append(')')
            }
        }
        append('\n')
    }
}

private fun StringBuilder.appendHeartbeatAdditionsSection(additions: List<ScheduledTask>) {
    append("\n\n## Heartbeat Additions\n")
    append("Standing instructions the user has set to run on every heartbeat (trigger=HEARTBEAT). Don't duplicate these when the user asks for similar behaviour; cancel via `cancel_task` if they want one removed.\n")
    for (t in additions) {
        append("- **")
        append(t.description)
        append("** (id: ")
        append(t.id)
        append("): ")
        append(t.prompt)
        append('\n')
    }
}

private fun StringBuilder.appendScheduledTasksSection(pendingTasks: List<ScheduledTask>) {
    append("\n\n## Запланированные задачи\n")
    for (t in pendingTasks) {
        append("- **")
        append(t.description)
        append("** (id: ")
        append(t.id)
        append(", запланировано на: ")
        append(t.scheduledAt)
        append(")")
        if (t.cron != null) {
            append(" [cron: ")
            append(t.cron)
            append("]")
        }
        append('\n')
    }
}

private fun StringBuilder.appendContextSection(runtime: ChatPromptRuntimeContext) {
    append("\n\n## Контекст\n")
    // Lead with local time so the model anchors on the user's wall clock when computing
    // relative times ("in 3 minutes", "tomorrow at 9"). Tools that accept a naive datetime
    // (e.g. `schedule_task`'s `execute_at`) interpret it in this zone.
    append("- Местное время: ")
    append(runtime.nowLocalIsoWithOffset)
    append(" (")
    append(runtime.timeZoneId)
    append(")\n")
    append("- UTC: ")
    append(runtime.nowUtcIsoString)
    append('\n')
    append("- Platform: ")
    append(runtime.platform)
    append('\n')
    append("- Model: ")
    append(runtime.modelId)
    append('\n')
    append("- Provider: ")
    append(runtime.providerName)
    append('\n')
}

private fun StringBuilder.appendDynamicUiSection() {
    append("\n## Dynamic UI\n")
    append("You can enhance your chat responses with interactive UI elements using katya-ui blocks. ")
    append("Proactively use them whenever you need input from the user — don't just ask in plain text if a form, selector, or buttons would be more natural. ")
    append("For example, if the user asks you to help plan a trip, present destination options as buttons; if you need preferences, show a form; if presenting choices, use interactive cards. ")
    append("Use katya-ui whenever collecting data, offering choices, presenting structured information, or guiding multi-step workflows. ")
    append("You can mix katya-ui blocks with regular markdown text naturally — use markdown for explanations and katya-ui for interactive elements.\n\n")
    append(KATYA_UI_COMPONENT_CATALOG)
    append("Layout tips:\n")
    append("- Put buttons INSIDE cards, directly below related content — never group all buttons separately at the bottom\n")
    append("- Use rows for groups of buttons or chips — rows wrap automatically, so any number of items is fine\n")
    append("- Keep button labels short (1-3 words)\n\n")
    append("Example:\n```katya-ui\n{\"type\":\"column\",\"children\":[{\"type\":\"text\",\"value\":\"Your name?\",\"style\":\"title\"},{\"type\":\"text_input\",\"id\":\"name\",\"placeholder\":\"Enter name\"},{\"type\":\"button\",\"label\":\"Submit\",\"action\":{\"type\":\"callback\",\"event\":\"submit\",\"collectFrom\":[\"name\"]}}]}\n```\n")
}

private fun StringBuilder.appendInteractiveUiSection() {
    append("\n## Interactive UI Mode (ACTIVE)\n")
    append("You are in full-screen interactive UI mode. The user ONLY sees rendered katya-ui components — they cannot see markdown, plain text, or anything outside a katya-ui fence.\n")
    append("Your ENTIRE response must be a single ```katya-ui code fence. No text before it, no text after it, no markdown. If you write anything outside the fence, the user will NOT see it.\n\n")
    append(KATYA_UI_COMPONENT_CATALOG)
    append("Rules:\n")
    append("- Each response is a COMPLETE screen layout. Include all content and actions in one katya-ui block.\n")
    append("- Always include clear primary action buttons so the user can proceed.\n")
    append("- Every screen MUST have at least one interactive element with a callback action (button, countdown with expiry action, etc.). A screen without any callback is a dead end the user cannot proceed from.\n")
    append("- Use headline text for screen titles. Structure screens with cards for grouping related content.\n")
    append("- Use descriptive callback events (e.g., \"select_destination\", \"submit_form\") so you understand what the user selected.\n")
    append("- Do NOT include back buttons, navigation bars, or any navigation controls. The app provides a back button and close button in the toolbar. The user can also type instructions in a text field below your UI.\n")
    append("Layout:\n")
    append("- Put buttons INSIDE cards, directly below related content — never group all buttons separately at the bottom\n")
    append("- Use rows for groups of buttons or chips — rows wrap automatically, so any number of items is fine\n")
    append("- Keep button labels short (1-3 words)\n")
    append("- Use columns for vertical flow. Use the full component set: tabs, accordion, alerts, progress, chips, icons, etc.\n\n")
    append("Limitations — respect these strictly:\n")
    append("- The UI is static once rendered. NEVER show loading, fetching, or verifying states. You cannot fetch data or run operations asynchronously. Present all content immediately.\n")
    append("- Never use indeterminate progress (progress without a value) or text like \"Loading...\", \"Fetching...\", \"Verifying...\" as if something will happen later — nothing will.\n")
    append("- Each screen is independent. Only conversation history carries state between screens — there is no client-side state persistence, no session storage, no variables that survive across responses.\n")
    append("- Do not attempt to build multi-screen stateful applications (e.g., shopping carts that accumulate items, dashboards that refresh). Each response is a fresh, self-contained screen.\n")
    append("- Only use the exact components and properties defined above. Do not invent attributes, component types, or behaviors that are not listed. If a component doesn't support a feature, do not pretend it does.\n")
    append("- Start with simple, clean layouts. A well-structured screen with a few cards and clear actions is better than a complex layout that pushes the component set beyond its capabilities.\n")
    append("- When unsure whether something will work, use a simpler approach. A working simple screen is always better than a broken ambitious one.\n\n")
    append("Example:\n```katya-ui\n{\"type\":\"column\",\"children\":[{\"type\":\"text\",\"value\":\"Welcome\",\"style\":\"headline\"},{\"type\":\"card\",\"children\":[{\"type\":\"text\",\"value\":\"What would you like to do?\",\"style\":\"title\"},{\"type\":\"button\",\"label\":\"Get Started\",\"action\":{\"type\":\"callback\",\"event\":\"get_started\"}}]}]}\n```\n")
}

/**
 * Pre-computed katya-ui component catalog text — shared between [appendDynamicUiSection]
 * and [appendInteractiveUiSection]. Pre-building the ~3KB of static text once (instead
 * of re-running ~30 `append` calls per message) is the main reason this is a top-level
 * val rather than a helper function.
 */
private val KATYA_UI_COMPONENT_CATALOG: String = buildString {
    append("Format: wrap a JSON object in ```katya-ui fences.\n\n")
    append("Components: column, row, card, box, text, button, text_input, checkbox, switch, select, radio_group, slider, chip_group, table, list, divider, image, icon, code, progress, countdown, alert, tabs, accordion, quote, badge, stat, avatar.\n")
    append("- text: {\"type\":\"text\",\"value\":\"...\",\"style\":\"headline|title|body|caption\",\"bold\":true,\"color\":\"primary|secondary|error\"} — do NOT use markdown formatting (**, *, #, etc.) in text values; use the bold/italic/style properties instead\n")
    append("- button: {\"type\":\"button\",\"label\":\"...\",\"action\":{...},\"variant\":\"filled|outlined|text|tonal\"}\n")
    append("- text_input: {\"type\":\"text_input\",\"id\":\"...\",\"label\":\"...\",\"placeholder\":\"...\",\"value\":\"...\"}\n")
    append("- checkbox: {\"type\":\"checkbox\",\"id\":\"...\",\"label\":\"...\",\"checked\":false}\n")
    append("- switch: {\"type\":\"switch\",\"id\":\"...\",\"label\":\"...\",\"checked\":false}\n")
    append("- select: {\"type\":\"select\",\"id\":\"...\",\"label\":\"...\",\"options\":[\"A\",\"B\"],\"selected\":\"A\"}\n")
    append("- radio_group: {\"type\":\"radio_group\",\"id\":\"...\",\"label\":\"...\",\"options\":[\"A\",\"B\"],\"selected\":\"A\"}\n")
    append("- slider: {\"type\":\"slider\",\"id\":\"...\",\"label\":\"...\",\"value\":50,\"min\":0,\"max\":100,\"step\":10}\n")
    append("- chip_group: {\"type\":\"chip_group\",\"id\":\"...\",\"chips\":[{\"label\":\"Tag\",\"value\":\"tag\"}],\"selection\":\"single|multi|none\"} — selection mode: \"single\" (default, one at a time), \"multi\" (any number), or \"none\" (display-only tags, no interaction, id not needed). For \"single\" and \"multi\" a button must collectFrom the chip_group id to send the selection.\n")
    append("- list: {\"type\":\"list\",\"items\":[...],\"ordered\":false} — bullet (or numbered) list; the app renders bullets/numbers automatically, so do NOT include bullet characters (•, -, *) or numbering in item text\n")
    append("- table: {\"type\":\"table\",\"headers\":[\"Col1\",\"Col2\"],\"rows\":[[\"a\",\"b\"]]} — columns share equal width; keep to 3-4 columns max on mobile, use short cell values\n")
    append("- icon: {\"type\":\"icon\",\"name\":\"home|settings|search|add|delete|edit|check|check_circle|close|arrow_back|arrow_forward|star|favorite|share|info|warning|person|group|mail|phone|calendar|location|refresh|menu|more|send|notifications|trending_up|trending_down|trending_flat|thumb_up|thumb_down|visibility|lock|shopping_cart|play|pause|stop|download|upload|cloud|attachment|link|code|terminal|build|bug|lightbulb|science|school|work|account_circle|language|translate|dark_mode|light_mode|bolt|rocket|money|credit_card|receipt|inventory|category|dashboard|analytics|chart|pie_chart|show_chart|timer|alarm|task|bookmark|flag|tag|pin|copy|paste|cut|undo|redo|filter|sort|swap|sync|wifi|bluetooth|battery|speed|shield|verified|health|fitness|food|coffee|airplane|hotel|car|earth|map|compass|pet|leaf|water|weather|party|trophy|medal|premium\",\"size\":24,\"color\":\"primary|secondary|error\"} — you can also use any emoji as the name (e.g. \"name\":\"⚔️\" or \"name\":\"🗺️\"); prefer emojis when they better convey the meaning than the generic Material icons\n")
    append("- code: {\"type\":\"code\",\"code\":\"...\",\"language\":\"kotlin\"} — a copy-to-clipboard icon is rendered automatically; do NOT add your own copy button next to it.\n")
    append("- progress: {\"type\":\"progress\",\"value\":0.5,\"label\":\"50%\"} (always provide a value 0.0-1.0 to show a determinate bar; do NOT omit value to fake a loading state)\n")
    append("- countdown: {\"type\":\"countdown\",\"seconds\":300,\"label\":\"Time left\",\"action\":{\"type\":\"callback\",\"event\":\"timer_done\"}} (seconds is relative duration from render; action is optional, fires on expiry)\n")
    append("- alert: {\"type\":\"alert\",\"message\":\"...\",\"title\":\"...\",\"severity\":\"info|success|warning|error\"}\n")
    append("- tabs: {\"type\":\"tabs\",\"tabs\":[{\"label\":\"Tab 1\",\"children\":[...]},{\"label\":\"Tab 2\",\"children\":[...]}],\"selectedIndex\":0}\n")
    append("- accordion: {\"type\":\"accordion\",\"title\":\"...\",\"children\":[...],\"expanded\":false}\n")
    append("- box: {\"type\":\"box\",\"children\":[...],\"contentAlignment\":\"center|top_start|top_center|top_end|center_start|center_end|bottom_start|bottom_center|bottom_end\"}\n")
    append("- quote: {\"type\":\"quote\",\"text\":\"...\",\"source\":\"Author Name\"} — blockquote with accent border\n")
    append("- badge: {\"type\":\"badge\",\"value\":\"3\",\"color\":\"primary|secondary|error\"} — small colored pill for counts or status\n")
    append("- stat: {\"type\":\"stat\",\"value\":\"\$1,234\",\"label\":\"Revenue\",\"description\":\"12% increase\"} — large metric display\n")
    append("- avatar: {\"type\":\"avatar\",\"name\":\"John Doe\",\"imageUrl\":\"https://...\",\"size\":40} — circular image or initials (24-80dp)\n\n")
    append("Actions (on buttons, countdown expiry):\n")
    append("- callback: {\"type\":\"callback\",\"event\":\"event_name\",\"data\":{\"key\":\"val\"},\"collectFrom\":[\"input_id1\",\"input_id2\"]} — collects input values and sends them back as a user message (e.g. \"Pressed: event_name\" or \"Responded with: key: value\"). You then reply with text or more UI. Use callbacks for: collecting choices, submitting forms, navigating between steps, confirming actions. Do NOT create callback buttons that imply operations you cannot perform — callbacks only send a message, they do not trigger system actions like printing, file export, or downloads.\n")
    append("- toggle: {\"type\":\"toggle\",\"targetId\":\"element_id\"} — shows/hides an element locally\n")
    append("- open_url: {\"type\":\"open_url\",\"url\":\"https://...\"}\n")
    append("- copy_to_clipboard: {\"type\":\"button\",\"action\":{\"type\":\"copy_to_clipboard\",\"text\":\"...\"}} — renders as a clipboard icon button; omit the button label. Offer next to copyable content like snippets, commands, or tokens.\n\n")
    append("- Form inputs (text_input, checkbox, switch, select, radio_group, slider, chip_group) only store state locally. Their values are ONLY sent when a button's collectFrom includes their id. Always pair form inputs with a submit button that collects from them.\n\n")
}
