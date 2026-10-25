package com.niconn.app

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 应用内语言的轻量镜像：UI 层用资源（strings.xml/values-en），ViewModel 等
 * 非组合层通过 [tr] 取当前语言文案。语言切换（AppCompatDelegate）触发
 * Activity 重建时由 MainActivity 刷新此状态。
 */
object AppLanguage {
    /** 当前是否英文（默认中文，跟随系统时按系统语言解析）。 */
    val isEnglish = MutableStateFlow(false)

    fun refresh(currentLocaleLanguage: String) {
        isEnglish.value = currentLocaleLanguage.startsWith("en")
    }

    /** 非组合层双语文案：按当前语言取 zh / en 之一。 */
    fun tr(zh: String, en: String): String = if (isEnglish.value) en else zh
}
