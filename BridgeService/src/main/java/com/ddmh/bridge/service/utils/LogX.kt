package com.ddmh.bridge.service.utils

import android.util.Log
import com.ddmh.bridge.service.helper.AppHelper.isDebug

/**
 * @Description: 
 * @Author: 𝑳𝒆𝒆𝒔𝒊𝒏
 * @CreateDate: 2026/06/12 17:17
 * @Version: 1.0
 */
object LogX {
    private fun formatTag(tag: String): String = "LogX-->$tag"

    private fun getCallerInfo(): String {
        val stackTrace = Throwable().stackTrace
        val element = stackTrace[2] // [0] getCallerInfo, [1] LogX.xx, [2] 调用者
        val className = element.className.substringAfterLast('.')
        val methodName = element.methodName
        val fileName = element.fileName
        val lineNumber = element.lineNumber
        val threadName = Thread.currentThread().name // 获取线程名
        return " <线程=$threadName | 位置 --> $className.$methodName($fileName:$lineNumber)>"
    }

    // ====== 带 tag 的 ======
    fun d(tag: String, msg: String) {
        if (isDebug()) Log.d(formatTag(tag), "$msg ${getCallerInfo()}")
    }

    fun e(tag: String, msg: String) {
        if (isDebug()) Log.e(formatTag(tag), "$msg ${getCallerInfo()}")
    }

    fun i(tag: String, msg: String) {
        if (isDebug()) Log.i(formatTag(tag), "$msg ${getCallerInfo()}")
    }

    fun w(tag: String, msg: String) {
        if (isDebug()) Log.w(formatTag(tag), "$msg ${getCallerInfo()}")
    }

    fun v(tag: String, msg: String) {
        if (isDebug()) Log.v(formatTag(tag), "$msg ${getCallerInfo()}")
    }

    // ====== 不带 tag 的 ======
    fun d(msg: String) {
        if (isDebug()) Log.d("LogX-->", "$msg ${getCallerInfo()}")
    }

    fun e(msg: String) {
        if (isDebug()) Log.e("LogX-->", "$msg ${getCallerInfo()}")
    }

    fun i(msg: String) {
        if (isDebug()) Log.i("LogX-->", "$msg ${getCallerInfo()}")
    }

    fun w(msg: String) {
        if (isDebug()) Log.w("LogX-->", "$msg ${getCallerInfo()}")
    }

    fun v(msg: String) {
        if (isDebug()) Log.v("LogX-->", "$msg ${getCallerInfo()}")
    }
}