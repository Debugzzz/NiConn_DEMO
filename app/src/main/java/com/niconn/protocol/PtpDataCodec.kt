package com.niconn.protocol

/**
 * PTP/IP 响应数据解析工具。
 */
object PtpDataCodec {
    /** ObjectInfo 数据集（规范 9.3；Z50II 实测多字节字段为大端）。 */
    data class ObjectInfoData(
        val format: Int,
        val compressedSize: Long,
        val imageWidth: Int,
        val imageHeight: Int,
        val filename: String?,
        val captureDate: String?,
    )

    /** LiveViewObject 头部的尺寸信息（规范 9.7）。 */
    data class LiveViewInfo(
        val wholeWidth: Int,
        val wholeHeight: Int,
        val imageWidth: Int,
        val imageHeight: Int,
    )

    /** LiveViewObject 头部中的 AF 框（规范 9.7，坐标为 Whole size）。 */
    data class AfFrame(
        val hSize: Int,
        val vSize: Int,
        val hCenter: Int,
        val vCenter: Int,
        val wholeWidth: Int,
        val wholeHeight: Int,
        val focusResult: Int,
        val focusDriving: Int,
    ) {
        val valid: Boolean
            get() = hSize > 0 && vSize > 0 && hCenter > 0 && vCenter > 0 &&
                wholeWidth > 0 && wholeHeight > 0
    }

    /** 解析 LiveViewObject 头部（版本 2.1），返回第一个有效 AF 框。 */
    fun parseAfFrame(data: ByteArray): AfFrame? {
        if (data.size < 56) return null
        val wholeWidth = bigEndianUShort(data, 16)
        val wholeHeight = bigEndianUShort(data, 18)
        val focusDriving = data[41].toInt() and 0xFF
        val focusResult = data[42].toInt() and 0xFF
        val afAreaNumber = data[44].toInt() and 0xFF
        if (afAreaNumber <= 0 || wholeWidth <= 0 || wholeHeight <= 0) return null
        return AfFrame(
            hSize = bigEndianUShort(data, 48),
            vSize = bigEndianUShort(data, 50),
            hCenter = bigEndianUShort(data, 52),
            vCenter = bigEndianUShort(data, 54),
            wholeWidth = wholeWidth,
            wholeHeight = wholeHeight,
            focusResult = focusResult,
            focusDriving = focusDriving,
        ).takeIf { it.valid }
    }

    /** 解析 Whole size / 图像尺寸，用于把点按像素坐标换算成 ChangeAfArea 需要的 Whole 坐标。 */
    fun parseViewInfo(data: ByteArray): LiveViewInfo? {
        if (data.size < 32) return null
        // Z50II 实测：LiveViewObject 头部多字节字段为大端（规范文档写小端）
        val wholeWidth = bigEndianUShort(data, 16)
        val wholeHeight = bigEndianUShort(data, 18)
        val imageWidth = bigEndianUShort(data, 28)
        val imageHeight = bigEndianUShort(data, 30)
        if (wholeWidth <= 0 || wholeHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) {
            return null
        }
        return LiveViewInfo(wholeWidth, wholeHeight, imageWidth, imageHeight)
    }

    /** GetObjectHandles 响应：NumElement(4LE) + ObjectHandle[NumElement](4LE)。 */
    fun parseObjectHandles(data: ByteArray): List<Int> {
        if (data.size < 4) return emptyList()
        val count = littleEndianInt(data, 0)
        val maxCount = (data.size - 4) / 4
        return (0 until minOf(count, maxCount)).map { littleEndianInt(data, 4 + it * 4) }
    }

    /** ObjectInfo 数据集：StorageID(4) + ObjectFormat(2) + ...（规范 9.2）。 */
    fun parseObjectFormat(data: ByteArray): Int? {
        if (data.size < 6) return null
        // Z50II 实测：ObjectInfo 是小端（与规范一致；LiveView 头部才是大端）
        return littleEndianUShort(data, 4)
    }

