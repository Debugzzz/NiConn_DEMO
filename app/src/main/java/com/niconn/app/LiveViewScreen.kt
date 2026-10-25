package com.niconn.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.niconn.service.AppSettingsStore
import com.niconn.service.ConnectionState
import com.niconn.service.PropSpecs
import kotlin.math.max
import kotlin.math.min

@Composable
fun LiveViewScreen(
    viewModel: ConnectionViewModel,
    settings: AppSettingsStore,
    onFullscreenChanged: (Boolean) -> Unit = {},
) {
    val lvViewModel: LiveViewViewModel = viewModel { LiveViewViewModel({ viewModel.liveSession }, settings) }
    val state by viewModel.state.collectAsState()
    val frame by lvViewModel.frame.collectAsState()
    val isLive by lvViewModel.isLive.collectAsState()
    val error by lvViewModel.error.collectAsState()
    val focusPoint by lvViewModel.focusPoint.collectAsState()
    val afFrame by lvViewModel.afFrame.collectAsState()
    val focusFactors by lvViewModel.focusFactors.collectAsState()
    val isoOptions by lvViewModel.isoOptions.collectAsState()
    val shutterOptions by lvViewModel.shutterOptions.collectAsState()
    val apertureOptions by lvViewModel.apertureOptions.collectAsState()
    val evOptions by lvViewModel.evOptions.collectAsState()
    val wbOptions by lvViewModel.wbOptions.collectAsState()
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var landscape by rememberSaveable { mutableStateOf(settings.defaultLandscape.value) }
    var activePicker by remember { mutableStateOf<PropKey?>(null) }
    val displayFrame = remember(landscape, frame) {
        if (landscape) frame?.let { rotateBitmap(it, 90) } else frame
    }
    val currentFrame by rememberUpdatedState(displayFrame)

    LaunchedEffect(state) {
        if (state is ConnectionState.Session) lvViewModel.start()
    }
    DisposableEffect(Unit) {
        onDispose {
            lvViewModel.stop()
            onFullscreenChanged(false)
        }
    }
    // 横屏时隐藏底部导航栏，竖屏恢复
    LaunchedEffect(landscape) { onFullscreenChanged(landscape) }
    // 取景页按设置保持屏幕常亮
    val view = LocalView.current
    val keepScreenOn by settings.keepScreenOn.collectAsState()
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // 上图像下控件（横竖屏同构）：控件区黑底、不显画面；横屏时控件保持原位仅自身旋转
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onSizeChanged { containerSize = it },
            ) {
                LiveViewCanvasLayers(
                    displayFrame = displayFrame,
                    focusPoint = focusPoint,
                    afFrame = afFrame,
                    focusFactors = focusFactors,
                    landscape = landscape,
                    onTap = { offset -> handleTap(offset, currentFrame, containerSize, landscape, lvViewModel) },
                )
            }
            ControlsBlock(
                lvViewModel,
                isoOptions,
                shutterOptions,
                apertureOptions,
                evOptions,
                wbOptions,
                landscape,
                activePicker = activePicker,
                onTogglePicker = { key ->
                    activePicker = if (activePicker == key) null else key
                },
            )
        }
        activePicker?.let { key ->
            OptionStripOverlay(
                title = stringResource(key.titleRes),
                values = pickerValues(key, isoOptions, shutterOptions, apertureOptions, evOptions, wbOptions),
                current = pickerCurrent(key, lvViewModel),
                label = pickerLabel(key, wbLabelTable(pickerValues(key, isoOptions, shutterOptions, apertureOptions, evOptions, wbOptions))),
                landscape = landscape,
                onSelect = { value ->
                    pickerSelect(key, lvViewModel, value)
                    activePicker = null
                },
                onClose = { activePicker = null },
            )
        }
        error?.let {
            Text(
                text = it,
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .background(Apple.scrimStrong, RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(34.dp)
                .background(Apple.scrim, CircleShape)
                .clickable { landscape = !landscape },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.ScreenRotation,
                contentDescription = stringResource(if (landscape) R.string.lv_rotate_portrait else R.string.lv_rotate_landscape),
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        if (!isLive) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Apple.scrimStrong, RoundedCornerShape(50))
                    .border(1.dp, Apple.hairlineOnDark, RoundedCornerShape(50))
                    .clickable { lvViewModel.start() }
                    .padding(horizontal = 28.dp, vertical = 14.dp),
            ) {
                Text(
                    stringResource(R.string.lv_start),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** 点按对焦：屏幕坐标按旋转 + Crop/Fit 换算回原生位图坐标。 */
private fun handleTap(
    offset: Offset,
    currentFrame: android.graphics.Bitmap?,
    containerSize: IntSize,
    landscape: Boolean,
    lvViewModel: LiveViewViewModel,
) {
    val bitmap = currentFrame ?: return
    val w = containerSize.width.toFloat()
    val h = containerSize.height.toFloat()
    if (w <= 0 || h <= 0) return
    val scale = if (landscape) {
        max(w / bitmap.width, h / bitmap.height)
    } else {
        min(w / bitmap.width, h / bitmap.height)
    }
    val dw = bitmap.width * scale
    val dh = bitmap.height * scale
    val x0 = (w - dw) / 2f
    val y0 = (h - dh) / 2f
    val dx = (offset.x - x0) / scale
    val dy = (offset.y - y0) / scale
    if (dx < 0 || dy < 0 || dx >= bitmap.width || dy >= bitmap.height) {
        return
    }
    val ox = if (landscape) dy.toInt() else dx.toInt()
    val oy = if (landscape) (bitmap.width - 1 - dx).toInt() else dy.toInt()
    lvViewModel.tapFocus(ox, oy)
}

/** 图像层：点按对焦 + 取景画面 + 对焦框（三者同一坐标系，严格对齐）。 */
@Composable
private fun LiveViewCanvasLayers(
    displayFrame: android.graphics.Bitmap?,
    focusPoint: Pair<Int, Int>?,
    afFrame: com.niconn.protocol.PtpDataCodec.AfFrame?,
    focusFactors: Pair<Float, Float>,
    landscape: Boolean,
    onTap: (Offset) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset -> onTap(offset) }
            },
    ) {
        displayFrame?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = stringResource(R.string.lv_frame_desc),
                modifier = Modifier.fillMaxSize(),
                contentScale = if (landscape) ContentScale.Crop else ContentScale.Fit,
            )
        }
        // 对焦框：优先用相机反馈的 AF 中心/尺寸（相机 Whole 坐标 → 原生位图像素 → 显示坐标）
        Canvas(modifier = Modifier.fillMaxSize()) {
            val bitmap = displayFrame ?: return@Canvas
            if (bitmap.width <= 0 || bitmap.height <= 0 || size.width <= 0 || size.height <= 0) return@Canvas
            val fx = if (focusFactors.first > 0f) focusFactors.first else 8f
            val fy = if (focusFactors.second > 0f) focusFactors.second else 8f
            val cxN: Float
            val cyN: Float
            val wN: Float
            val hN: Float
            val af = afFrame
            if (af != null && af.valid) {
                cxN = af.hCenter / fx
                cyN = af.vCenter / fy
                wN = af.hSize / fx
                hN = af.vSize / fy
            } else {
                val p = focusPoint ?: return@Canvas
                cxN = p.first.toFloat()
                cyN = p.second.toFloat()
                wN = 64f
                hN = 64f
            }
            // 原生位图 → 显示帧（横屏为旋转 90° 后的位图，宽高轴互换）
            val dispX: Float
            val dispY: Float
            val dispW: Float
            val dispH: Float
            if (landscape) {
                dispX = bitmap.width - 1f - cyN
                dispY = cxN
                dispW = hN
                dispH = wN
            } else {
                dispX = cxN
                dispY = cyN
                dispW = wN
                dispH = hN
            }
            val scale = if (landscape) {
                max(size.width / bitmap.width, size.height / bitmap.height)
            } else {
                min(size.width / bitmap.width, size.height / bitmap.height)
            }
            val dw = bitmap.width * scale
            val dh = bitmap.height * scale
            val x0 = (size.width - dw) / 2f
            val y0 = (size.height - dh) / 2f
            drawRect(
                color = Color(0xFF00E500),
                topLeft = Offset(
                    x0 + dispX * scale - dispW * scale / 2f,
                    y0 + dispY * scale - dispH * scale / 2f,
                ),
                size = Size(dispW * scale, dispH * scale),
                style = Stroke(width = 5f),
            )
        }
    }
}

