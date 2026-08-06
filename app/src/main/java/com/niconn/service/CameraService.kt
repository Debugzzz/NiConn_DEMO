package com.niconn.service

import com.niconn.discovery.CameraDiscovery
import com.niconn.discovery.CameraInfo
import com.niconn.discovery.DiscoveryHandle
import com.niconn.protocol.ConnectionMode
import com.niconn.protocol.PtpIpSession
import com.niconn.protocol.SessionInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Discovering : ConnectionState
    data class Connecting(val target: CameraInfo) : ConnectionState
    data class Session(val info: SessionInfo, val camera: CameraInfo) : ConnectionState
    data class PairingComplete(val code: String, val camera: CameraInfo) : ConnectionState
    data class Error(val reason: String, val recoverable: Boolean) : ConnectionState
}

class CameraService(
    private val discovery: CameraDiscovery,
    private val sessionFactory: (CameraInfo) -> PtpIpSession,
    val identity: ClientIdentity,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state
    private var discoveryHandle: DiscoveryHandle? = null

    /** 当前活动会话（连接成功后由 CameraService 持有并绑定会话号）。 */
    var session: PtpIpSession? = null
        private set

    fun startDiscovery(onFound: (CameraInfo) -> Unit) {
        _state.value = ConnectionState.Discovering
        discoveryHandle = discovery.discover(scope) { camera ->
            onFound(camera)
        }
    }

    fun connect(
        camera: CameraInfo,
        mode: ConnectionMode = ConnectionMode.SMART_DEVICE,
        identity: ClientIdentity = this.identity,
        pairingPhase: Boolean = true,
    ) {
        _state.value = ConnectionState.Connecting(camera)
        scope.launch {
            try {
                // 重连前先关闭旧会话，避免两个会话并存/残留 socket
                session?.close()
                session = null
                val newSession = sessionFactory(camera)
                val info = newSession.connect(
                    host = camera.host,
                    port = camera.port,
                    uuid = identity.uuid,
                    clientName = identity.name,
                    mode = mode,
                    pairingPhase = pairingPhase,
                )
                _state.value = if (info.pairingCode != null) {
                    newSession.close()
                    session = null
                    ConnectionState.PairingComplete(info.pairingCode, camera)
                } else {
                    newSession.attachSession(info.sessionId)
                    session = newSession
                    ConnectionState.Session(info, camera)
                }
            } catch (e: Exception) {
                _state.value = ConnectionState.Error(
                    reason = e.message ?: "连接失败",
                    recoverable = true,
                )
            }
        }
    }

    fun disconnect() {
        val closing = session
        session = null
        if (closing != null) {
            scope.launch { closing.close() }
        }
        discoveryHandle?.cancel()
        discoveryHandle = null
        _state.value = ConnectionState.Idle
    }
}
