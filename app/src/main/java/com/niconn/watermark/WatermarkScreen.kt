package com.niconn.watermark

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.niconn.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.niconn.app.Apple
import kotlinx.coroutines.launch

@Composable
fun WatermarkScreen() {
    val vm: WatermarkViewModel = viewModel()
    val state by vm.state.collectAsState()
    val style by vm.style.collectAsState()
    val exif by vm.exif.collectAsState()
    val rendered by vm.rendered.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSaved by remember { mutableStateOf(false) }

    // 用 SAF（OPEN_DOCUMENT）而不是系统相册选择器：
    // 小米 HyperOS 的相册选择器（com.hyper.photopicker）交给应用的是它 cache 里的
    // **副本**，其中 make/model/焦距/光圈/快门/ISO 等标签已被剥离，导致水印读不到参数；
    // SAF 打开的是文档本体，元数据完整，并且能按 MIME 只放行 JPG / NEF。
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            vm.selectImage(context, uri)
        }
    }

    LaunchedEffect(Unit) {
        // 初次进入进入 Idle 提示
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Apple.background)
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // 顶部大标题
            Text(
                stringResource(R.string.wm_title),
                color = Apple.label,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
            )
            Text(
                stringResource(R.string.wm_subtitle),
                color = Apple.secondaryLabel,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(Modifier.height(12.dp))

            // 版式选择（三段式 iOS 风格）
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WatermarkStyle.entries.forEach { s ->
                    val selected = style == s
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(9.dp))
                            .background(if (selected) Apple.blue else Apple.fill)
                            .clickable { vm.selectStyle(s) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(s.labelRes),
                            color = if (selected) Color.White else Apple.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 图片选择 / 预览区
            val current = rendered ?: null
            when {
                state is WatermarkState.Loading -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .aspectRatio(4f / 3f)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                            .background(Apple.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Apple.blue, strokeWidth = 2.dp)
                    }
                }
                current != null -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                            .background(Apple.surface)
                            .clickable {
                                picker.launch(PICK_MIME_TYPES)
                            }
                    ) {
                        Image(
                            bitmap = current.asImageBitmap(),
                            contentDescription = stringResource(R.string.wm_preview_desc),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(current.width.toFloat() / current.height.toFloat()),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                state is WatermarkState.Error -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .aspectRatio(4f / 3f)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                            .background(Apple.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            (state as WatermarkState.Error).message,
                            color = Apple.red,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                else -> {
                    // 空状态：选择照片
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .aspectRatio(4f / 3f)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                            .background(Apple.surface)
                            .clickable {
                                picker.launch(PICK_MIME_TYPES)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Filled.AddPhotoAlternate,
                            contentDescription = null,
                            tint = Apple.secondaryLabel,
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.wm_pick), color = Apple.secondaryLabel, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("JPG / NEF", color = Apple.secondaryLabel, fontSize = 12.sp)
                    }
                }
            }

            // EXIF 信息展示
            exif?.let { e ->
                if (e.cameraLabel().isNotBlank() || e.paramsLabel().isNotBlank()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .background(Apple.surface)
                            .padding(14.dp),
                    ) {
                        Text(stringResource(R.string.gallery_info_title), color = Apple.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        if (e.cameraLabel().isNotBlank()) {
                            InfoRow(stringResource(R.string.info_camera_short), e.cameraLabel())
                        }
                        if (!e.lensModel.isNullOrBlank()) {
                            InfoRow(stringResource(R.string.info_lens), e.lensModel!!)
                        }
                        if (!e.focalLength.isNullOrBlank()) {
                            InfoRow(stringResource(R.string.info_focal), e.focalLength!!)
                        }
                        if (!e.fNumber.isNullOrBlank()) {
                            InfoRow(stringResource(R.string.info_aperture), e.fNumber!!)
                        }
                        if (!e.exposureTime.isNullOrBlank()) {
                            InfoRow(stringResource(R.string.info_shutter), e.exposureTime!!)
                        }
                        if (!e.iso.isNullOrBlank()) {
                            InfoRow("ISO", e.iso!!)
                        }
                        if (!e.dateTimeOriginal.isNullOrBlank()) {
                            InfoRow(stringResource(R.string.info_time), e.dateTimeOriginal!!)
                        }
                    }
                }
            }

            // 参数面板：「显示内容」两个版式共用，版式专属参数另列
            FieldToggles(vm)
            when (style) {
                WatermarkStyle.CLASSIC -> ClassicSettings(vm)
                WatermarkStyle.ART -> ArtSettings(vm)
            }

            Spacer(Modifier.height(16.dp))

            // 保存按钮
            if (rendered != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .background(Apple.blue)
                            .clickable(enabled = !vm.isRendering) {
                                scope.launch {
                                    val bmp = rendered ?: return@launch
                                    val result = vm.saveResult(context, bmp)
                                    result.onSuccess { path ->
                                        // 只显示文件名；完整目录可用下方「打开保存文件夹」查看
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.wm_saved, path.substringAfterLast('/')),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }.onFailure {
                                        Toast.makeText(context, context.getString(R.string.wm_save_failed, it.message ?: ""), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Save, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.wm_save), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Box(
                        Modifier
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .background(Apple.fill)
                            .clickable {
                                picker.launch(PICK_MIME_TYPES)
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, null, tint = Apple.label, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // 打开保存文件夹：成品在 App 专属外部目录，无存储权限要求。
            // 放在保存按钮之外，未选照片时也能查看之前保存的成品。
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(Apple.fill)
                    .clickable {
                        if (!WatermarkStorage.openFolder(context)) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.wm_no_folder_app, WatermarkStorage.displayPath(context)),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FolderOpen, null, tint = Apple.blue, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.wm_open_folder),
                        color = Apple.blue,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = Apple.secondaryLabel, fontSize = 13.sp, modifier = Modifier.width(56.dp))
        Text(value, color = Apple.label, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(Apple.surface)
            .padding(14.dp),
    ) {
        Text(title, color = Apple.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Apple.label, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Apple.blue,
                checkedThumbColor = Color.White,
            ),
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    onValue: (Float) -> Unit,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    step: Float = 0.001f,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Apple.label, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(valueText, color = Apple.secondaryLabel, fontSize = 12.sp)
        }
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = range,
            steps = ((range.endInclusive - range.start) / step).toInt().coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = Apple.blue,
                activeTrackColor = Apple.blue,
            ),
        )
    }
}