    /** 解析 ObjectInfo：固定 52 字节（AssociationDesc=4）+ 文件名/拍摄日期（UTF-16LE 空终止）。 */
    fun parseObjectInfo(data: ByteArray): ObjectInfoData? {
        if (data.size < 52) return null
        val format = littleEndianUShort(data, 4)
        val size = littleEndianUInt(data, 8)
        val width = littleEndianUInt(data, 26).toInt()
        val height = littleEndianUInt(data, 30).toInt()
        var offset = 52
        fun readUtf16(): String? {
            if (offset >= data.size) return null
            // 自动判断编码：奇数位为 0x00 → UTF-16LE；否则 ASCII/UTF-8
            val sampleLen = minOf(48, data.size - offset)
            var utf16 = sampleLen >= 2
            for (i in 1 until sampleLen step 2) {
                if (data[offset + i] != 0.toByte()) {
                    utf16 = false
                    break
                }
            }
            if (utf16) {
                var end = offset
                while (end + 1 < data.size && !(data[end] == 0.toByte() && data[end + 1] == 0.toByte())) {
                    end += 2
                }
                val text = String(data, offset, end - offset, Charsets.UTF_16LE)
                offset = minOf(end + 2, data.size)
                return text.ifBlank { null }
            }
            var end = offset
            while (end < data.size && data[end] != 0.toByte()) end++
            val text = String(data, offset, end - offset, Charsets.UTF_8)
            offset = minOf(end + 1, data.size)
            return text.ifBlank { null }
        }
        val filename = readUtf16()
        val captureDate = readUtf16()
        return ObjectInfoData(format, size, width, height, filename, captureDate)
    }

    /** 从 LiveViewObject 数据中提取 JPEG（SOI..EOI）。 */
    fun extractJpeg(data: ByteArray): ByteArray? {
        val start = indexOf(data, 0xFF.toByte(), 0xD8.toByte(), 0)
        if (start < 0) return null
        val end = indexOf(data, 0xFF.toByte(), 0xD9.toByte(), start + 2)
        if (end < 0) return null
        return data.copyOfRange(start, end + 2)
    }

    /**
     * 解析 GetDevicePropDesc(0x1014) 的枚举 Form（规范 9.4/9.6.2）：
     * PropCode(2) + DataType(2) + GetSet(1) + Default(DTS) + Current(DTS)
     * + FormFlag(1) + [NumberOfValue(2) + SupportedValue[DTS]]。
     * 仅 2 字节属性码（0x1014 不支持 4 字节码）。
     */
    fun parsePropDescEnums(data: ByteArray): List<Long> {
        if (data.size < 9) return emptyList()
        var offset = 2 // DevicePropCode（2 字节）
        val dataType = littleEndianUShort(data, offset)
        offset += 2
        offset += 1 // GetSet
        val dts = when (dataType) {
            0x0003, 0x0004 -> 2
            0x0005, 0x0006 -> 4
            else -> return emptyList()
        }
        offset += dts // Factory Default
        offset += dts // Current
        if (offset >= data.size) return emptyList()
        val formFlag = data[offset].toInt() and 0xFF
        offset += 1
        if (formFlag != 2) return emptyList()
        if (offset + 2 > data.size) return emptyList()
        val count = littleEndianUShort(data, offset)
        offset += 2
        val result = ArrayList<Long>(count)
        repeat(count) {
            if (offset + dts > data.size) return result
            result += if (dts == 2) {
                littleEndianUShort(data, offset).toLong()
            } else {
                littleEndianInt(data, offset).toLong() and 0xFFFFFFFFL
            }
            offset += dts
        }
        return result
    }

    private fun indexOf(data: ByteArray, b0: Byte, b1: Byte, from: Int): Int {
        var i = from
        while (i < data.size - 1) {
            if (data[i] == b0 && data[i + 1] == b1) return i
            i++
        }
        return -1
    }

    private fun littleEndianUShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun bigEndianUShort(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun bigEndianUInt(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
            (bytes[offset + 3].toLong() and 0xFF)

    private fun littleEndianUInt(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xFF) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24)

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}
