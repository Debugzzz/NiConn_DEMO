package com.niconn.protocol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.concurrent.ThreadLocalRandom

data class SessionInfo(
    val guid: String,
    val modelSerial: String,
    val sessionId: Int,
    val pairingCode: String? = null,
)

/**
 * 相机侧连接模式，决定 Init 载荷里的 code 与客户端名字节序。
 *
 * - SMART_DEVICE：相机菜单「连接至智能设备」（SnapBridge 使用，code 0x1E61，
 *   名字为 UTF-16BE）；支持控制+监看+机内文件访问。
 * - COMPUTER：相机菜单「连接到计算机」（WTU 使用，code 0xCC0D，名字为 UTF-16LE）；
 *   支持控制+监看。
 */
enum class ConnectionMode(val code: Int, val charset: Charset, val displayName: String) {
    SMART_DEVICE(0x1E61, Charsets.UTF_16BE, "连接至智能设备"),
    COMPUTER(0xCC0D, Charsets.UTF_16LE, "连接到计算机"),
}

open class PtpIpSession(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 10_000,
) {
    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var eventSocket: Socket? = null
    private var eventInput: InputStream? = null
    private var eventOutput: OutputStream? = null
    private val commandMutex = Mutex()
    private var activeSessionId = 0
    private var txn = 0

    /** 当前命令会话号（由连接成功后的 ACK 解析得到，通常为 1）。 */
    val sessionId: Int get() = activeSessionId

    /** 连接成功后由调用方（CameraService / 测试）绑定会话号。 */
    fun attachSession(sessionId: Int) {
        activeSessionId = sessionId
    }

    open suspend fun connect(
        host: String,
        port: Int,
        uuid: ByteArray,
        clientName: String,
        mode: ConnectionMode = ConnectionMode.SMART_DEVICE,
        bufferSize: Int = 65536,
        onPairingCode: suspend (String) -> Boolean = { true },
        pairingPhase: Boolean = true,
    ): SessionInfo =
        withContext(Dispatchers.IO) {
            require(uuid.size == 16) { "client uuid must be 16 bytes" }
            val sock = Socket()
            sock.connect(InetSocketAddress(host, port), connectTimeoutMs)
            sock.soTimeout = readTimeoutMs
            socket = sock
            input = sock.getInputStream()
            output = sock.getOutputStream()

            writeFrame(1, buildInitPayload(uuid, mode, clientName, bufferSize))

            val ack = readFrame() ?: error("no init ack")
            require(ack.packetType == 2) {
                val hint = if (ack.packetType == 5) {
                    "相机拒绝连接（未配对或相机未处于配对界面）：模式=${mode.displayName}，" +
                        "uuid=${uuid.toHex()}；请在相机菜单停留在配对/连接界面后重试"
                } else {
                    "预期 type2 ACK"
                }
                "$hint，实际 type=${ack.packetType} " +
                    "payload=${ack.payload.joinToString("") { "%02x".format(it) }}"
            }
            val info = parseAck(ack.payload)

            // 命令会话号以 ACK 返回的 session 为准（实测恒为 1），不是 Init 里的 UUID 片段。
            val sessionId = info.sessionId
            // SnapBridge 实测顺序：Init ACK 后先建事件通道（type3→type4），再发命令。
            runStep("事件通道") { openEventChannel(host, port, sessionId) }
            runStep("GetDeviceInfo") {
                sendAndReadResponse(CommandRequest(sessionId, NkOps.GetDeviceInfo, nextTxn(), emptyList()))
            }
            if (!pairingPhase) {
                // 已配对后的重连：Init→事件通道→GetDeviceInfo→OpenSession，跳过配对码。
                runStep("OpenSession") {
                    sendAndReadResponse(CommandRequest(sessionId, NkOps.OpenSession, nextTxn(), listOf(2)))
                }
                return@withContext info.copy(pairingCode = null)
            }
            runStep("OpenSession") {
                sendAndReadResponse(CommandRequest(sessionId, NkOps.OpenSession, nextTxn(), listOf(2)))
            }
            val pairing = runStep("获取配对码") {
                sendAndReadResponse(CommandRequest(sessionId, NkOps.VENDOR_952B, nextTxn(), emptyList()))
            }
            val code = parsePairingCode(pairing.data)
            if (code != null && onPairingCode(code)) {
                runStep("完成配对") {
                    sendAndReadResponse(CommandRequest(sessionId, NkOps.VENDOR_935A, nextTxn(), listOf(0x2001)))
                }
                // 相机在配对完成后会自行结束会话（实测 CloseSession 阶段连接已关闭），
                // 这里静默关闭即可，不当作错误。
                closeQuietly()
            }
            info.copy(pairingCode = code)
        }

    open suspend fun sendCommand(request: CommandRequest): CommandResponse =
        withContext(Dispatchers.IO) {
            commandMutex.withLock {
                sendAndReadResponse(request.copy(txn = nextTxn()))
            }
        }

    /**
     * 便捷命令：自动使用当前会话号与事务号。
     * 仅新增方法，不改动既有连接流程。
     */
    open suspend fun sendCommand(opcode: Int, params: List<Int> = emptyList()): CommandResponse =
        withContext(Dispatchers.IO) {
            commandMutex.withLock {
                val request = CommandRequest(activeSessionId, opcode, nextTxn(), params)
                writeFrame(6, NkCommandCodec.encodeRequest(request))
                readResponse(request)
            }
        }

    /**
     * 数据阶段写命令（如 SetDevicePropValueEx）：
     * type6 请求 → type9 Start Data(session+length8) → type12 End Data(session+data)。
     */
    open suspend fun sendCommandWithData(
        opcode: Int,
        params: List<Int>,
        data: ByteArray,
    ): CommandResponse =
        withContext(Dispatchers.IO) {
            commandMutex.withLock {
                val request = CommandRequest(activeSessionId, opcode, nextTxn(), params)
                writeFrame(6, NkCommandCodec.encodeRequest(request))
                val start = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(request.session)
                    .putLong(data.size.toLong())
                    .array()
                writeFrame(9, start)
                val end = ByteBuffer.allocate(4 + data.size).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(request.session)
                    .put(data)
                    .array()
                writeFrame(12, end)
                readResponse(request)
            }
        }

    open suspend fun close() {
        withContext(Dispatchers.IO) {
            socket?.close()
            socket = null
            input = null
            output = null
            eventSocket?.close()
            eventSocket = null
            eventInput = null
            eventOutput = null
        }
    }

    private fun <T> runStep(name: String, block: () -> T): T =
        try {
            block()
        } catch (e: Exception) {
            throw IllegalStateException("$name 阶段失败：${e.message}", e)
        }

    private fun openEventChannel(host: String, port: Int, sessionId: Int) {
        val sock = Socket()
        sock.connect(InetSocketAddress(host, port), connectTimeoutMs)
        sock.soTimeout = readTimeoutMs
        eventSocket = sock
        eventInput = sock.getInputStream()
        eventOutput = sock.getOutputStream()
        val payload = ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(sessionId)
            .array()
        eventOutput!!.write(PtpIpCodec.encodeFrame(3, payload))
        eventOutput!!.flush()
        while (true) {
            val frame = readFrame(eventInput!!) ?: error("事件通道在建立时被关闭")
            if (frame.packetType == 4) return
        }
    }

    private fun closeQuietly() {
        runCatching { socket?.close() }
        runCatching { eventSocket?.close() }
        socket = null
        input = null
        output = null
        eventSocket = null
        eventInput = null
        eventOutput = null
    }

    private fun sendAndReadResponse(request: CommandRequest): CommandResponse {
        writeFrame(6, NkCommandCodec.encodeRequest(request))
        return readResponse(request)
    }

    private fun readResponse(request: CommandRequest): CommandResponse {
        val dataBuffer = java.io.ByteArrayOutputStream()
        var hasData = false
        while (true) {
            val frame = readFrame() ?: error("connection closed")
            when (frame.packetType) {
                9 -> Unit // Start Data：session(4) + length(8)，无数据
                10, 12 -> {
                    val payload = frame.payload
                    if (payload.size > 4) {
                        dataBuffer.write(payload, 4, payload.size - 4)
                        hasData = true
                    }
                }
                7 -> {
                    val response = NkCommandCodec.decodeResponse(frame.payload)
                    if (response.txn == request.txn) {
                        return if (hasData) response.copy(data = dataBuffer.toByteArray()) else response
                    }
                }
            }
        }
    }

    private fun writeFrame(packetType: Int, payload: ByteArray) {
        output!!.write(PtpIpCodec.encodeFrame(packetType, payload))
        output!!.flush()
    }

    private fun readFrame(stream: InputStream): PtpIpFrame? {
        val header = ByteArray(PtpIpCodec.HEADER_SIZE)
        if (!readFully(stream, header)) return null
        val length = littleEndianInt(header, 0)
        val packetType = littleEndianInt(header, 4)
        require(length >= PtpIpCodec.HEADER_SIZE && length <= MAX_FRAME_SIZE) {
            "invalid frame length=$length type=$packetType（流可能已错位）"
        }
        val body = ByteArray(length - PtpIpCodec.HEADER_SIZE)
        if (!readFully(stream, body)) return null
        return PtpIpFrame(packetType, length, body)
    }

    private fun readFrame(): PtpIpFrame? {
        val stream = input ?: return null
        return readFrame(stream)
    }

    private fun readFully(stream: InputStream, target: ByteArray): Boolean {
        var offset = 0
        while (offset < target.size) {
            val n = stream.read(target, offset, target.size - offset)
            if (n < 0) return false
            if (n == 0) continue
            offset += n
        }
        return true
    }

    private fun nextTxn(): Int {
        txn += 1
        return txn
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private companion object {
        const val MAX_FRAME_SIZE = 64 * 1024 * 1024
    }

    private fun parseAck(payload: ByteArray): SessionInfo {
        require(payload.size >= 20) { "ack payload too short" }
        val sessionId = littleEndianInt(payload, 0)
        val guid = payload.copyOfRange(4, 20).joinToString("") { "%02x".format(it) }
        val nameBytes = payload.copyOfRange(20, payload.size - 6)
        val modelSerial = String(nameBytes, Charsets.UTF_16LE)
        return SessionInfo(guid, modelSerial, sessionId)
    }
}

/**
 * 构造 type 1 Init Command Request 载荷（SnapBridge u7/he 逐字节对照）。
 *
 * 结构：uuid(16) + name(UTF-16) + null(2) + bufferSize(4LE)
 * - uuid 即客户端持久化 GUID（SnapBridge 存于 "BonjourConnectGuid"）；
 *   线上字节 2d ad 7e 8f 61 1e 4d 0a b7 10 99 ab d7 5e 89 d6
 *   对应 UUID 2dad7e8f-611e-4d0a-b710-99abd75e89d6。
 * - name 字节序由 mode 决定（智能设备 UTF-16BE / 计算机 UTF-16LE）。
 * - tail：名称空终止符 00 00 + bufferSize（默认 65536 = 00 00 01 00）。
 */
internal fun buildInitPayload(
    uuid: ByteArray,
    mode: ConnectionMode,
    name: String,
    bufferSize: Int = 65536,
): ByteArray {
    require(uuid.size == 16) { "client uuid must be 16 bytes" }
    val nameBytes = name.toByteArray(mode.charset)
    return ByteBuffer.allocate(16 + nameBytes.size + 6)
        .order(ByteOrder.LITTLE_ENDIAN)
        .put(uuid)
        .put(nameBytes)
        .putShort(0)
        .putInt(bufferSize)
        .array()
}

/**
 * 解析 0x952B GetPtpipPairingCode 响应数据（SnapBridge g6 逐字节对照）。
 *
 * 数据 = count(4LE) + count 个字节，每个字节的十进制值连起来就是验证码。
 * 例：04 00 00 00 05 00 07 07 → count=4 → "5" "0" "7" "7" = "5077"。
 */
internal fun parsePairingCode(data: ByteArray): String? {
    if (data.size < 4) return null
    val count = littleEndianIntAt(data, 0)
    if (count <= 0 || count > data.size - 4) return null
    return buildString {
        for (i in 0 until count) {
            append(data[4 + i].toInt() and 0xFF)
        }
    }
}

internal fun littleEndianIntAt(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xFF) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 3].toInt() and 0xFF) shl 24)

/**
 * 生成持久化客户端 UUID；第 5-6 字节写入模式 code（智能设备 0x1E61=61 1e，
 * 计算机 0xCC0D=0d cc），与 SnapBridge/WTU 线上结构一致。
 */
fun generateClientUuid(mode: ConnectionMode): ByteArray {
    val uuid = ByteArray(16)
    ThreadLocalRandom.current().nextBytes(uuid)
    uuid[4] = (mode.code and 0xFF).toByte()
    uuid[5] = ((mode.code shr 8) and 0xFF).toByte()
    return uuid
}
