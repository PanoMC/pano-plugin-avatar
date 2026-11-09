package com.panomc.plugins.avatar.util

import java.io.File

/**
 * Avatar uploads are typed by their content, never by the client's Content-Type or file name, and only these raster
 * image types are ever stored or served.
 */
object AvatarImages {
    val SUPPORTED_TYPES = mapOf(
        "image/png" to "png",
        "image/jpeg" to "jpg",
        "image/gif" to "gif",
        "image/webp" to "webp"
    )

    private val MIME_BY_EXTENSION = mapOf(
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "webp" to "image/webp"
    )

    /** The MIME type read from the file's first bytes, or null when it is not a supported image. */
    fun detectMimeType(file: File): String? {
        val header = ByteArray(12)
        val read = file.inputStream().use { it.read(header) }

        if (read < 12) {
            return null
        }

        fun at(index: Int, vararg bytes: Int) = bytes.withIndex().all { (i, b) -> header[index + i] == b.toByte() }

        return when {
            at(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "image/png"
            at(0, 0xFF, 0xD8, 0xFF) -> "image/jpeg"
            at(0, 0x47, 0x49, 0x46, 0x38) -> "image/gif"
            at(0, 0x52, 0x49, 0x46, 0x46) && at(8, 0x57, 0x45, 0x42, 0x50) -> "image/webp"
            else -> null
        }
    }

    fun extensionOf(mimeType: String): String? = SUPPORTED_TYPES[mimeType]

    /** The Content-Type for a stored avatar, or null when its extension is not a supported image. */
    fun mimeTypeOfFile(fileName: String): String? = MIME_BY_EXTENSION[fileName.substringAfterLast('.', "").lowercase()]
}
