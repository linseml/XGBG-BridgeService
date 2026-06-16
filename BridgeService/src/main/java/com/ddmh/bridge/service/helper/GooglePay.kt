package com.ddmh.bridge.service.helper

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.AcknowledgePurchaseResponseListener
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ConsumeResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.ddmh.bridge.service.ext.context
import com.ddmh.bridge.service.helper.Constant.TAG
import com.ddmh.bridge.service.interfaces.OnPayResultListener
import com.ddmh.bridge.service.utils.LogX
import java.util.concurrent.atomic.AtomicReference

/**
 * Google Play Billing 支付辅助类
 *
 * 职责：
 * 1. 初始化 BillingClient 并管理连接状态（自动重连）
 * 2. 查询商品详情（内购 & 订阅）
 * 3. 拉起 Google Play 支付界面
 * 4. 消耗型商品消耗（consumeAsync）和非消耗型/订阅确认（acknowledgeAsync）
 * 5. 查询已购买商品 & 恢复订阅购买
 *
 * 使用流程：
 * ```
 * // 1. 初始化（Application.onCreate 中）
 * GooglePay.init()
 *
 * // 2. 查询商品详情（可选，用于展示价格）
 * GooglePay.queryProductDetails(listOf("sku_gold_100"), ProductType.INAPP) { detailsList ->
 *     // detailsList 包含商品名称、价格等信息
 * }
 *
 * // 3. 拉起支付
 * GooglePay.launchPay(activity, "sku_gold_100", ProductType.INAPP, object : OnPayResultListener {
 *     override fun onPurchaseSuccess(purchase: Purchase) {
 *         GooglePay.consumePurchase(purchase)  // 消耗型商品必须消耗
 *     }
 *     override fun onPurchaseError(code: Int, message: String) { ... }
 *     override fun onPurchaseCancelled() { ... }
 * })
 *
 * // 4. 恢复订阅
 * GooglePay.restorePurchases(object : OnPayResultListener { ... })
 * ```
 *
 * @author 𝑳𝒆𝒆𝒔𝒊𝒏
 * @since 2026/06/16
 */
object GooglePay {

    /**
     * 商品类型常量，与 BillingClient.ProductType 对应
     *
     * Billing Library 8.x 中 SkuType 已废弃，统一使用 ProductType：
     * - [IAP]：一次性内购商品（消耗型 & 非消耗型）
     * - [SUBS]：订阅商品
     */
    object ProductType {
        /** 一次性内购商品（消耗型 & 非消耗型） */
        const val IAP = BillingClient.ProductType.INAPP

        /** 订阅商品 */
        const val SUBS = BillingClient.ProductType.SUBS
    }

    /** BillingClient 实例，在 [init] 中创建 */
    private var billingClient: BillingClient? = null

    /** BillingClient 是否已连接到 Google Play 服务 */
    private var isBillingConnected = false

    /** 当前活跃的支付结果回调，在拉起支付时设置，在购买结果回调时消费 */
    private var currentListener: OnPayResultListener? = null

    /**
     * 初始化 BillingClient 并建立连接
     *
     * 调用时机：Application.onCreate() 或库初始化阶段。
     * BillingClient 会自动尝试连接 Google Play 服务，
     * 连接断开时也会自动重连（最多重试若干次）。
     * 需在调用 [launchPay] 前先调用此方法，确保 BillingClient 已就绪。
     *
     * 注意：Billing Library 8.x 中 enablePendingPurchases() 需传入
     * PendingPurchasesParams 参数以启用一次性商品和预付费计划的 pending 购买。
     */
    fun init() {
        LogX.d(TAG, "GooglePay BillingClient 开始初始化")

        if (billingClient != null) {
            LogX.d(TAG, "BillingClient 已存在，跳过重复初始化")
            return
        }

        // Billing Library 8.x：enablePendingPurchases 需传入 PendingPurchasesParams
        val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .enablePrepaidPlans()
            .build()

        billingClient = BillingClient.newBuilder(context)
            .enablePendingPurchases(pendingPurchasesParams)
            .setListener(purchasesUpdatedListener)
            .build()

        startBillingConnection()
    }

