package com.niconn.watermark

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 版式几何的纯逻辑测试。
 *
 * 这些公式被 [WatermarkRenderer] 实际调用（旧版测试锁定的是一批渲染器从不调用的函数，
 * 属于"测了没在跑的代码"）；黄金值经独立复算核对，其中艺术版式一组还能反解出上游
 * `public/demo/1_背景模糊.JPG`（1080×728）的原图尺寸 915×609，可交叉验证公式链路。
 */
class WatermarkLayoutTest {

    // ---------------- 经典版式 ----------------

    @Test
    fun `classic bar height takes the smaller of width and height constraints`() {
        // 横图受高度约束：min(4000×0.12, 3000×0.08) = 240
        assertEquals(240, WatermarkLayout.classicMetrics(4000, 3000).watermarkBarHeight)
        // 竖图受宽度约束：min(1000×0.12, 3000×0.08) = 120
        assertEquals(120, WatermarkLayout.classicMetrics(1000, 3000).watermarkBarHeight)
    }

    @Test
    fun `classic bar height never collapses to zero`() {
        assertEquals(1, WatermarkLayout.classicMetrics(4, 4).watermarkBarHeight)
    }

    @Test
    fun `classic font sizes and spacing are ratios of bar height and width`() {
        val m = WatermarkLayout.classicMetrics(4000, 3000)
        assertEquals(240 * 0.25f, m.largeFontSize, 0.001f)
        assertEquals(240 * 0.2f, m.baseFontSize, 0.001f)
        assertEquals(4000 * 0.02f, m.commonSpacing, 0.001f)
    }

    @Test
    fun `classic baseline guides straddle the bar centre`() {
        val m = WatermarkLayout.classicMetrics(4000, 3000)
        val expectedCentre = 3000f + 240 / 2f
        assertEquals(expectedCentre, m.textCenterY, 0.001f)
        assertEquals(expectedCentre - 240 * 0.05f / 2f, m.textTopY, 0.001f)
        assertEquals(expectedCentre + 240 * 0.05f / 2f, m.textBottomY, 0.001f)
    }

    // ---------------- 艺术版式 ----------------

    @Test
    fun `art stage output and main sizes follow upstream rounding`() {
        // shadow.ts: padding = 2·sr 且画布尺寸截断 → resize.ts: ×1.15 / ×(1−margin) 且四舍五入
        val m = WatermarkLayout.artMetrics(6000, 4000)
        assertEquals(6160, m.stageWidth)
        assertEquals(4160, m.stageHeight)
        assertEquals(7084, m.outputWidth)
        assertEquals(4784, m.outputHeight)
        assertEquals(5421, m.mainWidth)
        assertEquals(3661, m.mainHeight)
    }

    @Test
    fun `art dimensions reproduce the upstream demo image geometry`() {
        val m = WatermarkLayout.artMetrics(915, 609)
        assertEquals(939, m.stageWidth)
        assertEquals(633, m.stageHeight)
        assertEquals(1080, m.outputWidth)   // = 上游 demo 实际尺寸
        assertEquals(728, m.outputHeight)
        assertEquals(826, m.mainWidth)
        assertEquals(557, m.mainHeight)
        assertEquals(127f, m.photoX, 0.001f)
        assertEquals(49.1f, m.photoY, 0.001f)
    }

    @Test
    fun `art portrait image stays portrait`() {
        val m = WatermarkLayout.artMetrics(4000, 6000)
        assertEquals(4240, m.stageWidth)
        assertEquals(6240, m.stageHeight)
        assertEquals(4876, m.outputWidth)
        assertEquals(7176, m.outputHeight)
    }

    @Test
    fun `art text inset and photo shift derive from the margin ratio`() {
        val m = WatermarkLayout.artMetrics(1000, 1000)
        // margin 默认 0.12 → ratio 1 → 文字底边留 8%、主图上移 5%
        assertEquals(0.08f * m.outputHeight, m.textBottomInset, 0.01f)
        val centred = (m.outputHeight - m.mainHeight) / 2f
        assertEquals(centred - 0.05f * m.outputHeight, m.photoY, 0.01f)
    }

    @Test
    fun `art margin is clamped to the upstream slider range`() {
        val belowRange = WatermarkLayout.artMetrics(1000, 1000, imageMargin = 0.01f)
        val atMin = WatermarkLayout.artMetrics(1000, 1000, imageMargin = 0.04f)
        assertEquals(atMin.mainWidth, belowRange.mainWidth)

        val aboveRange = WatermarkLayout.artMetrics(1000, 1000, imageMargin = 0.9f)
        val atMax = WatermarkLayout.artMetrics(1000, 1000, imageMargin = 0.22f)
        assertEquals(atMax.mainWidth, aboveRange.mainWidth)
    }

    @Test
    fun `art blur sigma and text line heights derive from the stage height`() {
        val m = WatermarkLayout.artMetrics(6000, 4000)
        assertEquals(0.05f * m.stageHeight, m.blurSigma, 0.01f)
        assertEquals(0.04f * m.stageHeight, m.cameraLineHeight, 0.01f)
        assertEquals(0.03f * m.stageHeight, m.paramsLineHeight, 0.01f)
    }

    // ---------------- Box 半径 ----------------

    @Test
    fun `box radius follows the svg three-pass approximation`() {
        // σ·3·√(2π)/4 ≈ σ·1.8799
        assertEquals(188, WatermarkLayout.boxRadius(100f))
        assertEquals(2, WatermarkLayout.boxRadius(1f))
    }

    @Test
    fun `box radius is at least one`() {
        assertEquals(1, WatermarkLayout.boxRadius(0.1f))
        assertEquals(1, WatermarkLayout.boxRadius(0f))
    }
}
