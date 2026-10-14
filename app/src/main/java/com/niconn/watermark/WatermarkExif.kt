package com.niconn.watermark

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.niconn.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * EXIF 数据模型 + 读取与格式化工具（对应 web 版 useExif / editor/exif.ts）。
 */
data class WatermarkExif(
    val make: String? = null,
    val model: String? = null,
    val lensModel: String? = null,
    val focalLength: String? = null,
    val fNumber: String? = null,
    val exposureTime: String? = null,
    val iso: String? = null,
    val dateTimeOriginal: String? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
) {
    /**
     * 相机标注文本（品牌 + 型号）。
     *
     * 与上游 `watermark.ts` / `richText.ts` 的 `parts.join(sep)` 等价，但多一步去重：
     * 尼康等机型的 Model 本身就带品牌（如 `NIKON Z50_2`），而 Make 去 CORPORATION 后
     * 仍是 `NIKON`，直接拼接会得到 `NIKON NIKON Z50_2`（上游默认行为，其官方 demo 图
     * 即为该效果）。本实现在型号已含品牌时只保留型号，得到 `NIKON Z50_2`。
     *
     * @param separator 品牌与型号之间的分隔（经典版式单空格、艺术版式双空格）
     * @param showBrand 是否输出品牌
     * @param showModel 是否输出型号
     */
    fun cameraLabel(
        separator: String = " ",
        showBrand: Boolean = true,
        showModel: Boolean = true,
    ): String {
        val brand = make?.replace("CORPORATION", "")?.trim()?.takeIf { it.isNotEmpty() }
        val modelText = model?.trim()?.takeIf { it.isNotEmpty() }
        // 型号已以品牌开头时，品牌是冗余的
        val brandRedundant = brand != null && modelText != null &&
            modelText.uppercase().startsWith(brand.uppercase())
        val parts = ArrayList<String>(2)
        if (showBrand && brand != null && !(showModel && brandRedundant)) parts += brand
        if (showModel && modelText != null) parts += modelText
        return parts.joinToString(separator)
    }

    /** 拍摄参数展示文本（焦距 光圈 快门 ISO [时间]）。 */
    fun paramsLabel(showDateTime: Boolean = true): String {
        val parts = mutableListOf<String>()
        if (!lensModel.isNullOrBlank() && lensModel != "Unknown Lens") parts += lensModel!!
        if (!focalLength.isNullOrBlank()) parts += focalLength!!
        if (!fNumber.isNullOrBlank()) parts += fNumber!!
        if (!exposureTime.isNullOrBlank()) parts += exposureTime!!
        if (!iso.isNullOrBlank()) parts += iso!!
        if (showDateTime && !dateTimeOriginal.isNullOrBlank()) parts += dateTimeOriginal!!
        return parts.joinToString("  ")
    }

    companion object {
        private const val TAG = "WM_EXIF"

        /** 认为"读全了"的字段数下限（make/model/lens/focal/f/shutter/iso/date 共 8 项）。
         *  低于该值就继续尝试其它读取路径，避免部分解析的结果被当成最终值。 */
        private const val REQUIRED_FIELD_COUNT = 6

        /**
         * 从 Uri 读取 EXIF 与像素尺寸。
         *
         * 注意：这里必须把读取结果**显式构造**成 WatermarkExif 返回。早期实现先用
         * `val builder = WatermarkExif()`，再调用 `builder.copy(...)` 期望"就地更新"——
         * 但 data class 的 copy() 返回的是**新对象**，语句形式的调用会被直接丢弃，
         * 于是永远返回全 null 的实例，三种版式的文字全部为空（只画得出空水印条）。
         */
        suspend fun fromUri(context: Context, uri: Uri): WatermarkExif = withContext(Dispatchers.IO) {
            // 依次尝试三条读取路径，取**字段最全**的结果。
            // 关键：不能"非空即用"——某些 provider（如 Photo Picker）给出不可 seek 的
            // fd/管道时，ExifInterface 可能只解析出部分标签（表现为"只有镜头和时间"），
            // 若按"全部为空才回退"判断就会把残缺结果留在界面上。
            val viaFd = readExifViaFileDescriptor(context, uri)
            val viaStream = if ((viaFd?.fieldCount ?: 0) < REQUIRED_FIELD_COUNT) readExifViaStream(context, uri) else null
            var best = listOfNotNull(viaFd, viaStream).maxByOrNull { it.fieldCount }

            if ((best?.fieldCount ?: 0) < REQUIRED_FIELD_COUNT) {
                // 兜底：复制到临时文件后按文件路径读（文件必然可 seek，最稳）
                val viaTemp = readExifViaTempFile(context, uri)
                if (viaTemp != null && viaTemp.fieldCount > (best?.fieldCount ?: 0)) best = viaTemp
            }

            val raw = best
            if (raw == null || raw.fieldCount == 0) {
                AppLog.d(TAG, "EXIF 读取失败 uri=$uri scheme=${uri.scheme} auth=${uri.authority} " +
                    "fd=${viaFd?.fieldCount ?: "-"} stream=${viaStream?.fieldCount ?: "-"}")
            } else {
                AppLog.d(TAG, "EXIF 读取成功 字段数=${raw.fieldCount} uri=${uri.authority} ${raw.summary()}")
            }

            val size = readImageSize(context, uri)
            val source = raw ?: RawExif()

            WatermarkExif(
                make = clean(source.make),
                model = clean(source.model),
                lensModel = clean(source.lens),
                focalLength = source.focal?.let { parseFocal(it) },
                fNumber = source.fNumber?.let { parseFNumber(it) },
                exposureTime = source.shutter?.let { parseShutterExif(it) },
                iso = clean(source.iso)?.let { "ISO $it" },
                dateTimeOriginal = source.date?.let { formatDateTimeRaw(it) },
                imageWidth = size?.first,
                imageHeight = size?.second,
            )
        }

        /** 原始 EXIF 字符串（未格式化），便于在多种读取路径之间传递。 */
        private class RawExif(
            val make: String? = null,
            val model: String? = null,
            val lens: String? = null,
            val focal: String? = null,
            val fNumber: String? = null,
            val shutter: String? = null,
            val iso: String? = null,
            val date: String? = null,
        ) {
            val isEmpty: Boolean get() = fieldCount == 0

            /** 取到的字段个数，用于在多条读取路径之间挑选最完整的结果。 */
            val fieldCount: Int
                get() = listOf(make, model, lens, focal, fNumber, shutter, iso, date).count { it != null }

            /** 供诊断日志：哪些字段取到了值。 */
            fun summary(): String {
                val found = buildList {
                    if (make != null) add("make")
                    if (model != null) add("model")
                    if (lens != null) add("lens")
                    if (focal != null) add("focal")
                    if (fNumber != null) add("f")
                    if (shutter != null) add("shutter")
                    if (iso != null) add("iso")
                    if (date != null) add("date")
                }
                return if (found.isEmpty()) "空" else "字段=${found.joinToString(",")}"
            }
        }

        private fun readFrom(exif: ExifInterface) = RawExif(
            make = exif.getAttribute(ExifInterface.TAG_MAKE),
            model = exif.getAttribute(ExifInterface.TAG_MODEL),
            lens = exif.getAttribute(ExifInterface.TAG_LENS_MODEL),
            focal = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH),
            fNumber = exif.getAttribute(ExifInterface.TAG_F_NUMBER),
            shutter = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME),
            iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                ?: exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
                ?: exif.getAttribute(ExifInterface.TAG_ISO_SPEED),
            date = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME),
        )

        /**
         * 首选 `openFileDescriptor`：MediaStore / Photo Picker 的 content:// URI 通过
         * ParcelFileDescriptor 暴露的是**可 seek** 的 fd，而管道流不可 seek——
         * ExifInterface 在不可 seek 的流上可能读不到任何标签（表现为"相册能看到参数、
         * App 却读不到"）。fd 路径对这类 URI 最可靠。
         */
        private fun readExifViaFileDescriptor(context: Context, uri: Uri): RawExif? =
            runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    readFrom(ExifInterface(pfd.fileDescriptor))
                }
            }.onFailure {
                AppLog.d(TAG, "EXIF fd 读取失败: ${it::class.java.simpleName}: ${it.message}")
            }.getOrNull()

        private fun readExifViaStream(context: Context, uri: Uri): RawExif? =
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { fis -> readFrom(ExifInterface(fis)) }
            }.onFailure {
                AppLog.d(TAG, "EXIF 流读取失败: ${it::class.java.simpleName}: ${it.message}")
            }.getOrNull()

        /**
         * 兜底：复制到临时文件后按文件路径读。
         *
         * Photo Picker 等 provider 可能只给出不可 seek 的管道，`ExifInterface` 在那种
         * 流上会读不到任何标签（"相册能看到参数、App 读不到"的典型成因）。文件路径必然
         * 可 seek，因此这条路径最稳；仅在前面两条都返回空时才会走，不影响正常开销。
         */
        private fun readExifViaTempFile(context: Context, uri: Uri): RawExif? {
            val temp = runCatching { File.createTempFile("exif", ".tmp", context.cacheDir) }
                .getOrNull() ?: return null
            return try {
                val copied = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    } != null
                }.getOrDefault(false)
                if (!copied || temp.length() == 0L) return null
                readFrom(ExifInterface(temp.absolutePath))
            } catch (e: Exception) {
                AppLog.d(TAG, "EXIF 临时文件读取失败: ${e::class.java.simpleName}: ${e.message}")
                null
            } finally {
                runCatching { temp.delete() }
            }
        }

        /**
         * EXIF 字符串常带尾部 NUL 填充（尼康 LensModel 实测补 30 个 0x00），
         * 需清理后再参与排版与测量；全空则归一为 null。
         */
        private fun clean(raw: String?): String? =
            raw?.trim()?.trimEnd('\u0000')?.trim()?.takeIf { it.isNotEmpty() }

        /** 只读图片头拿像素尺寸（inJustDecodeBounds），不做完整解码。 */
        private fun readImageSize(context: Context, uri: Uri): Pair<Int, Int>? = runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
            if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
        }.getOrNull()

        /** 解析 "50/1" 或 "18-140/3.5-5.6" 为 "50mm"。 */
        private fun parseFocal(raw: String): String {
            val parts = raw.split("/")
            if (parts.size < 2) return raw + "mm"
            val num = parts[0].toDoubleOrNull()
            val den = parts[1].toDoubleOrNull()
            if (num == null || den == null || den == 0.0) return raw + "mm"
            return "${formatNum(num / den)}mm"
        }

        /** 解析 "28/10" 等为 "f/2.8"。 */
        private fun parseFNumber(raw: String): String {
            val parts = raw.split("/")
            if (parts.size < 2) return "f/$raw"
            val num = parts[0].toDoubleOrNull()
            val den = parts[1].toDoubleOrNull()
            if (num == null || den == null || den == 0.0) return "f/$raw"
            return "f/${formatNum(num / den)}"
        }

        /** 解析快门值：字符串或分数。 */
        private fun parseShutterExif(raw: String): String {
            val parts = raw.split("/")
            if (parts.size == 2) {
                val num = parts[0].toDoubleOrNull()
                val den = parts[1].toDoubleOrNull()
                if (num != null && den != null && den != 0.0) {
                    return formatShutter(num / den)
                }
            }
            val v = raw.toDoubleOrNull()
            return if (v != null) formatShutter(v) else "${raw}s"
        }

        /** 格式化快门值：>=1s 显示 "Xs"，<1s 显示 "1/XXs"。 */
        fun formatShutter(seconds: Double): String {
            if (seconds >= 1.0) {
                val rounded = Math.round(seconds * 10) / 10.0
                return "${formatNum(rounded)}s"
            }
            return "1/${Math.round(1.0 / seconds)}s"
        }

        /** "2026:08:05 14:30:00" -> "2026-08-05 14:30"。 */
        private fun formatDateTimeRaw(raw: String): String {
            val cleaned = raw.trim()
            if (cleaned.isEmpty()) return cleaned
            val withDash = cleaned.replaceFirst(Regex("^(\\d{4}):(\\d{2}):(\\d{2})"), "$1-$2-$3")
            // 去除秒（若格式是 HH:mm:ss）
            return withDash.replaceFirst(Regex("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}):\\d{2}$"), "$1")
        }

        /** 数字去多余小数位。 */
        fun formatNum(v: Double): String {
            val rounded = Math.round(v * 100) / 100.0
            return if (rounded == Math.floor(rounded) && !rounded.isInfinite()) {
                rounded.toLong().toString()
            } else {
                rounded.toString()
            }
        }

        // 保持 web 版的公共格式化入口
        fun formatFocal(value: Any?): String {
            val raw = value.toString().trim()
            return if (raw.contains("mm", ignoreCase = true)) raw else "${raw}mm"
        }

        fun formatFNumber(value: Any?): String {
            val raw = value.toString().trim()
            return if (raw.startsWith("f/", ignoreCase = true)) raw else "f/$raw"
        }

        fun formatExposure(value: Any?): String {
            val raw = value.toString().trim()
            if (raw.contains("/") || raw.contains("s", ignoreCase = true)) return raw
            val v = raw.toDoubleOrNull() ?: return raw
            return formatShutter(v)
        }

        fun formatIso(value: Any?): String {
            val raw = value.toString().trim()
            return if (raw.startsWith("ISO", ignoreCase = true)) raw else "ISO $raw"
        }
    }
}
