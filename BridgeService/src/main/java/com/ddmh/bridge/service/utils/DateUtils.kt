package com.ddmh.bridge.service.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日期与时间格式化工具类。
 * 提供常用日期字符串获取、时间戳解析、日期格式转换等功能。
 */
object DateUtils {

    /**
     * 获取当前日期+时间字符串，格式为 `yyyy-MM-dd HH:mm:ss`。
     * 例如：`2026-06-16 14:30:00`
     */
    val nowDateTime: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    /**
     * 获取当前日期字符串，格式为 `yyyy-MM-dd`。
     * 例如：`2026-06-16`
     */
    val nowDate: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    /**
     * 获取当前时间字符串，格式为 `HH:mm:ss`。
     * 例如：`14:30:00`
     */
    val nowTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())


    /**
     * 将时间戳（毫秒）转换为指定格式的日期字符串。
     *
     * @param timeStamp 毫秒级时间戳，如 `System.currentTimeMillis()` 的返回值
     * @param pattern   输出日期格式，默认 `yyyy/MM/dd`；可根据需要传入其他格式，如 `yyyy-MM-dd HH:mm:ss`
     * @return 格式化后的日期字符串
     */
    fun parseStamp(timeStamp: Long, pattern: String = "yyyy/MM/dd"): String {
        val date = Date(timeStamp)
        val format = SimpleDateFormat(pattern, Locale.getDefault())
        return format.format(date)
    }


    /**
     * 将字符串从一种日期格式转换为另一种日期格式。
     * 解析失败或字符串为空白时，原样返回不做转换。
     *
     * @param inputPattern  输入字符串的日期格式，默认 `MM-dd-yyyy`
     * @param outputPattern 输出字符串的目标日期格式，默认 `MM/dd/yyyy`
     * @return 转换后的日期字符串；解析失败时返回原始字符串
     *
     * 示例：
     * ```
     * "06-16-2026".parseDate()                     // → "06/16/2026"
     * "06-16-2026".parseDate("MM-dd-yyyy", "yyyy年MM月dd日") // → "2026年06月16日"
     * "".parseDate()                               // → ""（空白原样返回）
     * ```
     */
    fun String.parseDate(
        inputPattern: String = "MM-dd-yyyy",
        outputPattern: String = "MM/dd/yyyy"
    ): String {
        if (this.isBlank()) return this
        return try {
            val inputFormat = SimpleDateFormat(inputPattern, Locale.getDefault())
            val date = inputFormat.parse(this)

            val outputFormat = SimpleDateFormat(outputPattern, Locale.getDefault())
            if (date != null) {
                outputFormat.format(date)
            } else {
                this
            }
        } catch (e: Exception) {
            println("parseDate error: ${e.message}")
            this
        }
    }

}
