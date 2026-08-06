package com.niconn.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.niconn.protocol.NkOps
import com.niconn.protocol.PtpDataCodec
import com.niconn.protocol.PtpIpSession
import com.niconn.service.DevicePropController
import com.niconn.service.LiveViewController
import com.niconn.service.LiveViewFrame
import com.niconn.service.PropSpecs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LiveViewViewModel(
    private val getSession: () -> PtpIpSession?,
) : ViewModel() {
    private val _frame = MutableStateFlow<Bitmap?>(null)
    val frame: StateFlow<Bitmap?> = _frame
    private val _iso = MutableStateFlow("--")
    val iso: StateFlow<String> = _iso
    private val _shutter = MutableStateFlow("--")
    val shutter: StateFlow<String> = _shutter
    private val _aperture = MutableStateFlow("--")
    val aperture: StateFlow<String> = _aperture
    private val _isoOptions = MutableStateFlow(listOf(100L, 200L, 400L, 800L, 1600L, 3200L))
    val isoOptions: StateFlow<List<Long>> = _isoOptions
    private val _shutterOptions = MutableStateFlow(listOf(1_0000L, 5_000L, 2_500L, 1_250L, 625L, 313L, 156L))
    val shutterOptions: StateFlow<List<Long>> = _shutterOptions
    private val _apertureOptions = MutableStateFlow(listOf(280L, 400L, 560L, 800L, 1100L))
    val apertureOptions: StateFlow<List<Long>> = _apertureOptions
    private val _isLive = MutableStateFlow(false)
    val isLive: StateFlow<Boolean> = _isLive
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _focusPoint = MutableStateFlow<Pair<Int, Int>?>(null)
    val focusPoint: StateFlow<Pair<Int, Int>?> = _focusPoint
    private val _afFrame = MutableStateFlow<PtpDataCodec.AfFrame?>(null)
    val afFrame: StateFlow<PtpDataCodec.AfFrame?> = _afFrame
    private val _viewInfo = MutableStateFlow<PtpDataCodec.LiveViewInfo?>(null)
    val viewInfo: StateFlow<PtpDataCodec.LiveViewInfo?> = _viewInfo
    private var liveController: LiveViewController? = null
    private var focusJob: Job? = null
    private var lastSentPoint: Pair<Int, Int>? = null

    fun start() {
        val session = getSession()
        if (session == null) {
            _error.value = "请先在连接页连接相机"
            return
        }
        if (liveController != null) return
        val controller = LiveViewController(session)
        liveController = controller
        _isLive.value = true
        controller.start {
            _error.value = it
            _isLive.value = false
        }
        viewModelScope.launch {
            controller.frame.collect { liveFrame: LiveViewFrame? ->
                liveFrame?.let {
                    _afFrame.value = it.af
                    _viewInfo.value = it.viewInfo
                    it.af?.let { af ->
                        println("NiConnLV af=${af.hCenter},${af.vCenter} size=${af.hSize}x${af.vSize} lastSent=$lastSentPoint")
                    }
                    _frame.value = BitmapFactory.decodeByteArray(it.jpeg, 0, it.jpeg.size)
                }
            }
        }
        viewModelScope.launch {
            try {
                val props = DevicePropController(session)
                props.supportedValues(PropSpecs.ISO).takeIf { it.isNotEmpty() }?.let { _isoOptions.value = it }
                props.supportedValues(PropSpecs.SHUTTER).takeIf { it.isNotEmpty() }?.let { _shutterOptions.value = it }
                props.supportedValues(PropSpecs.APERTURE).takeIf { it.isNotEmpty() }?.let { _apertureOptions.value = it }
                _iso.value = props.read(PropSpecs.ISO)?.toString() ?: "--"
                _shutter.value = formatShutter(props.read(PropSpecs.SHUTTER))
                _aperture.value = formatAperture(props.read(PropSpecs.APERTURE))
            } catch (e: Exception) {
                _error.value = "读取参数失败：${e.message}"
            }
        }
    }

    fun stop() {
        liveController?.stop()
        liveController = null
        _isLive.value = false
    }

    fun takePhoto() {
        val session = getSession() ?: return
        viewModelScope.launch {
            try {
                val capture = session.sendCommand(
                    NkOps.InitiateCaptureRecInMedia,
                    listOf(0xFFFFFFFF.toInt(), 0),
                )
                if (capture.code != NkOps.OK) {
                    _error.value = "拍摄失败：相机返回 0x%04X".format(capture.code)
                    return@launch
                }
                repeat(20) {
                    val ready = session.sendCommand(NkOps.DeviceReady)
                    if (ready.code == NkOps.OK) {
                        _error.value = null
                        return@launch
                    }
                    if (!isBusyCode(ready.code)) {
                        _error.value = "拍摄失败：相机返回 0x%04X".format(ready.code)
                        return@launch
                    }
                    delay(100)
                }
                _error.value = "拍摄超时"
            } catch (e: Exception) {
                _error.value = "拍摄失败：${e.message}"
            }
        }
    }

    fun tapFocus(xPx: Int, yPx: Int) {
        // 实测：7.5x 略偏左下、8.7x 略偏右下、AF 框 290 单位≈36px → 缩放系数 8.0
        val wholeX = xPx * 8
        val wholeY = yPx * 8
        lastSentPoint = xPx to yPx
        _focusPoint.value = xPx to yPx
        focusJob?.cancel()
        val session = getSession() ?: return
        focusJob = viewModelScope.launch {
            try {
                // 锁定单点 AF 区域模式（0xD05D=0x8010），避免对焦点被自动区域抢走
                val props = DevicePropController(session)
                val mode = props.read(PropSpecs.AF_AREA_MODE)
                if (mode != null && mode != 0x8010L) {
                    props.write(PropSpecs.AF_AREA_MODE, 0x8010L)
                }
                var area = session.sendCommand(NkOps.ChangeAfArea, listOf(wholeX, wholeY))
                var attempts = 0
                while (area.code != NkOps.OK && attempts < 4 && isBusyCode(area.code)) {
                    delay(80)
                    area = session.sendCommand(NkOps.ChangeAfArea, listOf(wholeX, wholeY))
                    attempts++
                }
                if (area.code == NkOps.NOT_LIVE_VIEW) {
                    liveController?.ensureLiveView()
                    area = session.sendCommand(NkOps.ChangeAfArea, listOf(wholeX, wholeY))
                }
                if (area.code != NkOps.OK) {
                    _error.value = "对焦区域设置失败 0x%04X".format(area.code)
                    return@launch
                }
                val af = session.sendCommand(NkOps.AfDrive)
                if (af.code != NkOps.OK && af.code != NkOps.DEVICE_BUSY) {
                    _error.value = "对焦失败 0x%04X".format(af.code)
                    return@launch
                }
                // SnapBridge AfDriveAction：AfDrive 后轮询 DeviceReady，直到对焦完成（最多 5s）
                var afRetries = 0
                repeat(50) {
                    val ready = session.sendCommand(NkOps.DeviceReady)
                    when (ready.code) {
                        NkOps.OK -> {
                            _error.value = null
                            return@launch
                        }
                        NkOps.OUT_OF_FOCUS -> {
                            if (afRetries < 2) {
                                afRetries++
                                delay(120)
                                session.sendCommand(NkOps.AfDrive)
                            } else {
                                _error.value = "未合焦（对焦点缺少对比度，请换个位置）"
                                return@launch
                            }
                        }
                        else -> {
                            if (!isBusyCode(ready.code)) {
                                _error.value = "对焦失败 0x%04X".format(ready.code)
                                return@launch
                            }
                            delay(100)
                        }
                    }
                }
                _error.value = "对焦超时"
            } catch (e: Exception) {
                _error.value = "对焦命令失败：${e.message}"
            }
        }
    }

    private fun isBusyCode(code: Int): Boolean =
        code == NkOps.DEVICE_BUSY || code == 0xA200 || code == 0xA201 || code == 0xA207

    fun setProp(spec: PropSpecs.PropSpec, value: Long) {
        val session = getSession() ?: return
        viewModelScope.launch {
            try {
                val props = DevicePropController(session)
                if (!props.write(spec, value)) {
                    _error.value = "设置失败：相机拒绝了该参数（可能当前拍摄模式不允许）"
                    return@launch
                }
                when (spec) {
                    PropSpecs.ISO -> _iso.value = props.read(PropSpecs.ISO)?.toString() ?: value.toString()
                    PropSpecs.SHUTTER -> _shutter.value = formatShutter(props.read(PropSpecs.SHUTTER))
                    PropSpecs.APERTURE -> _aperture.value = formatAperture(props.read(PropSpecs.APERTURE))
                    else -> Unit
                }
                _error.value = null
            } catch (e: Exception) {
                _error.value = "设置参数失败：${e.message}"
            }
        }
    }

    override fun onCleared() {
        stop()
    }

    private fun formatShutter(v: Long?): String = when (v) {
        null -> "--"
        0xFFFFFFFFL -> "Bulb"
        0xFFFFFFFDL -> "Time"
        else -> if (v >= 10000) "${v / 10000.0}s" else "1/${10000 / v}s"
    }

    private fun formatAperture(v: Long?): String = v?.let { "f/${it / 100.0}" } ?: "--"
}
