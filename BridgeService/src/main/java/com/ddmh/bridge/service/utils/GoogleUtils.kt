package com.ddmh.bridge.service.utils

import com.ddmh.bridge.service.ext.context
import com.ddmh.bridge.service.helper.Constant
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


object GoogleUtils {

    /**
     * 获取 Google Advertising ID
     * 切 IO 线程：AdvertisingIdClient.getAdvertisingIdInfo 是阻塞调用，不能在主线程执行
     */
    suspend fun fetchGoogleAID(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val adInfo = AdvertisingIdClient.getAdvertisingIdInfo(context)
                if (adInfo.isLimitAdTrackingEnabled) {
                    LogX.w(Constant.TAG, "用户限制了广告追踪或删除了广告 ID")
                }
                val aid = adInfo.id
                LogX.d(Constant.TAG, "获取到的 AID: $aid")
                aid
            } catch (e: Exception) {
                LogX.e(Constant.TAG, "获取 AID 失败: ${e.message}")
                null
            }
        }
    }
}
