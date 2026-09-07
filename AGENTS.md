# AGENTS.md for KatYa

## Installation & Build

### Basic Setup
```bash
# Android debug (includes all features):
./gradlew assembleDebug

# Android release (includes all features):
./gradlew assembleRelease

# For product-specific builds:
./gradlew assemblePlayStoreDebug  # Google Play store version
./gradlew assembleFossDebug       # FOSS version
```

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
   - Build APK with `./gradlew assembleDebug`
   - Test on device via USB or Android Studio
   - For Sandbox mode: Ensure device supports Linux containerization

3. **Version Updates**:
   - Edit `gradle/libs.versions.toml`
   - Update `appVersion` (UI) and `android-versionCode` (Play Store)
   - Run `./gradlew build` to regenerate `AppVersion.kt` in `composeApp/src/commonMain/kotlin/com/katya/app/AppVersion.kt`

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