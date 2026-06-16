package com.ddmh.bridge.service.helper

import android.app.Application

/**
 * @Description: 
 * @Author: 𝑳𝒆𝒆𝒔𝒊𝒏
 * @CreateDate: 2026/06/12 17:16
 * @Version: 1.0
 */
object AppHelper {

    private lateinit var app: Application

    private var isDebug = false

    fun init(application: Application, isDebug: Boolean) {
        this.app = application
        this.isDebug = isDebug
    }

    /**
     * 获取全局应用
     */
    fun getApplication() = app

    /**
     * 是否为debug环境
     */
    fun isDebug() = isDebug
}