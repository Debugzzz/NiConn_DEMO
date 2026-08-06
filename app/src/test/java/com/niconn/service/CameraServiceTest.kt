package com.niconn.service

import com.niconn.discovery.CameraDiscovery
import com.niconn.discovery.CameraInfo
import com.niconn.discovery.DiscoveryHandle
import com.niconn.protocol.CommandRequest
import com.niconn.protocol.CommandResponse
import com.niconn.protocol.ConnectionMode
import com.niconn.protocol.NkOps
import com.niconn.protocol.PtpIpSession
import com.niconn.protocol.SessionInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraServiceTest {
    private val camera = CameraInfo(
        instanceName = "Z50_2_8095684",
        host = "127.0.0.1",
        port = 15740,
        guid = "04b00455-0000-1001-8001-3cbee158f492",
        vid = "A",
        pid = "455",
        apps = "PAIR",
    )

    @Test
    fun `state machine reaches session and handles error`() = runBlocking {
        val discovered = CompletableDeferred<CameraInfo>()
        val fakeDiscovery = object : CameraDiscovery {
            override fun discover(
                scope: kotlinx.coroutines.CoroutineScope,
                onFound: (CameraInfo) -> Unit,
            ): DiscoveryHandle {
                onFound(camera)
                return DiscoveryHandle {}
            }
        }
        val fakeSession = FakeSession(ok = true)
        val service = CameraService(
            discovery = fakeDiscovery,
            sessionFactory = { fakeSession },
            identity = ClientIdentity("NiConn", ByteArray(16)),
        )

        assertEquals(ConnectionState.Idle, service.state.value)
        service.startDiscovery { discovered.complete(it) }
        assertEquals(camera, discovered.await())
        service.connect(camera)
        val session = awaitState(service) { it is ConnectionState.Session }
        assertTrue(session is ConnectionState.Session)
        assertEquals("Z50_2_8095684", (session as ConnectionState.Session).camera.instanceName)
    }

    @Test
    fun `connect failure maps to error state`() = runBlocking {
        val service = CameraService(
            discovery = FakeNoopDiscovery,
            sessionFactory = { FakeSession(ok = false) },
            identity = ClientIdentity("NiConn", ByteArray(16)),
        )
        service.connect(camera)
        val state = awaitState(service) { it is ConnectionState.Error }
        assertTrue(state is ConnectionState.Error)
        assertTrue((state as ConnectionState.Error).recoverable)
    }

    @Test
    fun `pairing phase reports PairingComplete with code`() = runBlocking {
        val service = CameraService(
            discovery = FakeNoopDiscovery,
            sessionFactory = { FakePairingSession() },
            identity = ClientIdentity("NiConn", ByteArray(16)),
        )
        service.connect(camera)
        val state = awaitState(service) { it is ConnectionState.PairingComplete }
        assertEquals("5077", (state as ConnectionState.PairingComplete).code)
    }

    private fun awaitState(
        service: CameraService,
        predicate: (ConnectionState) -> Boolean,
    ): ConnectionState {
        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline) {
            val state = service.state.value
            if (predicate(state)) return state
            Thread.sleep(10)
        }
        error("state did not reach expected value: ${service.state.value}")
    }

    private class FakeSession(private val ok: Boolean) : PtpIpSession() {
        override suspend fun connect(
            host: String,
            port: Int,
            uuid: ByteArray,
            clientName: String,
            mode: ConnectionMode,
            bufferSize: Int,
            onPairingCode: suspend (String) -> Boolean,
            pairingPhase: Boolean,
        ): SessionInfo {
            if (!ok) throw java.io.IOException("connect refused")
            return SessionInfo(
                guid = "04b004550000100180013cbee158f492",
                modelSerial = "Z50_2_8095684",
                sessionId = 1,
            )
        }

        override suspend fun sendCommand(request: CommandRequest): CommandResponse =
            CommandResponse(NkOps.OK, request.txn, 0)
    }

    private object FakeNoopDiscovery : CameraDiscovery {
        override fun discover(
            scope: kotlinx.coroutines.CoroutineScope,
            onFound: (CameraInfo) -> Unit,
        ): DiscoveryHandle = DiscoveryHandle {}
    }

    private class FakePairingSession : PtpIpSession() {
        override suspend fun connect(
            host: String,
            port: Int,
            uuid: ByteArray,
            clientName: String,
            mode: ConnectionMode,
            bufferSize: Int,
            onPairingCode: suspend (String) -> Boolean,
            pairingPhase: Boolean,
        ): SessionInfo = SessionInfo(
            guid = "04b004550000100180013cbee158f492",
            modelSerial = "Z50_2_8095684",
            sessionId = 1,
            pairingCode = "5077",
        )

        override suspend fun sendCommand(request: CommandRequest): CommandResponse =
            CommandResponse(NkOps.OK, request.txn, 0)
    }
}
