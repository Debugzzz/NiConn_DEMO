package com.niconn.service

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThumbCacheTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `store then load round-trips bytes`() {
        val cache = ThumbCache(tmp.root, "cam-A")
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        cache.store(7, bytes)
        assertArrayEquals(bytes, cache.load(7))
        assertNull(cache.load(8))
    }

    @Test
    fun `cache survives new instance reading same camera dir`() {
        val first = ThumbCache(tmp.root, "cam-A")
        first.store(1, byteArrayOf(9))
        // 模拟重启后重建缓存实例（跨会话复用的关键路径）
        val second = ThumbCache(tmp.root, "cam-A")
        assertNotNull(second.load(1))
    }

    @Test
    fun `different cameras do not share cache`() {
        ThumbCache(tmp.root, "cam-A").store(1, byteArrayOf(1))
        assertNull(ThumbCache(tmp.root, "cam-B").load(1))
    }

    @Test
    fun `retain keeps cache when camera still has all cached handles`() {
        val cache = ThumbCache(tmp.root, "cam-A")
        cache.store(1, byteArrayOf(1))
        cache.store(2, byteArrayOf(2))
        cache.retain(setOf(1, 2, 3)) // 新增照片（超集）不失效
        assertNotNull(cache.load(1))
        assertNotNull(cache.load(2))
    }

    @Test
    fun `retain wipes cache when a cached handle disappears`() {
        val cache = ThumbCache(tmp.root, "cam-A")
        cache.store(1, byteArrayOf(1))
        cache.store(2, byteArrayOf(2))
        cache.retain(setOf(2)) // 句柄 1 消失（有删除，句柄可能重排）→ 整册失效
        assertNull(cache.load(1))
        assertNull(cache.load(2))
    }

    @Test
    fun `remove purges single entry`() {
        val cache = ThumbCache(tmp.root, "cam-A")
        cache.store(1, byteArrayOf(1))
        cache.remove(1)
        assertNull(cache.load(1))
    }
}
