package com.niconn.watermark

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 水印渲染引擎（纯 Kotlin + android.graphics，可脱离 UI 测试）。
 *
 * 忠实复刻 photix-mark-web 的两个模板：
 * - [renderClassic] 经典版式 = `standard1` + `watermark` 处理器（底部四角文字水印条 + 右侧品牌 Logo）
 * - [renderArt]     艺术版式 = `blurBackground` 的 10 步处理器管线（模糊背景 + 圆角主图 + 底部文字）
 *
 * （个性文字版式 `personalText` 按需求未纳入本 App。）
 * 比例类参数一律按输入位图尺寸计算，与大分辨率原图兼容。
 */
object WatermarkRenderer {

    /**
     * 经典版式配置（对应 web `standard1` 模板 + `watermark` 处理器）。
     *
     * 颜色与字重沿用上游 standard1 的字面值：左上「品牌+型号」为 `#000000` 且粗体，
     * 左下镜头 / 右上参数 / 右下时间为 `#242424`，非粗体文字走 300（Light）字重。
     */
    data class ClassicConfig(
        val backgroundColor: Int = Color.WHITE,
        val textColor: Int = Color.parseColor("#242424"),
        /** 左上「品牌 + 型号」文字色（上游 standard1: `left_top.color = '#000000'`）。 */
        val brandColor: Int = Color.BLACK,
        val showBrand: Boolean = true,
        val showModel: Boolean = true,
        val showLens: Boolean = true,
        val showFocalLength: Boolean = true,
        val showAperture: Boolean = true,
        val showShutter: Boolean = true,
        val showISO: Boolean = true,
        val showDateTime: Boolean = true,
        val showLogo: Boolean = true,
        /** 水印条高度 = min(宽 × 该比例, 高 × 0.08)（上游 watermark.ts:28）。 */
        val watermarkHeightRatio: Float = 0.12f,
        /** 品牌 Logo（尼康方块图）；为 null 时右侧只排文字。 */
        val logo: Bitmap? = null,
    )

    /**
     * 艺术版式配置（对应 web `blurBackground` 模板的处理器常量与 userOptions）。
     *
     * [imageMargin] 同时驱动三处（上游 resize.ts / alignment.ts 的联动）：
     * 主图缩放 = 1 − margin、主图纵向权重 = −5%×(margin/0.12)、文字纵向权重 = −8%×(margin/0.12)。
     */
    data class ArtConfig(
        val textColor: Int = Color.WHITE,
        val shadowColor: Int = Color.BLACK,
        /** 主图边距（0.04–0.22），默认 0.12。 */
        val imageMargin: Float = 0.12f,
        /** 模糊 σ = 该比例 × 阴影画布高。 */
        val blurRadiusRatio: Float = 0.05f,
        /** 圆角半径 = 该比例 × 原图高。 */
        val cornerRadiusRatio: Float = 0.02f,
        /** 阴影半径 = 该比例 × 原图高。 */
        val shadowRadiusRatio: Float = 0.01f,
        /** 两行文字之间的间距 = 该比例 × 相机行层高。 */
        val textSpacingRatio: Float = 0.02f,
        val cameraLineHeightRatio: Float = 0.04f,
        val paramsLineHeightRatio: Float = 0.03f,
        val showBrand: Boolean = true,
        val showModel: Boolean = true,
        val showLens: Boolean = false,
        val showFocalLength: Boolean = true,
        val showAperture: Boolean = true,
        val showShutter: Boolean = true,
        val showISO: Boolean = true,
        val showDateTime: Boolean = false,
    )

    // ---------------- 入口 ----------------

