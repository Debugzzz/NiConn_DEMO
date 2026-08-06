package com.niconn.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class NkCommandCodecTest {
    @Test
    fun `decodes captured GetDeviceInfo request`() {
        // 抓包：01000000 0110 0000 0000（session=1 op=0x1001 txn=0 count=0）
        val payload = byteArrayOf(1, 0, 0, 0, 0x01, 0x10, 0, 0, 0, 0)
        val req = NkCommandCodec.decodeRequest(payload)
        assertEquals(1, req.session)
        assertEquals(0x1001, req.opcode)
        assertEquals(0, req.txn)
        assertEquals(emptyList<Int>(), req.params)
    }

    @Test
    fun `decodes captured OpenSession request`() {
        // 抓包：01000000 0210 0000 0000 01000000（保留字段 0000 + 参数 1）
        val payload = byteArrayOf(1, 0, 0, 0, 0x02, 0x10, 0, 0, 0, 0, 1, 0, 0, 0)
        val req = NkCommandCodec.decodeRequest(payload)
        assertEquals(0x1002, req.opcode)
        assertEquals(listOf(1), req.params)
    }

    @Test
    fun `encodes and decodes round trip`() {
        val req = CommandRequest(1, 0x9207, 5, listOf(-1, 0))
        assertEquals(req, NkCommandCodec.decodeRequest(NkCommandCodec.encodeRequest(req)))
    }

    @Test
    fun `decodes OK response`() {
        val resp = NkCommandCodec.decodeResponse(byteArrayOf(0x01, 0x20, 1, 0, 0, 0))
        assertEquals(0x2001, resp.code)
        assertEquals(1, resp.txn)
    }
}
