package com.niconn.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
        if (!landscape) {
            // 竖屏：上图像下控件，控件不压在画面上
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
                PortraitControls(lvViewModel, isoOptions, shutterOptions, apertureOptions, evOptions, wbOptions)
            }
        } else {
            // 横屏：左画面右竖排控件条，控件不压在画面上
            Row(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxHeight()
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
                LandscapeControls(lvViewModel, isoOptions, shutterOptions, apertureOptions, evOptions, wbOptions)
            }
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
                contentDescription = if (landscape) "切换竖屏" else "切换横屏",
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
                    "开始取景",
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
                contentDescription = "实时取景",
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

/** 竖屏控件：两行，位于画面下方（画面外）。 */
@Composable
private fun PortraitControls(
    lvViewModel: LiveViewViewModel,
    isoOptions: List<Long>,
    shutterOptions: List<Long>,
    apertureOptions: List<Long>,
    evOptions: List<Long>,
    wbOptions: List<Long>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PropPicker("曝光", lvViewModel.ev.collectAsState().value, evOptions, label = { v -> formatEvLabel(v) }) {
                lvViewModel.setProp(PropSpecs.EV, it)
            }
            PropPicker("白平衡", lvViewModel.wb.collectAsState().value, wbOptions, label = { v -> wbLabel(v) }) {
                lvViewModel.setProp(PropSpecs.WHITE_BALANCE, it)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PropPicker("ISO", lvViewModel.iso.collectAsState().value, isoOptions) {
                lvViewModel.setProp(PropSpecs.ISO, it)
            }
            PropPicker(
                "快门",
                lvViewModel.shutter.collectAsState().value,
                shutterOptions,
                label = { v -> if (v >= 10000) "${v / 10000.0}s" else "1/${10000 / v}s" },
            ) {
                lvViewModel.setProp(PropSpecs.SHUTTER, it)
            }
            PropPicker(
                "光圈",
                lvViewModel.aperture.collectAsState().value,
                apertureOptions,
                label = { v -> "f/${v / 100.0}" },
            ) {
                lvViewModel.setProp(PropSpecs.APERTURE, it)
            }
            ShutterButton(onClick = { lvViewModel.takePhoto() })
        }
    }
}

/** 横屏控件条：右侧竖排，各控件自身旋转 90°（画面外）。 */
@Composable
private fun LandscapeControls(
    lvViewModel: LiveViewViewModel,
    isoOptions: List<Long>,
    shutterOptions: List<Long>,
    apertureOptions: List<Long>,
    evOptions: List<Long>,
    wbOptions: List<Long>,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(68.dp)
            .background(Color.Black)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PropPicker("ISO", lvViewModel.iso.collectAsState().value, isoOptions, rotated = true) {
            lvViewModel.setProp(PropSpecs.ISO, it)
        }
        PropPicker(
            "快门",
            lvViewModel.shutter.collectAsState().value,
            shutterOptions,
            label = { v -> if (v >= 10000) "${v / 10000.0}s" else "1/${10000 / v}s" },
            rotated = true,
        ) {
            lvViewModel.setProp(PropSpecs.SHUTTER, it)
        }
        PropPicker(
            "光圈",
            lvViewModel.aperture.collectAsState().value,
            apertureOptions,
            label = { v -> "f/${v / 100.0}" },
            rotated = true,
        ) {
            lvViewModel.setProp(PropSpecs.APERTURE, it)
        }
        PropPicker(
            "曝光",
            lvViewModel.ev.collectAsState().value,
            evOptions,
            label = { v -> formatEvLabel(v) },
            rotated = true,
        ) {
            lvViewModel.setProp(PropSpecs.EV, it)
        }
        PropPicker(
            "白平衡",
            lvViewModel.wb.collectAsState().value,
            wbOptions,
            label = { v -> wbLabel(v) },
            rotated = true,
        ) {
            lvViewModel.setProp(PropSpecs.WHITE_BALANCE, it)
        }
        ShutterButton(onClick = { lvViewModel.takePhoto() }, rotated = true)
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

@Composable
private fun PropPicker(
    title: String,
    current: String,
    values: List<Long>,
    label: (Long) -> String = { it.toString() },
    rotated: Boolean = false,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .graphicsLayer { if (rotated) rotationZ = 90f }
                .background(Apple.scrim, CircleShape)
                .border(0.5.dp, Apple.hairlineOnDark, CircleShape)
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = Color(0xB3FFFFFF), fontSize = 10.sp)
                Text(
                    current,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(14.dp),
        ) {
            values.forEach { value ->
                DropdownMenuItem(
                    text = { Text(label(value)) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}
