package com.niconn.app

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 全屏照片查看手势状态：
 * - 1x 时不可拖动；放大后拖动被钳制在图片范围内（边缘不出屏）
 * - 双指缩放围绕手指中心点
 * - 双击：1x → 放大至 doubleTapScale（以点击点为中心）；已放大 → 动画回 1x
 * - 松手带惯性滑动，越界回弹
 */
class ZoomableState(
    private val minScale: Float = 1f,
    private val maxScale: Float = 5f,
    val doubleTapScale: Float = 2.5f,
) {
    var scale by mutableFloatStateOf(1f)
        private set
    var offsetX by mutableFloatStateOf(0f)
        private set
    var offsetY by mutableFloatStateOf(0f)
        private set
    var viewW by mutableFloatStateOf(0f)
        private set
    var viewH by mutableFloatStateOf(0f)
        private set

    /** 图片 Fit 后的基础显示尺寸（随视图/图片变化重算）。 */
    var baseW by mutableFloatStateOf(0f)
        private set
    var baseH by mutableFloatStateOf(0f)
        private set

    /** 单指拖动是否生效（放大后才有意义）。 */
    val panEnabled: Boolean get() = scale > minScale + 0.01f

    fun onSizeChanged(w: Float, h: Float, contentW: Int, contentH: Int) {
        viewW = w
        viewH = h
        if (contentW > 0 && contentH > 0 && w > 0 && h > 0) {
            val fit = minOf(w / contentW, h / contentH)
            baseW = contentW * fit
            baseH = contentH * fit
        }
        clampHere()
    }

    fun applyZoom(centroid: Offset, zoom: Float) {
        if (zoom == 1f) return
        val newScale = (scale * zoom).coerceIn(minScale, maxScale)
        val ratio = newScale / scale
        val (x, y) = zoomAboutCentroid(centroid.x, centroid.y, offsetX, offsetY, viewW, viewH, ratio)
        scale = newScale
        offsetX = x
        offsetY = y
        clampHere()
    }

    fun applyPan(pan: Offset) {
        if (!panEnabled) return
        offsetX += pan.x
        offsetY += pan.y
        clampHere()
    }

    fun reset() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    /** 手势结束后的落点：带惯性的目标 offset（速度投影后钳制）。 */
    fun settleTarget(velocityX: Float, velocityY: Float): Triple<Float, Float, Float> {
        val (cx, cy) = clampPan(
            offsetX + velocityX * VELOCITY_PROJECTION,
            offsetY + velocityY * VELOCITY_PROJECTION,
            baseW, baseH, viewW, viewH, scale,
        )
        return Triple(scale, cx, cy)
    }

    fun doubleTapTarget(tap: Offset): Triple<Float, Float, Float> {
        val (s, x, y) = doubleTapTarget(
            tap.x, tap.y, scale, offsetX, offsetY, viewW, viewH, doubleTapScale,
        )
        val (cx, cy) = clampPan(x, y, baseW, baseH, viewW, viewH, s)
        return Triple(s, cx, cy)
    }

    suspend fun animateTo(target: Triple<Float, Float, Float>) {
        val (ts, tx, ty) = target
        val s0 = scale
        val x0 = offsetX
        val y0 = offsetY
        if (abs(ts - s0) < 0.001f && abs(tx - x0) < 0.5f && abs(ty - y0) < 0.5f) return
        animate(0f, 1f, animationSpec = tween(durationMillis = ANIM_MS)) { t, _ ->
            scale = s0 + (ts - s0) * t
            offsetX = x0 + (tx - x0) * t
            offsetY = y0 + (ty - y0) * t
        }
    }

    private fun clampHere() {
        val (x, y) = clampPan(offsetX, offsetY, baseW, baseH, viewW, viewH, scale)
        offsetX = x
        offsetY = y
    }

    private companion object {
        const val ANIM_MS = 220
        const val VELOCITY_PROJECTION = 0.12f
    }
}

/**
 * 手势修饰符：双指缩放（围绕手指中心）、放大后边界内拖动、双击切换缩放、松手惯性回弹。
 * 尺寸同步（[ZoomableState.onSizeChanged]）由调用方在视图/图片变化时执行。
 */
fun Modifier.zoomable(
    state: ZoomableState,
    scope: CoroutineScope,
): Modifier =
    pointerInput(state) {
        awaitEachGesture {
            val tracker = VelocityTracker()
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                if (zoom != 1f) state.applyZoom(event.calculateCentroid(useCurrent = true), zoom)
                if (pan != Offset.Zero) state.applyPan(pan)
                event.changes.firstOrNull()?.let {
                    tracker.addPosition(it.uptimeMillis, it.position)
                }
                if (zoom != 1f || pan != Offset.Zero) {
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
            val velocity = tracker.calculateVelocity()
            scope.launch { state.animateTo(state.settleTarget(velocity.x, velocity.y)) }
        }
    }.pointerInput(state) {
        detectTapGestures(
            onDoubleTap = { tap -> scope.launch { state.animateTo(state.doubleTapTarget(tap)) } },
        )
    }
