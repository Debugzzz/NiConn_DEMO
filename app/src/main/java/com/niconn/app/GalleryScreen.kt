package com.niconn.app

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.niconn.protocol.PtpDataCodec
import com.niconn.protocol.PtpIpSession
import com.niconn.service.AppSettingsStore
import com.niconn.service.GalleryController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun GalleryScreen(viewModel: ConnectionViewModel, settings: AppSettingsStore) {
    val context = LocalContext.current
    val galleryViewModel: GalleryViewModel = viewModel {
        GalleryViewModel(
            getSession = { viewModel.liveSession },
            // 相机维度隔离缓存；guid 优先，缺失时退回实例名。调用时实时读取，
            // 换相机重连后自动落到新相机的缓存目录。
            getThumbCache = {
                val cam = viewModel.savedCamera.value
                val key = cam?.guid ?: cam?.instanceName ?: "default"
                com.niconn.service.ThumbCache(context.cacheDir, key)
            },
            getFullCache = {
                val cam = viewModel.savedCamera.value
                val key = cam?.guid ?: cam?.instanceName ?: "default"
                com.niconn.service.ThumbCache(context.cacheDir, key, rootName = "camera-full")
            },
        )
    }
    val session = viewModel.liveSession
    val items by galleryViewModel.items.collectAsState()
    val thumbs by galleryViewModel.thumbs.collectAsState()
    val selection by galleryViewModel.selection.collectAsState()
    val downloading by galleryViewModel.downloading.collectAsState()
    val error by galleryViewModel.error.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var previewHandle by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) { galleryViewModel.refresh() }

    // 全屏看图期间暂停缩略图预取：原图请求与预取共用一条串行会话，
    // 不暂停会被排队中的缩略图挡住（点开一直转圈的根因）
    LaunchedEffect(previewHandle) {
        galleryViewModel.setPrefetchPaused(previewHandle != null)
    }

    // 滚动预取：可见窗口上报给 ViewModel（可见优先 + 预取下一屏，按距离排序）
    LaunchedEffect(items.size) {
        snapshotFlow {
            val info = galleryViewModel.gridState.layoutInfo
            Triple(
                info.visibleItemsInfo.firstOrNull()?.index ?: 0,
                info.visibleItemsInfo.lastOrNull()?.index ?: 0,
                info.visibleItemsInfo.size,
            )
        }.distinctUntilChanged().collect { (first, last, size) ->
            galleryViewModel.requestWindow(first, last, size, items.size)
        }
    }

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
                val gridColumns by settings.gridColumns.collectAsState()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    state = galleryViewModel.gridState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items, key = { it.handle }, contentType = { "photo" }) { item ->
                        // 缩略图来自独立 map：到达时只更新对应格子，不重建网格列表
                        val thumb = thumbs[item.handle]
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
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Apple.background),
                            )
                            thumb?.let {
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
                // 右侧快速滚动条：滚动时淡入，可按住上下拖动定位
                val gridState = galleryViewModel.gridState
                val layoutInfo = gridState.layoutInfo
                val total = layoutInfo.totalItemsCount
                val visible = layoutInfo.visibleItemsInfo.size
                if (total > 0 && visible > 0 && total > visible) {
                    var trackHeightPx by remember { mutableStateOf(0f) }
                    var dragging by remember { mutableStateOf(false) }
                    var thumbCenterPx by remember { mutableStateOf(0f) }
                    val barAlpha by animateFloatAsState(
                        targetValue = if (dragging || gridState.isScrollInProgress) 1f else 0f,
                        label = "scrollbar",
                    )
                    val scope = rememberCoroutineScope()
                    val thumbH = (trackHeightPx * visible / total).coerceAtLeast(48f)
                    val trackRange = (trackHeightPx - thumbH).coerceAtLeast(1f)
                    val progress = (
                        (layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0).toFloat() /
                            (total - visible)
                        ).coerceIn(0f, 1f)
                    if (!dragging) thumbCenterPx = thumbH / 2f + trackRange * progress
                    fun scrollToProgress(p: Float) {
                        val maxIdx = total - visible
                        val idx = (p * maxIdx).toInt().coerceIn(0, maxIdx)
                        val avg = layoutInfo.visibleItemsInfo.firstOrNull()?.size?.height ?: 0
                        val frac = p * maxIdx - idx
                        scope.launch { gridState.scrollToItem(idx, (frac * avg).toInt()) }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(24.dp)
                            .padding(vertical = 8.dp)
                            .onSizeChanged { trackHeightPx = it.height.toFloat() }
                            .pointerInput(total, visible, trackHeightPx) {
                                detectVerticalDragGestures(
                                    onDragStart = { offset ->
                                        dragging = true
                                        val p = ((offset.y - thumbH / 2f).coerceIn(0f, trackRange)) / trackRange
                                        thumbCenterPx = thumbH / 2f + trackRange * p
                                        scrollToProgress(p)
                                    },
                                    onDragEnd = { dragging = false },
                                    onDragCancel = { dragging = false },
                                ) { change, dragAmount ->
                                    change.consume()
                                    thumbCenterPx = (thumbCenterPx + dragAmount)
                                        .coerceIn(thumbH / 2f, thumbH / 2f + trackRange)
                                    scrollToProgress((thumbCenterPx - thumbH / 2f) / trackRange)
                                }
                            },
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 6.dp)
                                .width(4.dp)
                                .height(with(LocalDensity.current) { thumbH.toDp() })
                                .graphicsLayer { translationY = thumbCenterPx - thumbH / 2f }
                                .alpha(barAlpha)
                                .background(Apple.blue, RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }
        }

        previewHandle?.let { handle ->
            FullImageOverlay(
                handle = handle,
                session = session,
                thumbnail = thumbs[handle],
                loadQuickPreview = { galleryViewModel.loadQuickPreview(handle) },
                loadSharpPreview = { galleryViewModel.loadSharpPreview(handle) },
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
    loadQuickPreview: suspend (Int) -> GalleryViewModel.PreviewResult?,
    loadSharpPreview: suspend (Int) -> GalleryViewModel.PreviewResult?,
    onClose: () -> Unit,
) {
    var preview by remember(handle) { mutableStateOf<Bitmap?>(null) }
    var rotation by remember(handle) { mutableStateOf(0) }
    var showInfo by remember(handle) { mutableStateOf(false) }
    var loadFailed by remember(handle) { mutableStateOf(false) }
    var info by remember(handle) { mutableStateOf<PtpDataCodec.ObjectInfoData?>(null) }
    var exif by remember(handle) { mutableStateOf<PhotoExif?>(null) }
    val scope = rememberCoroutineScope()
    val zoomState = remember(handle) { ZoomableState() }
    var viewSize by remember(handle) { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(handle) {
        // 两级取图（解码在 IO 线程）：快速档先上屏——命中原图缓存即最终画质，
        // 否则 640px 过渡图；原图档（GetObject，NEF 内嵌预览）到达后替换并落盘
        val quick = runCatching { loadQuickPreview(handle) }.getOrNull()
        quick?.bitmap?.let { preview = it }
        quick?.exif?.let { exif = it }
        var sharp: GalleryViewModel.PreviewResult? = null
        if (quick?.fromFullCache != true) {
            sharp = runCatching { loadSharpPreview(handle) }.getOrNull()
            sharp?.bitmap?.let { preview = it }
            sharp?.exif?.let { exif = it }
        }
        if (quick == null && sharp == null) loadFailed = true
        // 信息面板数据最后取，不挡图
        info = session?.let { GalleryController(it).objectInfo(handle) }
    }

    val content = preview ?: thumbnail
    LaunchedEffect(content, viewSize, rotation) {
        content?.let {
            // 90° 旋转经 graphicsLayer 完成（零拷贝），边界计算用交换后的宽高
            val (cw, ch) = if (rotation % 180 == 90) it.height to it.width else it.width to it.height
            zoomState.onSizeChanged(viewSize.width.toFloat(), viewSize.height.toFloat(), cw, ch)
        }
    }
    // 预览到达后交叉淡入
    val previewAlpha by animateFloatAsState(
        targetValue = if (preview != null) 1f else 0f,
        label = "previewFade",
    )

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { viewSize = it }
                .zoomable(zoomState, scope)
                .graphicsLayer {
                    scaleX = zoomState.scale
                    scaleY = zoomState.scale
                    translationX = zoomState.offsetX
                    translationY = zoomState.offsetY
                    rotationZ = rotation.toFloat()
                },
        ) {
            thumbnail?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "预览",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - previewAlpha },
                    contentScale = ContentScale.Fit,
                )
            }
            preview?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "大图 $handle",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = previewAlpha },
                    contentScale = ContentScale.Fit,
                )
            }
        }
        if (content == null) {
            if (loadFailed) {
                Text(
                    "加载失败，请检查相机连接",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        } else if (preview == null && !loadFailed) {
            // 缩略图已在展示，仅底部小提示
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .size(22.dp)
                    .alpha(0.7f),
            )
        } else if (preview == null && loadFailed) {
            Text(
                "原图加载失败，当前显示缩略图",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .alpha(0.8f),
            )
        }
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OverlayIconButton(Icons.Filled.Info, "照片信息") {
                showInfo = true
            }
            OverlayIconButton(Icons.Filled.Refresh, "旋转") {
                rotation = (rotation + 90) % 360
                zoomState.reset()
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
                InfoRow("大小", data?.compressedSize?.let { String.format(Locale.US, "%.2f MB", it / 1048576.0) } ?: "—")
                InfoRow(
                    "分辨率",
                    content?.let { "${it.width} × ${it.height}" }
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
