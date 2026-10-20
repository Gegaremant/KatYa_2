# AGENTS.md for KatYa

## Secrets and `.env` (do not get this wrong)

The point of the rule: **no hardcoded credentials in code.** Every login, password,
token and key belongs in `.env`, read at runtime — never written into a source file.

- **`.env.katya` stays where it is. It is supposed to be there.** It is the only place
  the credentials live; nobody will remember them otherwise. **Do not delete it, do not
  "clean it up", do not move it, do not suggest relocating it.** Ever.
- **It stays out of git.** `.env`, `.env.katya` and `.env.*` are already in
  `.gitignore`. Before the first push in *any* new repo, check that they are listed —
  and if they are not, add them before committing anything else.
- If a secret was ever committed, revoke the token on GitHub (Developer settings →
  Tokens) and issue a new one. Do not keep using it.
- CI gets its copy from GitHub Secrets (`KATYA_KEYSTORE_BASE64`, `KATYA_KEYSTORE_PASSWORD`,
  `KATYA_KEY_ALIAS`). The local file and the repo secrets do not conflict: the first is
  for this machine, the second is for builds.
- Never paste a secret's value into a command line in chat, and never print file
  contents to the log or output.

## Installation & Build

### Basic Setup
```bash
# Credentials come from .env.katya — see the section above; it is already gitignored.
# Load it, do not inline the values:
set -a && . ./.env.katya && set +a
./gradlew assembleFossFullDebug

# Debug-key build (development only) — unset the KEYSTORE_* vars first, otherwise
# gradle signs with the release key and the APK is not reproducible for you.
# For product-specific builds:
./gradlew assemblePlayStoreFullDebug  # Google Play store version
./gradlew assembleFossFullDebug       # FOSS version
```

Release builds are made by CI. Push a `v*` tag and three workflows run independently:

- `release.yml` — both APKs. Builds them, verifies the signing certificate against the
  expected SHA-256, runs the unit tests, **creates the GitHub release**.
- `desktop-linux.yml` — `.deb` and `.rpm` via `jpackage`.
- `desktop-windows.yml` — a zip containing the application image.

The two desktop workflows **append their files to the release** and never create or
rewrite it: re-creating it would wipe the description and both APKs. They wait up to
ten minutes for `release.yml` to publish the release first, because they otherwise race
it. A forced update to an already-pushed tag does *not* start a workflow — a new run
needs a new tag.

`workflow_dispatch` only works once the workflow file is on the default branch (`master`).
Release work happens on `dev`, which is ahead, so manual runs are unavailable until the
branches are merged.

Desktop packaging is verified before publication, not after: the `.deb` is unpacked and
every classpath entry in `KatYa.cfg` is checked against disk, and the launcher is run
headless until it reaches window creation. That check is what caught the release build
shipping without Skiko's native code.

Note `.gitignore` no longer ignores `.github` wholesale — that rule silently swallowed
new workflow files, so CI never saw them.

A local `assembleFossFullDebug` is a debug-signed build and will *not* install over an
existing release.

Windows publishes a zip, not an `.msi`: `jpackage`'s MSI builder (`light.exe`) exits with
code 311 on the GitHub Windows image — an internal-consistency failure whose message
`jpackage` does not log — and three separate attempts (manual WiX 3.14, short `TEMP` with
long paths enabled, `perUserInstall`) did not move it. `TargetFormat.Msi` is kept for
local builds on a configured Windows machine. Capturing `light.exe`'s own output would
need a wrapper around it and is the thing to do first if an installer is ever required.

### Three platforms: Android, Windows, Linux

`composeApp` is a KMP module with three targets sharing one `commonMain` (240
files). The platform is supplied through ~90 `expect` declarations; everything else
is written once.

| Target | Source set | Entry point |
|---|---|---|
| Android | `androidMain` | `androidApp` → `KatyaApplication` / `MainActivity` |
| Windows, Linux | `desktopMain` | `composeApp/src/desktopMain/.../Main.kt` |

Builds:

```bash
./gradlew :composeApp:run                       # run from source
./gradlew :composeApp:packageDeb                # Linux, .deb
./gradlew :composeApp:packageRpm                # Linux, .rpm
./gradlew :composeApp:packageMsi                # Windows
```

