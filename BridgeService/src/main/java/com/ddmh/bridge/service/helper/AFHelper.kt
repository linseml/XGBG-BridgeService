package com.ddmh.bridge.service.helper

import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import com.appsflyer.api.PurchaseClient
import com.appsflyer.api.Store
import com.appsflyer.attribution.AppsFlyerRequestListener
import com.appsflyer.internal.models.InAppPurchaseValidationResult
import com.appsflyer.internal.models.SubscriptionValidationResult
import com.ddmh.bridge.service.ext.context
import com.ddmh.bridge.service.ext.toJson
import com.ddmh.bridge.service.helper.Constant.TAG
import com.ddmh.bridge.service.utils.LogX
import com.google.gson.Gson

/**
 * AppsFlyer SDK 辅助工具类
 *
 * 职责：
 * 1. 初始化 AppsFlyer SDK 并监听归因回调（install / reinstall / deep link「不含」）
 * 2. 启动 SDK 上报，附带额外自定义参数
 * 3. 通过 PurchaseClient 监听 Google Play Billing 的内购 & 订阅交易，
 *    自动捕获购买回调并上报到 AppsFlyer 服务端进行验证
 *
 * @author 𝑳𝒆𝒆𝒔𝒊𝒏
 * @since 2026/06/16
 */
object AFHelper {

    /**
     * AppsFlyer Dev Key，由 [initSDK] 赃存，后续 [startAppsFlyer] 使用
     */
    private var key: String? = null

    /**
     * 初始化 AppsFlyer SDK
     *
     * 调用时机：Application.onCreate() 或_bridgeService 初始化阶段，必须先调用此方法再调用 [startAppsFlyer]。
     *
     * @param key AppsFlyer 开发者密钥（Dev Key），从 AppsFlyer 后台获取
     */
    fun initSDK(key: String) {
        this.key = key
        val startTime = System.currentTimeMillis()
        LogX.d(TAG, "AppsFlyer SDK 开始初始化, key=${key.take(6)}***")
        AppsFlyerLib.getInstance().setDebugLog(AppHelper.isDebug())
        AppsFlyerLib.getInstance().init(key, object : AppsFlyerConversionListener {

            /**
             * 归因数据获取成功回调
             *
             * 触发时机：每次首次安装或覆盖安装后的第一次启动都会触发。
             * conversionData 包含归因信息（媒体来源、广告系列、af_channel 等），
             * 可用于首次打开归因（First-touch Attribution）。
             */
            override fun onConversionDataSuccess(conversionData: Map<String, Any>) {
                val loadDuration = System.currentTimeMillis() - startTime
                LogX.d(
                    TAG,
                    "归因数据获取成功, 耗时=${loadDuration}ms, data=${conversionData.toJson()}"
                )
            }

            /**
             * 归因数据获取失败回调
             *
             * 触发时机：网络不可用、SDK 未完成初始化、AppsFlyer 服务端异常等。
             * errorMessage 描述了具体失败原因。
             */
            override fun onConversionDataFail(errorMessage: String?) {
                LogX.e(TAG, "归因数据获取失败, errorMessage=$errorMessage")
            }

            /**
             * Deep Link 打开应用时的归因回调
             *
             * 触发时机：用户通过 Deep Link（如 OneLink 短链）打开应用时，
             * attribution 数据包含 Deep Link 的参数映射。
             */
            override fun onAppOpenAttribution(attributionData: Map<String, String>?) {
                LogX.d(TAG, "Deep Link 归因回调, data=${Gson().toJson(attributionData)}")
            }

            /**
             * Deep Link 归因失败回调
             *
             * 触发时机：Deep Link 解析失败（如链接格式不合法、网络异常等）。
             */
            override fun onAttributionFailure(errorMessage: String?) {
                LogX.e(TAG, "Deep Link 归因失败, errorMessage=$errorMessage")
            }
        }, context)
        LogX.d(TAG, "AppsFlyer SDK 初始化完成")
    }

    /**
     * 启动 AppsFlyer SDK 上报，附带额外自定义参数
     *
     * 调用时机：必须在 [initSDK] 之后调用，否则 key 为空将导致上报失败。
     * params 中的自定义数据将随上报事件一起发送到 AppsFlyer 服务端，
     * 可用于后续的数据分析和用户分群。
     *
     * @param params 额外自定义参数，将作为 setAdditionalData 附加到上报请求中
     */
    fun startAppsFlyer(params: Map<String, Any>) {
        try {
            LogX.d(TAG, "AppsFlyer 开始启动上报, additionalData=${params.toJson()}")
            // 设置额外自定义数据，随上报事件一起发送
            AppsFlyerLib.getInstance().setAdditionalData(params)
            // 启动 SDK，注册请求结果回调
            AppsFlyerLib.getInstance().start(
                context,
                key,
                object : AppsFlyerRequestListener {

                    /** SDK 上报启动成功 */
                    override fun onSuccess() {
                        LogX.d(TAG, "AppsFlyer 启动上报成功")
                    }

                    /** SDK 上报启动失败，code 为错误码，msg 为错误描述 */
                    override fun onError(code: Int, msg: String) {
                        LogX.e(TAG, "AppsFlyer 启动上报失败, code=$code, msg=$msg")
                    }
                })
        } catch (t: Throwable) {
            // 启动异常兜底，确保 SDK 问题不影响应用正常运行
            LogX.e(TAG, "AppsFlyer 启动上报异常, message=${t.message}")
        }
    }

