package com.niconn.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 版本号解析与比较（检查更新的判定依据）。 */
class UpdateCheckerTest {

    @Test
    fun `parses tags with v prefix and suffixes`() {
        assertEquals(listOf(0, 1, 2), UpdateChecker.parseVersion("v0.1.2"))
        assertEquals(listOf(0, 1, 3), UpdateChecker.parseVersion("0.1.3"))
        assertEquals(listOf(0, 2, 0), UpdateChecker.parseVersion("0.2.0-rc1"))
        assertEquals(listOf(1, 0, 0), UpdateChecker.parseVersion("V1.0.0+build7"))
    }

    @Test
    fun `detects a newer patch release`() {
        assertTrue(UpdateChecker.isNewer("v0.1.3", "0.1.2"))
        assertTrue(UpdateChecker.isNewer("0.2.0", "0.1.9"))
        assertTrue(UpdateChecker.isNewer("1.0.0", "0.9.9"))
    }

    @Test
    fun `same or older version is not an update`() {
        assertFalse(UpdateChecker.isNewer("v0.1.2", "0.1.2"))
        assertFalse(UpdateChecker.isNewer("0.1.1", "0.1.2"))
        assertFalse(UpdateChecker.isNewer("v0.1.2", "v0.1.2"))
    }

    @Test
    fun `handles differing segment counts`() {
        // 0.2 视为 0.2.0，比 0.1.9 新
        assertTrue(UpdateChecker.isNewer("0.2", "0.1.9"))
        // 0.1.2.1 比 0.1.2 新
        assertTrue(UpdateChecker.isNewer("0.1.2.1", "0.1.2"))
        // 缺位按 0 补，故 0.1 与 0.1.0 相同
        assertFalse(UpdateChecker.isNewer("0.1", "0.1.0"))
    }

    @Test
    fun `tolerates malformed input`() {
        assertFalse(UpdateChecker.isNewer("", ""))
        assertEquals(listOf(0), UpdateChecker.parseVersion(""))
        assertEquals(listOf(0, 0), UpdateChecker.parseVersion("abc.def"))
    }

    // ---------------- 网页端点解析 ----------------

    @Test
    fun `extracts tag from releases-latest redirect url`() {
        assertEquals(
            "v0.1.2",
            UpdateChecker.parseTagFromRedirect(
                "https://github.com/Debugzzz/NiConn_DEMO/releases/tag/v0.1.2",
            ),
        )
        // 无发布记录时会重定向到发布列表页，此时取不到标签
        assertEquals(
            null,
            UpdateChecker.parseTagFromRedirect("https://github.com/Debugzzz/NiConn_DEMO/releases"),
        )
    }

    /** 真实 tags.atom 结构（取自线上，已裁剪）。 */
    private val atomFeed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom" xml:lang="en-US">
          <id>tag:github.com,2008:https://github.com/Debugzzz/NiConn_DEMO/releases</id>
          <title>Tags from NiConn_DEMO</title>
          <updated>2026-10-14T18:04:05Z</updated>
          <entry>
            <id>tag:github.com,2008:Repository/1325460914/v0.1.2</id>
            <updated>2026-10-14T18:06:58Z</updated>
            <link rel="alternate" type="text/html" href="https://github.com/Debugzzz/NiConn_DEMO/releases/tag/v0.1.2"/>
            <title>v0.1.2</title>
            <content type="html">&lt;p&gt;v0.1.2 — 照片水印&lt;/p&gt;</content>
          </entry>
          <entry>
            <title>v0.1.1</title>
          </entry>
        </feed>
    """.trimIndent()

    @Test
    fun `takes the first entry title as the latest tag`() {
        assertEquals("v0.1.2", UpdateChecker.parseAtomLatestTag(atomFeed))
    }

    @Test
    fun `atom parsing returns null when there is no entry`() {
        val empty = "<feed><title>Tags from NiConn_DEMO</title></feed>"
        assertEquals(null, UpdateChecker.parseAtomLatestTag(empty))
        assertEquals(null, UpdateChecker.parseAtomLatestTag(""))
    }
}
