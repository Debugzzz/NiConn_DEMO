package com.niconn.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PtpIpCodecTest {
    @Test
    fun `parses real init request frame`() {
        // 抓包证据：length=0x3a(58), type=1, payload 50 bytes
        val raw = byteArrayOf(
            0x3a, 0, 0, 0, 1, 0, 0, 0,
            0x2d, 0xad.toByte(), 0x7e, 0x8f.toByte(), 0x61, 0x1e, 0x4d, 0x00,
            0x65, 0x00, 0x6e, 0x00, 0x64, 0x00, 0x72, 0x00,
            0x6f, 0x00, 0x69, 0x00, 0x64, 0x00, 0x20, 0x00,
            0x44, 0x00, 0x65, 0x00, 0x76, 0x00, 0x69, 0x00,
            0x63, 0x00, 0x65, 0x00, 0x00, 0x00, 0x00, 0x01,
            0x00,
            0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
        val frame = PtpIpCodec.parseFrame(raw, 0)!!
        assertEquals(58, frame.length)
        assertEquals(1, frame.packetType)
        assertEquals(50, frame.payload.size)
    }

    @Test
    fun `returns null for truncated frame`() {
        assertNull(PtpIpCodec.parseFrame(byteArrayOf(0x3a, 0, 0, 0, 1, 0, 0), 0))
    }

    @Test
    fun `encodes length first little endian`() {
        val bytes = PtpIpCodec.encodeFrame(6, byteArrayOf(1, 2, 3, 4))
        assertArrayEquals(byteArrayOf(12, 0, 0, 0, 6, 0, 0, 0, 1, 2, 3, 4), bytes)
    }
}
