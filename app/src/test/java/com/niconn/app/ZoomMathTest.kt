package com.niconn.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomMathTest {
    // 视口 1000×2000，3:2 图片 Fit 后 1000×667
    private val viewW = 1000f
    private val viewH = 2000f
    private val baseW = 1000f
    private val baseH = 667f

    @Test
    fun `clamp pan is zero at 1x`() {
        assertEquals(0f to 0f, clampPan(500f, 500f, baseW, baseH, viewW, viewH, 1f))
    }

    @Test
    fun `clamp pan keeps image edges on screen when zoomed`() {
        // 2x：水平可拖 ±500，垂直 (667*2-2000)/2 < 0 → 0
        assertEquals(500f to 0f, clampPan(900f, 300f, baseW, baseH, viewW, viewH, 2f))
        assertEquals(-500f to 0f, clampPan(-900f, -300f, baseW, baseH, viewW, viewH, 2f))
    }

    @Test
    fun `zoom about centroid keeps the touched point stationary`() {
        // 从 1x 放大 2 倍，触摸点在 (250, 500)：新 offset 应让该点不动
        val (x, y) = zoomAboutCentroid(250f, 500f, 0f, 0f, viewW, viewH, 2f)
        // 该点变换后：center + (local-center)*s + o，local 由旧状态反解
        val cx = viewW / 2f
        val cy = viewH / 2f
        val localX = cx + (250f - 0f - cx) / 1f
        val localY = cy + (500f - 0f - cy) / 1f
        val screenX = cx + (localX - cx) * 2f + x
        val screenY = cy + (localY - cy) * 2f + y
        assertEquals(250f, screenX, 0.01f)
        assertEquals(500f, screenY, 0.01f)
    }

    @Test
    fun `double tap zooms in around tap point and resets when zoomed`() {
        val (s, _, _) = doubleTapTarget(250f, 500f, 1f, 0f, 0f, viewW, viewH, 2.5f)
        assertEquals(2.5f, s, 0.01f)
        val (s2, x2, y2) = doubleTapTarget(250f, 500f, 2.5f, 100f, 50f, viewW, viewH, 2.5f)
        assertEquals(1f, s2, 0.01f)
        assertEquals(0f to 0f, x2 to y2)
    }

    @Test
    fun `double tap at corner produces unclamped offset toward that corner`() {
        // 纯函数只负责"以点击点为中心"；边界钳制由 ZoomableState 层做
        val (_, x, y) = doubleTapTarget(10f, 10f, 1f, 0f, 0f, viewW, viewH, 2.5f)
        val expectedX = (10f - viewW / 2f) * (1f - 2.5f)
        val expectedY = (10f - viewH / 2f) * (1f - 2.5f)
        assertEquals(expectedX, x, 0.01f)
        assertEquals(expectedY, y, 0.01f)
    }
}
