package com.panomc.plugins.avatar.util

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

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

    /** Bigger images are refused before any pixel is decoded. */
    private const val MAX_SOURCE_PIXELS = 40_000_000L

    /** GIFs keep their animation, so they are stored as they are, only up to this size per side. */
    private const val MAX_GIF_DIMENSION = 1024

    /**
     * The avatar as it is stored: GIFs as uploaded (within [MAX_GIF_DIMENSION]), every other type decoded, scaled to
     * at most [maxDimension] per side and re-encoded as PNG (with transparency) or JPEG. Null when the file cannot be
     * read as an image or is too big.
     */
    fun normalize(file: File, mimeType: String, maxDimension: Int): Pair<ByteArray, String>? {
        ImageIO.createImageInputStream(file)?.use { input ->
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) {
                return null
            }

            val reader = readers.next()
            try {
                reader.setInput(input, true, true)

                val width = reader.getWidth(0)
                val height = reader.getHeight(0)

                if (width <= 0 || height <= 0 || width.toLong() * height.toLong() > MAX_SOURCE_PIXELS) {
                    return null
                }

                if (mimeType == "image/gif") {
                    if (width > MAX_GIF_DIMENSION || height > MAX_GIF_DIMENSION) {
                        return null
                    }

                    return file.readBytes() to mimeType
                }

                // Read only every n-th pixel of a big source, then scale the rest smoothly.
                val step = (maxOf(width, height) / (maxDimension * 2)).coerceAtLeast(1)
                val param = reader.defaultReadParam
                param.setSourceSubsampling(step, step, 0, 0)
                val source = reader.read(0, param) ?: return null

                val scale = minOf(1.0, maxDimension.toDouble() / maxOf(source.width, source.height))
                val targetWidth = (source.width * scale).toInt().coerceAtLeast(1)
                val targetHeight = (source.height * scale).toInt().coerceAtLeast(1)
                val hasAlpha = source.colorModel.hasAlpha()

                val target = BufferedImage(
                    targetWidth,
                    targetHeight,
                    if (hasAlpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
                )
                val graphics = target.createGraphics()
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null)
                graphics.dispose()

                val format = if (hasAlpha) "png" else "jpg"
                val output = ByteArrayOutputStream()
                if (!ImageIO.write(target, format, output)) {
                    return null
                }

                return output.toByteArray() to (if (hasAlpha) "image/png" else "image/jpeg")
            } catch (_: Exception) {
                return null
            } finally {
                reader.dispose()
            }
        }

        return null
    }

    /** The Content-Type for a stored avatar, or null when its extension is not a supported image. */
    fun mimeTypeOfFile(fileName: String): String? = MIME_BY_EXTENSION[fileName.substringAfterLast('.', "").lowercase()]
}
