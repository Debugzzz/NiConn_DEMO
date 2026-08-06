package com.niconn.protocol

import java.io.ByteArrayOutputStream

class TcpReassembler {
    private data class Segment(val seq: Int, val payload: ByteArray)

    private val segments = mutableListOf<Segment>()

    fun push(seq: Int, payload: ByteArray) {
        segments += Segment(seq, payload)
    }

    fun takeFrames(): List<PtpIpFrame> {
        val stream = merge()
        segments.clear()
        val frames = mutableListOf<PtpIpFrame>()
        var offset = 0
        while (offset + PtpIpCodec.HEADER_SIZE <= stream.size) {
            val frame = PtpIpCodec.parseFrame(stream, offset) ?: break
            frames += frame
            offset += frame.length
        }
        if (offset < stream.size) {
            segments += Segment(0, stream.copyOfRange(offset, stream.size))
        }
        return frames
    }

    private fun merge(): ByteArray {
        val sorted = segments.sortedBy { it.seq }
        val out = ByteArrayOutputStream()
        var expectedEnd: Int? = null
        for (segment in sorted) {
            val start = segment.seq
            val end = start + segment.payload.size
            if (expectedEnd == null || start >= expectedEnd) {
                out.write(segment.payload)
                expectedEnd = end
            } else if (end > expectedEnd) {
                out.write(segment.payload, expectedEnd - start, end - expectedEnd)
                expectedEnd = end
            }
        }
        return out.toByteArray()
    }
}