/** 控件区：两行，位于画面下方（黑底、不显画面）。横屏时控件保持原位、仅自身旋转。 */
@Composable
private fun ControlsBlock(
    lvViewModel: LiveViewViewModel,
    isoOptions: List<Long>,
    shutterOptions: List<Long>,
    apertureOptions: List<Long>,
    evOptions: List<Long>,
    wbOptions: List<Long>,
    landscape: Boolean,
    activePicker: PropKey?,
    onTogglePicker: (PropKey) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PropPicker(
                PropKey.EV,
                lvViewModel.ev.collectAsState().value,
                rotated = landscape,
                active = activePicker == PropKey.EV,
                onToggle = { onTogglePicker(PropKey.EV) },
            )
            PropPicker(
                PropKey.WB,
                wbDisplayLabel(lvViewModel.wbCode.collectAsState().value),
                rotated = landscape,
                active = activePicker == PropKey.WB,
                onToggle = { onTogglePicker(PropKey.WB) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PropPicker(
                PropKey.ISO,
                lvViewModel.iso.collectAsState().value,
                rotated = landscape,
                active = activePicker == PropKey.ISO,
                onToggle = { onTogglePicker(PropKey.ISO) },
            )
            PropPicker(
                PropKey.SHUTTER,
                lvViewModel.shutter.collectAsState().value,
                rotated = landscape,
                active = activePicker == PropKey.SHUTTER,
                onToggle = { onTogglePicker(PropKey.SHUTTER) },
            )
            PropPicker(
                PropKey.APERTURE,
                lvViewModel.aperture.collectAsState().value,
                rotated = landscape,
                active = activePicker == PropKey.APERTURE,
                onToggle = { onTogglePicker(PropKey.APERTURE) },
            )
            ShutterButton(onClick = { lvViewModel.takePhoto() }, rotated = landscape)
        }
    }
}

