package com.niconn.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import java.io.ByteArrayInputStream

/** 按目标最大边长降采样解码 JPEG，避免大图 OOM。 */
fun decodeSampled(bytes: ByteArray, maxDim: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

/** 按 EXIF 方向 + 手动旋转角度解码（竖拍/横拍自动摆正）。 */
fun decodeSampledRotated(bytes: ByteArray, maxDim: Int, extraRotation: Int = 0): Bitmap? {
    val bitmap = decodeSampled(bytes, maxDim) ?: return null
    val exifDegrees = runCatching {
        when (ExifInterface(ByteArrayInputStream(bytes))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)
    val degrees = (exifDegrees + extraRotation) % 360
    if (degrees == 0) return bitmap
    return rotateBitmap(bitmap, degrees)
}

fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
    val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

data class PhotoExif(
    val iso: String?,
    val exposure: String?,
    val fNumber: String?,
    val focal: String?,
    val model: String?,
    val dateTime: String?,
)

/** 从 JPEG/RAW 字节解析拍摄参数（ISO/快门/光圈等都在 EXIF 里，ObjectInfo 没有）。 */
fun parseExif(bytes: ByteArray): PhotoExif? = try {
    val exif = ExifInterface(ByteArrayInputStream(bytes))
    PhotoExif(
        iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS),
        exposure = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME),
        fNumber = exif.getAttribute(ExifInterface.TAG_F_NUMBER),
        focal = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH),
        model = exif.getAttribute(ExifInterface.TAG_MODEL),
        dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME),
    )
} catch (e: Exception) {
    null
}
