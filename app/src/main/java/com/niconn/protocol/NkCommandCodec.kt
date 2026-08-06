package com.niconn.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class CommandRequest(
    val session: Int,
    val opcode: Int,
    val txn: Int,
    val params: List<Int>,
)

data class CommandResponse(
    val code: Int,
    val txn: Int,
    val param: Int,
    val data: ByteArray = ByteArray(0),
)

object NkCommandCodec {
    fun encodeRequest(request: CommandRequest): ByteArray {
        val buffer = ByteBuffer.allocate(10 + request.params.size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(request.session)
        buffer.putShort(request.opcode.toShort())
        buffer.putShort(request.txn.toShort())
        buffer.putShort(0) // 保留字段（抓包实测恒为 0x0000，非参数计数）
        request.params.forEach { buffer.putInt(it) }
        return buffer.array()
    }

    fun decodeRequest(payload: ByteArray): CommandRequest {
        require(payload.size >= 10) { "request payload too short: ${payload.size}" }
        require((payload.size - 10) % 4 == 0) { "request params not 4-byte aligned" }
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val session = buffer.int
        val opcode = buffer.short.toInt() and 0xFFFF
        val txn = buffer.short.toInt() and 0xFFFF
        buffer.short // 保留字段
        val count = (payload.size - 10) / 4
        val params = List(count) { buffer.int }
        return CommandRequest(session, opcode, txn, params)
    }

    fun decodeResponse(payload: ByteArray): CommandResponse {
        require(payload.size >= 6) { "response payload too short: ${payload.size}" }
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val code = buffer.short.toInt() and 0xFFFF
        val txn = buffer.short.toInt() and 0xFFFF
        val param = buffer.short.toInt() and 0xFFFF
        return CommandResponse(code, txn, param)
    }
}
