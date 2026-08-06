package com.niconn.discovery

import kotlinx.coroutines.CoroutineScope

data class CameraInfo(
    val instanceName: String,
    val host: String,
    val port: Int,
    val guid: String?,
    val vid: String?,
    val pid: String?,
    val apps: String?,
)

fun interface DiscoveryHandle {
    fun cancel()
}

interface CameraDiscovery {
    fun discover(scope: CoroutineScope, onFound: (CameraInfo) -> Unit): DiscoveryHandle
}

object CameraInfoParser {
    private const val DEFAULT_PORT = 15740

    fun fromParts(
        instanceName: String,
        port: Int,
        txt: Map<String, String>,
    ): CameraInfo = CameraInfo(
        instanceName = instanceName,
        host = "",
        port = if (port > 0) port else DEFAULT_PORT,
        guid = txt["guid"],
        vid = txt["vid"],
        pid = txt["pid"],
        apps = txt["apps"],
    )
}
