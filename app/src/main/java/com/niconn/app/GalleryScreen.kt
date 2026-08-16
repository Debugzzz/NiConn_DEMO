package com.niconn.app

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    Box(Modifier.fillMaxSize().background(Apple.surface)) {
        Column(Modifier.fillMaxSize()) {
            // iOS「照片」风格大标题 + 工具行
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("照片", color = Apple.label, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(
                    "共 ${items.size} 张",
                    color = Apple.secondaryLabel,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 10.dp, top = 10.dp),
                )
                Spacer(Modifier.weight(1f))
                if (downloading) {
                    CircularProgressIndicator(
                        color = Apple.blue,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (selection.isNotEmpty()) {
                    GalleryTextButton(
                        if (downloading) "下载中…" else "下载 (${selection.size})",
                        enabled = !downloading,
                    ) { galleryViewModel.downloadSelected(context) }
                    GalleryTextButton("删除", color = Apple.red) { showDeleteConfirm = true }
                }
                GalleryTextButton("刷新") { galleryViewModel.refresh() }
            }
            error?.let {
                Text(
                    it,
                    color = Apple.red,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
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
                                .padding(1.dp)
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
                            // 格式角标（NEF/JPEG），放左下角避免与右上角选择圈冲突
                            item.formatLabel?.let { label ->
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(4.dp)
                                        .background(Apple.scrim, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                            // iOS 照片风格选择圈：白圈 → 蓝底白勾
                            if (item.handle in selection) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(24.dp)
                                        .background(Apple.blue, CircleShape)
                                        .border(1.5.dp, Color.White, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "已选择",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
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
                            color = Apple.blue,
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
        AppleAlert(
            title = "删除所选图像？",
            onDismiss = { showDeleteConfirm = false },
            confirmText = "删除",
            onConfirm = { galleryViewModel.deleteSelected() },
            destructive = true,
            hasBody = true,
        ) {
            Text(
                "将删除相机上的 ${selection.size} 张图像，此操作不可恢复。",
                fontSize = 13.sp,
                color = Apple.secondaryLabel,
            )
        }
    }
}

/** iOS 导航栏风格文字按钮 */
@Composable
private fun GalleryTextButton(
    text: String,
    enabled: Boolean = true,
    color: Color = Apple.blue,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = if (enabled) color else Apple.secondaryLabel,
        fontSize = 17.sp,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    )
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
    Box(Modifier.fillMaxSize().background(Color.Black)) {
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
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OverlayIconButton(Icons.Filled.Info, "照片信息") {
                scope.launch {
                    info = session?.let { GalleryController(it).objectInfo(handle) }
                }
                showInfo = true
            }
            OverlayIconButton(Icons.Filled.Refresh, "旋转") {
                rotation = (rotation + 90) % 360
            }
            OverlayIconButton(Icons.Filled.Close, "关闭", onClick = onClose)
        }
        if (showInfo) {
            AppleAlert(
                title = "照片信息",
                onDismiss = { showInfo = false },
                confirmText = "关闭",
                onConfirm = { },
                hasBody = true,
            ) {
                val data = info
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
        }
    }
}

/** 大图查看器右上角圆形半透明按钮 */
@Composable
private fun OverlayIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(Color(0x66000000), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(label, color = Apple.secondaryLabel, fontSize = 13.sp, modifier = Modifier.padding(end = 16.dp))
        Text(value, fontSize = 13.sp, modifier = Modifier.weight(1f))
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
