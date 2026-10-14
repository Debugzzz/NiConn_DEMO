package com.niconn.protocol

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PtpIpSessionTest {
    // 注：以下两个用例原以真实抓包中的客户端 UUID 与主机名作为字节锚点。
    // 那些值属于设备配对凭据（相机在已配对状态下仅凭该 UUID 识别客户端），
    // 不应入库，已替换为结构等价的合成值——编码规则与断言强度不变。
    // 合成 UUID 的第 5-6 字节仍写入模式码（计算机 0d cc / 智能设备 61 1e），
    // 以保留该约定的文档价值；真实锚点字节请仅留在本地。

    @Test
    fun `init payload encodes uuid + UTF-16LE name + tail (computer mode)`() {
        val payload = buildInitPayload(
            uuid = "010203040dcc05060708090a0b0c0d0e".hexToByteArray(),
            mode = ConnectionMode.COMPUTER,
            name = "WTU-Test-Host",
        )
        val expected =
            "010203040dcc05060708090a0b0c0d0e" +
                "5700540055002d0054006500730074002d0048006f0073007400" +
                "000000000100"
        assertArrayEquals(expected.hexToByteArray(), payload)
    }

    @Test
    fun `init payload encodes uuid + UTF-16BE name + tail (smart device mode)`() {
        val payload = buildInitPayload(
            uuid = "11223344611e5566778899aabbccddee".hexToByteArray(),
            mode = ConnectionMode.SMART_DEVICE,
            name = "Android Device",
        )
        val expected =
            "11223344611e5566778899aabbccddee" +
                "0041006e00640072006f006900640020004400650076006900630065" +
                "000000000100"
        assertArrayEquals(expected.hexToByteArray(), payload)
    }

    @Test
    fun `parses pairing code from 0x952B response data`() {
        assertEquals("5077", parsePairingCode("0400000005000707".hexToByteArray()))
        assertEquals("4660", parsePairingCode("0400000004060600".hexToByteArray()))
        assertEquals(null, parsePairingCode("0400000005".hexToByteArray()))
    }

    @Test
    fun `connects and performs handshake against mock camera`() {
        MockCameraServer().use { server ->
            val session = PtpIpSession()
            runBlocking {
                val info = session.connect(
                    host = "127.0.0.1",
                    port = server.port,
                    uuid = generateClientUuid(ConnectionMode.COMPUTER),
                    clientName = "NiConn-Test",
                    mode = ConnectionMode.COMPUTER,
                )
                assertEquals("04b004550000100180013cbee158f492", info.guid)
                assertTrue(info.modelSerial.contains("Z50_2_8095684"))
                assertEquals("5077", info.pairingCode)
                session.close()
            }
        }
    }

    @Test
    fun `reconnect phase skips pairing commands`() {
        MockCameraServer().use { server ->
            val session = PtpIpSession()
            runBlocking {
                val info = session.connect(
                    host = "127.0.0.1",
                    port = server.port,
                    uuid = generateClientUuid(ConnectionMode.COMPUTER),
                    clientName = "NiConn-Test",
                    mode = ConnectionMode.COMPUTER,
                    pairingPhase = false,
                )
                assertEquals(null, info.pairingCode)
                assertEquals("Z50_2_8095684", info.modelSerial)

                val response = session.sendCommand(
                    CommandRequest(2, NkOps.DeviceReady, 5, emptyList()),
                )
                assertEquals(NkOps.OK, response.code)
                session.close()
            }
        }
    }
}

private fun String.hexToByteArray(): ByteArray =
    chunked(2).map { it.toInt(16).toByte() }.toByteArray()
