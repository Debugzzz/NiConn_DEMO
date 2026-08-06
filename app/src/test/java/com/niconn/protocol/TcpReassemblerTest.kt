package com.niconn.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class TcpReassemblerTest {
    @Test
    fun `reassembles split frame`() {
        val re = TcpReassembler()
        val full = PtpIpCodec.encodeFrame(6, byteArrayOf(1, 2, 3, 4))
        re.push(1000, full.copyOfRange(0, 6))
        re.push(1006, full.copyOfRange(6, full.size))
        val frames = re.takeFrames()
        assertEquals(1, frames.size)
        assertEquals(6, frames[0].packetType)
    }

    @Test
    fun `handles coalesced frames`() {
        val re = TcpReassembler()
        val a = PtpIpCodec.encodeFrame(1, byteArrayOf())
        val b = PtpIpCodec.encodeFrame(4, byteArrayOf())
        re.push(2000, a + b)
        assertEquals(listOf(1, 4), re.takeFrames().map { it.packetType })
    }

    @Test
    fun `ignores duplicate overlap`() {
        val re = TcpReassembler()
        val full = PtpIpCodec.encodeFrame(7, byteArrayOf(9, 9, 9, 9))
        re.push(3000, full)
        re.push(3002, full.copyOfRange(2, full.size))
        assertEquals(1, re.takeFrames().size)
    }
}
