package com.ddmh.bridge.service.helper

import com.ddmh.bridge.service.R
import com.ddmh.bridge.service.ext.res
import com.ddmh.bridge.service.helper.Constant.TAG
import com.ddmh.bridge.service.utils.LogX
import com.scwang.smart.refresh.footer.ClassicsFooter
import com.scwang.smart.refresh.header.ClassicsHeader
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

/**
 * SmartRefreshLayout 国际化文案辅助工具类
 *
 * 职责：
 * 通过反射修改 ClassicsHeader / ClassicsFooter 的静态文案字段，
 * 实现下拉刷新与上拉加载组件的文案统一替换。
 *
 * SmartRefreshLayout 的 ClassicsHeader / ClassicsFooter 使用静态字段
 * 持有显示文案（如 REFRESH_HEADER_PULLING），SDK 本身不提供 XML 配置方式，
 * 只能通过反射修改静态字段来替换文案。
 * 本类在初始化时一次性将所有静态文案替换为库内 strings.xml 定义的字符串资源，
 * 并通过 ConcurrentHashMap 缓存反射 Field 以避免重复 getDeclaredField。
 *
 * ⚠️⚠️⚠️ 【接入方必读】使用 SmartRefreshLayout 前需完成以下步骤：
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 1. 确保 ServiceHelper.init() 已在 Application.onCreate() 中调用
 *    （否则 res.getString 将因 Context 缺失而失败）
 * 2. 在 ServiceHelper.init() 之后调用 SmartRefreshHelper.initSmartRefreshLanguage()
 *    （必须在首次创建 SmartRefreshLayout 之前完成文案替换）
 * 3. 如需自定义刷新文案，在接入方 app 模块的 strings.xml 中
 *    覆盖同名资源即可（如 srl_header_pulling），无需修改本类
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 *
 * 默认文案（定义于 BridgeService strings.xml，接入方可覆盖）：
 * ```
 * Header：
 *   下拉刷新 → 释放立即刷新 → 正在刷新… → 刷新完成 / 刷新失败
 * Footer：
 *   上拉加载更多 → 释放立即加载 → 正在加载… → 加载完成 / 加载失败 / —— 没有更多数据 ——
 * ```
 *
 * @author 𝑳𝒆𝒆𝒔𝒊𝒏
 * @since 2026/06/16
 */
object SmartRefreshHelper {

    /**
     * 缓存反射 Field，避免每次调用都执行 getDeclaredField + setAccessible。
     *
     * 反射 getDeclaredField 是耗时操作（涉及 JVM 安全检查），
     * ConcurrentHashMap 缓存后仅在首次调用时执行反射查找，
     * 后续直接从缓存取出 Field 对象使用，显著降低性能开销。
     *
     * key 格式："ClassName#FIELD_NAME"，如 "com.scwang.smart.refresh.header.ClassicsHeader#REFRESH_HEADER_PULLING"
     */
    private val fieldCache = ConcurrentHashMap<String, Field>()

    /**
     * 初始化 SmartRefreshLayout 国际化文案
     *
     * 通过反射将 ClassicsHeader 和 ClassicsFooter 的所有静态文案字段
     * 替换为库内 strings.xml 定义的中文字符串资源值。
     * 初始化完成后，后续所有 SmartRefreshLayout 实例都将显示替换后的文案。
     *
     * ⚠️⚠️⚠️ 【接入方必做】调用此方法前，必须确保 ServiceHelper.init() 已执行，
     * 否则 [res]（全局 Resources）无法获取字符串资源，导致初始化失败但不会 crash。
     *
     * ⚠️⚠️⚠️ 【接入方必做】必须在首次创建 SmartRefreshLayout 实例之前调用此方法，
     * 因为 ClassicsHeader / ClassicsFooter 的静态文案在视图构造时读取，
     * 若创建后再调用初始化，已有实例不会自动刷新文案。
     */
    fun initSmartRefreshLanguage() {
        try {
            updateHeaderTexts()
            updateFooterTexts()
        } catch (t: Throwable) {
            // 初始化异常兜底，确保反射问题不影响应用正常运行
            LogX.e(TAG, "SmartRefreshLayout 国际化文案初始化异常, message=${t.message}")
        }
    }

