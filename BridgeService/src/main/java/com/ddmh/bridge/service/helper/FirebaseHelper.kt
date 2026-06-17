package com.ddmh.bridge.service.helper

import android.os.Bundle
import com.ddmh.bridge.service.helper.Constant.TAG
import com.ddmh.bridge.service.utils.LogX
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

/**
 * Firebase Analytics SDK 辅助工具类
 *
 * 职责：
 * 1. 初始化 Firebase Analytics SDK 并配置 Debug 模式
 * 2. 上报自定义事件（支持带参数和不带参数两种方式）
 * 3. 设置公共事件属性（用户属性 + 事件超级参数）
 * 4. 设置用户级属性（如 user_id、channel 等）
 *
 * ⚠️⚠️⚠️ 【接入方必读】接入 Firebase 前需完成以下步骤 ⚠️⚠️⚠️
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 步骤 1：将 google-services.json 放入 app 模块根目录
 *         （从 Firebase Console → 项目设置 → 下载）
 *
 * 步骤 2：在 app/build.gradle 添加插件（必须放在文件顶部）：
 *         apply plugin: 'com.google.gms.google-services'
 *
 * 步骤 3：在根 build.gradle 的 buildscript.dependencies 添加 classpath：
 *         classpath 'com.google.gms:google-services:4.4.2'
 *
 * 步骤 4：在 Application.onCreate() 中调用初始化：
 *         FirebaseHelper.initSDK()
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 缺少 google-services.json 将导致 Firebase SDK 无法初始化，
 * 但本类的 try-catch 兜底机制确保不会导致应用 crash。
 *
 * @author 𝑳𝒆𝒆𝒔𝒊𝒏
 * @since 2026/06/16
 */
object FirebaseHelper {

    /** FirebaseAnalytics 实例，在 [initSDK] 中获取 */
    private var firebaseAnalytics: FirebaseAnalytics? = null

    /**
     * 初始化 Firebase Analytics SDK
     *
     * 调用时机：Application.onCreate() 或库初始化阶段。
     * 通过 [Firebase.analytics] 获取默认实例，
     * 并根据 [ServiceHelper.isDebug] 决定是否开启 SDK 日志及 Debug 模式。
     *
     * ⚠️⚠️⚠️ 【接入方必做】调用此方法前，必须确保：
     *   1. app 模块已添加 apply plugin: 'com.google.gms.google-services'
     *   2. google-services.json 已放入 app 模块根目录
     *   否则 Firebase SDK 将无法获取配置信息，初始化失败但不会 crash。
     */
    fun initSDK() {
        try {
            val startTime = System.currentTimeMillis()
            LogX.d(TAG, "Firebase Analytics SDK 开始初始化")
            firebaseAnalytics = Firebase.analytics

            // Firebase Analytics 默认即开启数据收集，此处显式调用确保初始化后处于激活状态
            firebaseAnalytics?.setAnalyticsCollectionEnabled(true)

            // ⚠️⚠️⚠️ 【接入方注意】调试事件实时上报需在设备上执行:
            // adb shell setprop debug.firebase.analytics.app <package_name>
            // 可使事件立即上报到 Firebase 后台，无需等待批量窗口
            if (ServiceHelper.isDebug()) {
                LogX.d(TAG, "Firebase Analytics Debug 模式已开启")
            }

            val loadDuration = System.currentTimeMillis() - startTime
            LogX.d(TAG, "Firebase Analytics SDK 初始化完成, 耗时=${loadDuration}ms")
        } catch (t: Throwable) {
            // 初始化异常兜底，确保 SDK 问题不影响应用正常运行
            LogX.e(TAG, "Firebase Analytics SDK 初始化异常, message=${t.message}")
        }
    }

    /**
     * 上报自定义事件（不带参数）
     *
     * @param code 事件名称，对应 Firebase 后台注册的事件名
     */
    fun track(code: String) {
        try {
            LogX.d(TAG, "Firebase 埋点：code = $code")
            firebaseAnalytics?.logEvent(code, null)
        } catch (t: Throwable) {
            // 上报异常兜底，确保 SDK 问题不影响应用正常运行
            LogX.e(TAG, "Firebase 埋点异常: code = $code, ${t.message}")
        }
    }

    /**
     * 上报自定义事件（带参数）
     *
     * @param code  事件名称，对应 Firebase 后台注册的事件名
     * @param event 事件参数映射，key 为参数名，value 为参数值（支持 String、Long、Double 等）
     */
    fun track(code: String, event: HashMap<String, String>?) {
        try {
            if (event != null) {
                val params = Bundle()
                event.forEach { (key, value) ->
                    params.putString(key, value)
                }
                firebaseAnalytics?.logEvent(code, params)
                LogX.d(TAG, "Firebase 埋点：code = $code \n event = $event")
            } else {
                firebaseAnalytics?.logEvent(code, null)
                LogX.d(TAG, "Firebase 埋点：code = $code")
            }
        } catch (t: Throwable) {
            // 上报异常兜底，确保 SDK 问题不影响应用正常运行
            LogX.e(TAG, "Firebase 埋点异常: code = $code, ${t.message}")
        }
    }

    /**
     * 上报自定义事件（带 Bundle 参数，支持更多类型如 Long、Double）
     *
     * @param code  事件名称
     * @param params 事件参数 Bundle，支持 String / Long / Double / Int 等多种类型
     */
    fun track(code: String, params: Bundle) {
        try {
            firebaseAnalytics?.logEvent(code, params)
            LogX.d(TAG, "Firebase 埋点：code = $code \n params = ${paramsToJson(params)}")
        } catch (t: Throwable) {
            // 上报异常兜底，确保 SDK 问题不影响应用正常运行
            LogX.e(TAG, "Firebase 埋点异常: code = $code, ${t.message}")
        }
    }

