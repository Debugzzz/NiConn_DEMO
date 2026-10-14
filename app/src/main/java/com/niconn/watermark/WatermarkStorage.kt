package com.niconn.watermark

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.DocumentsContract
import com.niconn.AppLog
import java.io.File

/**
 * 水印成品的存放位置与「打开保存文件夹」入口。
 *
 * 成品保存在 App 专属外部目录 `Android/data/<包名>/files/Watermark`，无需任何存储权限，
 * 卸载 App 时随之一并清除。
 *
 * 打开方式：向系统「文件」（DocumentsUI）发送该目录的 document URI。实测小米 15 /
 * Android 16 可直接定位；部分机型会限制浏览 `Android/data`，此时回退到通用选择器，
 * 仍无法拉起则由调用方提示目录路径。
 */
object WatermarkStorage {
    /** 成品所在子目录名。 */
    const val FOLDER_NAME = "Watermark"

    /** 外部存储文档提供者 authority（DocumentsUI 使用）。 */
    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    /**
     * 成品目录（不做创建，用于取路径）。
     *
     * 外部存储不可用（未挂载/已移除）时退回 App 内部目录，避免 `getExternalFilesDir`
     * 返回 null 导致写出相对路径。
     */
    fun folder(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, FOLDER_NAME)

    /** 成品目录，不存在时创建。 */
    fun ensureFolder(context: Context): File = folder(context).apply { if (!exists()) mkdirs() }

    /** 成品目录相对外部存储根的路径；不在其下时返回绝对路径。 */
    fun relativeDir(context: Context): String =
        relativeTo(Environment.getExternalStorageDirectory().absolutePath, folder(context).absolutePath)

    /** 成品目录的展示用路径，例如 `Android/data/com.niconn.app/files/Watermark`。 */
    fun displayPath(context: Context): String = relativeDir(context)

    /** DocumentsUI 的 document id，例如 `primary:Android/data/com.niconn.app/files/Watermark`。 */
    fun documentId(context: Context): String = "primary:${relativeDir(context)}"

    /**
     * 打开系统「文件」应用并定位到成品目录。
     *
     * @return true 表示已成功拉起界面；false 表示设备上没有可处理的界面（调用方应提示路径）。
     */
    fun openFolder(context: Context): Boolean {
        ensureFolder(context) // 目录不存在时 DocumentsUI 无法定位
        val uri = DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, documentId(context))
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // 调用方可能是非 Activity Context，带上该 flag 以免抛 AndroidRuntimeException
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // 该 intent 常被文件管理器/办公应用/浏览器同时声明，直接拉起会弹选择器；
        // 优先指定系统文档界面，命中不了再退回选择器。
        // 注意：能查到候选应用依赖 AndroidManifest 的 <queries> 声明（Android 11+）。
        val docsUi = context.packageManager.queryIntentActivities(view, 0)
            .firstOrNull { it.activityInfo.packageName.contains("documentsui", ignoreCase = true) }
        if (docsUi != null) {
            val direct = Intent(view).setClassName(
                docsUi.activityInfo.packageName,
                docsUi.activityInfo.name,
            )
            val failure = runCatching { context.startActivity(direct) }.exceptionOrNull()
            if (failure == null) return true
            AppLog.d(TAG, "直接拉起失败，回退: ${failure.message}")
        } else {
            AppLog.d(TAG, "未解析到 documentsui 候选（检查 <queries> 声明）")
        }
        // 回退 1：隐式启动，交由系统解析（不弹选择器，由系统决定最合适的应用）
        runCatching { context.startActivity(view) }
            .onSuccess { return true }
            .onFailure { AppLog.d(TAG, "隐式启动失败: ${it.message}") }
        // 回退 2：显式选择器，让用户自己挑应用
        return runCatching {
            context.startActivity(Intent.createChooser(view, "打开保存文件夹"))
            true
        }.onFailure {
            AppLog.d(TAG, "选择器也无法拉起: ${it.message}")
        }.getOrDefault(false)
    }

    private const val TAG = "WM_FOLDER"
}

/**
 * 取 [path] 相对于 [rootPath] 的路径；不在其下时原样返回 [path]。
 *
 * 形如 `/storage/emulated/0` + `/storage/emulated/0/Android/data/<包名>/files/Watermark`
 * → `Android/data/<包名>/files/Watermark`；根路径结尾的 `/` 不影响结果。
 */
internal fun relativeTo(rootPath: String, path: String): String {
    val prefix = rootPath.trimEnd('/') + "/"
    return if (path.startsWith(prefix)) path.removePrefix(prefix) else path
}
