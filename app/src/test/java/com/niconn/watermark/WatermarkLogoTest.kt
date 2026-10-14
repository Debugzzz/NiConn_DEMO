package com.niconn.watermark

import org.junit.Assert.assertEquals
import org.junit.Test

/** 品牌 Logo 资源选择（对齐上游 `logoMapper.getLogoPath` 的匹配语义）。 */
class WatermarkLogoTest {

    @Test
    fun `nikon make maps to the bundled nikon logo`() {
        assertEquals(WatermarkLogo.DEFAULT_ASSET, WatermarkLogo.assetFor("NIKON CORPORATION"))
        assertEquals(WatermarkLogo.DEFAULT_ASSET, WatermarkLogo.assetFor("Nikon"))
    }

    @Test
    fun `blank make falls back to the default logo`() {
        assertEquals(WatermarkLogo.DEFAULT_ASSET, WatermarkLogo.assetFor(null))
        assertEquals(WatermarkLogo.DEFAULT_ASSET, WatermarkLogo.assetFor(""))
        assertEquals(WatermarkLogo.DEFAULT_ASSET, WatermarkLogo.assetFor("   "))
    }

    @Test
    fun `unknown brand falls back to the default logo`() {
        // 目前仅内置尼康；按需求未知品牌也回落到尼康图
        assertEquals(WatermarkLogo.DEFAULT_ASSET, WatermarkLogo.assetFor("Canon"))
    }
}
