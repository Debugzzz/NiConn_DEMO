package com.niconn.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope

class NsdCameraDiscovery(private val context: Context) : CameraDiscovery {
    override fun discover(
        scope: CoroutineScope,
        onFound: (CameraInfo) -> Unit,
    ): DiscoveryHandle {
        val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "start discovery failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "stop discovery failed: $errorCode")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType != SERVICE_TYPE) return
                manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        Log.w(TAG, "resolve failed: $errorCode")
                    }

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val txt = mutableMapOf<String, String>()
                        info.attributes.forEach { (key, value) ->
                            txt[key] = value.toString(Charsets.UTF_8)
                        }
                        val camera = CameraInfoParser.fromParts(
                            instanceName = info.serviceName,
                            port = info.port,
                            txt = txt,
                        ).copy(host = info.host.hostAddress ?: "")
                        onFound(camera)
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
        }
        manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        return DiscoveryHandle { manager.stopServiceDiscovery(listener) }
    }

    companion object {
        private const val TAG = "NsdCameraDiscovery"
        private const val SERVICE_TYPE = "_ptp._tcp."
    }
}
