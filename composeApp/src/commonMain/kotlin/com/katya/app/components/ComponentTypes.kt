package com.katya.app.components

/** Тип скачиваемого компонента — определяет, куда его распаковывать и как использовать. */
enum class ComponentType(val dbValue: String) {
    /** Корневой образ ОС для proot-песочницы (tar.xz/zip). */
    ROOTFS("rootfs"),

    /** Нативные бинари и библиотеки (.so и т.п., упакованные в zip). */
    NATIVE("native"),

    /** Модели (STT/TTS/LLM). */
    MODEL("model"),
    ;

    companion object {
        fun from(value: String): ComponentType = entries.firstOrNull { it.dbValue == value } ?: MODEL
    }
}

/** Разовый «дефолтный» список — на первом запуске пишется в БД со статусом missing.
 *  Дальше ссылки живут в БД, и пользователь меняет их в UI («Альтернативные ссылки»). */
data class ComponentSeed(
    val id: String,
    val name: String,
    val componentType: ComponentType,
    val url: String,
    val version: String = "",
    val abi: String? = null,
)

/** Текущая аппаратная ABI устройства (arm64-v8a / armeabi-v7a / x86_64 / x86). */
expect fun currentAbi(): String

object ComponentDefaults {
    private fun archForAbi(abi: String): String = when (abi) {
        // В проот-дистрибутиве 32-битное arm именуется «arm» (armhf/armv7), а не «armhf».
        "armeabi-v7a" -> "arm"
        "x86_64" -> "x86_64"
        "arm64-v8a" -> "aarch64"
        else -> "aarch64"
    }

    /**
     * Все компоненты, известные приложению. Ссылки по умолчанию указывают на
     * публичный репозиторий проекта (релизы); пользователь может подменить любую
     * ссылку — значения лежат в БД, а не в коде.
     */
    fun seeds(): List<ComponentSeed> {
        val abis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        return buildList {
            abis.forEach { abi ->
                val arch = archForAbi(abi)
                add(
                    ComponentSeed(
                        id = "debian_$abi",
                        name = "Debian Linux (rootfs) · $abi",
                        componentType = ComponentType.ROOTFS,
                        url = "https://github.com/termux/proot-distro/releases/download/v4.29.0/debian-trixie-$arch-pd-v4.29.0.tar.xz",
                        version = "4.29.0",
                        abi = abi,
                    ),
                )
                add(
                    ComponentSeed(
                        id = "native_$abi",
                        name = "Proot/Xray runtime · $abi",
                        componentType = ComponentType.NATIVE,
                        version = "1",
                        abi = abi,
                        url = "https://github.com/Gegaremant/KatYa_2/releases/download/v3.1.3/native-$abi.zip",
                    ),
                )
            }
        }
    }
}
