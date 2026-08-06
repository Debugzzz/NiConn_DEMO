package com.niconn.protocol

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PtpIpSessionTest {
    @Test
    fun `init payload matches real WTU capture bytes`() {
        val payload = buildInitPayload(
            uuid = "e9a0e1fd0dcc559cfd59daf757a92b53".hexToByteArray(),
            mode = ConnectionMode.COMPUTER,
            name = "WTU-Debugz-MacBook Pro",
        )
        val expected =
            "e9a0e1fd0dcc559cfd59daf757a92b53" +
                "5700540055002d00440065006200750067007a002d004d006100630042006f006f006b002000500072006f00" +
                "000000000100"
        assertArrayEquals(expected.hexToByteArray(), payload)
    }

    @Test
    fun `init payload matches real SnapBridge capture bytes`() {
        val payload = buildInitPayload(
            uuid = "2dad7e8f611e4d0ab71099abd75e89d6".hexToByteArray(),
            mode = ConnectionMode.SMART_DEVICE,
            name = "Android Device",
        )
        val expected =
            "2dad7e8f611e4d0ab71099abd75e89d6" +
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
