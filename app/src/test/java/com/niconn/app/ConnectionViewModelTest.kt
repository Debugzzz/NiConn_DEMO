package com.niconn.app

import com.niconn.discovery.CameraDiscovery
import com.niconn.discovery.CameraInfo
import com.niconn.discovery.DiscoveryHandle
import com.niconn.protocol.CommandRequest
import com.niconn.protocol.CommandResponse
import com.niconn.protocol.ConnectionMode
import com.niconn.protocol.NkOps
import com.niconn.protocol.PtpIpSession
import com.niconn.protocol.SessionInfo
import com.niconn.service.CameraService
import com.niconn.service.ClientIdentity
import com.niconn.service.ConnectionState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionViewModelTest {
    private val camera = CameraInfo(
        instanceName = "Z50_2_8095684",
        host = "127.0.0.1",
        port = 15740,
        guid = null,
        vid = null,
        pid = null,
        apps = null,
    )

    @Test
    fun `discovery lists found camera and session shows info`() = runBlocking {
        val fakeDiscovery = object : CameraDiscovery {
            override fun discover(
                scope: kotlinx.coroutines.CoroutineScope,
                onFound: (CameraInfo) -> Unit,
            ): DiscoveryHandle {
                onFound(camera)
                return DiscoveryHandle {}
            }
        }
        val service = CameraService(
            fakeDiscovery,
            sessionFactory = { FakeSession() },
            identity = ClientIdentity("NiConn", ByteArray(16)),
        )
        val viewModel = ConnectionViewModel(service)

        viewModel.startDiscovery()
        val deadline = System.currentTimeMillis() + 3_000
        while (viewModel.cameras.value.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertEquals(listOf(camera), viewModel.cameras.value)

        viewModel.connect(camera)
        val deadline2 = System.currentTimeMillis() + 3_000
        while (!(viewModel.state.value is ConnectionState.Session) && System.currentTimeMillis() < deadline2) {
            Thread.sleep(10)
        }
        assertTrue(viewModel.state.value is ConnectionState.Session)
    }

    private class FakeSession : PtpIpSession() {
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
        )

        override suspend fun sendCommand(request: CommandRequest): CommandResponse =
            CommandResponse(NkOps.OK, request.txn, 0)
    }
}