App state lives in `~/.katya` on desktop (`conversations.db`, `katya.log`,
`secure-prefs`, `skills/`) instead of the Android app-private directory. The
backup format and the database schema are the same on both, so a backup taken on
a phone restores on a desktop and back.

What desktop deliberately does **not** have, and why — each returns a
well-typed "unsupported" rather than a fake success:

- **sandbox, root, VLESS/xray, FreeDeepSeek proxy** — proot and `su` are
  Android things. On desktop a command runs straight in the OS shell, which is
  the same job with none of the emulation.
- **SMS, notification listener, device admin, accessibility, battery
  optimization** — Android permission surfaces with no counterpart.
- **Vosk STT, wake word, Piper voices** — the engines play through
  `android.media.AudioTrack`; no port yet. The *voice output* is not among
  them: desktop speaks with the voice of the operating system.
- **scheduled tasks after the window closes** — needs `systemd --user` or an OS
  scheduler; while the app is open they do run.
- **audio focus** — AWT has no focus API, so music is *not* paused during
  speech rather than being paused blindly.

### Voice: one scale, four platforms

Speed and pitch are **percentages from −100 to +100**, not multipliers. Zero is
Katya's own voice, ±100 is ten times slower or faster; the percentage becomes a
multiplier through `10^(percent/100)`. A percentage is used because a slider has
to mean the same thing everywhere: "+10 %" on a fast voice and "+10 %" on a slow
one are different accelerations, and a multiplier hides that.

The scale is stored in `sys_tts_rate_percent` / `sys_tts_pitch_percent`. The old
keys (`sys_tts_rate`, `sys_tts_pitch`) held multipliers and are converted once,
on first read — see `migrateVoiceSettingsIfNeeded` in `AppSettings`.

Each engine maps the percentages onto its own scale and says what it cannot do
through `SpeechEngine.voiceTuningSupport`:

| Engine | Speed | Pitch |
|---|---|---|
| Android system TTS | multiplier on the system rate | multiplier on the system pitch |
| Piper (on-device) | `PlaybackParams.setSpeed` | `PlaybackParams.setPitch` |
| Cloud TTS (`/audio/speech`) | the `speed` parameter, 0.25…4.0 | not in the API |
| Linux speech-dispatcher | its own −100…+100 scale | its own −100…+100 scale |
| Windows SAPI5 | `Rate` −10…+10 | `Pitch` −50…+50 |
| macOS `say` | words per minute | not in the API |

The slider is disabled when the active engine cannot do it, and the reason is
written next to it. `SpeechEngine.applyTuning` is the single seam: the app never
reaches into a concrete engine to set a rate.

Desktop speech is `DesktopTts` (`desktopMain`): speech-dispatcher on Linux,
SAPI5 through PowerShell on Windows, `say` on macOS. If the system has no
synthesizer at all, `systemTtsUnavailableReason()` explains it in the settings
instead of leaving the user wondering why nothing is spoken.

Two details that cost real time to find:

- **speech-dispatcher may never confirm the end of a parameterised message.**
  With speed or pitch set, the daemon can finish speaking and still not answer
  the client, so `spd-say -w` waits forever. The engine therefore waits with a
  limit computed from the length of the text, kills the process and reports it
  in the log. Without the limit the "stop" button never came back.
- **Windows text goes through a temporary file.** The text is written to a file
  and read by the PowerShell script: in a command line it is visible in the
  process list, and that is private.

### Shared JVM code

Code that is plain JVM — no Android imports — goes in
`composeApp/src/jvmShared/kotlin/`, wired into both `androidMain` and
`desktopMain` via `kotlin.srcDir`. Do **not** create a `jvmShared` source set
with `dependsOn`: Gradle rejects the same file being a source for both
`jvmShared` and the `androidMain` that inherits it.

Currently there: SSH client, Hive crypto, e-mail socket, SSH tunnel,
`~/.ssh/config` manager, and the `/proc/stat` CPU sampler.

### Two bundles: `full` and `lite`

Two flavor dimensions, so a variant name carries both: `distribution` (`foss`,
`playStore`) and `bundle` (`full`, `lite`).

- **`full`** carries the sandbox payloads inside the APK (`rootfs` + proot, ~130 MB), so
  the sandbox comes up on a fresh install with no network at all.
- **`lite`** carries none of them and fetches everything on demand, trying the URL from
  the settings first and then known mirrors. Roughly 100 MB smaller.

