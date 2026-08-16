package com.niconn.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 取景轮询档位：省电(约5fps) / 均衡(约10fps) / 流畅(最高速率)。 */
enum class FrameRateMode(val label: String, val intervalMs: Long) {
    POWER("省电", 200),
    BALANCED("均衡", 80),
    SMOOTH("流畅", 33),
}

/** 应用设置：持久化到 SharedPreferences，并以 StateFlow 暴露给 UI。 */
class AppSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("niconn_settings", Context.MODE_PRIVATE)

    private val _frameRate = MutableStateFlow(
        prefs.getString(KEY_FRAME_RATE, null)?.let { name ->
            FrameRateMode.entries.firstOrNull { it.name == name }
        } ?: FrameRateMode.SMOOTH,
    )
    val frameRate: StateFlow<FrameRateMode> = _frameRate

    private val _defaultLandscape = MutableStateFlow(prefs.getBoolean(KEY_DEFAULT_LANDSCAPE, false))
    val defaultLandscape: StateFlow<Boolean> = _defaultLandscape

    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean(KEY_KEEP_SCREEN_ON, true))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn

    private val _gridColumns = MutableStateFlow(prefs.getInt(KEY_GRID_COLUMNS, 3))
    val gridColumns: StateFlow<Int> = _gridColumns

    /** LiveView 轮询间隔（毫秒），供控制器实时读取。 */
    val frameIntervalMs: Long get() = _frameRate.value.intervalMs

    fun setFrameRate(mode: FrameRateMode) {
        _frameRate.value = mode
        prefs.edit().putString(KEY_FRAME_RATE, mode.name).apply()
    }

    fun setDefaultLandscape(value: Boolean) {
        _defaultLandscape.value = value
        prefs.edit().putBoolean(KEY_DEFAULT_LANDSCAPE, value).apply()
    }

    fun setKeepScreenOn(value: Boolean) {
        _keepScreenOn.value = value
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()
    }

    fun setGridColumns(columns: Int) {
        _gridColumns.value = columns
        prefs.edit().putInt(KEY_GRID_COLUMNS, columns).apply()
    }

    private companion object {
        const val KEY_FRAME_RATE = "frame_rate"
        const val KEY_DEFAULT_LANDSCAPE = "default_landscape"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_GRID_COLUMNS = "grid_columns"
    }
}
