package com.niconn.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class PtpIpFrame(val packetType: Int, val length: Int, val payload: ByteArray)

object PtpIpCodec {
    const val HEADER_SIZE = 8

    fun parseFrame(data: ByteArray, offset: Int): PtpIpFrame? {
        if (data.size - offset < HEADER_SIZE) return null
        val length = ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
        if (length < HEADER_SIZE || data.size - offset < length) return null
        val packetType = ByteBuffer.wrap(data, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        return PtpIpFrame(
            packetType,
            length,
            data.copyOfRange(offset + HEADER_SIZE, offset + length),
        )
    }

    fun encodeFrame(packetType: Int, payload: ByteArray): ByteArray {
        val length = HEADER_SIZE + payload.size
        return ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(length)
            .putInt(packetType)
            .put(payload)
            .array()
    }
}
