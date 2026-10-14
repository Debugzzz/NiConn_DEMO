package com.niconn.watermark

import org.junit.Assert.assertEquals
import org.junit.Test

class WatermarkExifTest {

    @Test
    fun `camera label strips corporation`() {
        val exif = WatermarkExif(make = "NIKON CORPORATION", model = "Z50_2")
        assertEquals("NIKON Z50_2", exif.cameraLabel())
    }

    @Test
    fun `camera label handles missing fields`() {
        assertEquals("Z50_2", WatermarkExif(model = "Z50_2").cameraLabel())
        assertEquals("NIKON", WatermarkExif(make = "NIKON").cameraLabel())
        assertEquals("", WatermarkExif().cameraLabel())
    }

    @Test
    fun `camera label avoids repeating the brand already present in the model`() {
        // 尼康机身的 Model 自带品牌；上游直接拼接会得到 "NIKON NIKON Z50_2"
        val exif = WatermarkExif(make = "NIKON CORPORATION", model = "NIKON Z50_2")
        assertEquals("NIKON Z50_2", exif.cameraLabel())
    }

    @Test
    fun `camera label honours the display toggles`() {
        val exif = WatermarkExif(make = "NIKON CORPORATION", model = "NIKON Z50_2")
        assertEquals("NIKON Z50_2", exif.cameraLabel(" ", showBrand = false, showModel = true))
        assertEquals("NIKON", exif.cameraLabel(" ", showBrand = true, showModel = false))
        assertEquals("", exif.cameraLabel(" ", showBrand = false, showModel = false))

        // 品牌不冗余时两者都输出
        val canon = WatermarkExif(make = "Canon", model = "Canon EOS R5")
        assertEquals("Canon EOS R5", canon.cameraLabel())
        assertEquals("Canon EOS R5", WatermarkExif(make = "Canon", model = "EOS R5").cameraLabel())
    }

    @Test
    fun `camera label separator is configurable for the art layout`() {
        val exif = WatermarkExif(make = "Canon", model = "EOS R5")
        assertEquals("Canon  EOS R5", exif.cameraLabel("  "))
    }

    @Test
    fun `params label orders and formats`() {
        val exif = WatermarkExif(
            lensModel = "NIKKOR Z 50mm f/1.8 S",
            focalLength = "50mm",
            fNumber = "f/1.8",
            exposureTime = "1/125s",
            iso = "ISO 100",
        )
        assertEquals(
            "NIKKOR Z 50mm f/1.8 S  50mm  f/1.8  1/125s  ISO 100",
            exif.paramsLabel(showDateTime = false),
        )
    }

    @Test
    fun `shutter formatting`() {
        assertEquals("1/1000s", WatermarkExif.Companion.formatShutter(0.001))
        assertEquals("2s", WatermarkExif.Companion.formatShutter(2.0))
        assertEquals("1/2s", WatermarkExif.Companion.formatShutter(0.5))
    }

    @Test
    fun `focal and aperture formatting`() {
        assertEquals("50mm", WatermarkExif.Companion.formatFocal("50"))
        assertEquals("50mm", WatermarkExif.Companion.formatFocal("50mm"))
        assertEquals("f/1.8", WatermarkExif.Companion.formatFNumber("1.8"))
        assertEquals("f/1.8", WatermarkExif.Companion.formatFNumber("f/1.8"))
    }
}
