package com.niconn.service

import java.io.File

/**
 * 缩略图磁盘缓存：以「相机 + 句柄」为键持久化到 cacheDir，
 * 跨会话/重启复用，避免每次进相册都回源相机。
 *
 * 失效策略：refresh 时拿最新句柄集做校验，一旦缓存里出现相机上已不存在的句柄
 * （卡内有删除，后续句柄可能重排），整册清空重来，宁可慢一次也不串图。
 */
class ThumbCache(
    baseDir: File,
    cameraKey: String,
    rootName: String = ROOT_DIR,
) {
    private val dir = File(File(baseDir, rootName), sanitize(cameraKey))
    private val indexFile = File(dir, INDEX_FILE)
    private val lock = Any()

    /** 读取缓存的缩略图 JPEG；未缓存返回 null。 */
    fun load(handle: Int): ByteArray? = synchronized(lock) {
        runCatching { File(dir, "$handle.jpg").takeIf { it.isFile }?.readBytes() }.getOrNull()
    }

    /** 写入缩略图并登记索引。 */
    fun store(handle: Int, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        synchronized(lock) {
            runCatching {
                dir.mkdirs()
                File(dir, "$handle.jpg").writeBytes(bytes)
                writeIndex(readIndex() + handle)
            }
        }
    }

    /** 删除单张缓存（照片被删除后清掉，防同号句柄复用串图）。 */
    fun remove(handle: Int) {
        synchronized(lock) {
            runCatching {
                File(dir, "$handle.jpg").delete()
                writeIndex(readIndex() - handle)
            }
        }
    }

    /**
     * 用最新句柄集校验缓存：缓存句柄必须是相机句柄的子集，否则整册清空。
     * 返回后缓存中只保留相机上仍存在的句柄。
     */
    fun retain(handles: Set<Int>) {
        synchronized(lock) {
            val indexed = readIndex()
            if (indexed.isEmpty()) return
            if (indexed.any { it !in handles }) {
                runCatching { dir.deleteRecursively() }
                return
            }
        }
    }

    private fun readIndex(): Set<Int> =
        if (!indexFile.isFile) emptySet()
        else runCatching {
            indexFile.readLines().mapNotNull { it.trim().toIntOrNull() }.toSet()
        }.getOrDefault(emptySet())

    private fun writeIndex(handles: Set<Int>) {
        if (handles.isEmpty()) {
            indexFile.delete()
        } else {
            indexFile.writeText(handles.joinToString("\n"))
        }
    }

    private fun sanitize(key: String): String =
        key.map { if (it.isLetterOrDigit() || it == '_' || it == '-') it else '_' }
            .joinToString("")
            .take(64)
            .ifEmpty { "default" }

    private companion object {
        const val ROOT_DIR = "camera-thumbs"
        const val INDEX_FILE = "index"
    }
}