Those payloads are **not in git** — that is the point of `.gitignore`. Before building a
`full` bundle locally, fetch them:

```bash
./tools/fetch-bundled-components.sh   # → androidApp/src/full/assets/katya-components/
```

`BundledComponents` (androidMain) is the seam: it checks `assets/katya-components/<id>.<ext>`
before any network call and falls back to download-with-mirrors when the file is absent.
CI runs the same script, so both bundles come from the same archives a user would fetch.

### SQLite: миграции проверяются на пустой базе

`verifyMigrations.set(true)` в `composeApp/build.gradle.kts` прогоняет миграции против
**пустой** базы. Практическое следствие: любая миграция, трогающая уже существующую
таблицу — `ALTER TABLE … ADD COLUMN` или пересоздание таблицы, — падает проверку с
«No table found». Это проверено пробой на посторонней `conversationEntity`.

Значит структуру `memoryEntry` менять нельзя. Отсюда и решение v3.2.19: разделение
памяти по личностям сделано **в ключе**, а не в отдельном столбце — ключ и есть то
единственное поле, где различие уже выражается, ведь уникальность и требуется
раздельно по ролям. Код и обоснование — в `RoleScopedMemoryKey.kt`.

Если когда-нибудь понадобится настоящий столбец, сначала придётся починить саму
проверку, иначе миграция просто не соберётся.

### Gradle Dependencies
The project uses a centralized version catalog (`gradle/libs.versions.toml`) for dependency management. To update dependencies:
1. Edit versions in the `[versions]` section
2. Keep `appVersion` and `android-versionCode` in sync
3. Run `./gradlew build` to refresh generated files

## Project Structure

- **`androidApp/`**: Android application module with:
  - Build configuration and signing details
  - `AndroidManifest.xml`
  - Application flavors: `playStore` (Google Play) and `foss` (Foss distribution)
- **`composeApp/`**: Kotlin Multiplatform module with:
  - `src/androidMain/`: Android-specific code (Linux sandbox, STT/TTS, network tools)
  - `src/commonMain/`: Shared business logic (UI, inference, database)
- **`gradle/`**: Gradle version catalog (`libs.versions.toml`) and build scripts

## Development Workflow

### Mode Selection

- **God Mode (Root)**: Requires Magisk/KernelSU root. Gives full control over Android OS (shell commands, package management). Used for USB debugging and advanced features.

- **Sandbox (PRoot)**: Runs Debian terminal inside Android without root. Essential for:
  - Local Linux services (Node.js, VLESS proxy, SSH tunnels)
  - Direct access to device internals via `adb shell`
  - Running local AI models

### Key Development Steps

1. **Device Setup**:
   - Root device: Follow Magisk/KernelSU setup instructions
   - Non-root: Install PRoot Debian terminal

2. **Build & Deploy**:
   - Build APK with `./gradlew assembleFossFullDebug` (see signing note above)
   - Test on device via USB or Android Studio
   - For Sandbox mode: Ensure device supports Linux containerization

3. **Version Updates**:
   - Edit `gradle/libs.versions.toml`
   - Update `appVersion` (UI) and `android-versionCode` (Play Store)
   - `appVersion` / `android-versionCode` live only in `gradle/libs.versions.toml`.
     `Version.kt` is *generated* at `composeApp/build/generated/.../Version.kt` — do
     not look for a hand-written `AppVersion.kt`, it does not exist. Rebuild to refresh.
   - The release workflow greps `appVersion` out of the catalog and derives the tag as
     `v<appVersion>`, so the catalog is the single source of truth for the release tag.

## Common Tasks

### Version Management
```bash
# Update version in catalog and regenerate UI version:
echo 'appVersion = "3.2.0"' >> gradle/libs.versions.toml
echo 'android-versionCode = "200"' >> gradle/libs.versions.toml
./gradlew build
```

### Feature Testing
- **God Mode**: Test full OS control features on rooted devices
- **Sandbox Mode**: Test Linux containerization (Node.js, VLESS, SSH)
- **Multiplatform**: Test core functionality on both platforms using emulator or device

### Dependency Troubleshooting
- Use `./gradlew dependencies` to see dependency tree
- Check `build.log` for compilation errors
- Clean and rebuild with `./gradlew clean build` if needed