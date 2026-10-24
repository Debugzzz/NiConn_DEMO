package com.niconn.app

/**
 * 缩放查看手势的纯数学部分（无 Android/Compose 依赖，可单测）。
 *
 * 坐标系：offset 为图片中心相对视图中心的平移（px）；scale ≥ 1；
 * 图片按 ContentScale.Fit 铺底（baseW/baseH 为 Fit 后的基础尺寸），
 * 实际显示尺寸 = base × scale。
 */

/** 给定缩放级别下，offset 允许的最大幅度（图片边缘恰好贴住视图边缘）。 */
internal fun maxPan(base: Float, scale: Float, view: Float): Float =
    maxOf(0f, (base * scale - view) / 2f)

/** 把 offset 钳制到图片范围内：1x 时恒为 0，放大后不能把图片边缘拖出屏幕。 */
internal fun clampPan(
    x: Float,
    y: Float,
    baseW: Float,
    baseH: Float,
    viewW: Float,
    viewH: Float,
    scale: Float,
): Pair<Float, Float> {
    val mx = maxPan(baseW, scale, viewW)
    val my = maxPan(baseH, scale, viewH)
    // 空间为 0 时固定 +0f，避免 -0f 参与相等比较
    val cx = if (mx == 0f) 0f else x.coerceIn(-mx, mx)
    val cy = if (my == 0f) 0f else y.coerceIn(-my, my)
    return cx to cy
}

/**
 * 围绕手指中心点缩放：保持 centroid 在屏幕上的位置不变。
 * 推导（TransformOrigin.Center）：o' = (c - center) × (1 - ratio) + o × ratio。
 */
internal fun zoomAboutCentroid(
    centroidX: Float,
    centroidY: Float,
    offsetX: Float,
    offsetY: Float,
    viewW: Float,
    viewH: Float,
    ratio: Float,
): Pair<Float, Float> {
    val cx = viewW / 2f
    val cy = viewH / 2f
    val x = (centroidX - cx) * (1f - ratio) + offsetX * ratio
    val y = (centroidY - cy) * (1f - ratio) + offsetY * ratio
    return x to y
}

/**
 * 双击目标：1x → 以点击点为中心放大到 targetScale；
 * 已放大 → 回到 1x（原点）。返回 (scale, x, y)。
 */
internal fun doubleTapTarget(
    tapX: Float,
    tapY: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    viewW: Float,
    viewH: Float,
    targetScale: Float,
    zoomedThreshold: Float = 1.05f,
): Triple<Float, Float, Float> =
    if (scale > zoomedThreshold) {
        Triple(1f, 0f, 0f)
    } else {
        val ratio = targetScale / scale
        val (x, y) = zoomAboutCentroid(tapX, tapY, offsetX, offsetY, viewW, viewH, ratio)
        Triple(targetScale, x, y)
    }