/** 参数胶囊：点击打开横向选项条；横屏时自身原地旋转（文字随之可读）。 */
@Composable
private fun PropPicker(
    key: PropKey,
    current: String,
    rotated: Boolean = false,
    active: Boolean = false,
    onToggle: () -> Unit,
) {
    val title = stringResource(key.titleRes)
    Box(
        modifier = Modifier
            .graphicsLayer { if (rotated) rotationZ = 90f }
            .background(if (active) Apple.blue else Apple.scrim, CircleShape)
            .border(
                0.5.dp,
                if (active) Color.White else Apple.hairlineOnDark,
                CircleShape,
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color(0xB3FFFFFF), fontSize = 9.sp)
            Text(
                current,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** 全宽横向选项条：横屏时整条旋转 90°、长度撑满屏幕长边（点选菜单）。 */
@Composable
private fun OptionStripOverlay(
    title: String,
    values: List<Long>,
    current: String,
    label: (Long) -> String,
    landscape: Boolean,
    onSelect: (Long) -> Unit,
    onClose: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x88000000))
            .clickable(onClick = onClose),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val strip = @Composable {
                val listState = rememberLazyListState()
                LaunchedEffect(current) {
                    val idx = values.indexOfFirst { label(it) == current }
                    if (idx > 0) listState.scrollToItem(idx)
                }
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(values) { value ->
                        val selected = label(value) == current
                        Text(
                            text = label(value),
                            color = if (selected) Apple.blue else Color.White,
                            fontSize = 16.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selected) Color.White else Apple.scrimStrong, CircleShape)
                                .clickable {
                                    onSelect(value)
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            if (landscape) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer { rotationZ = 90f }
                        .width(maxHeight)
                        .height(52.dp)
                        .background(Apple.scrimStrong, RoundedCornerShape(26.dp)),
                ) {
                    strip()
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(Apple.scrimStrong, RoundedCornerShape(26.dp)),
                ) {
                    strip()
                }
            }
        }
    }
}

