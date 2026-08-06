package com.niconn.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.niconn.discovery.CameraInfo
import com.niconn.service.CameraService
import com.niconn.service.ConnectionState
import com.niconn.service.SavedCameraStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ConnectionViewModel(
    private val cameraService: CameraService,
    private val savedCameraStore: SavedCameraStore? = null,
) : ViewModel() {
    private val _cameras = MutableStateFlow<List<CameraInfo>>(emptyList())
    val cameras: StateFlow<List<CameraInfo>> = _cameras
    val state: StateFlow<ConnectionState> = cameraService.state
    private val knownCameras = LinkedHashMap<String, CameraInfo>()
    private val _savedCamera = MutableStateFlow(savedCameraStore?.load())
    val savedCamera: StateFlow<CameraInfo?> = _savedCamera

    init {
        viewModelScope.launch {
            cameraService.state.collect { state ->
                if (state is ConnectionState.Session) {
                    savedCameraStore?.save(state.camera)
                    _savedCamera.value = state.camera
                }
            }
        }
    }

    val liveSession: com.niconn.protocol.PtpIpSession? get() = cameraService.session

    fun startDiscovery() {
        knownCameras.clear()
        _cameras.value = emptyList()
        cameraService.startDiscovery { camera ->
            val key = camera.guid ?: camera.instanceName
            if (knownCameras[key] == null) {
                knownCameras[key] = camera
                _cameras.value = knownCameras.values.toList()
            }
        }
    }

    fun connect(camera: CameraInfo, pairingPhase: Boolean = true) {
        cameraService.connect(camera, identity = cameraService.identity, pairingPhase = pairingPhase)
    }

    fun reconnect(camera: CameraInfo) {
        connect(camera, pairingPhase = false)
    }

    /** 直接连接上一次成功连接的相机（跳过发现，假设已完成配对）。 */
    fun connectSaved() {
        val camera = _savedCamera.value ?: return
        cameraService.connect(camera, identity = cameraService.identity, pairingPhase = false)
    }

    /** 彻底删除保存的连接状态。 */
    fun clearSaved() {
        savedCameraStore?.clear()
        _savedCamera.value = null
    }

    fun disconnect() {
        cameraService.disconnect()
    }
}
