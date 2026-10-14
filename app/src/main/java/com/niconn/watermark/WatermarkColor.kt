package com.niconn.watermark

/**
 * 纯 Kotlin 颜色解析与格式化工具（不依赖 android.graphics，便于 JVM 单元测试）。
 *
 * 支持与 web 版相同的颜色格式：
 * - "#RRGGBB" / "#AARRGGBB" / "#RGB"
 * - "rgb(r,g,b)" / "rgba(r,g,b,a)"
 * - "(r,g,b)" / "(r,g,b,a)" 元组格式
 * - 命名颜色（black/white/red/...）
 *
 * 颜色以 0xAARRGGBB 整数表示。
 */
object WatermarkColor {

    /** 解析颜色字符串为 0xAARRGGBB 整数，失败时回退到 fallback。 */
    fun parse(color: String?, fallback: Int = 0xFF000000.toInt()): Int {
        if (color.isNullOrBlank()) return fallback
        val trimmed = color.trim()

        // (r,g,b) / (r,g,b,a) 元组
        val tuple = Regex("^\\((\\d+),\\s*(\\d+),\\s*(\\d+)(?:,\\s*(\\d+))?\\)$").find(trimmed)
        if (tuple != null) {
            val r = tuple.groupValues[1].toInt().coerceIn(0, 255)
            val g = tuple.groupValues[2].toInt().coerceIn(0, 255)
            val b = tuple.groupValues[3].toInt().coerceIn(0, 255)
            val a = tuple.groupValues[4].takeIf { it.isNotEmpty() }?.toInt()?.coerceIn(0, 255) ?: 255
            return argb(a, r, g, b)
        }

        // rgb()/rgba() 函数
        val func = Regex("^rgba?\\((\\d+(?:\\.\\d+)?),\\s*(\\d+(?:\\.\\d+)?),\\s*(\\d+(?:\\.\\d+)?)(?:,\\s*(\\d+(?:\\.\\d+)?))?\\)$").find(trimmed)
        if (func != null) {
            val r = func.groupValues[1].toFloat().coerceIn(0f, 255f).toInt()
            val g = func.groupValues[2].toFloat().coerceIn(0f, 255f).toInt()
            val b = func.groupValues[3].toFloat().coerceIn(0f, 255f).toInt()
            val a = (func.groupValues[4].takeIf { it.isNotEmpty() }?.toFloat()?.coerceIn(0f, 1f) ?: 1f) * 255
            return argb(Math.round(a).toInt(), r, g, b)
        }

        // #RRGGBB / #AARRGGBB / #RGB
        if (trimmed.startsWith("#")) {
            return try {
                when (trimmed.length) {
                    4 -> {
                        val r = trimmed[1].toString().repeat(2).toInt(16)
                        val g = trimmed[2].toString().repeat(2).toInt(16)
                        val b = trimmed[3].toString().repeat(2).toInt(16)
                        argb(255, r, g, b)
                    }
                    else -> fromHex(trimmed.substring(1)) ?: fallback
                }
            } catch (_: NumberFormatException) {
                fallback
            }
        }

        // 命名颜色
        return when (trimmed.lowercase()) {
            "black" -> 0xFF000000.toInt()
            "white" -> 0xFFFFFFFF.toInt()
            "red" -> 0xFFFF0000.toInt()
            "blue" -> 0xFF0000FF.toInt()
            "green" -> 0xFF008000.toInt()
            "gray", "grey" -> 0xFF808080.toInt()
            "transparent" -> 0
            else -> fromHex(trimmed.removePrefix("#")) ?: fallback
        }
    }

    /** 从 hex 字符串（无 #）解析 AARRGGBB 或 RRGGBB，非法字符返回 null。 */
    private fun fromHex(hex: String): Int? {
        return try {
            when (hex.length) {
                6 -> {
                    val r = hex.substring(0, 2).toInt(16)
                    val g = hex.substring(2, 4).toInt(16)
                    val b = hex.substring(4, 6).toInt(16)
                    argb(255, r, g, b)
                }
                8 -> {
                    val a = hex.substring(0, 2).toInt(16)
                    val r = hex.substring(2, 4).toInt(16)
                    val g = hex.substring(4, 6).toInt(16)
                    val b = hex.substring(6, 8).toInt(16)
                    argb(a, r, g, b)
                }
                else -> null
            }
        } catch (_: NumberFormatException) {
            null
        }
    }

    /** 组装 0xAARRGGBB。 */
    fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        ((a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255))

    fun red(color: Int): Int = (color ushr 16) and 0xFF
    fun green(color: Int): Int = (color ushr 8) and 0xFF
    fun blue(color: Int): Int = color and 0xFF
    fun alpha(color: Int): Int = (color ushr 24) and 0xFF

    /** 转成 CSS 风格 #RRGGBB（仅用于展示）。 */
    fun toHex(color: Int): String {
        return String.format("#%02X%02X%02X", red(color), green(color), blue(color))
    }
}
