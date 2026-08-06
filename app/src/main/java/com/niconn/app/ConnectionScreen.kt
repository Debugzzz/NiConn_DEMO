package com.niconn.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.niconn.discovery.CameraInfo
import com.niconn.service.ConnectionState

private val iOSBlue = Color(0xFF007AFF)

@Composable
fun ConnectionScreen(viewModel: ConnectionViewModel) {
    val state by viewModel.state.collectAsState()
    val cameras by viewModel.cameras.collectAsState()
    val savedCamera by viewModel.savedCamera.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "连接相机",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A1A),
        )
        GuideCard()
        savedCamera?.let { camera ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF2F2F7),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("已连接过的相机", color = Color(0xFF8E8E93), fontSize = 13.sp)
                    Text(camera.instanceName, fontWeight = FontWeight.Bold)
                    Text(camera.host, color = Color(0xFF8E8E93), fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { viewModel.connectSaved() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("连接")
                        }
                        TextButton(onClick = { viewModel.clearSaved() }) {
                            Text("删除记录", color = Color(0xFFFF3B30))
                        }
                    }
                }
            }
        }
        when (val current = state) {
            is ConnectionState.Idle -> {
                PrimaryButton("开始发现") { viewModel.startDiscovery() }
            }
            is ConnectionState.Discovering -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = iOSBlue)
                    Spacer(Modifier.height(0.dp))
                    Text("正在发现相机…", modifier = Modifier.padding(start = 12.dp))
                }
                cameras.forEach { camera ->
                    CameraRow(camera) { viewModel.connect(camera) }
                }
            }
            is ConnectionState.Connecting -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = iOSBlue)
                    Text("正在连接 ${current.target.instanceName}…", modifier = Modifier.padding(start = 12.dp))
                }
            }
            is ConnectionState.Session -> {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF2F2F7),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("已连接", color = Color(0xFF007AFF), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        InfoLine("型号/序列号", current.info.modelSerial)
                        InfoLine("GUID", current.info.guid)
                    }
                }
                Button(
                    onClick = viewModel::disconnect,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Text("断开连接")
                }
            }
            is ConnectionState.PairingComplete -> {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF2F2F7),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("配对成功", color = Color(0xFF007AFF), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("验证码：${current.code}", fontWeight = FontWeight.Bold)
                        Text("请在相机上按 OK，等待相机显示「正在连接」，然后点击重新连接。")
                    }
                }
                PrimaryButton("重新连接") { viewModel.reconnect(current.camera) }
                Button(
                    onClick = viewModel::disconnect,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5E5EA), contentColor = Color(0xFF1A1A1A)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Text("取消")
                }
            }
            is ConnectionState.Error -> {
                Text(current.reason, color = Color(0xFFFF3B30))
                PrimaryButton("重试") { viewModel.startDiscovery() }
            }
        }
    }
}

@Composable
private fun GuideCard() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF2F2F7),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Step(1, "手机开启移动热点即可")
            Step(2, "相机：网络菜单 → 连接至智能设备 → Wi-Fi 连接 (STA 模式) → 创建配置文件并连接热点，停留在配对界面")
            Step(3, "回到 App，点击「开始发现」，选择相机完成配对")
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(shape = RoundedCornerShape(50), color = iOSBlue) {
            Text(
                text = number.toString(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Text(text, modifier = Modifier.padding(start = 10.dp, top = 2.dp))
    }
}

@Composable
private fun CameraRow(camera: CameraInfo, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF2F2F7),
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(camera.instanceName, fontWeight = FontWeight.SemiBold)
                camera.guid?.let { Text(it, fontSize = 12.sp, color = Color(0xFF8E8E93)) }
            }
            Text("连接", color = iOSBlue, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(50.dp),
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.padding(top = 8.dp)) {
        Text(label, color = Color(0xFF8E8E93), modifier = Modifier.padding(end = 12.dp))
        Text(value, fontWeight = FontWeight.Medium)
    }
}
