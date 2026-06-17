package com.ddmh.bridge.service.helper

import android.app.Application

/**
 * 全局 Application 上下文辅助工具类
 *
 * 职责：
 * 1. 持有全局 Application 实例，供库内其他 Helper/Utils/Ext 在非 Activity/Fragment 场景下获取 Context
 * 2. 持有 Debug/Release 环境标记，供库内其他组件据此决定 SDK 日志、沙盒环境、调试行为等
 *
 * ⚠️⚠️⚠️ 【接入方必读】使用本库前必须完成以下步骤：
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 1. 在 Application.onCreate() 中调用 ServiceHelper.init(application, isDebug)
 * 2. 必须在所有其他 Helper 初始化之前调用，因为以下组件依赖 ServiceHelper：
 *    - AFHelper.initSDK() → 需要 ServiceHelper.getApplication() 和 ServiceHelper.isDebug()
 *    - AEHelper.initSDK() → 需要 ServiceHelper.getApplication() 和 ServiceHelper.isDebug()
 *    - FirebaseHelper.initSDK() → 需要 ServiceHelper.isDebug()
 *    - SmartRefreshHelper.initSmartRefreshLanguage() → 需要 res（依赖 ServiceHelper.getApplication()）
 *    - GooglePay.init() → 需要 ServiceHelper.getApplication()
 *    - LogX → 需要 ServiceHelper.isDebug() 决定是否输出日志
 *    - ResourceExt（context / res）→ 需要 ServiceHelper.getApplication()
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 *
 * @author 𝑳𝒆𝒆𝒔𝒊𝒏
 * @since 2026/06/12
 */
object ServiceHelper {

    /**
     * 全局 Application 实例
     *
     * 通过 [init] 赃存，后续由 [getApplication] 提供给库内所有组件使用。
     * 使用 lateinit var 而非 lazy，因为初始化时机由接入方在 Application.onCreate() 中控制，
     * 必须在首次访问前完成赋值，否则将抛出 UninitializedPropertyAccessException。
     *
     * ⚠️⚠️⚠️ 【接入方必做】必须在 Application.onCreate() 中调用 init() 赋值，
     * 否则所有依赖 [getApplication] 的组件将因 lateinit 未初始化而 crash。
     */
    private lateinit var app: Application

    /**
     * Debug / Release 环境标记
     *
     * 通过 [init] 赃存，影响库内多个组件的行为：
     * - AFHelper：控制 AppsFlyer SDK 调试日志开关与 PurchaseClient 沙盒验证环境
     * - AEHelper：控制数数 SDK 日志开关
     * - FirebaseHelper：控制 Firebase Analytics 调试日志与 Debug 上报模式
     * - LogX：控制所有 LogX 日志是否输出（Debug 输出，Release 静默）
     * - GooglePay：未直接使用，但可通过接入方自行扩展判断
     *
     * ⚠️⚠️⚠️ 【接入方注意】传入 true 时所有 SDK 将开启调试模式，
     * 产生大量日志输出且可能使用沙盒验证环境，务必在 Release 包中传入 false。
     */
    private var isDebug = false

    /**
     * 初始化全局 Application 上下文和环境标记
     *
     * 调用时机：Application.onCreate()，必须在所有其他 Helper 初始化之前调用。
     * 本方法是整个库的初始化入口，后续所有 Helper/Utils/Ext 的正常运行
     * 都依赖 [app] 和 [isDebug] 已被正确赋值。
     *
     * ⚠️⚠️⚠️ 【接入方必做】初始化顺序要求：
     * ```
     * // Application.onCreate() 中
     * ServiceHelper.init(this, BuildConfig.DEBUG)  // ← 必须最先调用
     * AFHelper.initSDK("af_dev_key")
     * AEHelper.initSDK("ta_app_id", "ta_server_url")
     * FirebaseHelper.initSDK()
     * SmartRefreshHelper.initSmartRefreshLanguage()
     * GooglePay.init()
     * ```
     *
     * @param application Application 实例，通常为接入方自定义的 Application 子类
     * @param isDebug     是否为 Debug 环境，建议传入 BuildConfig.DEBUG
     */
    fun init(application: Application, isDebug: Boolean) {
        this.app = application
        this.isDebug = isDebug
    }

    /**
     * 获取全局 Application 实例
     *
     * 供库内所有组件在非 Activity/Fragment 场景下获取 Context，
     * 如 SDK 初始化、资源访问、弹窗创建等。
     *
     * ⚠️⚠️⚠️ 【接入方注意】若未在 Application.onCreate() 中调用 init()，
     * 访问此方法将抛出 Kotlin UninitializedPropertyAccessException，导致应用 crash。
     *
     * @return 全局 Application 实例
     */
    fun getApplication() = app

    /**
     * 是否为 Debug 环境
     *
     * 供库内所有组件据此决定调试行为：
     * - true：开启 SDK 调试日志、使用沙盒验证环境、输出 LogX 日志
     * - false：静默运行、使用正式验证环境、关闭 LogX 日志
     *
     * ⚠️⚠️⚠️ 【接入方注意】Release 包务必传入 false，
     * 否则 SDK 将持续输出调试日志并使用沙盒验证环境，影响生产环境数据准确性。
     *
     * @return true 为 Debug 环境，false 为 Release 环境
     */
    fun isDebug() = isDebug
}
