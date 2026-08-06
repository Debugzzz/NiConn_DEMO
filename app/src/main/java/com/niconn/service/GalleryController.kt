package com.niconn.service

import com.niconn.protocol.NkOps
import com.niconn.protocol.PtpDataCodec
import com.niconn.protocol.PtpIpSession

/**
 * 相册控制器：对象句柄列表、缩略图、原图下载、删除（Z50II MTP 规范 6.2.1）。
 */
class GalleryController(private val session: PtpIpSession) {
    private var rawLogged = false
    private var errorLogged = false

    suspend fun listHandles(): List<Int> {
        val response = session.sendCommand(
            NkOps.GetObjectHandles,
            listOf(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0),
        )
        if (response.code != NkOps.OK) return emptyList()
        return PtpDataCodec.parseObjectHandles(response.data)
    }

    /** 按格式过滤获取句柄（GetObjectHandles 实测稳定，用于格式角标）。 */
    suspend fun listHandlesFor(format: Int): List<Int> {
        val response = session.sendCommand(
            NkOps.GetObjectHandles,
            listOf(0xFFFFFFFF.toInt(), format, 0xFFFFFFFF.toInt()),
        )
        if (response.code != NkOps.OK) return emptyList()
        return PtpDataCodec.parseObjectHandles(response.data)
    }

    suspend fun loadThumb(handle: Int): ByteArray? =
        session.sendCommand(NkOps.GetThumb, listOf(handle))
            .takeIf { it.code == NkOps.OK }
            ?.data

    /** ObjectInfo 中的 ObjectFormatCode（规范 9.2，偏移 4）。 */
    suspend fun objectFormat(handle: Int): Int? {
        val response = session.sendCommand(NkOps.GetObjectInfo, listOf(handle))
        if (response.code != NkOps.OK) {
            if (!errorLogged) {
                errorLogged = true
                println("NiConnGal fmt code=0x%04X handle=$handle".format(response.code))
            }
            return null
        }
        response.data?.let { data ->
            if (!rawLogged) {
                rawLogged = true
                println("NiConnGal raw=" + data.take(96).joinToString("") { "%02x".format(it) })
            }
        }
        return response.data?.let { PtpDataCodec.parseObjectFormat(it) }
    }

    /** 完整 ObjectInfo（文件名/大小/分辨率/拍摄日期）。 */
    suspend fun objectInfo(handle: Int): PtpDataCodec.ObjectInfoData? {
        val response = session.sendCommand(NkOps.GetObjectInfo, listOf(handle))
        if (response.code != NkOps.OK) {
            if (!errorLogged) {
                errorLogged = true
                println("NiConnGal objInfo code=0x%04X handle=$handle".format(response.code))
            }
            return null
        }
        return response.data
            ?.also { data ->
                if (!rawLogged) {
                    rawLogged = true
                    println("NiConnGal raw=" + data.take(96).joinToString("") { "%02x".format(it) })
                }
            }
            ?.let { PtpDataCodec.parseObjectInfo(it) }
    }

    suspend fun loadImage(handle: Int): ByteArray? =
        session.sendCommand(NkOps.GetObject, listOf(handle))
            .takeIf { it.code == NkOps.OK }
            ?.data

    suspend fun delete(handle: Int): Boolean =
        session.sendCommand(NkOps.DeleteObject, listOf(handle)).code == NkOps.OK
}
