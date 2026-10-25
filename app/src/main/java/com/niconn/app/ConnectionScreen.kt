package com.niconn.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.niconn.discovery.CameraInfo
import com.niconn.service.ConnectionState

@Composable
fun ConnectionScreen(viewModel: ConnectionViewModel, onOpenSettings: () -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    val cameras by viewModel.cameras.collectAsState()
    val savedCamera by viewModel.savedCamera.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Apple.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 大标题即设置入口：点击从左侧滑出设置抽屉
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenSettings)
                .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("NiConn", color = Apple.label, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Filled.Settings,
                contentDescription = stringResource(R.string.settings_title),
                tint = Apple.secondaryLabel,
            )
        }
        when (val current = state) {
            is ConnectionState.Idle -> {
                GuideCard()
                savedCamera?.let { camera ->
                    SavedCameraCard(
                        camera,
                        onConnect = { viewModel.connectSaved() },
                        onDelete = { viewModel.clearSaved() },
                    )
                }
                AppleFilledButton(
                    stringResource(R.string.conn_start_discovery),
                    onClick = { viewModel.startDiscovery() },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            is ConnectionState.Discovering -> {
                StatusCard(stringResource(R.string.conn_discovering))
                cameras.forEach { camera ->
                    CameraRow(camera) { viewModel.connect(camera) }
                }
            }
            is ConnectionState.Connecting -> {
                StatusCard(stringResource(R.string.conn_connecting, current.target.instanceName))
            }
            is ConnectionState.Session -> {
                ConnectedCard(current)
                AppleFilledButton(
                    stringResource(R.string.conn_disconnect),
                    onClick = viewModel::disconnect,
                    color = Apple.red,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            is ConnectionState.PairingComplete -> {
                PairingCard(
                    code = current.code,
                    onReconnect = { viewModel.reconnect(current.camera) },
                    onCancel = viewModel::disconnect,
                )
            }
            is ConnectionState.Error -> {
                Text(
                    current.reason,
                    color = Apple.red,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 8.dp),
                )
                AppleFilledButton(
                    stringResource(R.string.conn_retry),
                    onClick = { viewModel.startDiscovery() },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
    }
}

/** 使用引导卡片（分组白卡 + 蓝色数字圆点） */
@Composable
private fun GuideCard() {
    AppleCard(Modifier.padding(horizontal = 20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GuideStep(1, stringResource(R.string.conn_guide_1))
            GuideStep(2, stringResource(R.string.conn_guide_2))
            GuideStep(3, stringResource(R.string.conn_guide_3))
        }
    }
}

@Composable
private fun GuideStep(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(Apple.blue, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text,
            fontSize = 15.sp,
            color = Apple.label,
            modifier = Modifier.padding(start = 10.dp, top = 1.dp),
        )
    }
}

/** 「连接过的相机」卡片：主信息 + 蓝色连接按钮/红色删除 */
@Composable
private fun SavedCameraCard(camera: CameraInfo, onConnect: () -> Unit, onDelete: () -> Unit) {
    AppleCard(Modifier.padding(horizontal = 20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.conn_saved_title), color = Apple.secondaryLabel, fontSize = 13.sp)
            Text(camera.instanceName, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(camera.host, color = Apple.secondaryLabel, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.conn_connect),
                    color = Apple.blue,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(Apple.background, CircleShape)
                        .clickable(onClick = onConnect)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
                Text(
                    stringResource(R.string.conn_delete_record),
                    color = Apple.red,
                    fontSize = 17.sp,
                    modifier = Modifier
                        .background(Apple.background, CircleShape)
                        .clickable(onClick = onDelete)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** 加载状态卡片（转圈 + 文案，类似 iOS 表格加载行） */
@Composable
private fun StatusCard(text: String) {
    AppleCard(Modifier.padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                color = Apple.blue,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text,
                fontSize = 15.sp,
                color = Apple.label,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/** 发现到的相机行：白卡 + 名称 + GUID + 雪佛龙 */
@Composable
private fun CameraRow(camera: CameraInfo, onClick: () -> Unit) {
    AppleCard(
        Modifier.padding(horizontal = 20.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(camera.instanceName, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                camera.guid?.let {
                    Text(it, fontSize = 13.sp, color = Apple.secondaryLabel)
                }
            }
            Text(stringResource(R.string.conn_connect), color = Apple.blue, fontSize = 17.sp)
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Apple.secondaryLabel,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/** 已连接卡片：绿色圆点 + 设置风格信息行（发丝线分隔） */
@Composable
private fun ConnectedCard(current: ConnectionState.Session) {
    AppleCard(Modifier.padding(horizontal = 20.dp)) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(Apple.green, CircleShape),
                )
                Text(
                    stringResource(R.string.conn_connected),
                    color = Apple.label,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            HorizontalDivider(thickness = 0.5.dp, color = Apple.separator,
                modifier = Modifier.padding(start = 16.dp))
            InfoRow(stringResource(R.string.conn_model_serial), current.info.modelSerial)
            HorizontalDivider(thickness = 0.5.dp, color = Apple.separator,
                modifier = Modifier.padding(start = 16.dp))
            InfoRow("GUID", current.info.guid)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Text(label, color = Apple.secondaryLabel, fontSize = 15.sp)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** 配对成功卡片：大号验证码居中 + 操作按钮 */
@Composable
private fun PairingCard(code: String, onReconnect: () -> Unit, onCancel: () -> Unit) {
    AppleCard(Modifier.padding(horizontal = 20.dp)) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.conn_pairing_success), color = Apple.blue, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(
                code,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
            )
            Text(
                stringResource(R.string.conn_pairing_hint),
                fontSize = 13.sp,
                color = Apple.secondaryLabel,
                textAlign = TextAlign.Center,
            )
        }
    }
    AppleFilledButton(stringResource(R.string.conn_reconnect), onClick = onReconnect, modifier = Modifier.padding(horizontal = 20.dp))
    AppleGrayButton(stringResource(R.string.conn_cancel), onClick = onCancel, modifier = Modifier.padding(horizontal = 20.dp))
}
