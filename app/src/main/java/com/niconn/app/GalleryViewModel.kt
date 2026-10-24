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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryViewModel(
    private val getSession: () -> PtpIpSession?,
    private val getThumbCache: () -> ThumbCache? = { null },
) : ViewModel() {
    data class GalleryItem(
        val handle: Int,
        val thumbnail: Bitmap? = null,
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

    /** 会话内缓存：句柄 → 缩略图/格式。滚动/刷新复用，不重复回源。 */
    private val cache = LinkedHashMap<Int, GalleryItem>()

    /** 会话内格式表（refresh 时 3 次过滤查询得到，替代逐张 GetObjectInfo）。 */
    private val handleFormats = HashMap<Int, Int>()

    /** 格式表所属会话：换会话（重连/换相机）时清空，避免句柄重排后串角标。 */
    private var formatsSession: PtpIpSession? = null

    /** 进行中的按需加载（去重：同一句柄不并发回源）。 */
    private val inFlight = java.util.concurrent.ConcurrentHashMap<Int, Unit>()

    /** 加载失败黑名单：避免每个可见项反复重试打爆会话；手动刷新时清空重试。 */
    private val failed = MutableStateFlow<Set<Int>>(emptySet())

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
                _items.value = handles.map { cache[it] ?: GalleryItem(it, formatLabel = label(it)) }
                // 格式角标：3 次按格式过滤的批量查询，不再逐张 GetObjectInfo
                val formats = controller.formatByHandle()
                handleFormats.putAll(formats)
                _items.value = handles.map { cache[it] ?: GalleryItem(it, formatLabel = label(it)) }
                com.niconn.AppLog.d("GAL", "refresh total=${handles.size} cached=${cache.size} formats=${formats.size}")
            } catch (e: Exception) {
                _error.value = "读取相册失败：${e.message}"
            }
        }
    }

    /**
     * 按需加载单个条目缩略图（网格单元可见时调用）：
     * 内存 → 磁盘缓存 → GetThumb 的缓存优先顺序。
     */
    fun ensureLoaded(handle: Int) {
        if (cache.containsKey(handle)) return
        if (handle in failed.value) return
        if (inFlight.putIfAbsent(handle, Unit) != null) return
        val session = getSession()
        if (session == null) {
            inFlight.remove(handle)
            return
        }
        viewModelScope.launch {
            try {
                val item = withContext(Dispatchers.Default) {
                    val cacheStore = getThumbCache()
                    // 缓存未命中才回源相机；磁盘文件解码失败视为未命中
                    val cached = cacheStore?.load(handle)
                    var bitmap = cached?.let { decodeSampled(it, 320) }
                    if (bitmap == null) {
                        val bytes = GalleryController(session).loadThumb(handle)
                        bitmap = bytes?.let { decodeSampled(it, 320) }
                        if (bytes != null) {
                            // 会话换代后（重连）不落盘，防止旧连接数据污染新相机缓存
                            if (getSession() === session) cacheStore?.store(handle, bytes)
                        }
                    }
                    GalleryItem(handle, bitmap, label(handle))
                }
                cache[handle] = item
                _items.value = _items.value.map { if (it.handle == handle) item else it }
            } catch (e: Exception) {
                failed.value = failed.value + handle
                com.niconn.AppLog.d("GAL", "load failed handle=$handle: ${e.message}")
            } finally {
                inFlight.remove(handle)
            }
        }
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
                deleted.forEach {
                    cache.remove(it)
                    handleFormats.remove(it)
                    // 同步清磁盘缓存，防同号句柄复用时串图
                    getThumbCache()?.remove(it)
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
