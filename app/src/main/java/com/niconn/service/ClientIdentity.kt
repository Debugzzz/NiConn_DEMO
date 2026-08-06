package com.niconn.service

import android.content.Context
import java.util.concurrent.ThreadLocalRandom

/**
 * 客户端身份：相机按 Init 载荷中的 16 字节客户端 UUID（+名称）识别已配对客户端。
 * SnapBridge 将其持久化在 "BonjourConnectGuid"，重配对也不改变；
 * 首次生成后持久化，后续连接复用，否则每次连接都像「新客户端」。
 */
data class ClientIdentity(val name: String, val uuid: ByteArray)

class ClientIdentityStore(context: Context) {
    private val prefs = context.getSharedPreferences("niconn", Context.MODE_PRIVATE)

    fun getOrCreate(): ClientIdentity {
        val uuidHex = prefs.getString(KEY_UUID, null)
        val name = prefs.getString(KEY_NAME, null)
        if (uuidHex != null && name != null) {
            return ClientIdentity(
                name = name,
                uuid = uuidHex.hexToBytes(),
            )
        }
        val identity = ClientIdentity(
            name = "Android Device",
            uuid = ByteArray(16).also { ThreadLocalRandom.current().nextBytes(it) },
        )
        prefs.edit()
            .putString(KEY_UUID, identity.uuid.toHex())
            .putString(KEY_NAME, identity.name)
            .apply()
        return identity
    }

    private companion object {
        const val KEY_UUID = "client_uuid"
        const val KEY_NAME = "client_name"

        fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

        fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
