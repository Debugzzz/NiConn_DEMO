package com.niconn.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.niconn.service.AppSettingsStore
import com.niconn.service.FrameRateMode

private const val REPO_URL = "https://github.com/Debugzzz/NiConn_DEMO"

/**
 * iOS 风格左侧设置抽屉：占屏宽 3/4，深色遮罩点击关闭，支持返回键关闭。
 */
@Composable
fun SettingsDrawer(
    settings: AppSettingsStore,
    visible: Boolean,
    onClose: () -> Unit,
) {
    BackHandler(enabled = visible) { onClose() }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
                    .clickable(onClick = onClose),
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.75f)
                    .background(Apple.surface)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                DrawerHeader()
                val frameRate by settings.frameRate.collectAsState()
                val defaultLandscape by settings.defaultLandscape.collectAsState()
                val keepScreenOn by settings.keepScreenOn.collectAsState()
                AppleSectionHeader("实时取景")
                AppleCard(Modifier.padding(horizontal = 20.dp)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("帧率", fontSize = 17.sp)
                        Spacer(Modifier.height(8.dp))
                        IosSegmented(
                            options = FrameRateMode.entries.map { it.label },
                            selected = frameRate.label,
                        ) { label ->
                            FrameRateMode.entries.firstOrNull { it.label == label }
                                ?.let { settings.setFrameRate(it) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "省电约 5fps · 均衡约 10fps · 流畅按最高速率轮询",
                            fontSize = 12.sp,
                            color = Apple.secondaryLabel,
                        )
                        DividerInset()
                        SwitchRow(
                            title = "默认横屏",
                            subtitle = "进入取景页时自动旋转为横屏",
                            checked = defaultLandscape,
                        ) { settings.setDefaultLandscape(it) }
                        DividerInset()
                        SwitchRow(
                            title = "屏幕常亮",
                            subtitle = "取景页保持屏幕不锁定",
                            checked = keepScreenOn,
                        ) { settings.setKeepScreenOn(it) }
                    }
                }
                val gridColumns by settings.gridColumns.collectAsState()
                AppleSectionHeader("相册")
                AppleCard(Modifier.padding(horizontal = 20.dp)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("缩略图列数", fontSize = 17.sp)
                        Spacer(Modifier.height(8.dp))
                        IosSegmented(
                            options = listOf("3", "4", "5"),
                            selected = gridColumns.toString(),
                        ) { settings.setGridColumns(it.toInt()) }
                    }
                }
                AppleSectionHeader("关于")
                val context = LocalContext.current
                AppleCard(Modifier.padding(horizontal = 20.dp)) {
                    Column {
                        AboutValueRow("版本", BuildConfig.VERSION_NAME)
                        DividerInset()
                        AboutActionRow("GitHub 仓库", Icons.Filled.ChevronRight) {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)))
                            }
                        }
                        DividerInset()
                        AboutValueRow("开源许可", "PolyForm NC 1.0.0")
                        DividerInset()
                        AboutValueRow("设备支持", "尼康 Z50II（实测）")
                    }
                }
                Text(
                    "本应用为独立实现的第三方兼容工具，与 Nikon Corporation 无隶属或合作关系。",
                    fontSize = 12.sp,
                    color = Apple.secondaryLabel,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun DrawerHeader() {
    Column(Modifier.padding(start = 24.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
        Row {
            Text(
                "Ni",
                color = Color(0xFFFFCC00),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            )
            Text(
                "Conn",
                color = Apple.label,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            )
        }
        Text("设置", fontSize = 13.sp, color = Apple.secondaryLabel)
    }
}

/** iOS 风格分段选择器（灰底轨道 + 白色选中段）。 */
@Composable
fun <T> IosSegmented(
    options: List<T>,
    selected: T,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Apple.fill, RoundedCornerShape(9.dp))
            .padding(2.dp)
            .height(30.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        if (isSelected) Color.White else Color.Transparent,
                        RoundedCornerShape(7.dp),
                    )
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option.toString(),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Apple.label else Apple.secondaryLabel,
                )
            }
        }
    }
}

/** iOS 风格开关行。 */
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp)
            subtitle?.let {
                Text(it, fontSize = 12.sp, color = Apple.secondaryLabel)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Apple.green,
                uncheckedTrackColor = Apple.fill,
                checkedThumbColor = Color.White,
                uncheckedThumbColor = Color.White,
            ),
        )
    }
}

@Composable
private fun AboutValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 17.sp)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 15.sp, color = Apple.secondaryLabel)
    }
}

@Composable
private fun AboutActionRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 17.sp)
        Spacer(Modifier.weight(1f))
        Icon(icon, contentDescription = null, tint = Apple.secondaryLabel)
    }
}

@Composable
private fun DividerInset() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = Apple.separator,
        modifier = Modifier.padding(start = 0.dp),
    )
}
