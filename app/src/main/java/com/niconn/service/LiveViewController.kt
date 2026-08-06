package com.niconn.service

import com.niconn.protocol.NkOps
import com.niconn.protocol.PtpDataCodec
import com.niconn.protocol.PtpIpSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * LiveView 控制器：StartLiveView → 轮询 GetLiveViewImageEx（~10fps）→ EndLiveView。
 * 帧以 JPEG 字节暴露，解码交给 UI 层。
 */
data class LiveViewFrame(
    val jpeg: ByteArray,
    val af: PtpDataCodec.AfFrame?,
    val viewInfo: PtpDataCodec.LiveViewInfo?,
)

class LiveViewController(private val session: PtpIpSession) {
    private var headerLogged = false
    private var frameCount = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _frame = MutableStateFlow<LiveViewFrame?>(null)
    val frame: StateFlow<LiveViewFrame?> = _frame
    @Volatile
    private var running = false

    fun start(onError: (String) -> Unit) {
        if (running) return
        running = true
        scope.launch {
            try {
                session.sendCommand(NkOps.StartLiveView)
                waitDeviceReady()
                while (running) {
                    val response = session.sendCommand(NkOps.GetLiveViewImageEx)
                    when (response.code) {
                        NkOps.OK -> response.data.takeIf { it.isNotEmpty() }?.let { data ->
                            val jpeg = PtpDataCodec.extractJpeg(data) ?: return@let
                            frameCount++
                            if (!headerLogged || frameCount % 200 == 0) {
                                headerLogged = true
                                val major = if (data.size > 1) data[0].toInt() and 0xFF else -1
                                val minor = if (data.size > 3) data[1].toInt() and 0xFF else -1
                                val head = data.take(64).joinToString("") { "%02x".format(it) }
                                var soi = -1
                                for (i in 0 until data.size - 1) {
                                    if (data[i] == 0xFF.toByte() && data[i + 1] == 0xD8.toByte()) {
                                        soi = i
                                        break
                                    }
                                }
                                println(
                                    "NiConnLV ver=$major.$minor whole=${PtpDataCodec.parseViewInfo(data)} " +
                                        "af=${PtpDataCodec.parseAfFrame(data)} dataLen=${data.size} " +
                                        "head=$head soi=$soi",
                                )
                            }
                            _frame.value = LiveViewFrame(
                                jpeg = jpeg,
                                af = PtpDataCodec.parseAfFrame(data),
                                viewInfo = PtpDataCodec.parseViewInfo(data),
                            )
                        }
                        NkOps.NOT_LIVE_VIEW -> {
                            // 相机退出远程取景（如在相机屏上操作），自动重新进入
                            session.sendCommand(NkOps.StartLiveView)
                            waitDeviceReady()
                        }
                        NkOps.DEVICE_BUSY -> delay(100)
                        else -> Unit
                    }
                    delay(33)
                }
            } catch (e: Exception) {
                if (running) onError(e.message ?: "实时取景失败")
            } finally {
                runCatching { session.sendCommand(NkOps.EndLiveView) }
                running = false
            }
        }
    }

    fun stop() {
        running = false
    }

    private suspend fun waitDeviceReady() {
        var attempts = 0
        while (attempts++ < 30) {
            val response = session.sendCommand(NkOps.DeviceReady)
            if (response.code == NkOps.OK) return
            delay(100)
        }
    }

    /** 供对焦/拍摄等命令在 Not_LiveView 时调用：重新进入远程取景。 */
    suspend fun ensureLiveView() {
        session.sendCommand(NkOps.StartLiveView)
        waitDeviceReady()
    }
}
