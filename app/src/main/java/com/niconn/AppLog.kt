package com.niconn

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/** 内存环形日志：协议与会话关键事件，供设置页导出、排查问题。 */
object AppLog {
    internal const val MAX_ENTRIES = 2000
    private val buffer = ArrayDeque<String>(MAX_ENTRIES)
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun d(tag: String, message: String) {
        synchronized(buffer) {
            if (buffer.size >= MAX_ENTRIES) buffer.removeFirst()
            buffer.addLast("${timeFormat.format(Date())} [$tag] $message")
        }
    }

    /** 导出全部日志（换行分隔）；缓冲为空时返回占位提示。 */
    fun export(): String = synchronized(buffer) {
        if (buffer.isEmpty()) "(暂无日志)" else buffer.joinToString("\n")
    }

    fun clear() = synchronized(buffer) { buffer.clear() }
}
