package com.niconn.watermark

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [relativeTo] 的纯逻辑测试；`openFolder` 依赖真实 Context/DocumentsUI，不在 JVM 单测范围。
 */
class WatermarkStorageTest {
    private val root = "/storage/emulated/0"
    private val folder = "/storage/emulated/0/Android/data/com.niconn.app/files/Watermark"

    @Test
    fun `strips external storage root prefix`() {
        assertEquals("Android/data/com.niconn.app/files/Watermark", relativeTo(root, folder))
    }

    @Test
    fun `tolerates trailing slash on root`() {
        assertEquals("Android/data/com.niconn.app/files/Watermark", relativeTo("$root/", folder))
    }

    @Test
    fun `returns absolute path when not under root`() {
        val other = "/storage/1234-5678/Android/data/com.niconn.app/files/Watermark"
        assertEquals(other, relativeTo(root, other))
    }

    @Test
    fun `does not strip a sibling directory sharing the root name prefix`() {
        // /storage/emulated/01 不应被 /storage/emulated/0 的规则误截
        val sibling = "/storage/emulated/01/Watermark"
        assertEquals(sibling, relativeTo(root, sibling))
    }

    @Test
    fun `document id uses primary volume prefix`() {
        assertEquals(
            "primary:Android/data/com.niconn.app/files/Watermark",
            "primary:${relativeTo(root, folder)}",
        )
    }
}
