package com.ddmh.bridge.service.ext

import com.ddmh.bridge.service.utils.GsonHolder
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

/**
 * 获取泛型的具体类型 [Type]
 * 利用 reified 特性在运行时保留泛型信息，解决 Java 泛型擦除问题
 * @param T 目标类型
 * @return 对应的 Type 实例
 */
inline fun <reified T> typeOf(): Type = object : TypeToken<T>() {}.type

/**
 * 将任意对象转换为 JSON 字符串
 *
 * @receiver 待转换的对象（支持可空类型）
 * @return JSON 字符串。若对象为 null 则返回字符串 "null"；若转换异常则返回空 JSON 对象 "{}"
 * * 示例：
 * ```
 * val list = listOf(1, 2, 3)
 * val json = list.toJson() // "[1,2,3]"
 * ```
 */
inline fun <reified T> T?.toJson(): String {
    return try {
        this?.let { GsonHolder.instance.toJson(it, typeOf<T>()) } ?: "null"
    } catch (e: Exception) {
        e.printStackTrace()
        "{}"
    }
}

/**
 * 将 JSON 字符串解析为指定的对象 [T]
 *
 * @receiver JSON 格式字符串
 * @return 转换后的对象 [T]。若字符串为空或解析失败，则返回 null
 * * 示例：
 * ```
 * val user = jsonStr.toBean<User>()
 * val users = jsonStr.toBean<List<User>>()
 * ```
 */
inline fun <reified T> String?.toBean(): T? {
    if (this.isNullOrBlank()) return null
    return try {
        GsonHolder.instance.fromJson<T>(this, typeOf<T>())
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}