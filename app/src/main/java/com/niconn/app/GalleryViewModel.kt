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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GalleryViewModel(
    private val getSession: () -> PtpIpSession?,
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
                // 网格立即可见：只取句柄列表，缩略图由可见单元按需加载（ensureLoaded）
                _items.value = handles.map { cache[it] ?: GalleryItem(it) }
                com.niconn.AppLog.d("GAL", "refresh total=${handles.size} cached=${cache.size}")
            } catch (e: Exception) {
                _error.value = "读取相册失败：${e.message}"
            }
        }
    }

    /** 按需加载单个条目（ObjectInfo + 缩略图）：网格单元可见时调用。 */
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
                val item = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val controller = GalleryController(session)
                    val info = controller.objectInfo(handle)
                    val label = when (info?.format) {
                        0x3000, 0x3800 -> "NEF"
                        0x3801 -> "JPEG"
                        else -> null
                    }
                    val thumb = controller.loadThumb(handle)?.let { decodeSampled(it, 320) }
                    GalleryItem(handle, thumb, label)
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
                deleted.forEach { cache.remove(it) }
                _selection.value = emptySet()
                com.niconn.AppLog.d("GAL", "deleted=${deleted.size} remaining=${_items.value.size}")
            } catch (e: Exception) {
                _error.value = "删除失败：${e.message}"
            }
        }
    }

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
