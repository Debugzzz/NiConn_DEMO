package com.niconn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLogTest {
    @Test
    fun `exports entries in order with tag`() {
        AppLog.clear()
        AppLog.d("T", "first")
        AppLog.d("T", "second")
        val out = AppLog.export()
        assertTrue(out.contains("[T] first"))
        assertTrue(out.indexOf("first") < out.indexOf("second"))
    }

    @Test
    fun `ring buffer drops oldest entries beyond capacity`() {
        AppLog.clear()
        repeat(AppLog.MAX_ENTRIES + 500) { AppLog.d("T", "entry-$it") }
        val lines = AppLog.export().lines()
        assertEquals(AppLog.MAX_ENTRIES, lines.size)
        assertFalse(lines.any { it.contains("entry-0") })
        assertTrue(lines.last().contains("entry-${AppLog.MAX_ENTRIES + 499}"))
    }

    @Test
    fun `clear empties the buffer`() {
        AppLog.d("T", "x")
        AppLog.clear()
        assertEquals(1, AppLog.export().lines().size)
        assertEquals("(暂无日志)", AppLog.export())
    }
}
