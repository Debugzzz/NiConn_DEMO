package com.niconn.watermark

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** NEF/RAW 内嵌 JPEG 预览提取（`BitmapFactory` 无法解码 NEF，据此取预览）。 */
class EmbeddedJpegTest {

    private val soi = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
    private val eoi = byteArrayOf(0xFF.toByte(), 0xD9.toByte())

    private fun jpeg(payloadSize: Int): ByteArray =
        soi + ByteArray(payloadSize) { 0x11 } + eoi

    @Test
    fun `extracts a single embedded jpeg`() {
        val jpeg = jpeg(10)
        val container = ByteArray(4) { 0x00 } + jpeg + ByteArray(4) { 0x00 }
        assertArrayEquals(jpeg, extractLargestEmbeddedJpeg(container))
    }

    @Test
    fun `returns the largest when several are embedded`() {
        val small = jpeg(5)
        val large = jpeg(200)
        val container = small + ByteArray(3) + large + ByteArray(3)
        val found = extractLargestEmbeddedJpeg(container)
        assertEquals(large.size, found?.size)
        assertArrayEquals(large, found)
    }

    @Test
    fun `returns null when there is no jpeg`() {
        assertNull(extractLargestEmbeddedJpeg(ByteArray(64) { 0x7F }))
        assertNull(extractLargestEmbeddedJpeg(ByteArray(0)))
    }

    @Test
    fun `ignores a truncated jpeg without end marker`() {
        val truncated = soi + ByteArray(32) { 0x22 }   // 有 SOI 无 EOI
        assertNull(extractLargestEmbeddedJpeg(truncated))
    }

    @Test
    fun `tolerates payload containing ff bytes`() {
        // 载荷里出现 0xFF 不应被误判为结束（只有 FF D9 才是）
        val payload = byteArrayOf(0x11, 0xFF.toByte(), 0x11, 0xFF.toByte(), 0x11)
        val container = soi + payload + eoi
        assertArrayEquals(container, extractLargestEmbeddedJpeg(container))
    }
}
