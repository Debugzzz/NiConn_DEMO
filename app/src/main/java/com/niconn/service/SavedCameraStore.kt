package com.niconn.service

import android.content.Context
import com.niconn.discovery.CameraInfo

/** 持久化最近一次成功连接的相机信息，供连接页快速重连。 */
class SavedCameraStore(context: Context) {
    private val prefs = context.getSharedPreferences("niconn_saved", Context.MODE_PRIVATE)

    fun save(camera: CameraInfo) {
        prefs.edit()
            .putString(KEY_NAME, camera.instanceName)
            .putString(KEY_HOST, camera.host)
            .putInt(KEY_PORT, camera.port)
            .putString(KEY_GUID, camera.guid)
            .apply()
    }

    fun load(): CameraInfo? {
        val name = prefs.getString(KEY_NAME, null) ?: return null
        return CameraInfo(
            instanceName = name,
            host = prefs.getString(KEY_HOST, "") ?: "",
            port = prefs.getInt(KEY_PORT, 15740),
            guid = prefs.getString(KEY_GUID, null),
            vid = null,
            pid = null,
            apps = null,
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_NAME = "camera_name"
        const val KEY_HOST = "camera_host"
        const val KEY_PORT = "camera_port"
        const val KEY_GUID = "camera_guid"
    }
}
