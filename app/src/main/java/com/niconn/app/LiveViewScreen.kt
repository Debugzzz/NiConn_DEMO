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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.niconn.service.ConnectionState
import com.niconn.service.PropSpecs
import kotlin.math.max
import kotlin.math.min

@Composable
fun LiveViewScreen(viewModel: ConnectionViewModel) {
    val lvViewModel: LiveViewViewModel = viewModel { LiveViewViewModel { viewModel.liveSession } }
    val state by viewModel.state.collectAsState()
    val frame by lvViewModel.frame.collectAsState()
    val isLive by lvViewModel.isLive.collectAsState()
    val error by lvViewModel.error.collectAsState()
    val focusPoint by lvViewModel.focusPoint.collectAsState()
    val afFrame by lvViewModel.afFrame.collectAsState()
    val isoOptions by lvViewModel.isoOptions.collectAsState()
    val shutterOptions by lvViewModel.shutterOptions.collectAsState()
    val apertureOptions by lvViewModel.apertureOptions.collectAsState()
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var landscape by rememberSaveable { mutableStateOf(false) }
    val displayFrame = remember(landscape, frame) {
        if (landscape) frame?.let { rotateBitmap(it, 90) } else frame
    }
    val currentFrame by rememberUpdatedState(displayFrame)

    LaunchedEffect(state) {
        if (state is ConnectionState.Session) lvViewModel.start()
    }
    DisposableEffect(Unit) {
        onDispose { lvViewModel.stop() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // 底层：全屏点按对焦（坐标按旋转+Crop 换算回原图）
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { containerSize = it }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val bitmap = currentFrame ?: return@detectTapGestures
                        val w = containerSize.width.toFloat()
                        val h = containerSize.height.toFloat()
                        if (w <= 0 || h <= 0) return@detectTapGestures
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
                            return@detectTapGestures
                        }
                        val ox = if (landscape) dy.toInt() else dx.toInt()
                        val oy = if (landscape) (bitmap.width - 1 - dx).toInt() else dy.toInt()
                        lvViewModel.tapFocus(ox, oy)
                    }
                },
        )
        displayFrame?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "实时取景",
                modifier = Modifier.fillMaxSize(),
                contentScale = if (landscape) ContentScale.Crop else ContentScale.Fit,
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
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 88.dp),
        ) {
            val bitmap = displayFrame ?: return@Canvas
            val point = focusPoint ?: return@Canvas
            if (bitmap.width <= 0 || bitmap.height <= 0 || size.width <= 0 || size.height <= 0) {
                return@Canvas
            }
            val dx = if (landscape) (bitmap.width - 1 - point.second).toFloat() else point.first.toFloat()
            val dy = if (landscape) point.first.toFloat() else point.second.toFloat()
            val scale = if (landscape) {
                max(size.width / bitmap.width, size.height / bitmap.height)
            } else {
                min(size.width / bitmap.width, size.height / bitmap.height)
            }
            val dw = bitmap.width * scale
            val dh = bitmap.height * scale
            val x0 = (size.width - dw) / 2f
            val y0 = (size.height - dh) / 2f
            val sx = x0 + dx * scale
            val sy = y0 + dy * scale
            val af = afFrame
            val boxW = if (af != null && af.valid) {
                af.hSize.toFloat() / af.wholeWidth * bitmap.width
            } else {
                64f
            }
            val boxH = if (af != null && af.valid) {
                af.vSize.toFloat() / af.wholeHeight * bitmap.height
            } else {
                64f
            }
            drawRect(
                color = Color(0xFF00E500),
                topLeft = Offset(sx - boxW * scale / 2f, sy - boxH * scale / 2f),
                size = Size(boxW * scale, boxH * scale),
                style = Stroke(width = 6f),
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalArrangement = if (landscape) {
                Arrangement.spacedBy(22.dp)
            } else {
                Arrangement.SpaceEvenly
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PropPicker("ISO", lvViewModel.iso.collectAsState().value, isoOptions, rotated = landscape) {
                lvViewModel.setProp(PropSpecs.ISO, it)
            }
            PropPicker(
                "快门",
                lvViewModel.shutter.collectAsState().value,
                shutterOptions,
                label = { v -> if (v >= 10000) "${v / 10000.0}s" else "1/${10000 / v}s" },
                rotated = landscape,
            ) {
                lvViewModel.setProp(PropSpecs.SHUTTER, it)
            }
            PropPicker(
                "光圈",
                lvViewModel.aperture.collectAsState().value,
                apertureOptions,
                label = { v -> "f/${v / 100.0}" },
                rotated = landscape,
            ) {
                lvViewModel.setProp(PropSpecs.APERTURE, it)
            }
            ShutterButton(onClick = { lvViewModel.takePhoto() }, rotated = landscape)
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

@Composable
private fun ShutterButton(onClick: () -> Unit, rotated: Boolean) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .graphicsLayer { if (rotated) rotationZ = 90f }
            .border(4.dp, Color.White, CircleShape)
            .clickable(onClick = onClick)
            .padding(12.dp),
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
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = Color(0xB3FFFFFF), fontSize = 11.sp)
                Text(
                    current,
                    color = Color.White,
                    fontSize = 14.sp,
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
