package com.niconn.service

import com.niconn.protocol.ConnectionMode

/**
 * 调试用「已配对兼容身份」（正式配对流程可用后移除）。
 *
 * 相机按 Init 载荷中的 16 字节客户端 UUID（+名称）识别已配对客户端。
 * 这两个 UUID 来自真实抓包（session A / A2-A3），用于验证 mDNS→TCP→Init→ACK→命令链路。
 */
object CompatIdentity {
    /** 会话 A 抓包：SnapBridge「连接至智能设备」身份。 */
    val snapBridge = ClientIdentity(
        name = "Android Device",
        uuid = "2dad7e8f611e4d0ab71099abd75e89d6".hexToBytes(),
    )

    /** 会话 A2/A3 抓包：WTU「连接到计算机」身份。 */
    val wtu = ClientIdentity(
        name = "WTU-Debugz-MacBook Pro",
        uuid = "e9a0e1fd0dcc559cfd59daf757a92b53".hexToBytes(),
    )

    fun forMode(mode: ConnectionMode): ClientIdentity = when (mode) {
        ConnectionMode.SMART_DEVICE -> snapBridge
        ConnectionMode.COMPUTER -> wtu
    }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
