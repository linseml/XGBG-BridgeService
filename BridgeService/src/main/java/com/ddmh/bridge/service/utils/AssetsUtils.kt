package com.ddmh.bridge.service.utils

import android.content.Context

/**
 * @Description: 
 * @Author: 𝑳𝒆𝒆𝒔𝒊𝒏
 * @CreateDate: 2026/06/16 16:34
 * @Version: 1.0
 */
object AssetsUtils {

    /**
     * 从assets 目录中读取 JSON 文件并返回字符串
     * @param context Android 上下文，用于访问 AssetManager
     * @param fileName 文件在 assets 目录下的路径（例如 "data/config.json"）
     * @return 文件的文本内容；如果读取失败（如文件不存在）则返回 null
     */
    fun readJson(context: Context, fileName: String): String? {
        return try {
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}