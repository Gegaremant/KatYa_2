plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.katya.app"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "com.inspiredandroid.katya"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode =
            libs.versions.android.versionCode
                .get()
                .toInt()
        versionName = libs.versions.appVersion.get()
        // Launcher label carries the version too, e.g. "KatYa 3.1.11",
        // so you always see which build is installed.
        manifestPlaceholders["appLabel"] = "KatYa ${libs.versions.appVersion.get()}"
    }

    flavorDimensions += listOf("distribution", "bundle")
    productFlavors {
        create("playStore") {
            dimension = "distribution"
        }
        create("foss") {
            dimension = "distribution"
            isDefault = true
        }

        // Two distributions of the same app, differing only in what ships inside the APK.
        //
        // `full` carries everything it needs — the xray runtime with its geo databases, the
        // Debian rootfs and the proot binaries — so the sandbox works on first launch with
        // no network at all. That matters because GitHub was simply unreachable for some
        // users: both downloads timed out and the sandbox could never start.
        //
        // `lite` carries none of it. It is roughly 60 MB smaller and every component is
        // fetched on demand, which is the right trade on a metered connection.
        //
        // Bundled payloads live under `src/full/assets` and are not in git — they are
        // fetched by `tools/prepare-full-bundle.sh`, the same script that builds the
        // distributables.
        create("full") {
            dimension = "bundle"
            isDefault = true
        }
        create("lite") {
            dimension = "bundle"
        }
    }

    sourceSets {
        getByName("full") {
            assets.srcDir("src/full/assets")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    // Полезная нагрузка full-сборки — уже сжатые архивы. Deflate по ним даёт ровно
    // 0% (проверено: 35409704 → 35419569), то есть упаковка 137 МБ впустую жжёт время,
    // а на устройстве Android вынужден распаковывать поток на лету. Хранить как есть —
    // тогда AssetManager отдаёт размер через openFd() без второго прохода по файлу.
    androidResources {
        noCompress("xz", "zip")
    }

    signingConfigs {
        create("release") {
            val ksFile = System.getenv("KEYSTORE_FILE")
            if (ksFile != null) {
                storeFile = file(ksFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                // The key is a PKCS12 store, where keytool ties the private-key
                // password to the store password; a separate KEY_PASSWORD is
                // rejected at packaging time with "final block not properly padded".
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        // Release APKs ship as `assembleFossFullDebug` / `assembleFossLiteDebug`. The stock debug keystore lives
        // in ~/.android and is generated on first use, so every CI runner signs
        // with a fresh key — each release then has a different certificate and
        // Android refuses to install it over the previous version. Signing debug
        // with the release keystore whenever one is provided keeps the
        // certificate identical across all future builds.
        getByName("debug") {
            if (System.getenv("KEYSTORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }

        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "../composeApp/proguard-rules.pro")
            signingConfig =
                if (System.getenv("KEYSTORE_FILE") != null) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.foundation.android)
    implementation(libs.compose.material3)
    implementation(libs.koin.android)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.filekit.core)
    implementation(libs.filekit.compose)
    implementation(libs.tts)
    implementation(libs.tts.compose)
    implementation(libs.compose.components.uiToolingPreview)
    debugImplementation(libs.compose.ui.tooling)
    "playStoreImplementation"(libs.play.review)
}
