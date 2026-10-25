package com.niconn.app

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.niconn.service.AppSettingsStore
import com.niconn.service.FrameRateMode
import com.niconn.update.UpdateChecker
import com.niconn.update.UpdateResult
import kotlinx.coroutines.launch

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

    // 检查更新状态
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var updateHint by remember { mutableStateOf<String?>(null) }
    var pendingUpdate by remember { mutableStateOf<UpdateResult.Available?>(null) }

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
                // 语言：应用内切换（AppCompatDelegate 持久化，Activity 自动重建）
                AppleSectionHeader(stringResource(R.string.settings_language))
                AppleCard(Modifier.padding(horizontal = 20.dp)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        val langTags = listOf("system", "zh", "en")
                        val langLabels = listOf(
                            stringResource(R.string.settings_language_system),
                            "中文",
                            "English",
                        )
                        val currentTag = currentLanguageTag()
                        IosSegmented(
                            options = langLabels,
                            selected = langLabels[langTags.indexOf(currentTag).coerceAtLeast(0)],
                        ) { label ->
                            setAppLanguage(langTags[langLabels.indexOf(label)])
                        }
                    }
                }
                AppleSectionHeader(stringResource(R.string.settings_liveview))
                AppleCard(Modifier.padding(horizontal = 20.dp)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(stringResource(R.string.settings_frame_rate), fontSize = 17.sp)
                        Spacer(Modifier.height(8.dp))
                        val frameRateLabels = FrameRateMode.entries.map { stringResource(it.labelRes) }
                        IosSegmented(
                            options = frameRateLabels,
                            selected = stringResource(frameRate.labelRes),
                        ) { label ->
                            FrameRateMode.entries.getOrNull(frameRateLabels.indexOf(label))
                                ?.let { settings.setFrameRate(it) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.settings_frame_rate_hint),
                            fontSize = 12.sp,
                            color = Apple.secondaryLabel,
                        )
                        DividerInset()
                        SwitchRow(
                            title = stringResource(R.string.settings_default_landscape),
                            subtitle = stringResource(R.string.settings_default_landscape_hint),
                            checked = defaultLandscape,
                        ) { settings.setDefaultLandscape(it) }
                        DividerInset()
                        SwitchRow(
                            title = stringResource(R.string.settings_keep_screen_on),
                            subtitle = stringResource(R.string.settings_keep_screen_on_hint),
                            checked = keepScreenOn,
                        ) { settings.setKeepScreenOn(it) }
                    }
                }
                val gridColumns by settings.gridColumns.collectAsState()
                AppleSectionHeader(stringResource(R.string.settings_gallery))
                AppleCard(Modifier.padding(horizontal = 20.dp)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(stringResource(R.string.settings_grid_columns), fontSize = 17.sp)
                        Spacer(Modifier.height(8.dp))
                        IosSegmented(
                            options = listOf("3", "4", "5"),
                            selected = gridColumns.toString(),
                        ) { settings.setGridColumns(it.toInt()) }
                    }
                }
                AppleSectionHeader(stringResource(R.string.settings_diagnostics))
                val context = LocalContext.current
                AppleCard(Modifier.padding(horizontal = 20.dp)) {
                    Column {
                        AboutActionRow(stringResource(R.string.settings_export_logs), Icons.Filled.ChevronRight) {
                            exportLogs(context)
                        }
                        DividerInset()
                        AboutActionRow(stringResource(R.string.settings_clear_logs), Icons.Filled.ChevronRight) {
                            com.niconn.AppLog.clear()
                        }
                    }
                }
                AppleSectionHeader(stringResource(R.string.settings_about))
                AppleCard(Modifier.padding(horizontal = 20.dp)) {
                    Column {
                        AboutValueRow(stringResource(R.string.settings_version), BuildConfig.VERSION_NAME)
                        DividerInset()
                        AboutActionRow(
                            label = stringResource(R.string.settings_check_update),
                            icon = Icons.Filled.ChevronRight,
                            value = if (checking) stringResource(R.string.settings_checking) else updateHint,
                        ) {
                            if (checking) return@AboutActionRow
                            checking = true
                            updateHint = null
                            scope.launch {
                                when (val result = UpdateChecker.check(BuildConfig.VERSION_NAME)) {
                                    is UpdateResult.Available -> {
                                        updateHint = localString(context, R.string.settings_update_available, result.latestVersion)
                                        pendingUpdate = result
                                    }
                                    UpdateResult.UpToDate -> updateHint = context.getString(R.string.settings_up_to_date)
                                    is UpdateResult.Failed -> updateHint = context.getString(R.string.settings_check_failed)
                                }
                                checking = false
                            }
                        }
                        DividerInset()
                        AboutActionRow(stringResource(R.string.settings_github), Icons.Filled.ChevronRight) {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)))
                            }
                        }
                        DividerInset()
                        AboutValueRow(stringResource(R.string.settings_license), "PolyForm NC 1.0.0")
                        DividerInset()
                        AboutValueRow(stringResource(R.string.settings_device_support), stringResource(R.string.settings_device_support_value))
                    }
                }
                Text(
                    stringResource(R.string.settings_legal),
                    fontSize = 12.sp,
                    color = Apple.secondaryLabel,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                )

                pendingUpdate?.let { info ->
                    AppleAlert(
                        title = localString(context, R.string.settings_update_dialog_title, info.latestVersion),
                        onDismiss = { pendingUpdate = null },
                        confirmText = stringResource(R.string.settings_update_go),
                        onConfirm = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl)))
                            }
                            pendingUpdate = null
                        },
                        dismissText = stringResource(R.string.settings_update_later),
                        hasBody = true,
                    ) {
                        Text(
                            localString(context, R.string.settings_update_dialog_body, BuildConfig.VERSION_NAME),
                            fontSize = 13.sp,
                            color = Apple.secondaryLabel,
                        )
                    }
                }
            }
        }
    }
}


/** 非组合上下文（如协程回调）取带参本地化字符串。 */
private fun localString(context: android.content.Context, res: Int, arg: String): String =
    context.getString(res, arg)

/** 当前应用语言标签：system / zh / en（以 AppCompatDelegate 状态为准）。 */
private fun currentLanguageTag(): String {
    val locales = AppCompatDelegate.getApplicationLocales()
    return when {
        locales.isEmpty -> "system"
        locales[0]?.language == "zh" -> "zh"
        else -> "en"
    }
}

/** 应用内切换语言：system = 跟随系统；由 AppCompatDelegate 持久化并重建界面。 */
private fun setAppLanguage(tag: String) {
    AppCompatDelegate.setApplicationLocales(
        if (tag == "system") LocaleListCompat.getEmptyLocaleList()
        else LocaleListCompat.forLanguageTags(tag),
    )
}

/** 把环形日志写入缓存文件并通过系统分享面板导出。 */
private fun exportLogs(context: android.content.Context) {
    runCatching {
        val dir = java.io.File(context.cacheDir, "logs").apply { mkdirs() }
        val file = java.io.File(dir, "niconn-log-${System.currentTimeMillis()}.txt")
        file.writeText(com.niconn.AppLog.export())
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                context.getString(R.string.settings_export_logs),
            ),
        )
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
        Text(stringResource(R.string.settings_title), fontSize = 13.sp, color = Apple.secondaryLabel)
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
private fun AboutActionRow(
    label: String,
    icon: ImageVector,
    value: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 17.sp)
        Spacer(Modifier.weight(1f))
        value?.let {
            Text(
                it,
                fontSize = 15.sp,
                color = Apple.secondaryLabel,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
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
