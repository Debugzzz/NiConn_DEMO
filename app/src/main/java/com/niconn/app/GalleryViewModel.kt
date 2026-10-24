package com.niconn.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.niconn.protocol.PtpIpSession
import com.niconn.service.GalleryController
import com.niconn.service.ThumbCache
import com.niconn.watermark.extractLargestEmbeddedJpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class GalleryViewModel(
    private val getSession: () -> PtpIpSession?,
    private val getThumbCache: () -> ThumbCache? = { null },
    private val getFullCache: () -> ThumbCache? = { null },
) : ViewModel() {
    /** 网格条目：句柄 + 角标。加载完成后保持不变，缩略图走独立 map。 */
    data class GalleryItem(
        val handle: Int,
        val formatLabel: String? = null,
    )

    val gridState = LazyGridState()
    private val _items = MutableStateFlow<List<GalleryItem>>(emptyList())
    val items: StateFlow<List<GalleryItem>> = _items
    private val _selection = MutableStateFlow<Set<Int>>(emptySet())
    val selection: StateFlow<Set<Int>> = _selection
    private val _downloading = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = _downloading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** 句柄 → 已解码缩略图。增量发布，条目到达不重建网格列表。 */
    private val _thumbs = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val thumbs: StateFlow<Map<Int, Bitmap>> = _thumbs

    /** 会话内格式表（refresh 时 3 次过滤查询得到，替代逐张 GetObjectInfo）。 */
    private val handleFormats = HashMap<Int, Int>()

    /** 格式表所属会话：换会话（重连/换相机）时清空，避免句柄重排后串角标。 */
    private var formatsSession: PtpIpSession? = null

    /** 加载失败黑名单：避免反复重试打爆会话；手动刷新时清空重试。 */
    private val failed = MutableStateFlow<Set<Int>>(emptySet())

    /** 预取队列：单工作协程顺序回源（相机命令本就串行，排队还能按视口距离重排优先级）。 */
    private val fetchQueue = Channel<Int>(Channel.UNLIMITED)
    private val queued = java.util.concurrent.ConcurrentHashMap<Int, Unit>()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            for (handle in fetchQueue) {
                fetchThumb(handle)
                queued.remove(handle)
            }
        }
    }

    fun refresh() {
        val session = getSession()
        if (session == null) {
            _error.value = "请先在连接页连接相机"
            return
        }
        viewModelScope.launch {
            _error.value = null
            try {
                val controller = GalleryController(session)
                val handles = controller.listHandles()
                failed.value = emptySet()
                if (formatsSession !== session) {
                    handleFormats.clear()
                    formatsSession = session
                }
                // 网格立即可见：只取句柄列表；磁盘缓存校验（句柄消失则整册失效）
                getThumbCache()?.retain(handles.toSet())
                getFullCache()?.retain(handles.toSet())
                _items.value = handles.map { GalleryItem(it, label(it)) }
                // 格式角标：3 次按格式过滤的批量查询，不再逐张 GetObjectInfo
                val formats = controller.formatByHandle()
                handleFormats.putAll(formats)
                _items.value = handles.map { GalleryItem(it, label(it)) }
                com.niconn.AppLog.d("GAL", "refresh total=${handles.size} thumbs=${_thumbs.value.size} formats=${formats.size}")
            } catch (e: Exception) {
                _error.value = "读取相册失败：${e.message}"
            }
        }
    }

    /**
     * 网格滚动时上报可见窗口：可见项优先、再预取下方一屏，
     * 按与窗口中心的距离排序入队（滚得快时后入队的最新意图仍按序消费）。
     */
    fun requestWindow(firstIndex: Int, lastIndex: Int, pageSize: Int, total: Int) {
        if (total <= 0 || pageSize <= 0) return
        val items = _items.value
        val center = (firstIndex + lastIndex) / 2f
        val from = firstIndex.coerceIn(0, items.size - 1)
        val to = minOf(items.size - 1, maxOf(lastIndex, firstIndex + pageSize - 1) + pageSize)
        (from..to)
            .sortedBy { abs(it - center) }
            .map { items[it].handle }
            .forEach { enqueue(it) }
    }

    private fun enqueue(handle: Int) {
        if (_thumbs.value.containsKey(handle)) return
        if (handle in failed.value) return
        if (queued.putIfAbsent(handle, Unit) != null) return
        fetchQueue.trySend(handle)
    }

    /** 缩略图回源：磁盘缓存 → GetThumb；磁盘文件解码失败视为未命中。 */
    private suspend fun fetchThumb(handle: Int) {
        if (_thumbs.value.containsKey(handle) || handle in failed.value) return
        val session = getSession() ?: return
        try {
            val cacheStore = getThumbCache()
            var bitmap = cacheStore?.load(handle)?.let { decodeSampled(it, 320) }
            if (bitmap == null) {
                val bytes = GalleryController(session).loadThumb(handle)
                bitmap = bytes?.let { decodeSampled(it, 320) }
                if (bytes != null && getSession() === session) cacheStore?.store(handle, bytes)
            }
            if (bitmap != null) {
                _thumbs.value = _thumbs.value + (handle to bitmap)
            } else {
                failed.value = failed.value + handle
            }
        } catch (e: Exception) {
            failed.value = failed.value + handle
            com.niconn.AppLog.d("GAL", "thumb failed handle=$handle: ${e.message}")
        }
    }

    /**
     * 全屏查看·快速档：磁盘缓存（原图档落盘的字节，命中即最终画质）→
     * GetLargeThumb（0x90C4）。注意 3:2 照片按规范优先级实际返回 MPF class 1
     * 约 640×424，只作过渡显示，不落盘。
     */
    suspend fun loadQuickPreview(handle: Int): ByteArray? = withContext(Dispatchers.IO) {
        val fullCache = getFullCache()
        fullCache?.load(handle)?.let { return@withContext it }
        val session = getSession() ?: return@withContext null
        GalleryController(session).loadLargeThumb(handle)
    }

    /**
     * 全屏查看·原图档：磁盘缓存命中直接返回（二次打开零网络且即清晰版）；
     * 否则 GetObject（NEF 取内嵌 JPEG 预览），字节落盘供下次秒开。
     */
    suspend fun loadSharpPreview(handle: Int): ByteArray? = withContext(Dispatchers.IO) {
        val fullCache = getFullCache()
        fullCache?.load(handle)?.let { return@withContext it }
        val session = getSession() ?: return@withContext null
        val bytes = GalleryController(session).loadImage(handle)
            ?.let { raw -> extractLargestEmbeddedJpeg(raw) ?: raw }
        if (bytes != null && getSession() === session) fullCache?.store(handle, bytes)
        bytes
    }

    fun toggleSelect(handle: Int) {
        _selection.value = if (handle in _selection.value) {
            _selection.value - handle
        } else {
            _selection.value + handle
        }
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    fun downloadSelected(context: Context) {
        val session = getSession() ?: return
        if (_selection.value.isEmpty()) return
        _downloading.value = true
        viewModelScope.launch {
            try {
                val controller = GalleryController(session)
                _selection.value.forEach { handle ->
                    controller.loadImage(handle)?.let { saveToDownloads(context, "NiConn_$handle.jpg", it) }
                }
            } catch (e: Exception) {
                _error.value = "下载失败：${e.message}"
            } finally {
                _downloading.value = false
                _selection.value = emptySet()
            }
        }
    }

    fun deleteSelected() {
        val session = getSession() ?: return
        if (_selection.value.isEmpty()) return
        viewModelScope.launch {
            try {
                val controller = GalleryController(session)
                val deleted = _selection.value.filter { controller.delete(it) }.toSet()
                // 本地直接移除已删项，不做整册刷新（照片多时太慢）
                _items.value = _items.value.filter { it.handle !in deleted }
                _thumbs.value = _thumbs.value.filterKeys { it !in deleted }
                deleted.forEach {
                    handleFormats.remove(it)
                    // 同步清两级磁盘缓存，防同号句柄复用时串图
                    getThumbCache()?.remove(it)
                    getFullCache()?.remove(it)
                }
                _selection.value = emptySet()
                com.niconn.AppLog.d("GAL", "deleted=${deleted.size} remaining=${_items.value.size}")
            } catch (e: Exception) {
                _error.value = "删除失败：${e.message}"
            }
        }
    }

    private fun label(handle: Int): String? =
        handleFormats[handle]?.let { GalleryController.formatLabel(it) }

    private fun saveToDownloads(context: Context, name: String, bytes: ByteArray) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
    }
}
