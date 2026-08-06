package com.niconn.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Socket

class MockCameraServerTest {
    @Test
    fun `answers init request with camera identity`() {
        MockCameraServer().use { server ->
            Socket("127.0.0.1", server.port).use { socket ->
                val initPayload = byteArrayOf(
                    0x11, 0x22, 0x33, 0x44, 0x61, 0x1e,
                ) + byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10) +
                    "NiConn-Test".toByteArray(Charsets.UTF_16LE) +
                    byteArrayOf(0, 0, 0, 0, 1, 0)
                socket.getOutputStream().write(PtpIpCodec.encodeFrame(1, initPayload))
                socket.getOutputStream().flush()

                val ack = readFrame(socket)
                assertEquals(2, ack.packetType)
                val text = String(ack.payload, Charsets.UTF_16LE)
                assertTrue(text.contains("Z50_2_8095684"))
            }
            val handshake = server.awaitConnection(5_000)
            assertEquals("NiConn-Test", handshake.clientName)
        }
    }

    @Test
    fun `answers command with OK`() {
        MockCameraServer().use { server ->
            Socket("127.0.0.1", server.port).use { socket ->
                val initPayload = byteArrayOf(1, 0, 0, 0, 0x61, 0x1e) +
                    byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10) +
                    "NiConn-Test".toByteArray(Charsets.UTF_16LE) +
                    byteArrayOf(0, 0, 0, 0, 1, 0)
                socket.getOutputStream().write(PtpIpCodec.encodeFrame(1, initPayload))
                socket.getOutputStream().flush()
                readFrame(socket) // type2

                val req = NkCommandCodec.encodeRequest(CommandRequest(1, 0x1002, 0, listOf(2)))
                socket.getOutputStream().write(PtpIpCodec.encodeFrame(6, req))
                socket.getOutputStream().flush()

                val response = readFrame(socket)
                assertEquals(7, response.packetType)
                val decoded = NkCommandCodec.decodeResponse(response.payload)
                assertEquals(NkOps.OK, decoded.code)
            }
            server.awaitConnection(5_000)
        }
    }

    private fun readFrame(socket: Socket): PtpIpFrame {
        val input = socket.getInputStream()
        val header = ByteArray(PtpIpCodec.HEADER_SIZE)
        var read = 0
        while (read < header.size) {
            val n = input.read(header, read, header.size - read)
            require(n > 0) { "connection closed before frame header" }
            read += n
        }
        val length = (header[0].toInt() and 0xFF) or
            ((header[1].toInt() and 0xFF) shl 8) or
            ((header[2].toInt() and 0xFF) shl 16) or
            ((header[3].toInt() and 0xFF) shl 24)
        val body = ByteArray(length - PtpIpCodec.HEADER_SIZE)
        var bodyRead = 0
        while (bodyRead < body.size) {
            val n = input.read(body, bodyRead, body.size - bodyRead)
            require(n > 0) { "connection closed before frame body" }
            bodyRead += n
        }
        return PtpIpFrame(
            packetType = (header[4].toInt() and 0xFF) or
                ((header[5].toInt() and 0xFF) shl 8) or
                ((header[6].toInt() and 0xFF) shl 16) or
                ((header[7].toInt() and 0xFF) shl 24),
            length = length,
            payload = body,
        )
    }
}
