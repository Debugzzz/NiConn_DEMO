package com.niconn.app

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.niconn.protocol.PtpDataCodec
import com.niconn.protocol.PtpIpSession
import com.niconn.service.GalleryController
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun GalleryScreen(viewModel: ConnectionViewModel) {
    val galleryViewModel: GalleryViewModel = viewModel { GalleryViewModel { viewModel.liveSession } }
    val session = viewModel.liveSession
    val items by galleryViewModel.items.collectAsState()
    val selection by galleryViewModel.selection.collectAsState()
    val downloading by galleryViewModel.downloading.collectAsState()
    val error by galleryViewModel.error.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var previewHandle by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) { galleryViewModel.refresh() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            error?.let {
                Text(it, color = Color(0xFFFF3B30), modifier = Modifier.padding(8.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("共 ${items.size} 张 · 已选 ${selection.size}", color = Color(0xFF1A1A1A))
                Row {
                    if (selection.isNotEmpty()) {
                        Button(
                            onClick = { galleryViewModel.downloadSelected(context) },
                            enabled = !downloading,
                        ) {
                            Text(if (downloading) "下载中…" else "下载")
                        }
                        Button(onClick = { showDeleteConfirm = true }) { Text("删除") }
                    }
                    Button(onClick = { galleryViewModel.refresh() }) { Text("刷新") }
                }
            }
            Box(Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = galleryViewModel.gridState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items, key = { it.handle }) { item ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .combinedClickable(
                                    onClick = {
                                        if (selection.isEmpty()) {
                                            previewHandle = item.handle
                                        } else {
                                            galleryViewModel.toggleSelect(item.handle)
                                        }
                                    },
                                    onLongClick = { galleryViewModel.toggleSelect(item.handle) },
                                ),
                        ) {
                            item.thumbnail?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "缩略图 ${item.handle}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            item.formatLabel?.let { label ->
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(3.dp)
                                        .background(Color(0x99000000), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                            if (item.handle in selection) {
                                Box(Modifier.fillMaxSize().background(Color(0x8800A0FF)))
                                Text(
                                    "✓",
                                    color = Color.White,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                )
                            }
                        }
                    }
                }
                // 最右侧滚动进度条
                Canvas(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(3.dp)
                        .padding(vertical = 8.dp),
                ) {
                    val info = galleryViewModel.gridState.layoutInfo
                    val total = info.totalItemsCount
                    val visible = info.visibleItemsInfo.size
                    if (total > 0 && visible > 0 && total > visible) {
                        val thumbH = (size.height * visible / total).coerceAtLeast(24f)
                        val progress = (info.visibleItemsInfo.firstOrNull()?.index ?: 0).toFloat() /
                            (total - visible)
                        val y = (size.height - thumbH) * progress
                        drawRoundRect(
                            color = Color(0xFF007AFF),
                            topLeft = Offset(0f, y),
                            size = Size(size.width, thumbH),
                            cornerRadius = CornerRadius(size.width / 2f),
                        )
                    }
                }
            }
        }

        previewHandle?.let { handle ->
            FullImageOverlay(
                handle = handle,
                session = session,
                thumbnail = items.find { it.handle == handle }?.thumbnail,
                onClose = { previewHandle = null },
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除所选图像？") },
            text = { Text("将删除相机上的 ${selection.size} 张图像，此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    galleryViewModel.deleteSelected()
                    showDeleteConfirm = false
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun FullImageOverlay(
    handle: Int,
    session: PtpIpSession?,
    thumbnail: Bitmap?,
    onClose: () -> Unit,
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rotation by remember { mutableStateOf(0) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showInfo by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<PtpDataCodec.ObjectInfoData?>(null) }
    var exif by remember { mutableStateOf<PhotoExif?>(null) }
    var fileSize by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    val displayBitmap = remember(bitmap, rotation) {
        bitmap?.let { rotateBitmap(it, rotation) }
    }
    LaunchedEffect(handle) {
        val controller = session?.let { GalleryController(it) }
        info = controller?.objectInfo(handle)
        val bytes = controller?.loadImage(handle)
        if (bytes != null) {
            fileSize = bytes.size.toLong()
            exif = parseExif(bytes)
            bitmap = decodeSampledRotated(bytes, 2048)
        }
    }
    Box(Modifier.fillMaxSize()) {
        displayBitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "大图 $handle",
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset += pan
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentScale = ContentScale.Fit,
            )
        } ?: run {
            thumbnail?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "预览",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            CircularProgressIndicator(
                color = Color(0xFF8E8E93),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0x66000000), RoundedCornerShape(50))
                    .clickable {
                        scope.launch {
                            info = session?.let { GalleryController(it).objectInfo(handle) }
                        }
                        showInfo = true
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("ℹ", color = Color.White, fontSize = 18.sp)
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0x66000000), RoundedCornerShape(50))
                    .clickable { rotation = (rotation + 90) % 360 },
                contentAlignment = Alignment.Center,
            ) {
                Text("↻", color = Color.White, fontSize = 20.sp)
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0x66000000), RoundedCornerShape(50))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", color = Color.White, fontSize = 18.sp)
            }
        }
        if (showInfo) {
            val data = info
            AlertDialog(
                onDismissRequest = { showInfo = false },
                title = { Text("照片信息") },
                text = {
                    Column {
                        InfoRow("文件名", data?.filename ?: "IMG_$handle")
                        InfoRow("格式", formatName(data?.format))
                        InfoRow("大小", fileSize?.let { String.format(Locale.US, "%.2f MB", it / 1048576.0) } ?: "—")
                        InfoRow(
                            "分辨率",
                            bitmap?.let { "${it.width} × ${it.height}" }
                                ?: data?.let { "${it.imageWidth} × ${it.imageHeight}" }
                                ?: "—",
                        )
                        InfoRow("ISO", exif?.iso ?: "—")
                        InfoRow("快门", exif?.exposure?.let { "${it}s" } ?: "—")
                        InfoRow("光圈", exif?.fNumber?.let { "f/${"%.1f".format(rationalValue(it) ?: return@let null)}" } ?: "—")
                        InfoRow("焦距", exif?.focal?.let { "${"%.1f".format(rationalValue(it) ?: return@let null)}mm" } ?: "—")
                        InfoRow("机型", exif?.model ?: "—")
                        InfoRow("拍摄日期", exif?.dateTime ?: data?.captureDate ?: "—")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInfo = false }) { Text("关闭") }
                },
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(label, color = Color(0xFF8E8E93), modifier = Modifier.padding(end = 12.dp))
        Text(value)
    }
}

private fun formatName(format: Int?): String = when (format) {
    0x3000, 0x3800 -> "NEF"
    0x3801 -> "JPEG"
    else -> format?.let { "0x%04X".format(it) } ?: "—"
}

private fun rationalValue(value: String?): Double? {
    val v = value ?: return null
    val parts = v.split("/")
    return if (parts.size == 2) {
        val n = parts[0].toDoubleOrNull() ?: return null
        val d = parts[1].toDoubleOrNull() ?: return null
        if (d == 0.0) null else n / d
    } else {
        v.toDoubleOrNull()
    }
}
