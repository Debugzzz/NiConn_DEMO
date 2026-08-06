package com.niconn.protocol

import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class CameraHandshake(val sessionId: Int, val clientName: String)

class MockCameraServer(private val listenPort: Int = 0) : AutoCloseable {
    private val server = ServerSocket(listenPort)
    private val executor = Executors.newCachedThreadPool()
    private val handshakes = java.util.concurrent.ConcurrentLinkedQueue<CameraHandshake>()

    val port: Int
        get() = server.localPort

    init {
        executor.submit {
            while (!server.isClosed) {
                try {
                    val socket = server.accept()
                    executor.submit { handle(socket) }
                } catch (_: Exception) {
                    break
                }
            }
        }
    }

    fun awaitConnection(timeoutMs: Long): CameraHandshake {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            handshakes.poll()?.let { return it }
            Thread.sleep(20)
        }
        error("no connection within ${timeoutMs}ms")
    }

    private fun handle(socket: Socket) {
        socket.use { s ->
            val input = s.getInputStream()
            var clientName = ""
            while (true) {
                val frame = readFrame(input) ?: return
                when (frame.packetType) {
                    1 -> {
                        clientName = decodeClientName(frame.payload)
                        handshakes += CameraHandshake(
                            sessionId = 1,
                            clientName = clientName,
                        )
                        writeFrame(
                            s,
                            PtpIpCodec.encodeFrame(
                                2,
                                byteArrayOf(1, 0, 0, 0) +
                                    "04b004550000100180013cbee158f492".hexToByteArray() +
                                    "Z50_2_8095684".toByteArray(Charsets.UTF_16LE) +
                                    byteArrayOf(0, 0, 0, 0, 1, 0),
                            ),
                        )
                    }
                    6 -> {
                        val request = NkCommandCodec.decodeRequest(frame.payload)
                        if (request.opcode == NkOps.GetDeviceInfo) {
                            writeFrame(s, PtpIpCodec.encodeFrame(9, byteArrayOf(1, 0, 0, 0, 0x01, 0, 0, 0, 0, 0, 0, 0)))
                            writeFrame(s, PtpIpCodec.encodeFrame(12, byteArrayOf(1, 0, 0, 0, 0x01, 0, 0, 0) + byteArrayOf(0x6e, 0, 0x22, 0)))
                        } else if (request.opcode == NkOps.VENDOR_952B) {
                            writeFrame(s, PtpIpCodec.encodeFrame(9, byteArrayOf(1, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0, 0)))
                            writeFrame(s, PtpIpCodec.encodeFrame(12, byteArrayOf(1, 0, 0, 0) + "0400000005000707".hexToByteArray()))
                        } else if (request.opcode == NkOps.GetLiveViewImageEx) {
                            writeFrame(s, PtpIpCodec.encodeFrame(9, byteArrayOf(1, 0, 0, 0, MOCK_JPEG.size.toByte(), 0, 0, 0, 0, 0, 0, 0)))
                            writeFrame(s, PtpIpCodec.encodeFrame(10, byteArrayOf(1, 0, 0, 0) + MOCK_JPEG.copyOfRange(0, 4)))
                            writeFrame(s, PtpIpCodec.encodeFrame(12, byteArrayOf(1, 0, 0, 0) + MOCK_JPEG.copyOfRange(4, MOCK_JPEG.size)))
                        } else if (request.opcode == NkOps.GetDevicePropValue) {
                            val code = request.params.firstOrNull() ?: 0
                            val value = when (code) {
                                0x500F -> byteArrayOf(0x64, 0x00)
                                0x500D -> byteArrayOf(0x10, 0x27, 0, 0)
                                0x5007 -> byteArrayOf(0x20, 0x03)
                                else -> byteArrayOf(0, 0)
                            }
                            writeFrame(s, PtpIpCodec.encodeFrame(9, byteArrayOf(1, 0, 0, 0, value.size.toByte(), 0, 0, 0, 0, 0, 0, 0)))
                            writeFrame(s, PtpIpCodec.encodeFrame(12, byteArrayOf(1, 0, 0, 0) + value))
                        } else if (request.opcode == NkOps.GetDevicePropDesc) {
                            val code = request.params.firstOrNull() ?: 0
                            val values = when (code) {
                                0x500F -> listOf(100L, 200L, 400L)
                                0x500D -> listOf(156L, 313L, 625L)
                                0x5007 -> listOf(280L, 400L, 560L)
                                else -> emptyList()
                            }
                            val dts = if (code == 0x500D) 4 else 2
                            val type = if (code == 0x500D) 0x0006 else 0x0004
                            val payload = java.nio.ByteBuffer
                                .allocate(2 + 2 + 1 + dts + dts + 1 + 2 + values.size * dts)
                                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                            payload.putShort(code.toShort())
                            payload.putShort(type.toShort())
                            payload.put(1)
                            values.firstOrNull()?.let { putValue(payload, dts, it) } ?: ByteArray(dts)
                            values.firstOrNull()?.let { putValue(payload, dts, it) } ?: ByteArray(dts)
                            payload.put(2)
                            payload.putShort(values.size.toShort())
                            values.forEach { putValue(payload, dts, it) }
                            val bytes = payload.array()
                            writeFrame(s, PtpIpCodec.encodeFrame(9, byteArrayOf(1, 0, 0, 0, bytes.size.toByte(), 0, 0, 0, 0, 0, 0, 0)))
                            writeFrame(s, PtpIpCodec.encodeFrame(12, byteArrayOf(1, 0, 0, 0) + bytes))
                        } else if (request.opcode == NkOps.GetObjectHandles) {
                            val handles = byteArrayOf(3, 0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 3, 0, 0, 0)
                            writeFrame(s, PtpIpCodec.encodeFrame(9, byteArrayOf(1, 0, 0, 0, handles.size.toByte(), 0, 0, 0, 0, 0, 0, 0)))
                            writeFrame(s, PtpIpCodec.encodeFrame(12, byteArrayOf(1, 0, 0, 0) + handles))
                        } else if (request.opcode == NkOps.GetObjectInfo) {
                            // 大端：StorageID + ObjectFormat=0x3801 + Protection + Size + 图像尺寸 + 文件名
                            val info = byteArrayOf(
                                0, 0, 0, 1, // storage
                                0x01, 0x38, // JPEG (LE)
                                0, 0, // protection
                                0x10, 0, 0, 0, // size 16 (LE)
                                0, 0, 0, 0, 0, 0, 0, 0, // thumb
                                0x80.toByte(), 2, 0, 0, // w 640 (LE)
                                0xA8.toByte(), 1, 0, 0, // h 424 (LE)
                                0, 0, 0, 0x18, // bit depth
                                0, 0, 0, 2, // parent
                                0, 0, 0, 0, 0, 0, 0, 0, // assoc type + desc + seq
                            ) + "DSC_0001.JPG".toByteArray(Charsets.UTF_16LE) +
                                byteArrayOf(0, 0) + "2026:08:06 12:00:00".toByteArray(Charsets.UTF_16LE) +
                                byteArrayOf(0, 0)
                            writeFrame(s, PtpIpCodec.encodeFrame(9, byteArrayOf(1, 0, 0, 0, info.size.toByte(), 0, 0, 0, 0, 0, 0, 0)))
                            writeFrame(s, PtpIpCodec.encodeFrame(12, byteArrayOf(1, 0, 0, 0) + info))
                        } else if (request.opcode == NkOps.GetThumb || request.opcode == NkOps.GetObject) {
                            writeFrame(s, PtpIpCodec.encodeFrame(9, byteArrayOf(1, 0, 0, 0, MOCK_JPEG.size.toByte(), 0, 0, 0, 0, 0, 0, 0)))
                            writeFrame(s, PtpIpCodec.encodeFrame(12, byteArrayOf(1, 0, 0, 0) + MOCK_JPEG))
                        }
                        val response = encodeResponse(request)
                        writeFrame(s, PtpIpCodec.encodeFrame(7, response))
                    }
                    3 -> writeFrame(s, PtpIpCodec.encodeFrame(4, byteArrayOf()))
                    13 -> writeFrame(s, PtpIpCodec.encodeFrame(14, byteArrayOf()))
                    else -> Unit
                }
            }
        }
    }

    private fun decodeClientName(payload: ByteArray): String {
        // 结构：uuid(16) + name(UTF-16) + null(2) + bufferSize(4)
        val nameBytes = payload.copyOfRange(16, payload.size - 6)
        val charset = if (nameBytes.isNotEmpty() && nameBytes[0] == 0.toByte()) {
            Charsets.UTF_16BE
        } else {
            Charsets.UTF_16LE
        }
        return String(nameBytes, charset)
    }

    private fun readFrame(input: InputStream): PtpIpFrame? {
        val header = ByteArray(PtpIpCodec.HEADER_SIZE)
        var read = 0
        while (read < header.size) {
            val n = input.read(header, read, header.size - read)
            if (n < 0) return null
            if (n == 0) continue
            read += n
        }
        val length = littleEndianInt(header, 0)
        val packetType = littleEndianInt(header, 4)
        val body = ByteArray(length - PtpIpCodec.HEADER_SIZE)
        var bodyRead = 0
        while (bodyRead < body.size) {
            val n = input.read(body, bodyRead, body.size - bodyRead)
            if (n < 0) return null
            if (n == 0) continue
            bodyRead += n
        }
        return PtpIpFrame(packetType, length, body)
    }

    private fun writeFrame(socket: Socket, frame: ByteArray) {
        socket.getOutputStream().write(frame)
        socket.getOutputStream().flush()
    }

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    override fun close() {
        server.close()
        executor.shutdownNow()
        executor.awaitTermination(2, TimeUnit.SECONDS)
    }

    companion object {
        val MOCK_JPEG = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            0xFF.toByte(), 0xD9.toByte(),
        )

        private fun putValue(buffer: java.nio.ByteBuffer, dts: Int, value: Long) {
            if (dts == 2) buffer.putShort(value.toShort()) else buffer.putInt(value.toInt())
        }
    }
}

private fun encodeResponse(request: CommandRequest): ByteArray {
    val buffer = java.nio.ByteBuffer.allocate(6).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    buffer.putShort(NkOps.OK.toShort())
    buffer.putShort(request.txn.toShort())
    buffer.putShort(0)
    return buffer.array()
}

private fun String.hexToByteArray(): ByteArray =
    chunked(2).map { it.toInt(16).toByte() }.toByteArray()
