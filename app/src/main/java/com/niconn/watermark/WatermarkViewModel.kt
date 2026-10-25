package com.niconn.watermark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.niconn.app.AppLanguage
import com.niconn.app.R
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

/** 版式枚举（仅保留复刻自 photix-mark-web 的经典与艺术两种）。 */
enum class WatermarkStyle(@StringRes val labelRes: Int) {
    CLASSIC(R.string.wm_style_classic),
    ART(R.string.wm_style_art),
}

/** 渲染阶段状态。 */
sealed interface WatermarkState {
    data object Idle : WatermarkState
    data class NoImage(@StringRes val hintRes: Int = R.string.wm_pick) : WatermarkState
    data object Loading : WatermarkState
    data class Ready(val exif: WatermarkExif) : WatermarkState
    data class Error(val message: String) : WatermarkState
}

class WatermarkViewModel : ViewModel() {

    private val _state = MutableStateFlow<WatermarkState>(WatermarkState.Idle)
    val state: StateFlow<WatermarkState> = _state.asStateFlow()

    private val _style = MutableStateFlow(WatermarkStyle.CLASSIC)
    val style: StateFlow<WatermarkStyle> = _style.asStateFlow()

    private val _exif = MutableStateFlow<WatermarkExif?>(null)
    val exif: StateFlow<WatermarkExif?> = _exif.asStateFlow()

    private val _rendered = MutableStateFlow<Bitmap?>(null)
    val rendered: StateFlow<Bitmap?> = _rendered.asStateFlow()

    /** 渲染是否进行中。 */
    var isRendering by mutableStateOf(false)
        private set

    // 显示内容开关：两个版式共用（上游两个模板的 exifFields 是同一组键，默认全开）。
    // 品牌不作为可选项：型号本身就带品牌（NIKON Z50_2 / Xiaomi 15），
    // 由 WatermarkExif.cameraLabel 去重后不会重复。
    var showModel by mutableStateOf(true)
    var showLens by mutableStateOf(true)
    var showFocal by mutableStateOf(true)
    var showAperture by mutableStateOf(true)
    var showShutter by mutableStateOf(true)
    var showISO by mutableStateOf(true)
    var showDateTime by mutableStateOf(true)
    /** 经典版式专属：右侧品牌 Logo。 */
    var showLogo by mutableStateOf(true)

    // 艺术版式参数
    var artImageMargin by mutableFloatStateOf(0.12f)
    var artBlurRadius by mutableFloatStateOf(0.05f)

    // 最近使用的 Uri（当前编辑照片）
    private var currentUri: Uri? = null

    private var sourceBitmap: Bitmap? = null

    /** 当前照片的品牌 Logo（按 EXIF Make 选，默认尼康）；渲染经典版式时传入。 */
    private var brandLogo: Bitmap? = null

