package com.ddmh.bridge.service.utils

import android.content.res.Resources
import com.ddmh.bridge.service.helper.Constant.TAG
import com.ddmh.bridge.service.utils.Internation.supportedCountries
import java.util.Locale

/**
 * 国际化工具类
 *
 * 职责：
 * 1. 获取系统国家/地区代码，并与业务支持的国家列表做匹配过滤
 * 2. 获取系统语言标签（BCP 47 格式，如 en-US、fr-FR），
 *    对中文语言做兜底处理（返回默认值），适配多版本 API
 *
 * @author 𝑳𝒆𝒆𝒔𝒊𝒏
 * @since 2026/06/16
 */
object Internation {

    /**
     * 业务支持的国家/地区代码集合
     *
     * 当前覆盖：加拿大(CA)、英国(GB)、澳大利亚(AU)、新西兰(NZ)、
     * 德国(DE)、法国(FR)、意大利(IT)、葡萄牙(PT)、西班牙(ES)。
     * 若系统地区不在此集合中，将返回 [default] 默认值。
     */
    private val supportedCountries = setOf("CA", "GB", "AU", "NZ", "DE", "FR", "IT", "PT", "ES")

    /**
     * 获取系统国家/地区代码，并与业务支持列表做匹配过滤
     *
     * 逻辑：
    1. 从 Locale.getDefault().country 获取系统地区码（如 "US"、"DE"）
    2. 转为大写后，检查是否在 [supportedCountries] 中
    3. 若匹配则返回系统地区码；否则返回 [default]
    4. 若获取过程中出现异常，兜底返回 [default]
     *
     * @param default 默认国家代码，当系统地区不在支持列表中或获取异常时返回。默认 "US"
     * @return 匹配成功的系统地区码，或 [default]
     */
    fun getSystemCountryCode(default: String = "US"): String {
        return try {
            val area = Locale.getDefault().country.uppercase(Locale.ROOT)
            LogX.d(TAG, "系统国家/地区代码 = $area")
            if (supportedCountries.contains(area)) {
                area
            } else {
                default
            }
        } catch (e: Exception) {
            LogX.e(TAG, "获取系统国家代码异常, 返回默认值 = $default, message = ${e.message}")
            default
        }
    }

    /**
     * 获取系统语言标签（BCP 47 格式）
     *
     * 逻辑：
     * 1. 从系统 Configuration 获取首选语言 Locale（config.locales.get(0)）
     * 2. 转换为 BCP 47 语言标签（如 en-US、fr-FR）
     * 3. 若语言标签以 "zh" 开头（中文），返回 [default] 默认值
     * 4. 其他语言则返回实际语言标签
     *
     * @param default 默认语言标签，当系统语言为中文或获取异常时返回。默认 "en-US"
     * @return BCP 47 格式的语言标签，或 [default]
     */
    fun getSystemLanguageTag(default: String = "en-US"): String {
        val config = Resources.getSystem().configuration
        // 项目 minSdk 24，无需兼容 API < 24，直接使用 config.locales
        val languageTag = config.locales.get(0).toLanguageTag()
        LogX.d(TAG, "系统语言标签 = $languageTag")

        return if (languageTag.startsWith("zh", ignoreCase = true)) {
            // 中文语言兜底：业务不支持中文，返回默认语言标签
            LogX.d(TAG, "系统语言为中文, 返回默认值 = $default")
            default
        } else {
            languageTag
        }
    }
}
