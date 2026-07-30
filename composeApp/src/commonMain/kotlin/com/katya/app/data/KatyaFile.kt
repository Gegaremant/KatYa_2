package com.katya.app.data

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.mimeType
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.size

interface KatyaFile {
    val name: String
    val extension: String
    fun mimeType(): String?
    suspend fun readBytes(): ByteArray
    fun size(): Long
}

class PlatformKatyaFile(val platformFile: PlatformFile) : KatyaFile {
    override val name: String get() = platformFile.name
    override val extension: String get() = platformFile.extension
    override fun mimeType(): String? = platformFile.mimeType()?.toString()
    override suspend fun readBytes(): ByteArray = platformFile.readBytes()
    override fun size(): Long = platformFile.size() ?: 0L
}

class BytesKatyaFile(
    private val bytes: ByteArray,
    override val name: String,
    override val extension: String,
    private val mime: String?
) : KatyaFile {
    override fun mimeType(): String? = mime
    override suspend fun readBytes(): ByteArray = bytes
    override fun size(): Long = bytes.size.toLong()
}
