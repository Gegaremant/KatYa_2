import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("KatyaDatabase") {
            packageName.set("com.katya.app.db")
            // The schema lives on user devices — structural changes need a
            // numbered .sqm migration file (see conversation.sq header).
            // This makes the build verify that migrations reproduce the schema.
            verifyMigrations.set(true)
        }
    }
}

composeCompiler {
    stabilityConfigurationFiles.add(project.layout.projectDirectory.file("compose_stability.conf"))
}

compose.desktop {
    application {
        mainClass = "com.katya.app.MainKt"
        nativeDistributions {
            // Иконка не задаётся: у DSL `nativeDistributions` такого свойства нет
            // (проверено по сигнатурам compose-gradle-plugin 1.11.1), а `jpackage`
            // подставляет generic-java. Для иконки приложения есть отдельный путь
            // через ресурсы окна, но он тянет иконки Compose-ресурсов в общий
            // набор — отдельная задача, не этот раунд.
            //
            // Windows и Linux. Dmg пропущен намеренно: задача — десктоп на десктопе,
            // а macOS-сборка тянет за собой codesign и notarization, без которых пакет
            // всё равно не запустится у пользователя.
            targetFormats(
                TargetFormat.Msi,
                TargetFormat.Deb,
                TargetFormat.Rpm,
            )
            packageName = "KatYa"
            packageVersion = libs.versions.appVersion.get()
            description = "Катя — ассистент в кармане"
            vendor = "Gegaremant"
            windows {
                menu = true
                // Ровно тот UUID, что был в сборке до переезда. jpackage отказывается
                // обновлять установку, если uuid меняется, — пользователь получил бы
                // вторую копию рядом с первой.
                upgradeUuid = "9b6a4f2e-6c1d-4a55-9f0b-2c7e5d3f8a10"
            }
            linux {
                packageName = "katya"
                debMaintainer = "maintainer@example.com"
                appCategory = "Development"
            }
        }
    }
}

kotlin {
    jvmToolchain(21)

    androidLibrary {
        namespace = "com.katya.app.shared"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
        androidResources {
            enable = true
        }
        withHostTest {}
    }

    // Windows и Linux. Тот же commonMain, что и на Android, — 240 файлов общего кода
    // не трогаем, платформа подставляется через 43 expect-объявления.
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(layout.buildDirectory.dir("generated/src/commonMain/kotlin"))
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.turbine)
                implementation(libs.multiplatform.settings.test)
            }
            // Real backup files the import tests read. They are test data, not code:
            // absent ones make the test skip rather than fail.
            resources.srcDir("src/commonTest/resources")
        }

        // Код, который одинаково работает на Android и на десктопе: шифрование
        // Hive, SSH-клиент, почтовый сокет, туннель, чтение /proc/stat.
        //
        // Файлы лежат один раз в `src/jvmShared/kotlin`, а в сборку попадают
        // через srcDir в оба source set. Отдельный промежуточный source set
        // (`dependsOn`) здесь не годится: Gradle считает, что один файл не может
        // быть источником сразу для `jvmShared` и для наследующего его `androidMain`,
        // и валит конфигурацию.
        val androidMain by getting {
            kotlin.srcDir("src/jvmShared/kotlin")
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.spght.encryptedprefs)
            implementation(libs.ktor.client.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.webkit)
            implementation(libs.koin.android)
            implementation(libs.material)
            implementation(libs.bouncycastle.provider)
            implementation(libs.litert.lm)
            implementation(libs.tensorflow.lite)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.jsch)
            implementation(libs.commons.compress)
            implementation(libs.xz)
            implementation("com.alphacephei:vosk-android:0.3.38@aar")
            implementation("net.java.dev.jna:jna:5.13.0@aar")
        }

        val desktopMain by getting {
            kotlin.srcDir("src/jvmShared/kotlin")
            dependencies {
                // Нативная библиотека Skiko для текущей ОС. Без неё приложение
                // падает на старте с `LibraryLoadException`: проверено на
                // распакованном .deb — в classpath был skiko-awt без
                // skiko-awt-linux-x64, то есть без самого нативного кода.
                // Именно этот currentOs и приносит `-linux-x64` / `-windows-x64`.
                implementation(compose.desktop.currentOs)
                // Всё, что нужно JVM, но не нужно Android-модулю: OkHttp вместо
                // Android-движка, файловые диалоги, синтезатор речи.
                implementation(libs.compose.ui)
                implementation(libs.compose.material3)
                implementation(libs.compose.material.icons.core)
                implementation(libs.compose.material.icons.extended)
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.components.resources)
                implementation(libs.compose.components.uiToolingPreview)
                implementation(libs.androidx.lifecycle.viewmodel.compose)
                implementation(libs.androidx.lifecycle.runtime.compose)
                implementation(libs.androidx.navigation.compose)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.sqldelight.sqlite.driver)
                implementation(libs.multiplatform.settings)
                implementation(libs.filekit.core)
                implementation(libs.filekit.compose)
                implementation(libs.tts)
                implementation(libs.tts.compose)
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(libs.koin.compose.viewmodel)
                implementation(libs.reorderable)
                // Тот же набор, что и в androidMain: эти библиотеки нужны общему
                // коду из jvmShared, и без них desktopMain его не соберёт.
                implementation(libs.jsch)
                implementation(libs.bouncycastle.provider)
                implementation(libs.commons.compress)
                implementation(libs.xz)
            }
        }

        commonMain.dependencies {
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.core)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.components.uiToolingPreview)

            implementation(libs.androidx.navigation.compose)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.lifecycle.viewmodel.compose)

            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)

            implementation(libs.tts)
            implementation(libs.tts.compose)

            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.core)

            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)

            implementation(libs.filekit.core)
            implementation(libs.filekit.compose)

            implementation(libs.coil.compose)
            implementation(libs.coil.svg)
            implementation(libs.coil.network.ktor3)

            implementation(libs.reorderable)

            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)
        }
    }
}

class VersionGeneratorPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.afterEvaluate {
            val appVersion = libs.versions.appVersion.get()
            val versionCode =
                libs.versions.android.versionCode
                    .get()

            // Generate Kotlin version file
            val versionFile =
                layout.buildDirectory
                    .file("generated/src/commonMain/kotlin/com/katya/app/Version.kt")
                    .get()
                    .asFile
            versionFile.parentFile?.mkdirs()
            versionFile.writeText(
                """
                package com.katya.app

                object Version {
                    const val appVersion = "$appVersion"
                    const val versionCode = $versionCode
                }
                """.trimIndent(),
            )
        }
    }
}

apply<VersionGeneratorPlugin>()