private fun pickerValues(
    key: PropKey,
    isoOptions: List<Long>,
    shutterOptions: List<Long>,
    apertureOptions: List<Long>,
    evOptions: List<Long>,
    wbOptions: List<Long>,
): List<Long> = when (key) {
    PropKey.ISO -> isoOptions
    PropKey.SHUTTER -> shutterOptions
    PropKey.APERTURE -> apertureOptions
    PropKey.EV -> evOptions
    PropKey.WB -> wbOptions
}

@Composable
private fun pickerCurrent(key: PropKey, lvViewModel: LiveViewViewModel): String = when (key) {
    PropKey.ISO -> lvViewModel.iso.collectAsState().value
    PropKey.SHUTTER -> lvViewModel.shutter.collectAsState().value
    PropKey.APERTURE -> lvViewModel.aperture.collectAsState().value
    PropKey.EV -> lvViewModel.ev.collectAsState().value
    PropKey.WB -> wbDisplayLabel(lvViewModel.wbCode.collectAsState().value)
}

/** WB 标签预解析表（组合上下文中一次性构建，供非组合 lambda 使用）。 */
@Composable
private fun wbLabelTable(values: List<Long>): Map<Long, String> =
    values.associateWith { v -> wbDisplayLabel(v) }

@Composable
private fun pickerLabel(key: PropKey, wbLabels: Map<Long, String>): (Long) -> String = when (key) {
    PropKey.ISO -> { v -> v.toString() }
    PropKey.SHUTTER -> { v -> if (v >= 10000) "${v / 10000.0}s" else "1/${10000 / v}s" }
    PropKey.APERTURE -> { v -> "f/${v / 100.0}" }
    PropKey.EV -> { v -> formatEvLabel(v) }
    PropKey.WB -> { v -> wbLabels[v] ?: "0x%04X".format(v) }
}

private fun pickerSelect(key: PropKey, lvViewModel: LiveViewViewModel, value: Long) {
    when (key) {
        PropKey.ISO -> lvViewModel.setProp(PropSpecs.ISO, value)
        PropKey.SHUTTER -> lvViewModel.setProp(PropSpecs.SHUTTER, value)
        PropKey.APERTURE -> lvViewModel.setProp(PropSpecs.APERTURE, value)
        PropKey.EV -> lvViewModel.setProp(PropSpecs.EV, value)
        PropKey.WB -> lvViewModel.setProp(PropSpecs.WHITE_BALANCE, value)
    }
}

/** 取景参数胶囊键：本地化标题经资源 id 关联（避免以文案做逻辑键）。 */
private enum class PropKey(val titleRes: Int) {
    EV(R.string.prop_ev),
    WB(R.string.prop_wb),
    ISO(R.string.prop_iso),
    SHUTTER(R.string.prop_shutter),
    APERTURE(R.string.prop_aperture),
}

/** 白平衡码 → 显示名（本地化），未知码回退十六进制。 */
@Composable
internal fun wbDisplayLabel(v: Long?): String {
    if (v == null) return "--"
    return when (v) {
        0x0002L -> stringResource(R.string.wb_auto)
        0x0004L -> stringResource(R.string.wb_daylight)
        0x0005L -> stringResource(R.string.wb_fluorescent)
        0x0006L -> stringResource(R.string.wb_incandescent)
        0x0007L -> stringResource(R.string.wb_flash)
        0x8010L -> stringResource(R.string.wb_cloudy)
        0x8011L -> stringResource(R.string.wb_shade)
        0x8012L -> stringResource(R.string.wb_colortemp)
        0x8013L -> stringResource(R.string.wb_preset)
        0x8016L -> stringResource(R.string.wb_natural)
        else -> "0x%04X".format(v)
    }
}

@Composable
private fun ShutterButton(onClick: () -> Unit, rotated: Boolean = false) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer { if (rotated) rotationZ = 90f }
            .border(3.dp, Color.White, CircleShape)
            .clickable(onClick = onClick)
            .padding(9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFFFF3B30), CircleShape),
        )
    }
}