    /**
     * 渲染经典版式（忠实复刻 web `standard1` + `watermark` 处理器）。
     *
     * 版面：底部白色水印条；条内四角排布文字，右侧为品牌 Logo 图片。
     * - 左上：品牌（去掉 `CORPORATION`）+ 型号，粗体，`#000000`
     * - 左下：镜头，缺失时回落到 `Unknown Lens`（上游模板的 `default()` 兜底）
     * - 右上：焦距 / 光圈 / 快门 / ISO（粗体）
     * - 右下：拍摄时间
     * - 右侧：品牌 Logo 方块，高度 = 水印条 × 0.8，右外边距 = 宽度 × 4%
     *
     * 单侧只有一个元素时该文字放大 1.15 倍并在条内垂直居中（上游 `singleElementMultiplier`）。
     * 右侧文字采用上游 `right_alignment: 'left'` 语义：两行左边缘对齐，
     * 使较宽一行的右边缘正好落在 Logo 左侧的预留位置。
     */
    fun renderClassic(source: Bitmap, exif: WatermarkExif, cfg: ClassicConfig): Bitmap {
        val width = source.width
        val height = source.height

        // 上游 watermark.ts:28-56 —— 水印条高度、间距、字号与三条基线参考线
        val metrics = WatermarkLayout.classicMetrics(width, height, cfg.watermarkHeightRatio)
        val wmHeight = metrics.watermarkBarHeight

        val out = Bitmap.createBitmap(width, height + wmHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(source, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        // 上游 watermark.ts:40-42 —— 水印背景条
        canvas.drawRect(
            0f, height.toFloat(), width.toFloat(), (height + wmHeight).toFloat(),
            Paint().apply { color = cfg.backgroundColor },
        )

        val watermarkY = height.toFloat()
        val commonSpacing = metrics.commonSpacing
        val baseFontSize = metrics.baseFontSize
        val largeFontSize = metrics.largeFontSize
        val textTopY = metrics.textTopY
        val textBottomY = metrics.textBottomY
        val textCenterY = metrics.textCenterY
        val singleMultiplier = WatermarkLayout.SINGLE_ELEMENT_MULTIPLIER

        // ---- 元素存在性判定（上游 watermark.ts:58-102）----
        // 品牌 + 型号（型号自带品牌时去重，见 WatermarkExif.cameraLabel）
        val brandText = exif.cameraLabel(" ", cfg.showBrand, cfg.showModel)

        // 上游 standard1 的 left_bottom 模板写死 default("Unknown Lens")：镜头缺失也要出字
        val lensText = if (cfg.showLens) {
            exif.lensModel?.takeIf { it.isNotBlank() } ?: "Unknown Lens"
        } else {
            ""
        }

        val paramParts = ArrayList<String>(4)
        if (cfg.showFocalLength) exif.focalLength?.takeIf { it.isNotBlank() }?.let { paramParts += it }
        if (cfg.showAperture) exif.fNumber?.takeIf { it.isNotBlank() }?.let { paramParts += it }
        if (cfg.showShutter) exif.exposureTime?.takeIf { it.isNotBlank() }?.let { paramParts += it }
        if (cfg.showISO) exif.iso?.takeIf { it.isNotBlank() }?.let { paramParts += it }
        val paramsText = paramParts.joinToString(" ")

        val dateText = if (cfg.showDateTime) {
            exif.dateTimeOriginal?.takeIf { it.isNotBlank() } ?: ""
        } else {
            ""
        }

        val leftCount = (if (brandText.isNotEmpty()) 1 else 0) + (if (lensText.isNotEmpty()) 1 else 0)
        val rightCount = (if (paramsText.isNotEmpty()) 1 else 0) + (if (dateText.isNotEmpty()) 1 else 0)

        val leftX = commonSpacing

        // ---- 左上：品牌 + 型号（上游 watermark.ts:111-145）----
        if (brandText.isNotEmpty()) {
            val single = leftCount == 1
            drawText(
                canvas, brandText, leftX,
                if (single) textCenterY else textTopY,
                if (single) "middle" else "bottom",
                cfg.brandColor,
                if (single) largeFontSize * singleMultiplier else largeFontSize,
                bold = true,
            )
        }

        // ---- 左下：镜头（上游 watermark.ts:148-167）----
        if (lensText.isNotEmpty()) {
            val single = leftCount == 1
            drawText(
                canvas, lensText, leftX,
                if (single) textCenterY else textBottomY,
                if (single) "middle" else "top",
                cfg.textColor,
                if (single) baseFontSize * singleMultiplier else baseFontSize,
                bold = false,
            )
        }

        // ---- 右侧 Logo 预留（上游 watermark.ts:169-212）----
        val logoHeight = wmHeight * 0.8f
        val logoRightMargin = width * 0.04f          // standard1: logo_right_margin = 0.04
        var rightX = width - logoRightMargin
        val logo = cfg.logo
        if (cfg.showLogo && logo != null && logo.height > 0) {
            val aspect = logo.width.toFloat() / logo.height.toFloat()
            val logoWidth = logoHeight * aspect
            val logoCenterX = width - logoRightMargin - logoWidth / 2f
            val logoCenterY = watermarkY + wmHeight / 2f
            canvas.drawBitmap(
                logo, null,
                RectF(
                    logoCenterX - logoWidth / 2f, logoCenterY - logoHeight / 2f,
                    logoCenterX + logoWidth / 2f, logoCenterY + logoHeight / 2f,
                ),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            rightX = logoCenterX - logoWidth / 2f - commonSpacing
        }
        // 上游 delimiter_color 为 'transparent'（分隔线不可见），故不绘制

        // ---- 右侧文字：right_alignment = 'left'（上游 watermark.ts:258-332）----
        // 两行取相同左边缘 minX = rightX - max(两行宽度)，较宽一行右边缘恰好落在 rightX
        val rightFontSize = if (rightCount == 1) baseFontSize * singleMultiplier else baseFontSize
        val paramsWidth = if (paramsText.isNotEmpty()) measureText(paramsText, rightFontSize, bold = true) else 0f
        val dateWidth = if (dateText.isNotEmpty()) measureText(dateText, rightFontSize, bold = false) else 0f
        val rightTextX = rightX - maxOf(paramsWidth, dateWidth)

        if (paramsText.isNotEmpty()) {
            val single = rightCount == 1
            drawText(
                canvas, paramsText, rightTextX,
                if (single) textCenterY else textTopY,
                if (single) "middle" else "bottom",
                cfg.textColor, rightFontSize, bold = true,
            )
        }
        if (dateText.isNotEmpty()) {
            val single = rightCount == 1
            drawText(
                canvas, dateText, rightTextX,
                if (single) textCenterY else textBottomY,
                if (single) "middle" else "top",
                cfg.textColor, rightFontSize, bold = false,
            )
        }

        return out
    }

    // ---------------- 艺术版式 ----------------

    /**
     * 渲染艺术版式（忠实复刻 web `blurBackground` 模板的 10 步处理器管线）。
     *
     * 管线：圆角 → 阴影画布 → 相机文字层 → 参数文字层 → 纵向合并 →
     * 模糊阴影画布 → 放大 1.15 作背景 → 缩到 (1−margin) 作主图 →
     * 主图居中偏上叠到背景 → 文字块居中贴底。
     *
     * 注意两处易错点（均按上游照抄）：
     * 1. 模糊与主图都取自**阴影画布**（含四周透明 padding），不是原图；
     * 2. 输出尺寸为 `round(1.15·trunc(W+0.04H)) × round(1.15·trunc(1.04H))`，
     *    比原图**大**，不是等尺寸输出。
     */
    fun renderArt(source: Bitmap, exif: WatermarkExif, cfg: ArtConfig): Bitmap {
        // 上游 shadow.ts → resize.ts → alignment.ts 的尺寸与落位（纯计算集中在 WatermarkLayout）
        val art = WatermarkLayout.artMetrics(
            width = source.width,
            height = source.height,
            imageMargin = cfg.imageMargin,
            blurRadiusRatio = cfg.blurRadiusRatio,
            shadowRadiusRatio = cfg.shadowRadiusRatio,
            cameraLineHeightRatio = cfg.cameraLineHeightRatio,
            paramsLineHeightRatio = cfg.paramsLineHeightRatio,
        )

        // Step 1 圆角（roundedCorner.ts）：radius = border_radius × H0
        val rounded = roundCorners(source, cfg.cornerRadiusRatio * source.height)

        // Step 2 阴影画布（shadow.ts）：单边 padding = 2·sr
        val pad = cfg.shadowRadiusRatio * source.height * 2f
        val stage = shadowStage(
            rounded, art.stageWidth, art.stageHeight, pad,
            cfg.shadowRadiusRatio * source.height, cfg.shadowColor,
        )
        if (rounded !== source) rounded.recycle()

        // Step 3/4 文字层（richText.ts）：层高基准 = 阴影画布高，字号 = 层高 × 0.8
        val cameraLayer = textLayer(
            buildCameraLine(exif, cfg), art.cameraLineHeight, cfg.textColor, bold = true,
        )
        val paramsLayer = textLayer(
            buildParamsLine(exif, cfg), art.paramsLineHeight, cfg.textColor, bold = false,
        )
        // Step 5 纵向合并（concat.ts）：间距 = 0.02 × 第一层高度
        val textBlock = concatVertical(
            listOfNotNull(cameraLayer, paramsLayer),
            cfg.textSpacingRatio,
            cameraLayer?.height ?: 0,
        )

        // Step 6 模糊（blur.ts）：作用于阴影画布
        val blurred = decalBlur(stage, art.blurSigma)

        val out = Bitmap.createBitmap(art.outputWidth, art.outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        // 上游末层无底色，透明处导出 JPEG 时会合成到黑底（参考图四角发暗即此因）；
        // 这里显式铺黑，保证预览与保存结果一致。
        canvas.drawColor(Color.BLACK)
        val filter = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(
            blurred, null,
            RectF(0f, 0f, art.outputWidth.toFloat(), art.outputHeight.toFloat()), filter,
        )
        blurred.recycle()

        // Step 7/9 主图叠到背景（alignment.ts）：居中，纵向再上移 5%×(margin/0.12)
        canvas.drawBitmap(
            stage, null,
            RectF(art.photoX, art.photoY, art.photoX + art.mainWidth, art.photoY + art.mainHeight),
            filter,
        )
        stage.recycle()

        // Step 10 文字块居中贴底（alignment.ts）：底边留 8%×(margin/0.12)
        if (textBlock != null) {
            canvas.drawBitmap(
                textBlock,
                (art.outputWidth - textBlock.width) / 2f,
                art.outputHeight - textBlock.height - art.textBottomInset,
                filter,
            )
            textBlock.recycle()
        }

        return out
    }

    /** 相机行（richText.ts auto_camera）：品牌 + 型号，双空格分隔，型号含品牌时去重。 */
    private fun buildCameraLine(exif: WatermarkExif, cfg: ArtConfig): String =
        exif.cameraLabel("  ", cfg.showBrand, cfg.showModel)

    /**
     * 参数行（richText.ts auto_params）：镜头 → 焦距 → 光圈 → 快门 → ISO → 时间，
     * 次序固定（与开关无关），双空格分隔。
     */
    private fun buildParamsLine(exif: WatermarkExif, cfg: ArtConfig): String {
        val parts = ArrayList<String>(6)
        if (cfg.showLens) exif.lensModel?.takeIf { it.isNotBlank() }?.let { parts += it }
        if (cfg.showFocalLength) exif.focalLength?.takeIf { it.isNotBlank() }?.let { parts += it }
        if (cfg.showAperture) exif.fNumber?.takeIf { it.isNotBlank() }?.let { parts += it }
        if (cfg.showShutter) exif.exposureTime?.takeIf { it.isNotBlank() }?.let { parts += it }
        if (cfg.showISO) exif.iso?.takeIf { it.isNotBlank() }?.let { parts += it }
        if (cfg.showDateTime) exif.dateTimeOriginal?.takeIf { it.isNotBlank() }?.let { parts += it }
        return parts.joinToString("  ")
    }

    /**
     * Step 1：圆角。复刻上游 `roundedCorner.ts` 的**二次贝塞尔**路径
     * （比真圆弧内收 0.043r，不能用 `Path.addRoundRect`）。
     */
    private fun roundCorners(src: Bitmap, radius: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        if (radius <= 0f) {
            canvas.drawBitmap(src, 0f, 0f, paint)
            return out
        }
        val w = src.width.toFloat()
        val h = src.height.toFloat()
        val r = radius.coerceAtMost(min(w, h) / 2f)
        val path = Path().apply {
            moveTo(r, 0f)
            lineTo(w - r, 0f); quadTo(w, 0f, w, r)
            lineTo(w, h - r); quadTo(w, h, w - r, h)
            lineTo(r, h); quadTo(0f, h, 0f, h - r)
            lineTo(0f, r); quadTo(0f, 0f, r, 0f)
            close()
        }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(src, 0f, 0f, paint)
        canvas.restore()
        return out
    }

    /**
     * Step 2：阴影画布（上游 `shadow.ts`）。
     *
     * Canvas 的 `shadowBlur = sr` 对应高斯 σ = sr/2；Android `setShadowLayer` 的
     * radius 对应 σ ≈ 0.57735·radius + 0.5，故反解 `radius = (sr/2 − 0.5)/0.57735`，
     * 偏移 `(0, 0.3·sr)`，颜色纯黑。Bitmap 上的 Canvas 是软件画布，位图阴影可用。
     */
    private fun shadowStage(
        src: Bitmap, outW: Int, outH: Int, pad: Float, sr: Float, color: Int,
    ): Bitmap {
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            setShadowLayer(((sr / 2f - 0.5f) / 0.57735f).coerceAtLeast(0.1f), 0f, sr * 0.3f, color)
        }
        Canvas(out).drawBitmap(src, pad, pad, paint)
        return out
    }

    /**
     * Step 3/4：单行文字层（上游 `richText.ts`）。
     *
     * 层宽 = `ceil(measureText)`，层高 = `trunc(height)`（WebIDL 截断），
     * 字号 = 未截断的 `height × 0.8`，文字以 `middle` 基线居中于 `(层宽/2, height/2)`。
     */
    private fun textLayer(text: String, height: Float, color: Int, bold: Boolean): Bitmap? {
        if (text.isBlank() || height < 1f) return null
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = height * 0.8f
            typeface = typefaceFor(bold)
            textAlign = Paint.Align.CENTER
        }
        val width = ceil(paint.measureText(text).toDouble()).toInt().coerceAtLeast(1)
        val layer = Bitmap.createBitmap(width, height.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        Canvas(layer).drawText(text, width / 2f, baselineY(height / 2f, "middle", paint), paint)
        return layer
    }

    /**
     * Step 5：纵向拼接文字层（上游 `concat.ts`）。
     * 间距 = [spacingRatio] × 第一层高度；各层在合并层内水平居中；y 累加保留小数。
     */
    private fun concatVertical(layers: List<Bitmap>, spacingRatio: Float, firstHeight: Int): Bitmap? {
        if (layers.isEmpty()) return null
        val spacing = spacingRatio * firstHeight
        val maxWidth = layers.maxOf { it.width }
        val totalHeight = layers.sumOf { it.height } + spacing * (layers.size - 1)
        val out = Bitmap.createBitmap(maxWidth, totalHeight.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        var y = 0f
        layers.forEach { layer ->
            canvas.drawBitmap(layer, (maxWidth - layer.width) / 2f, y, paint)
            y += layer.height + spacing
            layer.recycle()
        }
        return out
    }

    /**
     * Step 6：带 decal 语义的模糊（上游 `blur.ts` 用 CSS `filter: blur(σpx)`）。
     *
     * CSS 模糊把画布外视为全透明，四周 alpha 因此向外衰减——艺术版式参考图边缘
     * 偏暗正源于此。`RenderEffect(CLAMP)` 与常见 stack blur 都不衰减，故自行实现：
     * 滑动窗口 Box Blur 迭代三次近似高斯，窗口越界一律按透明（0）计入。
     */
    private fun decalBlur(src: Bitmap, sigma: Float): Bitmap {
        if (sigma <= 0.5f) return src.copy(Bitmap.Config.ARGB_8888, false)
        // 半径很大时先降采样：σ 同比缩小，视觉几乎无差但快一个数量级
        var factor = 1
        while ((src.width / (factor * 2)).toLong() * (src.height / (factor * 2)) > 1_600_000L) {
            factor *= 2
        }
        var current = if (factor > 1) {
            Bitmap.createScaledBitmap(src, src.width / factor, src.height / factor, true)
        } else {
            src.copy(Bitmap.Config.ARGB_8888, false)
        }
        // 三次 Box 迭代逼近高斯：按 SVG 规范的盒半径换算
        val radius = WatermarkLayout.boxRadius(sigma / factor)
        repeat(3) {
            val pass1 = boxPass(current, radius, horizontal = true)
            current.recycle()
            current = boxPass(pass1, radius, horizontal = false)
            pass1.recycle()
        }
        return if (factor > 1) {
            Bitmap.createScaledBitmap(current, src.width, src.height, true).also { current.recycle() }
        } else {
            current
        }
    }

    /** 分离式 Box Blur 单趟：滑动窗口累加（O(N)，与半径无关），越界按透明计入。 */
    private fun boxPass(src: Bitmap, radius: Int, horizontal: Boolean): Bitmap {
        val w = src.width
        val h = src.height
        val srcPixels = IntArray(w * h)
        src.getPixels(srcPixels, 0, w, 0, 0, w, h)
        val dstPixels = IntArray(w * h)
        val window = radius * 2 + 1
        val lineLength = if (horizontal) w else h
        val lineCount = if (horizontal) h else w
        val base0 = if (horizontal) 0 else 1
        val step = if (horizontal) 1 else w

        for (line in 0 until lineCount) {
            val base = if (horizontal) line * w else line
            var a = 0; var r = 0; var g = 0; var b = 0
            // 初始窗口：越界处补 0（decal 语义）
            for (i in -radius..radius) {
                if (i in 0 until lineLength) {
                    val c = srcPixels[base + i * step]
                    a += c ushr 24 and 0xFF; r += c ushr 16 and 0xFF
                    g += c ushr 8 and 0xFF; b += c and 0xFF
                }
            }
            for (i in 0 until lineLength) {
                dstPixels[base + i * step] =
                    ((a / window) shl 24) or ((r / window) shl 16) or
                        ((g / window) shl 8) or (b / window)
                val outIndex = i - radius
                if (outIndex >= 0) {
                    val c = srcPixels[base + outIndex * step]
                    a -= c ushr 24 and 0xFF; r -= c ushr 16 and 0xFF
                    g -= c ushr 8 and 0xFF; b -= c and 0xFF
                }
                val inIndex = i + radius + 1
                if (inIndex < lineLength) {
                    val c = srcPixels[base + inIndex * step]
                    a += c ushr 24 and 0xFF; r += c ushr 16 and 0xFF
                    g += c ushr 8 and 0xFF; b += c and 0xFF
                }
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(dstPixels, 0, w, 0, 0, w, h)
        }
    }

    // ---------------- 内部工具 ----------------

    /**
     * 按 web Canvas textBaseline 语义计算 Android drawText 的 Y 值。
     * - "top":    文字顶部在 y
     * - "bottom": 文字底部在 y（含 descent）
     * - "middle": 文字中线在 y
     */
    private fun baselineY(rawY: Float, baseline: String, paint: Paint): Float {
        val fm = paint.fontMetrics
        return when (baseline) {
            "top" -> rawY - fm.top
            "middle" -> rawY - (fm.ascent + fm.descent) / 2f
            else -> rawY - fm.bottom  // bottom
        }
    }

    /** 左侧文字：按基线绘制。 */
    private fun drawText(
        canvas: Canvas, text: String, x: Float, y: Float, baseline: String,
        color: Int, fontSize: Float, bold: Boolean,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = fontSize
            typeface = typefaceFor(bold)
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText(text, x, baselineY(y, baseline, paint), paint)
    }

    /**
     * 字重映射（上游 `richText.ts:85` / `watermark.ts:141,310` 的语义）：
     * `is_bold` 为真取 700，否则取 **300（Light）**——不是常规 400。
     * 用系统 `sans-serif-light` / `sans-serif` 对应，避免合成加粗改变字宽。
     */
    private fun typefaceFor(bold: Boolean): Typeface =
        if (bold) Typeface.create("sans-serif", Typeface.BOLD)
        else Typeface.create("sans-serif-light", Typeface.NORMAL)

    /** 按字号测量文本宽度（用于经典版式右侧对齐计算）。 */
    private fun measureText(text: String, fontSize: Float, bold: Boolean): Float =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = fontSize
            typeface = typefaceFor(bold)
        }.measureText(text)

    /** 解析相对尺寸（0-1 视作比例，否则视作像素）。 */
    private fun resolve(value: Float, reference: Float, fallbackRatio: Float): Float {
        if (value > 0f && value <= 1f) return value * reference
        return value.coerceAtLeast(0f)
    }
}
