package com.ddmh.bridge.service.interfaces

/**
 * 弹窗生命周期回调接口
 *
 * 所有方法均有默认空实现，按需覆写即可
 */
interface OnPopupLifecycleListener {
    fun onShow() {}
    fun beforeShow() {}
    fun dismiss() {}
    fun beforeDismiss() {}
}
