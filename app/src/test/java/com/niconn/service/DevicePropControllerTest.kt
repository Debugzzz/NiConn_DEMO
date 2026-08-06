package com.niconn.service

import com.niconn.protocol.ConnectionMode
import com.niconn.protocol.MockCameraServer
import com.niconn.protocol.PtpIpSession
import com.niconn.protocol.generateClientUuid
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePropControllerTest {
    @Test
    fun `reads iso and writes shutter value`() = runBlocking {
        MockCameraServer().use { server ->
            val session = PtpIpSession()
            val info = session.connect(
                host = "127.0.0.1",
                port = server.port,
                uuid = generateClientUuid(ConnectionMode.COMPUTER),
                clientName = "Test",
                mode = ConnectionMode.COMPUTER,
                pairingPhase = false,
            )
            session.attachSession(info.sessionId)
            val controller = DevicePropController(session)
            assertEquals(100L, controller.read(PropSpecs.ISO))
            assertEquals(listOf(100L, 200L, 400L), controller.supportedValues(PropSpecs.ISO))
            assertTrue(controller.write(PropSpecs.SHUTTER, 1_2500L))
            session.close()
        }
    }
}
