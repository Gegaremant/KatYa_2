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
./gradlew assembleFossDebug

# Debug-key build (development only) — unset the KEYSTORE_* vars first, otherwise
# gradle signs with the release key and the APK is not reproducible for you.
# For product-specific builds:
./gradlew assemblePlayStoreDebug  # Google Play store version
./gradlew assembleFossDebug       # FOSS version
```

Release builds are made by CI (`.github/workflows/release.yml`): push a `v*` tag and it
builds, verifies the signing certificate against the expected SHA-256, runs the unit
tests and publishes the APK. A local `assembleFossDebug` is a debug-signed build and
will *not* install over an existing release.

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
   - Build APK with `./gradlew assembleFossDebug` (see signing note above)
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