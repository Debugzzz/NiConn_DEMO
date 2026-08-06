package com.niconn.service

import com.niconn.protocol.DevicePropValueCodec
import com.niconn.protocol.NkOps
import com.niconn.protocol.PtpDataCodec
import com.niconn.protocol.PtpIpSession

enum class PropType(val mtoCode: Int) {
    UINT16(DevicePropValueCodec.TYPE_UINT16),
    INT16(DevicePropValueCodec.TYPE_INT16),
    UINT32(DevicePropValueCodec.TYPE_UINT32),
}

object PropSpecs {
    data class PropSpec(val code: Int, val type: PropType)

    val ISO = PropSpec(0x500F, PropType.UINT16)
    val SHUTTER = PropSpec(0x500D, PropType.UINT32)
    val APERTURE = PropSpec(0x5007, PropType.UINT16)
    val EV = PropSpec(0x5010, PropType.INT16)
    val AF_AREA_MODE = PropSpec(0xD05D, PropType.UINT16)
}

/**
 * 设备属性读取/写入（Z50II MTP 规范 6.5）：
 * 读取 0x1015，写入 0x943C（数据阶段携带 DevicePropValue 数据集）。
 */
class DevicePropController(private val session: PtpIpSession) {
    /** 相机实际支持的枚举值（GetDevicePropDesc 0x1014）。 */
    suspend fun supportedValues(spec: PropSpecs.PropSpec): List<Long> {
        val response = session.sendCommand(NkOps.GetDevicePropDesc, listOf(spec.code))
        if (response.code != NkOps.OK) return emptyList()
        return PtpDataCodec.parsePropDescEnums(response.data)
    }

    suspend fun read(spec: PropSpecs.PropSpec): Long? {
        val response = session.sendCommand(NkOps.GetDevicePropValue, listOf(spec.code))
        if (response.code != NkOps.OK) return null
        return when (spec.type) {
            PropType.UINT16 -> DevicePropValueCodec.decodeUInt16(response.data).toLong()
            PropType.INT16 -> DevicePropValueCodec.decodeInt16(response.data).toLong()
            PropType.UINT32 -> DevicePropValueCodec.decodeUInt32(response.data)
        }
    }

    suspend fun write(spec: PropSpecs.PropSpec, value: Long): Boolean {
        val payload = DevicePropValueCodec.encode(spec.type.mtoCode, value)
        // SnapBridge fb/cd/mb：写入用 0x1016 SetDevicePropValue（2 字节属性码 + 裸值数据）。
        return session.sendCommandWithData(NkOps.SetDevicePropValue, listOf(spec.code), payload)
            .code == NkOps.OK
    }
}
