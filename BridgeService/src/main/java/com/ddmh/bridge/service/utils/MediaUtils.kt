package com.ddmh.bridge.service.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import com.ddmh.bridge.service.utils.MediaUtils.imagePicker
import com.ddmh.bridge.service.utils.MediaUtils.multiPhotoPicker
import com.ddmh.bridge.service.utils.MediaUtils.singlePhotoPicker
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

    /**
     * 清理 cacheDir 下所有已累积的 picked_ 前缀缓存图片。
     *
     * 每次选图都会在 cacheDir 中生成 picked_xxx.jpg 文件，
     * 为防止缓存无限累积，在 uriToCachePath 执行前调用此方法清除旧文件。
     */
    private fun Context.clearPickedCache() {
        cacheDir.listFiles()?.filter { it.name.startsWith("picked_") }?.forEach { it.delete() }
    }

    /**
     * 将 Uri 内容复制到应用缓存目录，返回本地文件绝对路径。
     *
     * 执行前会先清理旧的 picked_ 缓存文件，防止缓存无限累积。
     * Photo Picker 返回的 Uri 属于 ContentProvider，无法直接当文件路径使用，
     * 因此先通过 ContentResolver 读取输入流，再写入缓存文件，便于后续业务直接操作路径。
     *
     * @param uri  ContentProvider Uri（来自 Photo Picker 等）
     * @return     缓存文件绝对路径；读写失败时返回 null
     */
    private fun Context.uriToCachePath(uri: Uri): String? {
        try {
            // 用前清理旧缓存，防止 picked_ 文件无限累积
            clearPickedCache()
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val file = File(cacheDir, "picked_${System.currentTimeMillis()}.jpg")
            inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
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
            // 截断作为兜底：Android 13 及以下 UI 不限制数量，靠此处截断
            val paths = uris.mapNotNull { uriToCachePath(it) }.take(maxSelect)
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
