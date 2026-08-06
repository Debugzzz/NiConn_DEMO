package com.niconn.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PtpDataCodecTest {
    @Test
    fun `parses object handle array`() {
        val data = byteArrayOf(3, 0, 0, 0, 0x29, 0x1E, 0x11, 0x53, 1, 0, 0, 0, 2, 0, 0, 0)
        assertEquals(listOf(0x53111E29, 1, 2), PtpDataCodec.parseObjectHandles(data))
    }

    @Test
    fun `extracts jpeg between SOI and EOI`() {
        val data = byteArrayOf(0, 0, 0, 0, 0xFF.toByte(), 0xD8.toByte(), 1, 2, 0xFF.toByte(), 0xD9.toByte(), 9, 9)
        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 0xFF.toByte(), 0xD9.toByte()),
            PtpDataCodec.extractJpeg(data),
        )
    }

    @Test
    fun `returns null when no jpeg markers`() {
        assertNull(PtpDataCodec.extractJpeg(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `parses prop desc enumeration form`() {
        // ISO 0x500F: UINT16, GetSet=1, default=100, current=100, FormFlag=2, count=4
        val data = byteArrayOf(
            0x0F, 0x50, // prop code
            0x04, 0x00, // UINT16
            0x01, // GetSet
            0x64, 0x00, // default 100
            0x64, 0x00, // current 100
            0x02, // Enumeration
            0x04, 0x00, // count 4
            0x64, 0x00, // 100
            0xC8.toByte(), 0x00, // 200
            0x90.toByte(), 0x01, // 400
            0x20, 0x03, // 800
        )
        assertEquals(listOf(100L, 200L, 400L, 800L), PtpDataCodec.parsePropDescEnums(data))
    }

    @Test
    fun `parses af frame from liveview header`() {
        val data = ByteArray(60)
        data[16] = 0x06; data[17] = 0x00 // whole width 1536 (big-endian)
        data[18] = 0x04; data[19] = 0x00 // whole height 1024
        data[41] = 1 // focus driving
        data[42] = 2 // focused
        data[44] = 1 // af area number
        data[48] = 0x01; data[49] = 0x40 // hSize 320
        data[50] = 0x01; data[51] = 0x20 // vSize 288
        data[52] = 0x03; data[53] = 0x00 // hCenter 768
        data[54] = 0x02; data[55] = 0x00 // vCenter 512
        val af = PtpDataCodec.parseAfFrame(data)
        assertEquals(320, af?.hSize)
        assertEquals(288, af?.vSize)
        assertEquals(768, af?.hCenter)
        assertEquals(512, af?.vCenter)
        assertEquals(2, af?.focusResult)
    }

    @Test
    fun `parses whole and image sizes from liveview header`() {
        val data = ByteArray(40)
        data[16] = 0x06; data[17] = 0x00 // whole width 1536
        data[18] = 0x04; data[19] = 0x00 // whole height 1024
        data[28] = 0x02; data[29] = 0x80.toByte() // image width 640
        data[30] = 0x01; data[31] = 0xA8.toByte() // image height 424
        val info = PtpDataCodec.parseViewInfo(data)
        assertEquals(1536, info?.wholeWidth)
        assertEquals(1024, info?.wholeHeight)
        assertEquals(640, info?.imageWidth)
        assertEquals(424, info?.imageHeight)
    }

    @Test
    fun `parses object format from object info`() {
        val data = byteArrayOf(0, 0, 0, 0, 0x01, 0x38, 0, 0, 0x10, 0, 0, 0)
        assertEquals(0x3801, PtpDataCodec.parseObjectFormat(data))
    }

    @Test
    fun `parses object info with filename and date`() {
        val bytes = java.io.ByteArrayOutputStream()
        bytes.write(byteArrayOf(0, 0, 0, 1)) // storage
        bytes.write(byteArrayOf(0x01, 0x38)) // format JPEG (LE)
        bytes.write(byteArrayOf(0, 0)) // protection
        bytes.write(byteArrayOf(0x10, 0, 0, 0)) // size 16 (LE)
        bytes.write(byteArrayOf(0, 0)) // thumb format
        bytes.write(byteArrayOf(0, 0, 0, 0)) // thumb size
        bytes.write(byteArrayOf(0, 0, 0, 0)) // thumb w
        bytes.write(byteArrayOf(0, 0, 0, 0)) // thumb h
        bytes.write(byteArrayOf(0x80.toByte(), 2, 0, 0)) // image w 640 (LE)
        bytes.write(byteArrayOf(0xA8.toByte(), 1, 0, 0)) // image h 424 (LE)
        bytes.write(byteArrayOf(0, 0, 0, 0x18)) // bit depth
        bytes.write(byteArrayOf(0, 0, 0, 2)) // parent
        bytes.write(byteArrayOf(0, 0)) // assoc type
        bytes.write(byteArrayOf(0, 0, 0, 0)) // assoc desc (4 bytes)
        bytes.write(byteArrayOf(0, 0, 0, 0)) // seq
        bytes.write("DSC_0001.JPG".toByteArray(Charsets.UTF_16LE))
        bytes.write(byteArrayOf(0, 0))
        bytes.write("2026:08:06 12:00:00".toByteArray(Charsets.UTF_16LE))
        bytes.write(byteArrayOf(0, 0))
        val info = PtpDataCodec.parseObjectInfo(bytes.toByteArray())
        assertEquals(0x3801, info?.format)
        assertEquals(16L, info?.compressedSize)
        assertEquals(640, info?.imageWidth)
        assertEquals(424, info?.imageHeight)
        assertEquals("DSC_0001.JPG", info?.filename)
        assertEquals("2026:08:06 12:00:00", info?.captureDate)
    }

    @Test
    fun `decodes device prop values`() {
        assertEquals(3200, DevicePropValueCodec.decodeUInt16(byteArrayOf(0x80.toByte(), 0x0C)))
        assertEquals(1_2500L, DevicePropValueCodec.decodeUInt32(byteArrayOf(0xD4.toByte(), 0x30, 0, 0)))
        assertEquals(-333, DevicePropValueCodec.decodeInt16(byteArrayOf(0xB3.toByte(), 0xFE.toByte())))
        assertArrayEquals(
            byteArrayOf(0x64, 0x00),
            DevicePropValueCodec.encode(DevicePropValueCodec.TYPE_UINT16, 100),
        )
    }
}
