package com.niconn.watermark

import org.junit.Assert.assertEquals
import org.junit.Test

class WatermarkColorTest {

    @Test
    fun `parses hex colors`() {
        assertEquals(0xFF242424.toInt(), WatermarkColor.parse("#242424"))
        assertEquals(0xFFFFFFFF.toInt(), WatermarkColor.parse("#FFFFFF"))
        assertEquals(0xFF000000.toInt(), WatermarkColor.parse("#000"))
        // 8 位 hex = AARRGGBB
        assertEquals(0x80FF0000.toInt(), WatermarkColor.parse("#80FF0000"))
    }

    @Test
    fun `parses rgba functions`() {
        assertEquals(0xFFFF0000.toInt(), WatermarkColor.parse("rgb(255,0,0)"))
        assertEquals(0x80FF0000.toInt(), WatermarkColor.parse("rgba(255,0,0,0.5)"))
    }

    @Test
    fun `parses tuple format`() {
        assertEquals(0xFF242424.toInt(), WatermarkColor.parse("(36,36,36)"))
        assertEquals(0x80FFFFFF.toInt(), WatermarkColor.parse("(255,255,255,128)"))
    }

    @Test
    fun `parses named colors`() {
        assertEquals(0xFF000000.toInt(), WatermarkColor.parse("black"))
        assertEquals(0xFFFFFFFF.toInt(), WatermarkColor.parse("white"))
        assertEquals(0xFFFF0000.toInt(), WatermarkColor.parse("red"))
        assertEquals(0, WatermarkColor.parse("transparent"))
    }

    @Test
    fun `falls back on invalid`() {
        assertEquals(0xFF000000.toInt(), WatermarkColor.parse(null))
        assertEquals(0xFF000000.toInt(), WatermarkColor.parse(""))
        assertEquals(0xFF123456.toInt(), WatermarkColor.parse("nonsense", 0xFF123456.toInt()))
    }

    @Test
    fun `component extraction`() {
        val c = 0xAABBCCDD.toInt()
        assertEquals(0xAA, WatermarkColor.alpha(c))
        assertEquals(0xBB, WatermarkColor.red(c))
        assertEquals(0xCC, WatermarkColor.green(c))
        assertEquals(0xDD, WatermarkColor.blue(c))
    }
}