    /**
     * 替换 ClassicsHeader 静态文案字段
     *
     * ClassicsHeader 是 SmartRefreshLayout 的经典下拉刷新头部组件，
     * 其所有显示文案由静态字段持有，在视图构造时读取。
     * 本方法通过反射将以下 7 个字段替换为库内定义的中文文案：
     *
     * | 静态字段                  | 默认中文文案     | 字符串资源                |
     * |--------------------------|----------------|--------------------------|
     * | REFRESH_HEADER_PULLING   | 下拉刷新       | srl_header_pulling       |
     * | REFRESH_HEADER_RELEASE   | 释放立即刷新   | srl_header_release       |
     * | REFRESH_HEADER_REFRESHING| 正在刷新…      | srl_header_refreshing    |
     * | REFRESH_HEADER_LOADING   | 正在加载…      | srl_header_loading       |
     * | REFRESH_HEADER_FINISH    | 刷新完成       | srl_header_finish        |
     * | REFRESH_HEADER_FAILED    | 刷新失败       | srl_header_failed        |
     * | REFRESH_HEADER_UPDATE    | 上次更新 %s    | srl_header_update        |
     */
    private fun updateHeaderTexts() {
        val headerClazz = ClassicsHeader::class.java
        val headerMappings = listOf(
            "REFRESH_HEADER_PULLING" to R.string.srl_header_pulling,
            "REFRESH_HEADER_RELEASE" to R.string.srl_header_release,
            "REFRESH_HEADER_REFRESHING" to R.string.srl_header_refreshing,
            "REFRESH_HEADER_LOADING" to R.string.srl_header_loading,
            "REFRESH_HEADER_FINISH" to R.string.srl_header_finish,
            "REFRESH_HEADER_FAILED" to R.string.srl_header_failed,
            "REFRESH_HEADER_UPDATE" to R.string.srl_header_update,
        )
        headerMappings.forEach { (fieldName, resId) ->
            setStaticField(headerClazz, fieldName, res.getString(resId))
        }
    }

    /**
     * 替换 ClassicsFooter 静态文案字段
     *
     * ClassicsFooter 是 SmartRefreshLayout 的经典上拉加载底部组件，
     * 其所有显示文案由静态字段持有，在视图构造时读取。
     * 本方法通过反射将以下 7 个字段替换为库内定义的中文文案：
     *
     * | 静态字段                  | 默认中文文案       | 字符串资源                |
     * |--------------------------|------------------|--------------------------|
     * | REFRESH_FOOTER_PULLING   | 上拉加载更多     | srl_footer_pulling       |
     * | REFRESH_FOOTER_RELEASE   | 释放立即加载     | srl_footer_release       |
     * | REFRESH_FOOTER_REFRESHING| 正在刷新…        | srl_footer_refreshing    |
     * | REFRESH_FOOTER_LOADING   | 正在加载…        | srl_footer_loading       |
     * | REFRESH_FOOTER_FINISH    | 加载完成         | srl_footer_finish        |
     * | REFRESH_FOOTER_FAILED    | 加载失败         | srl_footer_failed        |
     * | REFRESH_FOOTER_NOTHING   | —— 没有更多数据—— | srl_footer_nothing       |
     */
    private fun updateFooterTexts() {
        val footerClazz = ClassicsFooter::class.java
        val footerMappings = listOf(
            "REFRESH_FOOTER_PULLING" to R.string.srl_footer_pulling,
            "REFRESH_FOOTER_RELEASE" to R.string.srl_footer_release,
            "REFRESH_FOOTER_REFRESHING" to R.string.srl_footer_refreshing,
            "REFRESH_FOOTER_LOADING" to R.string.srl_footer_loading,
            "REFRESH_FOOTER_FINISH" to R.string.srl_footer_finish,
            "REFRESH_FOOTER_FAILED" to R.string.srl_footer_failed,
            "REFRESH_FOOTER_NOTHING" to R.string.srl_footer_nothing,
        )
        footerMappings.forEach { (fieldName, resId) ->
            setStaticField(footerClazz, fieldName, res.getString(resId))
        }
    }

    /**
     * 通过反射设置类的静态字段值
     *
     * SmartRefreshLayout 的 ClassicsHeader / ClassicsFooter 文案字段
     * 均为 public static String 类型，SDK 未提供 setter 方法，
     * 只能通过反射修改。本方法使用 ConcurrentHashMap 缓存已获取的 Field 对象，
     * 避免每次调用都执行 getDeclaredField + setAccessible 的性能开销。
     *
     * 缓存策略：
     * - 首次调用：执行 getDeclaredField → setAccessible(true) → 缓存 Field
     * - 后续调用：直接从 fieldCache 取出 Field → field.set(null, value)
     *
     * ⚠️⚠️⚠️ 【注意】反射修改的是静态字段，所有 ClassicsHeader / ClassicsFooter
     * 实例共享同一份文案，修改后立即生效于后续创建的所有实例，
     * 但已创建的实例不会自动刷新（需手动触发视图更新）。
     *
     * @param clazz     目标类（ClassicsHeader 或 ClassicsFooter）
     * @param fieldName 静态字段名，如 "REFRESH_HEADER_PULLING"
     * @param value     要设置的字符串值（从 strings.xml 获取）
     */
    private fun setStaticField(clazz: Class<*>, fieldName: String, value: String) {
        try {
            val cacheKey = "${clazz.name}#$fieldName"
            val field = fieldCache[cacheKey] ?: clazz.getDeclaredField(fieldName).also {
                it.isAccessible = true
                fieldCache[cacheKey] = it
            }
            field.set(null, value)
        } catch (t: Throwable) {
            // 反射异常兜底，确保单个字段设置失败不影响其他字段
            LogX.e(TAG, "反射设置静态字段失败: ${clazz.simpleName}.$fieldName -> $value, ${t.message}")
        }
    }
}
