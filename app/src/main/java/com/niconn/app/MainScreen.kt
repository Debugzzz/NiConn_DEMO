package com.niconn.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.niconn.service.AppSettingsStore

enum class AppTab(val label: String, val icon: ImageVector) {
    CONNECTION("连接", Icons.Filled.Wifi),
    LIVE_VIEW("取景", Icons.Filled.PhotoCamera),
    GALLERY("相册", Icons.Filled.PhotoLibrary),
}

@Composable
fun MainScreen(viewModel: ConnectionViewModel, settings: AppSettingsStore) {
    var tab by rememberSaveable { mutableStateOf(AppTab.CONNECTION) }
    var showSettings by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Apple.background,
            bottomBar = {
                Box {
                    // iOS Tab Bar 顶部发丝分隔线
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(Apple.separator),
                    )
                    NavigationBar(
                        containerColor = Apple.surface,
                        tonalElevation = 0.dp,
                    ) {
                        AppTab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                // iOS Tab Bar 无胶囊背景，仅图标/文字变蓝
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Apple.blue,
                                    selectedTextColor = Apple.blue,
                                    indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unselectedIconColor = Apple.secondaryLabel,
                                    unselectedTextColor = Apple.secondaryLabel,
                                ),
                                icon = {
                                    Icon(item.icon, contentDescription = item.label)
                                },
                                label = {
                                    Text(item.label, fontSize = 10.sp)
                                },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Apple.background)
                    .padding(innerPadding),
            ) {
                when (tab) {
                    AppTab.CONNECTION -> ConnectionScreen(
                        viewModel,
                        onOpenSettings = { showSettings = true },
                    )
                    AppTab.LIVE_VIEW -> LiveViewScreen(viewModel, settings)
                    AppTab.GALLERY -> GalleryScreen(viewModel, settings)
                }
            }
        }
        SettingsDrawer(settings = settings, visible = showSettings, onClose = { showSettings = false })
    }
}
