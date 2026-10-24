package com.niconn.service

import com.niconn.protocol.ConnectionMode
import com.niconn.protocol.MockCameraServer
import com.niconn.protocol.PtpIpSession
import com.niconn.protocol.generateClientUuid
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryControllerTest {
    @Test
    fun `lists thumbs downloads and deletes`() = runBlocking {
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
            val controller = GalleryController(session)
            assertEquals(listOf(1, 2, 3), controller.listHandles())
            assertEquals(0x3801, controller.objectFormat(1))
            assertArrayEquals(MockCameraServer.MOCK_JPEG, controller.loadThumb(1))
            assertArrayEquals(MockCameraServer.MOCK_JPEG, controller.loadLargeThumb(1))
            assertArrayEquals(MockCameraServer.MOCK_JPEG, controller.loadImage(1))
            assertTrue(controller.delete(1))
            session.close()
        }
    }

    @Test
    fun `formatByHandle labels all objects in three filtered queries`() = runBlocking {
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
            val controller = GalleryController(session)
            assertEquals(listOf(2), controller.listHandlesFor(0x3000))
            assertEquals(
                mapOf(1 to 0x3801, 2 to 0x3000, 3 to 0x3801),
                controller.formatByHandle(),
            )
            assertEquals("NEF", GalleryController.formatLabel(0x3000))
            assertEquals("JPEG", GalleryController.formatLabel(0x3801))
            session.close()
        }
    }
}
