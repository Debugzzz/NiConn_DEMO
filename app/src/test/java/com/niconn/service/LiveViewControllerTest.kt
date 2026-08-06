package com.niconn.service

import com.niconn.protocol.ConnectionMode
import com.niconn.protocol.MockCameraServer
import com.niconn.protocol.PtpIpSession
import com.niconn.protocol.generateClientUuid
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveViewControllerTest {
    @Test
    fun `polls frames until stopped`() = runBlocking {
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
            val controller = LiveViewController(session)
            var error: String? = null
            controller.start { error = it }
            delay(350)
            controller.stop()
            delay(100)
            assertNull(error)
            assertArrayEquals(MockCameraServer.MOCK_JPEG, controller.frame.value?.jpeg)
            session.close()
        }
    }
}
