package com.niconn.watermark

/**
 * 从 RAW/容器字节中提取**最大**的一段 JPEG（SOI…EOI）。
 *
 * 尼康 NEF 是 TIFF 容器，其中内嵌一张全尺寸 JPEG 预览——相册与看图软件显示的就是它，
 * 而 `BitmapFactory` 无法直接解码 NEF。这里按标记扫描取最大的那段，
 * 对 NEF 之外的容器（如带预览的其它 RAW）同样适用。
 *
 * 单趟扫描：内层找到 EOI 后外层直接跳到该段之后，整体 O(n)。
 */
internal fun extractLargestEmbeddedJpeg(bytes: ByteArray): ByteArray? {
    var best: ByteArray? = null
    var i = 0
    while (i + 1 < bytes.size) {
        if (bytes[i] == 0xFF.toByte() && bytes[i + 1] == 0xD8.toByte()) {
            var j = i + 2
            while (j + 1 < bytes.size &&
                !(bytes[j] == 0xFF.toByte() && bytes[j + 1] == 0xD9.toByte())
            ) {
                j++
            }
            if (j + 1 < bytes.size) {
                val end = j + 2
                if (best == null || end - i > best!!.size) best = bytes.copyOfRange(i, end)
                i = end
                continue
            }
        }
        i++
    }
    return best
}