    /**
     * 建立 BillingClient 与 Google Play 服务的连接
     *
     * 连接成功后 [isBillingConnected] 设为 true。
     * 连接失败时会自动重试（最多 3 次，每次间隔递增）。
     */
    private fun startBillingConnection() {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    isBillingConnected = true
                    retryCount = 0 // 连接成功，重置重试计数，为下次断线重连预留完整次数
                    LogX.d(TAG, "BillingClient 连接成功")
                } else {
                    isBillingConnected = false
                    LogX.e(
                        TAG,
                        "BillingClient 连接失败, code=${billingResult.responseCode}, msg=${billingResult.debugMessage}"
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                isBillingConnected = false
                LogX.d(TAG, "BillingClient 连接断开，将尝试重连")
                retryBillingConnection()
            }
        })
    }

    /**
     * 重试连接 BillingClient
     *
     * 最大重试次数 3，每次间隔递增（2s → 4s → 8s）。
     * 超过最大次数后停止重试，等待接入方再次调用 [init] 或 [launchPay] 时触发。
     */
    private var retryCount = 0
    private const val MAX_RETRY_COUNT = 3

    private fun retryBillingConnection() {
        if (retryCount >= MAX_RETRY_COUNT) {
            LogX.e(TAG, "BillingClient 重连已达最大次数($MAX_RETRY_COUNT)，停止重试")
            return
        }
        retryCount++
        val delayMs = 2000L * retryCount // 递增间隔：2s → 4s → 8s
        LogX.d(TAG, "BillingClient 第${retryCount}次重连，延迟${delayMs}ms")

        Thread {
            try {
                Thread.sleep(delayMs)
                startBillingConnection()
            } catch (e: InterruptedException) {
                LogX.e(TAG, "BillingClient 重连线程被中断")
            }
        }.start()
    }

    /**
     * 查询商品详情
     *
     * 用于在支付前获取商品的价格、名称、描述等信息，供接入方展示给用户。
     * 必须在 BillingClient 连接成功后调用，否则查询会失败。
     *
     * @param productIdList 商品 ID 列表，对应 Google Play Console 中配置的 product ID
     * @param productType   商品类型：[ProductType.IAP]（内购）或 [ProductType.SUBS]（订阅）
     * @param callback      查询结果回调，成功时返回 ProductDetails 列表，失败时返回 null
     */
    fun queryProductDetails(
        productIdList: List<String>,
        productType: String,
        callback: ((List<ProductDetails>?) -> Unit)? = null
    ) {
        if (!ensureBillingConnected()) {
            LogX.e(TAG, "查询商品详情失败：BillingClient 未连接")
            callback?.invoke(null)
            return
        }

        LogX.d(TAG, "查询商品详情, ids=$productIdList, type=$productType")

        val productList = productIdList.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(productType)
                .build()
        }

        val queryParams = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient?.queryProductDetailsAsync(queryParams, productDetailsResponseListener)
        // 保存回调，在 productDetailsResponseListener 中使用（AtomicReference 保证线程安全）
        queryProductDetailsCallback.set(callback)
    }

    /**
     * 拉起 Google Play 支付界面
     *
     * 核心支付方法。先查询商品详情，再构建 BillingFlowParams 拉起支付。
     * 支付结果通过 [OnPayResultListener] 回传给接入方。
     *
     * 注意：
     * - 订阅商品（[ProductType.SUBS]）需要 offerToken，
     *   本方法自动从 ProductDetails 中提取第一个可用 offer
     * - 内购商品（[ProductType.IAP]）无需 offerToken
     *
     * @param activity    发起支付的 Activity，Google Play 支付界面将在此 Activity 上弹出
     * @param productId   商品 ID，对应 Google Play Console 中配置的 product ID
     * @param productType 商品类型：[ProductType.IAP]（内购）或 [ProductType.SUBS]（订阅）
     * @param listener    支付结果回调，[OnPayResultListener] 的三种回调覆盖所有终端状态
     */
    fun launchPay(
        activity: Activity,
        productId: String,
        productType: String,
        listener: OnPayResultListener
    ) {
        LogX.d(TAG, "拉起支付, productId=$productId, productType=$productType")
        currentListener = listener

        if (!ensureBillingConnected()) {
            LogX.e(TAG, "拉起支付失败：BillingClient 未连接")
            listener.onPurchaseError(
                BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
                "BillingClient is not connected"
            )
            return
        }

        // 先查询商品详情，再拉起支付
        queryProductDetails(listOf(productId), productType) { productDetailsList ->
            onProductDetailsForLaunch(
                activity,
                productType,
                productId,
                listener,
                productDetailsList
            )
        }
    }

    /**
     * 商品详情查询完成后构建并拉起支付界面
     *
     * 从 [launchPay] 的回调中提取，避免 lambda 内使用 return。
     */
    private fun onProductDetailsForLaunch(
        activity: Activity,
        productType: String,
        productId: String,
        listener: OnPayResultListener,
        productDetailsList: List<ProductDetails>?
    ) {
        val productDetails = productDetailsList?.firstOrNull()
        if (productDetails == null) {
            LogX.e(TAG, "拉起支付失败：商品详情查询为空, productId=$productId")
            listener.onPurchaseError(
                BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
                "Product details not found for productId=$productId"
            )
            return
        }

        // 构建 BillingFlowParams
        // 订阅商品必须设置 offerToken；内购商品无需设置
        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)

        if (productType == ProductType.SUBS) {
            // 从 ProductDetails 中提取订阅 offerToken
            val offerToken = productDetails.subscriptionOfferDetails
                ?.firstOrNull()?.offerToken
            if (offerToken == null) {
                LogX.e(TAG, "拉起支付失败：订阅 offerToken 为空, productId=$productId")
                listener.onPurchaseError(
                    BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
                    "No subscription offer found for productId=$productId"
                )
                return
            }
            productDetailsParamsBuilder.setOfferToken(offerToken)
            LogX.d(TAG, "订阅 offerToken=$offerToken")
        }

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
            .build()

        val billingResult = billingClient?.launchBillingFlow(activity, billingFlowParams)
        if (billingResult != null && billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            LogX.e(
                TAG,
                "拉起支付界面失败, code=${billingResult.responseCode}, msg=${billingResult.debugMessage}"
            )
            listener.onPurchaseError(billingResult.responseCode, billingResult.debugMessage)
        } else {
            LogX.d(TAG, "支付界面已拉起")
        }
    }

    /**
     * 消耗型商品消耗
     *
     * 消耗型商品（如金币、道具）购买成功后必须调用此方法消耗，
     * 否则该商品无法再次购买。消耗成功后 Google Play 会将该商品标记为已消耗，
     * 用户可以再次购买同一商品。
     *
     * @param purchase 需要消耗的 Purchase 对象，来自 [OnPayResultListener.onPurchaseSuccess]
     */
    fun consumePurchase(purchase: Purchase) {
        LogX.d(TAG, "消耗购买, orderId=${purchase.orderId}, productId=${purchase.products}")

        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient?.consumeAsync(consumeParams, consumeResponseListener)
    }

    /**
     * 非消耗型商品或订阅确认
     *
     * 非消耗型商品（如解锁功能）和订阅购买成功后必须调用此方法确认，
     * 否则 Google Play 会在 3 天后自动退款。
     *
     * @param purchase 需要确认的 Purchase 对象，来自 [OnPayResultListener.onPurchaseSuccess]
     */
    fun acknowledgePurchase(purchase: Purchase) {
        LogX.d(TAG, "确认购买, orderId=${purchase.orderId}, productId=${purchase.products}")

        val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient?.acknowledgePurchase(acknowledgeParams, acknowledgeResponseListener)
    }

    /**
     * 查询已购买的商品
     *
     * 用于补单场景：用户购买成功但因网络等原因未收到回调时，
     * 可通过此方法查询 Google Play 服务端的购买记录来补发。
     *
     * @param productType 商品类型：[ProductType.IAP]（内购）或 [ProductType.SUBS]（订阅）
     * @param callback    查询结果回调，成功时返回 Purchase 列表，失败时返回 null
     */
    fun queryPurchases(
        productType: String,
        callback: ((List<Purchase>?) -> Unit)? = null
    ) {
        if (!ensureBillingConnected()) {
            LogX.e(TAG, "查询已购买商品失败：BillingClient 未连接")
            callback?.invoke(null)
            return
        }

        LogX.d(TAG, "查询已购买商品, type=$productType")

        val queryParams = QueryPurchasesParams.newBuilder()
            .setProductType(productType)
            .build()

        billingClient?.queryPurchasesAsync(queryParams, purchasesResponseListener)
        // 保存回调，在 purchasesResponseListener 中使用（AtomicReference 保证线程安全）
        queryPurchasesCallback.set(callback)
    }

    /** 暂存 queryProductDetails 的回调，使用 AtomicReference 保证线程安全 */
    private val queryProductDetailsCallback = AtomicReference<((List<ProductDetails>?) -> Unit)?>()

    /** 暂存 queryPurchases 的回调，使用 AtomicReference 保证线程安全 */
    private val queryPurchasesCallback = AtomicReference<((List<Purchase>?) -> Unit)?>()

    /**
     * 恢复订阅购买
     *
     * 用于用户在新设备上安装应用后恢复之前的订阅。
     * 查询所有有效订阅购买，逐个回调 [OnPayResultListener.onPurchaseSuccess]。
     *
     * @param listener 恢复结果回调，每个有效订阅都会触发一次 [OnPayResultListener.onPurchaseSuccess]
     */
    fun restorePurchases(listener: OnPayResultListener?) {
        LogX.d(TAG, "恢复订阅购买")

        queryPurchases(ProductType.SUBS) { purchases ->
            if (purchases.isNullOrEmpty()) {
                LogX.d(TAG, "恢复订阅购买：无有效订阅")
            } else {
                for (purchase in purchases) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        LogX.d(TAG, "恢复订阅购买成功, productId=${purchase.products}")
                        listener?.onPurchaseSuccess(purchase)
                    }
                }
            }
        }
    }

    // ============== 内部监听器 ==============

    /**
     * Google Play 购买更新监听器
     *
     * 所有支付结果（包括 [launchPay] 拉起支付和恢复购买）都通过此回调接收。
     * 根据响应码分发到 [currentListener] 的对应回调方法。
     *
     * 注意：Billing Library 8.x 中 PurchasesUpdatedListener 的 SAM lambda 转换已废弃，
     * 必须使用显式 object 实现。
     */
    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        LogX.d(
            TAG,
            "购买更新回调, code=${billingResult.responseCode}, msg=${billingResult.debugMessage}"
        )

        val listener = currentListener

        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                // 购买成功
                val purchase = purchases?.firstOrNull()
                if (purchase != null) {
                    LogX.d(
                        TAG,
                        "购买成功, orderId=${purchase.orderId}, productId=${purchase.products}"
                    )
                    listener?.onPurchaseSuccess(purchase)
                } else {
                    LogX.e(TAG, "购买成功但 purchases 为空")
                    listener?.onPurchaseError(
                        billingResult.responseCode,
                        "Purchase list is empty"
                    )
                }
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                // 用户主动取消
                LogX.d(TAG, "用户取消支付")
                listener?.onPurchaseCancelled()
            }

            else -> {
                // 其他错误
                LogX.e(
                    TAG,
                    "购买失败, code=${billingResult.responseCode}, msg=${billingResult.debugMessage}"
                )
                listener?.onPurchaseError(billingResult.responseCode, billingResult.debugMessage)
            }
        }

        // 回调消费后清空 listener，避免重复回调
        currentListener = null
    }

    /**
     * 消耗购买结果监听器
     *
     * 注意：Billing Library 8.x 中 ConsumeResponseListener 的 SAM lambda 转换已废弃，
     * 必须使用显式 object 实现。
     */
    private val consumeResponseListener = ConsumeResponseListener { billingResult, purchaseToken ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            LogX.d(TAG, "消耗购买成功, purchaseToken=$purchaseToken")
        } else {
            LogX.e(
                TAG,
                "消耗购买失败, code=${billingResult.responseCode}, msg=${billingResult.debugMessage}"
            )
        }
    }

    /**
     * 确认购买结果监听器
     *
     * 注意：Billing Library 8.x 中 AcknowledgePurchaseResponseListener 的 SAM lambda 转换已废弃，
     * 必须使用显式 object 实现。
     */
    private val acknowledgeResponseListener = object : AcknowledgePurchaseResponseListener {
        override fun onAcknowledgePurchaseResponse(billingResult: BillingResult) {
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                LogX.d(TAG, "确认购买成功")
            } else {
                LogX.e(
                    TAG,
                    "确认购买失败, code=${billingResult.responseCode}, msg=${billingResult.debugMessage}"
                )
            }
        }
    }

    /**
     * 商品详情查询结果监听器
     *
     * Billing Library 8.x 中 queryProductDetailsAsync 使用
     * ProductDetailsResponseListener 替代旧的内联 lambda 回调。
     * 回调参数为 QueryProductDetailsResult（而非 List<ProductDetails>?），
     * 需通过 getProductDetailsList() 获取商品详情列表。
     */
    private val productDetailsResponseListener =
        ProductDetailsResponseListener { billingResult, result ->
            val callback = queryProductDetailsCallback.getAndSet(null)

            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val productDetailsList = result.productDetailsList
                LogX.d(TAG, "查询商品详情成功, count=${productDetailsList.size}")
                callback?.invoke(productDetailsList)
            } else {
                LogX.e(
                    TAG,
                    "查询商品详情失败, code=${billingResult.responseCode}, msg=${billingResult.debugMessage}"
                )
                callback?.invoke(null)
            }
        }

    /**
     *
     * Billing Library 8.x 使用 PurchasesResponseListener 替代了旧的
     * 直接 lambda 回调模式，明确区分了"购买更新"和"查询响应"两种场景。
     */
    private val purchasesResponseListener = PurchasesResponseListener { billingResult, purchases ->
        val callback = queryPurchasesCallback.getAndSet(null)

        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            LogX.d(TAG, "查询已购买商品成功, count=${purchases.size}")
            callback?.invoke(purchases)
        } else {
            LogX.e(
                TAG,
                "查询已购买商品失败, code=${billingResult.responseCode}, msg=${billingResult.debugMessage}"
            )
            callback?.invoke(null)
        }
    }

    // ============== 工具方法 ==============

    /**
     * 确保 BillingClient 已连接
     *
     * 若未连接则自动尝试重新连接。
     * @return true 已连接，false 未连接且重连失败
     */
    private fun ensureBillingConnected(): Boolean {
        if (isBillingConnected && billingClient?.isReady == true) {
            return true
        }
        LogX.d(TAG, "BillingClient 未连接，尝试重连")
        startBillingConnection()
        return false
    }

    /**
     * 断开 BillingClient 连接
     *
     * 调用时机：应用退出或不再需要支付功能时。
     * 断开后 [isBillingConnected] 设为 false，[billingClient] 设为 null。
     */
    fun disconnect() {
        LogX.d(TAG, "断开 BillingClient 连接")
        billingClient?.endConnection()
        billingClient = null
        isBillingConnected = false
        retryCount = 0
    }
}
