package com.ddmh.bridge.service.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder

/**
 * @Description: 
 * @Author: 𝑳𝒆𝒆𝒔𝒊𝒏
 * @CreateDate: 2026/06/12 17:21
 * @Version: 1.0
 */
object GsonHolder {
    val instance: Gson = GsonBuilder()
        //是否序列化 null 字段
        .serializeNulls()
        // 避免特殊字符被转义成 Unicode
        .disableHtmlEscaping()
        .create()
}