    /**
     * 设置公共事件属性
     *
     * 接入方在获取到用户信息后调用，传入自定义的公共属性。
     * 通过 [FirebaseAnalytics.setDefaultEventParameters] 注册后，
     * 每条 [track] 事件都会自动携带这些参数，无需在每次上报时手动传入。
     *
     * ⚠️⚠️⚠️ 【接入方注意】Firebase Analytics 公共事件参数限制：
     *   - 参数名最长 40 字符
     *   - 参数值最长 100 字符
     *   - 最多设置 25 个公共参数
     *
     * @param superProperties 接入方构建的公共属性 Bundle，如 channel、version 等
     */
    fun setCommonProperties(superProperties: Bundle) {
        try {
            firebaseAnalytics?.setDefaultEventParameters(superProperties)
            LogX.d(TAG, "Firebase 设置公共事件属性: ${paramsToJson(superProperties)}")
        } catch (t: Throwable) {
            LogX.e(TAG, "Firebase 设置公共属性异常: ${t.message}")
        }
    }

    /**
     * 设置公共事件属性（HashMap 格式，自动转换为 Bundle）
     *
     * @param superProperties 接入方构建的公共属性 HashMap，如 channel、version 等
     */
    fun setCommonProperties(superProperties: HashMap<String, String>) {
        try {
            val params = Bundle()
            superProperties.forEach { (key, value) ->
                params.putString(key, value)
            }
            firebaseAnalytics?.setDefaultEventParameters(params)
            LogX.d(TAG, "Firebase 设置公共事件属性: $superProperties")
        } catch (t: Throwable) {
            LogX.e(TAG, "Firebase 设置公共属性异常: ${t.message}")
        }
    }

    /**
     * 设置用户属性
     *
     * 用户属性用于描述用户群体的特征，可在 Firebase 后台用于受众群体定义和用户分群。
     * 常见属性：user_id、channel、language、country 等。
     *
     * ⚠️⚠️⚠️ 【接入方注意】用户属性限制：
     *   - 属性名最长 24 字符
     *   - 属性值最长 36 字符
     *   - 仅支持 String 类型
     *   - 最多设置 25 个用户属性
     *
     * @param name  属性名称，如 "user_id"、"channel"
     * @param value 属性值，如 "12345"、"google_play"
     */
    fun setUserProperty(name: String, value: String) {
        try {
            firebaseAnalytics?.setUserProperty(name, value)
            LogX.d(TAG, "Firebase 设置用户属性: name=$name, value=$value")
        } catch (t: Throwable) {
            LogX.e(TAG, "Firebase 设置用户属性异常: name=$name, ${t.message}")
        }
    }

    /**
     * 设置用户 ID
     *
     * 为当前用户设置唯一标识，用于跨设备用户追踪和受众群体定义。
     * 设置后可在 Firebase 后台通过 user_id 维度分析数据。
     *
     * ⚠️⚠️⚠️ 【接入方注意】用户 ID 限制：
     *   - 最长 256 字符
     *   - 不可包含前导/尾随空格
     *   - 不可设为空字符串
     *   - 设置为 null 可清除已设置的 userId
     *
     * @param userId 用户唯一标识，如玩家 ID、账户 ID 等
     */
    fun setUserId(userId: String) {
        try {
            firebaseAnalytics?.setUserId(userId)
            LogX.d(TAG, "Firebase 设置用户ID: userId=$userId")
        } catch (t: Throwable) {
            LogX.e(TAG, "Firebase 设置用户ID异常: ${t.message}")
        }
    }

    /**
     * 控制 Firebase Analytics 数据收集开关
     *
     * 可在运行时动态开关数据收集，例如用户选择退出分析时调用。
     * 默认为 true（开启），调用 setAnalyticsCollectionEnabled(false) 可暂停上报。
     *
     * ⚠️⚠️⚠️ 【接入方注意】隐私合规：
     *   - GDPR/隐私法规要求：应在用户同意前调用 setAnalyticsCollectionEnabled(false)
     *   - 用户同意后再调用 setAnalyticsCollectionEnabled(true) 开启上报
     *
     * @param enabled true 开启数据收集，false 关闭数据收集
     */
    fun setAnalyticsCollectionEnabled(enabled: Boolean) {
        try {
            firebaseAnalytics?.setAnalyticsCollectionEnabled(enabled)
            LogX.d(TAG, "Firebase 数据收集开关: enabled=$enabled")
        } catch (t: Throwable) {
            LogX.e(TAG, "Firebase 数据收集开关设置异常: ${t.message}")
        }
    }

    // ============== 内部工具方法 ==============

    /**
     * 将 Bundle 参数转换为可读的 JSON 字符串（用于日志输出）
     *
     * Firebase Analytics 的参数以 Bundle 形式传递，
     * 此方法遍历 Bundle 的所有 key 并格式化为类似 JSON 的字符串，
     * 方便在 LogX 中打印调试信息。
     *
     * @param params 待转换的 Bundle
     * @return 格式化字符串，如 {"key1":"value1","key2":"value2"}
     */
    private fun paramsToJson(params: Bundle): String {
        val sb = StringBuilder("{")
        val keys = params.keySet()
        for (key in keys) {
            if (sb.length > 1) sb.append(",")
            sb.append("\"$key\":\"${params.get(key)}\"")
        }
        sb.append("}")
        return sb.toString()
    }
}
