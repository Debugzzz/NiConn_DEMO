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
                _items.value = handles.map { GalleryItem(it) }
                // 触发第一张 ObjectInfo 原始字节日志，便于校准文件名/格式解析
                handles.firstOrNull()?.let { controller.objectInfo(it) }
                handles.forEach { handle ->
                    val label = try {
                        when (controller.objectFormat(handle)) {
                            0x3000, 0x3800 -> "NEF"
                            0x3801 -> "JPEG"
                            else -> null
                        }
                    } catch (e: Exception) {
                        null
                    }
                    val thumb = controller.loadThumb(handle)?.let { decodeSampled(it, 512) }
                    _items.value = _items.value.map { item ->
                        if (item.handle == handle) {
                            item.copy(thumbnail = thumb, formatLabel = label)
                        } else {
                            item
                        }
                    }
                }
            } catch (e: Exception) {
                _error.value = "读取相册失败：${e.message}"
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
                _selection.value.forEach { controller.delete(it) }
                _selection.value = emptySet()
                refresh()
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
