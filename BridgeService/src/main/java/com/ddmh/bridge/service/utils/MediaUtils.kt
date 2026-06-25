package com.ddmh.bridge.service.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileOutputStream

/**
 * 媒体选择工具类，封装 Android Photo Picker 的单选/多选图片功能，
 * 将选中图片的 Uri 复制到缓存目录并返回本地文件路径。
 * 兼容 Android 14+ 的 UI 层多选数量限制，低版本通过截断兜底。
 *
 * 使用方式：
 * ```kotlin
 * // 1. 在 Activity中注册 Launcher
 * private val singlePicker = singlePhotoPicker { path ->
 *     path?.let { showToast("选中: $it") }
 * }
 *
 * private val multiPicker = multiPhotoPicker(maxSelect = 3) { paths ->
 *     showToast("选中 ${paths.size} 张")
 * }
 *
 * // 2. 在需要触发选择时调用 imagePicker()
 * singlePicker.imagePicker()   // 单选
 * multiPicker.imagePicker()    // 多选（最多3张）
 * ```
 *
 */
object MediaUtils {

    /** 单张图片最大允许大小：20 MB，超过此大小视为异常文件，拒绝缓存以防范 OOM */
    private const val MAX_IMAGE_SIZE = 20 * 1024 * 1024L

    /**
     * 清理 cacheDir 下所有已累积的 picked_ 前缀缓存图片。
     *
     * 每次选图都会在 cacheDir 中生成 picked_ 前缀文件，
     * 为防止缓存无限累积，在选图回调入口调用此方法一次性清除旧文件。
     */
    private fun Context.clearPickedCache() {
        cacheDir.listFiles()?.filter { it.name.startsWith("picked_") }?.forEach { it.delete() }
    }

    /**
     * 查询 ContentProvider Uri 对应的文件大小。
     *
     * 通过 [OpenableColumns.SIZE] 列查询，若查询失败或大小不可获取则返回 null。
     *
     * @param uri  ContentProvider Uri
     * @return     文件大小（字节），无法获取时返回 null
     */
    private fun Context.queryFileSize(uri: Uri): Long? {
        return contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
            } else null
        }
    }

    /**
     * 将 Uri 内容复制到应用缓存目录，返回本地文件绝对路径。
     *
     * Photo Picker 返回的 Uri 属于 ContentProvider，无法直接当文件路径使用，
     * 因此先通过 ContentResolver 读取输入流，再写入缓存文件，便于后续业务直接操作路径。
     *
     * 写入前会检查源文件大小，超过 [MAX_IMAGE_SIZE] 时跳过并返回 null，防范 OOM。
     * 文件名包含序号，避免多选快速连续写入时时间戳冲突。
     *
     * @param uri    ContentProvider Uri（来自 Photo Picker 等）
     * @param index  序号，用于区分多选场景中各图片的缓存文件名
     * @return       缓存文件绝对路径；读写失败或文件过大时返回 null
     */
    private fun Context.uriToCachePath(uri: Uri, index: Int = 0): String? {
        try {
            // 写入前检查文件大小，防范 OOM
            val fileSize = queryFileSize(uri)
            if (fileSize != null && fileSize > MAX_IMAGE_SIZE) return null

            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val file = File(cacheDir, "picked_${System.currentTimeMillis()}_${index}.jpg")
            inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            // 写入后再校验实际文件大小（部分 ContentProvider 不报告 SIZE）
            if (file.length() > MAX_IMAGE_SIZE) {
                file.delete()
                return null
            }
            return file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * 注册单张图片选择器（Photo Picker 单选模式）。
     *
     * 在 Activity.onCreate 中调用以注册 Launcher，之后通过 [imagePicker] 触发选择。
     * 选中后 Uri 会被复制到缓存目录，回调返回本地路径；未选中或失败时返回 null。
     *
     * @param onResult  选择结果回调，参数为缓存文件路径或 null
     * @return          可用于 [imagePicker] 的 ActivityResultLauncher
     */
    fun ComponentActivity.singlePhotoPicker(
        onResult: (path: String?) -> Unit
    ): ActivityResultLauncher<PickVisualMediaRequest> =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            clearPickedCache()
            val path = uri?.let { uriToCachePath(it) }
            onResult(path)
        }

    /**
     * 注册多张图片选择器（Photo Picker 多选模式，支持最大选择数量）。
     *
     * - Android 14+：UI 层直接限制可选数量（[PickMultipleVisualMediaWithMax]）。
     * - Android 13 及以下：UI 不限制数量，通过 [take] 截断作为兜底。
     *
     * 在 Activity.onCreate 中调用以注册 Launcher，之后通过 [imagePicker] 触发选择。
     *
     * @param maxSelect  最大可选数量，默认不限制（Int.MAX_VALUE）
     * @param onResult   选择结果回调，参数为缓存文件路径列表
     * @return           可用于 [imagePicker] 的 ActivityResultLauncher
     */
    fun ComponentActivity.multiPhotoPicker(
        maxSelect: Int = Int.MAX_VALUE,
        onResult: (paths: List<String>) -> Unit
    ): ActivityResultLauncher<PickVisualMediaRequest> =
        registerForActivityResult(PickMultipleVisualMediaWithMax(maxSelect)) { uris ->
            clearPickedCache()
            // 截断作为兜底：Android 13 及以下 UI 不限制数量，靠此处截断
            val paths = uris.mapIndexedNotNull { index, uri -> uriToCachePath(uri, index) }.take(maxSelect)
            onResult(paths)
        }

    /**
     * 启动图片选择（仅限图片类型）。
     *
     * 可由 [singlePhotoPicker] 或 [multiPhotoPicker] 返回的 Launcher 调用，
     * 内部限定 MediaType 为 ImageOnly，过滤视频等其他媒体类型。
     */
    fun ActivityResultLauncher<PickVisualMediaRequest>.imagePicker() {
        launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

}

/**
 * 自定义多选 Contract，在 [ActivityResultContracts.PickMultipleVisualMedia] 基础上
 * 增加 Android 14+ 的 UI 层最大选择数量限制。
 *
 * - Android 14（UPSIDE_DOWN_CAKE）及以上：通过 [MediaStore.EXTRA_PICK_IMAGES_MAX] Intent extra
 *   让 Photo Picker UI 直接限制可选数量，超选时用户无法继续勾选。
 * - Android 13 及以下：UI 层不支持该 extra，需在回调中通过 [take] 截断兜底。
 *
 * @param maxSelect  最大可选数量，默认不限制
 */
private class PickMultipleVisualMediaWithMax(
    private val maxSelect: Int = Int.MAX_VALUE
) : ActivityResultContract<PickVisualMediaRequest, List<Uri>>() {

    private val baseContract = ActivityResultContracts.PickMultipleVisualMedia()

    override fun createIntent(context: Context, input: PickVisualMediaRequest): Intent {
        val intent = baseContract.createIntent(context, input)
        // 只在Android 14+ 支持在 UI 层限制选择数量
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && maxSelect < Int.MAX_VALUE) {
            intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, maxSelect)
        }
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        return baseContract.parseResult(resultCode, intent)
    }
}
