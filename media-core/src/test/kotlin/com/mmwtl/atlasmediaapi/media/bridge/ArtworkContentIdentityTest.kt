package com.mmwtl.atlasmediaapi.media.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ArtworkContentIdentityTest {
    @Test
    fun `separate decodes with identical pixels have the same token`() {
        val firstDecode = arrayOf(
            intArrayOf(0xff112233.toInt(), 0xff445566.toInt()),
            intArrayOf(0x00112233, 0xffffffff.toInt()),
        )
        val secondDecode = firstDecode.map(IntArray::clone).toTypedArray()

        val first = token(firstDecode)
        val second = token(secondDecode)

        assertEquals(first, second)
    }

    @Test
    fun `pixel content and dimensions participate in token`() {
        val original = arrayOf(intArrayOf(0xff112233.toInt(), 0xff445566.toInt()))
        val changed = arrayOf(intArrayOf(0xff112233.toInt(), 0xff445567.toInt()))
        val reshaped = arrayOf(
            intArrayOf(0xff112233.toInt()),
            intArrayOf(0xff445566.toInt()),
        )

        assertNotEquals(token(original), token(changed))
        assertNotEquals(token(original), token(reshaped))
    }

    private fun token(pixels: Array<IntArray>): String = ArtworkContentIdentity.token(
        width = pixels.first().size,
        height = pixels.size,
        rowPixels = pixels::get,
    )
}
