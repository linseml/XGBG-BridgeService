package com.ddmh.bridge.service.helper

import cn.thinkingdata.analytics.TDAnalytics
import cn.thinkingdata.analytics.TDConfig
import com.ddmh.bridge.service.ext.context
import com.ddmh.bridge.service.helper.AEHelper.track
import com.ddmh.bridge.service.helper.Constant.TAG
import com.ddmh.bridge.service.utils.LogX
import org.json.JSONObject

/**
 * @Description: 数数(TalkingData)SDK 辅助类，提供事件上报与公共属性设置
 * @Author: 𝑳𝒆𝒆𝒔𝒊𝒏
 * @CreateDate: 2026/06/12 17:04
 * @Version: 1.0
 */
object AEHelper {

    /**
     * 初始化数数 SDK
     *
     * 由接入方在 Application.onCreate 中调用，传入 appId 和 serverUrl。
     * 通过 [AppHelper.isDebug] 判断当前是否为 Debug 环境，决定是否开启 SDK 日志。
     *
     * @param appId    数数项目 appId，由接入方提供
     * @param serverUrl 数数数据上报地址，由接入方提供
     */
    fun initSDK(appId: String, serverUrl: String) {
        val config = TDConfig.getInstance(context, appId, serverUrl)
        TDAnalytics.enableLog(AppHelper.isDebug())
        TDAnalytics.init(config)
    }

    /**
     * 上报自定义事件
     *
     * @param code  事件名称
     * @param event 事件特有属性（拓展标签）
     */
    fun track(code: String, event: HashMap<String, String>? = null) {
        try {
            if (event != null) {
                val properties = JSONObject(event as Map<*, *>)
                TDAnalytics.track(code, properties)
                LogX.d(TAG, "数数埋点：code = $code \n event = $properties")
            } else {
                TDAnalytics.track(code)
                LogX.d(TAG, "数数埋点：code = $code")
            }
        } catch (t: Throwable) {
            // 上报异常兜底，确保 SDK 问题不影响应用正常运行
            LogX.e(TAG, "数数埋点异常: code = $code, ${t.message}")
        }
    }

    /**
     * 单一拓展标签
     */
    fun Pair<String, String>.toEvent(): HashMap<String, String> {
        return hashMapOf(this)
    }

    /**
     * 设置公共事件属性
     *
     * 接入方在获取到用户信息后调用，传入自定义的公共属性 JSONObject。
     * 通过 [TDAnalytics.setSuperProperties] 注册后，每条 [track] 事件都会自动携带这些属性，
     * 无需在每次上报时手动传入
     *
     * @param superProperties 接入方构建的公共属性，如 user_id、channel、version 等
     */
    fun setCommonProperties(superProperties: JSONObject) {
        TDAnalytics.setSuperProperties(superProperties)
    }
}