    fun selectImage(context: Context, uri: Uri) {
        currentUri = uri
        viewModelScope.launch {
            _state.value = WatermarkState.Loading
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    decodeSampledBitmap(context, uri, maxDim = 2400)
                } ?: throw IllegalStateException(AppLanguage.tr("无法解码所选图片", "Cannot decode the selected image"))
                sourceBitmap?.let { if (it !== bitmap) it.recycle() }
                sourceBitmap = bitmap
                com.niconn.AppLog.d(
                    "WM",
                    "选图 uri=$uri scheme=${uri.scheme} 解码=${bitmap.width}x${bitmap.height}",
                )
                val exifData = WatermarkExif.fromUri(context, uri)
                _exif.value = exifData
                com.niconn.AppLog.d(
                    "WM",
                    "EXIF 结果 相机='${exifData.cameraLabel()}' 镜头=${exifData.lensModel} " +
                        "焦距=${exifData.focalLength} 光圈=${exifData.fNumber} " +
                        "快门=${exifData.exposureTime} ISO=${exifData.iso} 时间=${exifData.dateTimeOriginal}",
                )
                brandLogo = WatermarkLogo.forMake(context, exifData.make)
                _state.value = WatermarkState.Ready(exifData)
                _rendered.value = null
                // 选图后立即出图：此前这里只清空预览而不触发渲染，
                // 必须切一次版式才会显示，属于明显的行为缺陷。
                render()
            } catch (e: Exception) {
                com.niconn.AppLog.d("WM", "选图失败: ${e::class.java.simpleName}: ${e.message}")
                _state.value = WatermarkState.Error(e.message ?: AppLanguage.tr("读取图片失败", "Failed to load image"))
            }
        }
    }

    fun selectStyle(style: WatermarkStyle) {
        _style.value = style
        render()
    }

    fun updateAndRender() {
        render()
    }

    /** 触发渲染（协程池内执行模糊等耗时操作）。 */
    fun render() {
        val bmp = sourceBitmap ?: return
        val exif = _exif.value ?: return
        if (isRendering) return
        viewModelScope.launch {
            isRendering = true
            val result = withContext(Dispatchers.Default) {
                try {
                    when (_style.value) {
                        WatermarkStyle.CLASSIC -> WatermarkRenderer.renderClassic(
                            bmp, exif,
                            WatermarkRenderer.ClassicConfig(
                                showModel = showModel,
                                showLens = showLens,
                                showFocalLength = showFocal,
                                showAperture = showAperture,
                                showShutter = showShutter,
                                showISO = showISO,
                                showDateTime = showDateTime,
                                showLogo = showLogo,
                                logo = brandLogo,
                            ),
                        )
                        // 艺术版式共用同一组显示开关（上游两个模板的 exifFields 键相同）
                        WatermarkStyle.ART -> WatermarkRenderer.renderArt(
                            bmp, exif,
                            WatermarkRenderer.ArtConfig(
                                imageMargin = artImageMargin,
                                blurRadiusRatio = artBlurRadius,
                                showModel = showModel,
                                showLens = showLens,
                                showFocalLength = showFocal,
                                showAperture = showAperture,
                                showShutter = showShutter,
                                showISO = showISO,
                                showDateTime = showDateTime,
                            ),
                        )
                    }
                } catch (e: Exception) {
                    _state.value = WatermarkState.Error(e.message ?: AppLanguage.tr("渲染失败", "Rendering failed"))
                    null
                }
            }
            _rendered.value = result
            isRendering = false
        }
    }

    /** 保存渲染结果到 [WatermarkStorage] 的成品目录，返回文件绝对路径。 */
    suspend fun saveResult(context: Context, bitmap: Bitmap): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = WatermarkStorage.ensureFolder(context)
            val fileName = "watermark_${System.currentTimeMillis()}_${Random.nextInt(1000)}.jpg"
            val file = File(dir, fileName)
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
            }
            file.absolutePath
        }
    }

    /** 解码采样位图，防止内存溢出。 */
    /**
     * 解码采样位图。
     *
     * 支持 JPG 与 NEF（尼康 RAW）：`BitmapFactory` 读不了 NEF，此时改为提取文件内
     * **内嵌的 JPEG 预览**再解码——NEF 会内嵌一张全尺寸 JPEG，正是相册/看图软件所用的那份。
     */
    private fun decodeSampledBitmap(context: Context, uri: Uri, maxDim: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        val w = opts.outWidth
        val h = opts.outHeight
        if (w > 0 && h > 0) {
            var sample = 1
            while (w / (sample * 2) >= maxDim || h / (sample * 2) >= maxDim) sample *= 2
            val decode = BitmapFactory.Options().apply { inSampleSize = sample }
            return context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decode)
            }
        }
        // BitmapFactory 无法识别（典型为 NEF/RAW）：退回内嵌 JPEG 预览
        val preview = context.contentResolver.openInputStream(uri)?.use { extractLargestEmbeddedJpeg(it.readBytes()) }
        if (preview == null) {
            com.niconn.AppLog.d("WM", "解码失败：既非可解码图片，也未找到内嵌 JPEG 预览")
            return null
        }
        com.niconn.AppLog.d("WM", "RAW 内嵌 JPEG 预览 ${preview.size} B")
        val previewOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(preview, 0, preview.size, previewOpts)
        var sample = 1
        while (previewOpts.outWidth / (sample * 2) >= maxDim ||
            previewOpts.outHeight / (sample * 2) >= maxDim
        ) {
            sample *= 2
        }
        return BitmapFactory.decodeByteArray(
            preview, 0, preview.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }
}