@Composable
private fun FieldToggles(vm: WatermarkViewModel) {
    SectionCard(stringResource(R.string.wm_section_display)) {
        ToggleRow(stringResource(R.string.wm_show_model), vm.showModel) { vm.showModel = it; vm.updateAndRender() }
        ToggleRow(stringResource(R.string.info_lens), vm.showLens) { vm.showLens = it; vm.updateAndRender() }
        ToggleRow(stringResource(R.string.info_focal), vm.showFocal) { vm.showFocal = it; vm.updateAndRender() }
        ToggleRow(stringResource(R.string.info_aperture), vm.showAperture) { vm.showAperture = it; vm.updateAndRender() }
        ToggleRow(stringResource(R.string.info_shutter), vm.showShutter) { vm.showShutter = it; vm.updateAndRender() }
        ToggleRow("ISO", vm.showISO) { vm.showISO = it; vm.updateAndRender() }
        ToggleRow(stringResource(R.string.info_time), vm.showDateTime) { vm.showDateTime = it; vm.updateAndRender() }
    }
}

@Composable
private fun ClassicSettings(vm: WatermarkViewModel) {
    SectionCard(stringResource(R.string.wm_section_classic)) {
        ToggleRow(stringResource(R.string.wm_show_logo), vm.showLogo) { vm.showLogo = it; vm.updateAndRender() }
    }
}

@Composable
private fun ArtSettings(vm: WatermarkViewModel) {
    SectionCard(stringResource(R.string.wm_section_art)) {
        SliderRow(
            stringResource(R.string.wm_margin),
            vm.artImageMargin,
            { vm.artImageMargin = it; vm.updateAndRender() },
            "${((vm.artImageMargin) * 100).toInt()}%",
            0.04f..0.22f,
        )
        SliderRow(
            stringResource(R.string.wm_blur),
            vm.artBlurRadius,
            { vm.artBlurRadius = it; vm.updateAndRender() },
            "${((vm.artBlurRadius) * 100).toInt()}%",
            0.01f..0.15f,
        )
    }
}

/** 水印输入只放行 JPG 与尼康 NEF（SAF 的 MIME 过滤）。 */
private val PICK_MIME_TYPES = arrayOf(
    "image/jpeg",
    "image/x-nikon-nef",
    "image/nef",
)
