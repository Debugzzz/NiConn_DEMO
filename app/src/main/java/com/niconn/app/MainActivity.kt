package com.niconn.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.niconn.discovery.NsdCameraDiscovery
import com.niconn.protocol.PtpIpSession
import com.niconn.service.CameraService
import com.niconn.service.ClientIdentityStore
import com.niconn.service.SavedCameraStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cameraService = CameraService(
            discovery = NsdCameraDiscovery(this),
            sessionFactory = { PtpIpSession() },
            identity = ClientIdentityStore(this).getOrCreate(),
        )
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF007AFF),
                    onPrimary = Color.White,
                    background = Color.White,
                    surface = Color.White,
                ),
            ) {
                MainScreen(ConnectionViewModel(cameraService, SavedCameraStore(this)))
            }
        }
    }
}
