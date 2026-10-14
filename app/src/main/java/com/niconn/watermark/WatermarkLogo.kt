package com.niconn.watermark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * 品牌 Logo 资源（对应 web 版 `utils/logoMapper.ts` 的 `getLogoPath` + `drawLogo`）。
 *
 * 上游按 EXIF Make 选品牌图，未匹配时回落 `default.png`。本项目按需求内置尼康作为
 * 默认品牌图（机型以尼康为主），需要新增品牌时把 PNG 放进 `assets/logos/`
 * 并在 [ASSETS] 登记即可。
 */
object WatermarkLogo {

    /** 默认（尼康）Logo 资源名。 */
    const val DEFAULT_ASSET = "logos/nikon.png"

    /** 品牌关键字 → 资源名（对齐上游大小写不敏感的包含匹配）。 */
    private val ASSETS = mapOf(
        "NIKON" to DEFAULT_ASSET,
    )

    private val cache = mutableMapOf<String, Bitmap>()

    /** 取品牌 Logo；[make] 为空或未登记品牌时使用默认（尼康）。 */
    fun forMake(context: Context, make: String?): Bitmap? = load(context, assetFor(make))

    /** Make → 资源名。抽出为纯函数以便单测。 */
    internal fun assetFor(make: String?): String {
        val normalized = make?.uppercase().orEmpty()
        if (normalized.isBlank()) return DEFAULT_ASSET
        return ASSETS.entries.firstOrNull { normalized.contains(it.key) }?.value ?: DEFAULT_ASSET
    }

    private fun load(context: Context, asset: String): Bitmap? =
        cache[asset] ?: runCatching {
            context.assets.open(asset).use { BitmapFactory.decodeStream(it) }
        }.getOrNull()?.also { cache[asset] = it }

    /** 释放缓存（测试或内存紧张时使用）。 */
    fun clearCache() = cache.clear()
}
