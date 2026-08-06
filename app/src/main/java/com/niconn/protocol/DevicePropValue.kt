package com.niconn.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 尼康 PTP/IP 的 DevicePropValue：**只有裸值字节，无 DataType 前缀**
 * （SnapBridge b2/cd/mb 逐字节对照；属性码与值语义见 refs/z50ii-mtp.txt 6.5）。
 */
object DevicePropValueCodec {
    const val TYPE_INT16 = 0x0003
    const val TYPE_UINT16 = 0x0004
    const val TYPE_UINT32 = 0x0006

    fun decodeUInt16(data: ByteArray): Int {
        require(data.size >= 2) { "uint16 prop value too short" }
        return (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
    }

    fun decodeInt16(data: ByteArray): Int {
        val raw = decodeUInt16(data)
        return if (raw >= 0x8000) raw - 0x10000 else raw
    }

    fun decodeUInt32(data: ByteArray): Long {
        require(data.size >= 4) { "uint32 prop value too short" }
        var v = 0L
        for (i in 0 until 4) v = v or ((data[i].toLong() and 0xFF) shl (8 * i))
        return v
    }

    fun encode(type: Int, value: Long): ByteArray {
        return when (type) {
            TYPE_UINT16, TYPE_INT16 -> ByteBuffer.allocate(2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort(value.toShort())
                .array()
            else -> ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(value.toInt())
                .array()
        }
    }
}
