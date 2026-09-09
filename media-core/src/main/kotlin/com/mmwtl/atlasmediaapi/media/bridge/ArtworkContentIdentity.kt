package com.mmwtl.atlasmediaapi.media.bridge

import android.graphics.Bitmap
import java.security.MessageDigest

/** Canonical pixel identity, independent of Bitmap allocation and generationId. */
object ArtworkContentIdentity {
    fun token(bitmap: Bitmap): String {
        val pixels = IntArray(bitmap.width)
        return token(bitmap.width, bitmap.height) { row ->
            bitmap.getPixels(pixels, 0, bitmap.width, 0, row, bitmap.width, 1)
            pixels
        }
    }

    fun token(width: Int, height: Int, rowPixels: (Int) -> IntArray): String {
        require(width > 0 && height > 0)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.updateInt(width)
        digest.updateInt(height)
        val bytes = ByteArray(width * Int.SIZE_BYTES)
        repeat(height) { row ->
            val pixels = rowPixels(row)
            require(pixels.size == width)
            pixels.forEachIndexed { index, pixel ->
                val offset = index * Int.SIZE_BYTES
                bytes[offset] = (pixel ushr 24).toByte()
                bytes[offset + 1] = (pixel ushr 16).toByte()
                bytes[offset + 2] = (pixel ushr 8).toByte()
                bytes[offset + 3] = pixel.toByte()
            }
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun MessageDigest.updateInt(value: Int) {
        update((value ushr 24).toByte())
        update((value ushr 16).toByte())
        update((value ushr 8).toByte())
        update(value.toByte())
    }
}
