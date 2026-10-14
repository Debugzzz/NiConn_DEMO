package com.niconn.watermark

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 水印版式的纯几何计算（不依赖 Android，可在 JVM 单测）。
 *
 * 这里的公式全部与 photix-mark-web 的处理器逐行对应，并被 [WatermarkRenderer] 实际调用；
 * 取整规则也照抄上游（画布尺寸用 WebIDL 截断、`resize` 用 `Math.round`）。
 */
object WatermarkLayout {

    /** 单侧只有一行文字时的放大倍数（上游 `singleElementMultiplier`）。 */
    const val SINGLE_ELEMENT_MULTIPLIER = 1.15f

    // ---------------- 经典版式（watermark.ts）----------------

    /**
     * 经典版式水印条的关键坐标与字号。
     *
     * 上游 watermark.ts:28-56：水印条高 = `min(宽×0.12, 高×0.08)`，
     * 间距按条高与图宽推导，字号取条高的 20% / 25%。
     */
    data class ClassicMetrics(
        val watermarkBarHeight: Int,
        val commonSpacing: Float,
        val middleSpacing: Float,
        val textTopY: Float,
        val textBottomY: Float,
        val textCenterY: Float,
        val baseFontSize: Float,
        val largeFontSize: Float,
    )

    fun classicMetrics(width: Int, height: Int, heightRatio: Float = 0.12f): ClassicMetrics {
        val barHeight = min(width * heightRatio, height * 0.08f).roundToInt().coerceAtLeast(1)
        val commonSpacing = width * 0.02f
        val middleSpacing = barHeight * 0.05f
        val centerY = height + barHeight / 2f
        return ClassicMetrics(
            watermarkBarHeight = barHeight,
            commonSpacing = commonSpacing,
            middleSpacing = middleSpacing,
            textTopY = centerY - middleSpacing / 2f,
            textBottomY = centerY + middleSpacing / 2f,
            textCenterY = centerY,
            baseFontSize = barHeight * 0.2f,
            largeFontSize = barHeight * 0.25f,
        )
    }

    // ---------------- 艺术版式（blurBackground 管线）----------------

    /**
     * 艺术版式各阶段画布尺寸与落位。
     *
     * 对应上游 shadow.ts（padding = 2×阴影半径）→ resize.ts（×1.15 背景、×(1−margin) 主图）
     * → alignment.ts（主图居中上移 5%、文字块贴底留 8%，权重均按 `margin/0.12` 缩放）。
     */
    data class ArtMetrics(
        val stageWidth: Int,
        val stageHeight: Int,
        val outputWidth: Int,
        val outputHeight: Int,
        val mainWidth: Int,
        val mainHeight: Int,
        val blurSigma: Float,
        val cameraLineHeight: Float,
        val paramsLineHeight: Float,
        val photoX: Float,
        val photoY: Float,
        /** 文字块底边距画布底部的距离。 */
        val textBottomInset: Float,
    )

    fun artMetrics(
        width: Int,
        height: Int,
        imageMargin: Float = 0.12f,
        blurRadiusRatio: Float = 0.05f,
        shadowRadiusRatio: Float = 0.01f,
        cameraLineHeightRatio: Float = 0.04f,
        paramsLineHeightRatio: Float = 0.03f,
    ): ArtMetrics {
        // 阴影画布：单边 padding = 2×sr，尺寸按 WebIDL 截断
        val sr = shadowRadiusRatio * height
        val pad = sr * 2f
        val stageWidth = (width + pad * 2f).toInt()
        val stageHeight = (height + pad * 2f).toInt()

        // 背景放大 1.15 即最终输出；主图缩到 (1 − margin)
        val outputWidth = (stageWidth * 1.15f).roundToInt()
        val outputHeight = (stageHeight * 1.15f).roundToInt()
        val margin = imageMargin.coerceIn(0.04f, 0.22f)
        val mainWidth = (stageWidth * (1f - margin)).roundToInt()
        val mainHeight = (stageHeight * (1f - margin)).roundToInt()

        // 权重随 margin 等比缩放（margin = 0.12 时为 1）
        val ratio = margin / 0.12f
        return ArtMetrics(
            stageWidth = stageWidth,
            stageHeight = stageHeight,
            outputWidth = outputWidth,
            outputHeight = outputHeight,
            mainWidth = mainWidth,
            mainHeight = mainHeight,
            blurSigma = blurRadiusRatio * stageHeight,
            cameraLineHeight = cameraLineHeightRatio * stageHeight,
            paramsLineHeight = paramsLineHeightRatio * stageHeight,
            photoX = (outputWidth - mainWidth) / 2f,
            photoY = (outputHeight - mainHeight) / 2f - 0.05f * ratio * outputHeight,
            textBottomInset = 0.08f * ratio * outputHeight,
        )
    }

    /**
     * 三次 Box 迭代逼近高斯时的盒半径。
     *
     * 上游用 CSS `filter: blur(σ)`；本实现以滑动窗口 Box Blur 迭代三次近似，
     * 半径按 SVG 规范的换算 `σ·3·√(2π)/4` ≈ `σ·1.88` 取整（至少 1）。
     */
    fun boxRadius(sigma: Float): Int = maxOf(1, (sigma * 1.88f + 0.5f).toInt())
}