    /**
     * 启动 PurchaseClient 监听 Google Play Billing 的内购 & 订阅交易
     *
     * PurchaseClient 将自动捕获 BillingClient 的购买回调并上报到 AppsFlyer，
     * 同时在服务端进行购买验证（订阅验证 & 一次性内购验证）。
     *
     * 配置说明：
     * - logSubscriptions(true)：自动记录订阅购买并上报
     * - autoLogInApps(true)：自动记录一次性内购并上报
     * - setSandbox：Debug 模式使用沙盒验证环境，Release 使用正式环境
     */
    fun startAFObservingTransactions() {
        try {
            LogX.d(TAG, "AppsFlyer PurchaseClient 开始构建并监听交易")
            val afPurchaseClient = PurchaseClient.Builder(context, Store.GOOGLE)
                .logSubscriptions(true) // 自动记录订阅购买并上报
                .autoLogInApps(true) // 自动记录一次性内购并上报
                .setSandbox(AppHelper.isDebug()) // Debug 模式下使用沙盒验证环境，Release 使用正式环境
                .setSubscriptionValidationResultListener(mSubscriptionPurchaseListener) // 订阅验证结果监听
                .setInAppValidationResultListener(mInAppPurchaseListener) // 内购验证结果监听
                .build()
            // 开始监听 Google Play Billing 的购买交易，
            // PurchaseClient 将自动捕获 BillingClient 的购买回调并上报到 AppsFlyer
            afPurchaseClient.startObservingTransactions()
            LogX.d(TAG, "AppsFlyer PurchaseClient 交易监听已启动")
        } catch (t: Throwable) {
            // 监听启动异常兜底，确保 PurchaseClient 问题不影响应用正常运行
            LogX.e(TAG, "AppsFlyer PurchaseClient 启动监听异常, message=${t.message}")
        }
    }

    /**
     * 订阅购买验证结果监听器
     *
     * 当 PurchaseClient 向 AppsFlyer 服务端发起订阅验证请求后，
     * 服务端返回验证结果时触发此回调。
     * - 验证成功：可获取 [SubscriptionValidationResult.subscriptionPurchase] 订阅详情
     * - 验证失败：可获取 [SubscriptionValidationResult.failureData] 失败详情
     * - 请求失败：触发 [onFailure]，如网络异常、服务端不可达等
     */
    private val mSubscriptionPurchaseListener = object :
        PurchaseClient.SubscriptionPurchaseValidationResultListener {

        /**
         * 订阅验证结果回调
         *
         * @param result 订阅验证结果映射，key 为订阅 ID，value 为验证结果
         */
        override fun onResponse(result: Map<String, SubscriptionValidationResult>?) {
            LogX.d(TAG, "订阅验证结果回调, data=${result.toJson()}")
            result?.forEach { (k: String, v: SubscriptionValidationResult) ->
                if (v.success) {
                    // 订阅验证成功，获取订阅购买详情（含订阅ID、价格、状态等）
                    val subscriptionPurchase = v.subscriptionPurchase
                    LogX.d(TAG, "订阅验证成功, subscriptionId=$k, purchase=$subscriptionPurchase")
                } else {
                    // 订阅验证失败，获取失败详情（含失败原因、错误码等）
                    val failureData = v.failureData
                    LogX.e(TAG, "订阅验证失败, subscriptionId=$k, failure=$failureData")
                }
            }
        }

        /**
         * 订阅验证请求失败回调
         *
         * 触发时机：验证请求本身失败（如网络异常、AppsFlyer 服务端不可达等）。
         *
         * @param result 失败描述信息
         * @param error 异常对象
         */
        override fun onFailure(result: String, error: Throwable?) {
            LogX.e(TAG, "订阅验证请求失败, result=$result, error=${error?.message}")
        }
    }

    /**
     * 一次性内购验证结果监听器
     *
     * 当 PurchaseClient 向 AppsFlyer 服务端发起内购验证请求后，
     * 服务端返回验证结果时触发此回调。
     * - 验证成功：可获取 [InAppPurchaseValidationResult.productPurchase] 内购详情
     * - 验证失败：可获取 [InAppPurchaseValidationResult.failureData] 失败详情
     * - 请求失败：触发 [onFailure]，如网络异常、服务端不可达等
     */
    private val mInAppPurchaseListener = object :
        PurchaseClient.InAppPurchaseValidationResultListener {

        /**
         * 内购验证结果回调
         *
         * @param result 内购验证结果映射，key 为商品 ID，value 为验证结果
         */
        override fun onResponse(result: Map<String, InAppPurchaseValidationResult>?) {
            LogX.d(TAG, "内购验证结果回调, data=${result.toJson()}")
            result?.forEach { (k: String, v: InAppPurchaseValidationResult) ->
                if (v.success) {
                    // 内购验证成功，获取购买详情（含商品ID、价格、购买状态等）
                    val productPurchase = v.productPurchase
                    LogX.d(TAG, "内购验证成功, productId=$k, purchase=$productPurchase")
                } else {
                    // 内购验证失败，获取失败详情（含失败原因、错误码等）
                    val failureData = v.failureData
                    LogX.e(TAG, "内购验证失败, productId=$k, failure=$failureData")
                }
            }
        }

        /**
         * 内购验证请求失败回调
         *
         * 触发时机：验证请求本身失败（如网络异常、AppsFlyer 服务端不可达等）。
         *
         * @param result 失败描述信息
         * @param error 异常对象
         */
        override fun onFailure(result: String, error: Throwable?) {
            LogX.e(TAG, "内购验证请求失败, result=$result, error=${error?.message}")
        }
    }
}
