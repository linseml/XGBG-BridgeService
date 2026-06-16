package com.ddmh.bridge.service.interfaces

import com.android.billingclient.api.Purchase

/**
 * Google Play 支付结果回调接口
 *
 * 接入方实现此接口来接收支付流程的最终结果。
 * 三种回调覆盖了所有可能的支付终端状态：
 * - [onPurchaseSuccess]：购买成功，可在此消耗或确认购买
 * - [onPurchaseError]：购买失败，code 为 BillingResponseCode，message 为错误描述
 * - [onPurchaseCancelled]：用户主动取消支付
 *
 * 使用示例：
 * ```
 * GooglePay.launchPay(activity, "sku_gold_100", ProductType.INAPP, object : OnPayResultListener {
 *     override fun onPurchaseSuccess(purchase: Purchase) {
 *         GooglePay.consumePurchase(purchase)  // 消耗型商品需消耗
 *         // 处理发货逻辑
 *     }
 *     override fun onPurchaseError(code: Int, message: String) {
 *         // 处理失败提示
 *     }
 *     override fun onPurchaseCancelled() {
 *         // 用户取消，可选择提示或静默处理
 *     }
 * })
 * ```
 *
 * @author 𝑳𝒆𝒆𝒔𝒊𝒏
 * @since 2026/06/16
 */
interface OnPayResultListener {

    /**
     * 购买成功回调
     *
     * @param purchase Google Play 购买对象，包含订单ID、购买Token、商品ID等信息。
     *                 消耗型商品需调用 [GooglePay.consumePurchase] 消耗后才能再次购买；
     *                 非消耗型商品和订阅需调用 [GooglePay.acknowledgePurchase] 确认，
     *                 否则 3 天后 Google 将自动退款。
     */
    fun onPurchaseSuccess(purchase: Purchase)

    /**
     * 购买失败回调
     *
     * @param code    BillingResponseCode 错误码，常见值：
     *                - BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED：已拥有此商品
     *                - BillingClient.BillingResponseCode.SERVICE_DISCONNECTED：服务断开
     *                - BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE：服务不可用
     *                等
     * @param message 错误描述信息
     */
    fun onPurchaseError(code: Int, message: String)

    /**
     * 用户主动取消支付回调
     *
     * 触发时机：用户在 Google Play 支付界面点击返回或关闭。
     * 此回调与 [onPurchaseError] 互斥：用户取消时只触发此回调，
     * 不会同时触发 onPurchaseError。
     */
    fun onPurchaseCancelled()
}
