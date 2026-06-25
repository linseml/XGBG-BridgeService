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
import androidx.fragment.app.Fragment
import com.ddmh.bridge.service.utils.MediaUtils.MAX_IMAGE_SIZE
import com.ddmh.bridge.service.utils.MediaUtils.imagePicker
import com.ddmh.bridge.service.utils.MediaUtils.multiPhotoPicker
import com.ddmh.bridge.service.utils.MediaUtils.singlePhotoPicker
import java.io.File
import java.io.FileOutputStream

/**
 * 多选图片的启动参数，包装 [PickVisualMediaRequest] 与动态最大选择数量。
 *
 * 用于 [multiPhotoPicker] 返回的 Launcher，在启动时传入当前剩余可选数量，
 * 使 Android 14+ 的 Photo Picker UI 层实时限制可选数量。
 *
 * @param request   图片选择请求（如 ImageOnly）
 * @param maxSelect 本次启动允许的最大选择数量，默认不限制
 */
data class PickVisualMediaRequestWithMax(
    val request: PickVisualMediaRequest,
    val maxSelect: Int = Int.MAX_VALUE
)

/**
 * 多选图片的回调结果，携带 [maxSelect] 以在 Android 13 及以下做截断兜底。
 *
 * @param uris      选中的 Uri 列表
 * @param maxSelect 本次启动时传入的最大选择数量
 */
data class PickMultipleVisualMediaResult(
    val uris: List<Uri>,
    val maxSelect: Int
)

/**
 * 媒体选择工具类，封装 Android Photo Picker 的单选/多选图片功能，
 * 将选中图片的 Uri 复制到缓存目录并返回本地文件路径。
 * 兼容 Android 14+ 的 UI 层多选数量限制，低版本通过截断兜底。
 *
 * 使用方式：
 * ```kotlin
 * // 1. 单选：注册 Launcher，启动时调用 imagePicker()
 * private val singlePicker = singlePhotoPicker { path ->
 *     path?.let { showToast("选中: $it") }
 * }
 * singlePicker.imagePicker()   // 启动单选
 *
 * // 2. 多选：注册 Launcher（无固定 maxSelect），启动时动态传入限制数量
 * private val multiPicker = multiPhotoPicker { paths ->
 *     showToast("选中 ${paths.size} 张")
 * }
 * multiPicker.imagePicker(maxSelect = 3)                    // 本次最多选 3 张
 * multiPicker.imagePicker(maxSelect = 3 - currentCount)     // 根据剩余槽位动态限制
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
        return contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
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
     * 注册多张图片选择器（Photo Picker 多选模式）。
     *
     * 在 Activity.onCreate 中调用以注册 Launcher，之后通过 [imagePicker] 触发选择。
     *
     * 最大选择数量不在注册时固定，而是在每次启动时通过 [imagePicker] 的 maxSelect 参数动态传入：
     * - Android 14+：UI 层直接限制可选数量（[PickMultipleVisualMediaWithMax]）。
     * - Android 13 及以下：UI 不限制数量，回调中通过 [take] 截断兜底。
     *
     * @param onResult   选择结果回调，参数为缓存文件路径列表
     * @return           可用于 [imagePicker] 的 ActivityResultLauncher
     */
    fun ComponentActivity.multiPhotoPicker(
        onResult: (paths: List<String>) -> Unit
    ): ActivityResultLauncher<PickVisualMediaRequestWithMax> =
        registerForActivityResult(PickMultipleVisualMediaWithMax()) { result ->
            clearPickedCache()
            // Android 14+ UI 层已限选，Android 13 及以下靠 take 截断兜底
            val paths = result.uris
                .mapIndexedNotNull { index, uri -> uriToCachePath(uri, index) }
                .take(result.maxSelect)
            onResult(paths)
        }

    /**
     * 启动单选图片选择（仅限图片类型）。
     *
     * 由 [singlePhotoPicker] 返回的 Launcher 调用，
     * 内部限定 MediaType 为 ImageOnly，过滤视频等其他媒体类型。
     */
    fun ActivityResultLauncher<PickVisualMediaRequest>.imagePicker() {
        launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    /**
     * 启动多选图片选择（仅限图片类型），支持动态最大选择数量。
     *
     * 由 [multiPhotoPicker] 返回的 Launcher 调用，
     * 内部限定 MediaType 为 ImageOnly，过滤视频等其他媒体类型。
     *
     * @param maxSelect  本次启动允许的最大选择数量，默认不限制（Int.MAX_VALUE）
     */
    fun ActivityResultLauncher<PickVisualMediaRequestWithMax>.imagePicker(maxSelect: Int = Int.MAX_VALUE) {
        launch(
            PickVisualMediaRequestWithMax(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                maxSelect
            )
        )
    }

    // ------------------------- Fragment 版本 -------------------------

    /**
     * 注册单张图片选择器（Photo Picker 单选模式）— Fragment 版本。
     *
     * 在 Fragment.onCreate 中调用以注册 Launcher，之后通过 [imagePicker] 触发选择。
     * 功能与 [ComponentActivity.singlePhotoPicker] 一致，仅注册宿主不同。
     *
     * @param onResult  选择结果回调，参数为缓存文件路径或 null
     * @return          可用于 [imagePicker] 的 ActivityResultLauncher
     */
    fun Fragment.singlePhotoPicker(
        onResult: (path: String?) -> Unit
    ): ActivityResultLauncher<PickVisualMediaRequest> =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            requireContext().clearPickedCache()
            val path = uri?.let { requireContext().uriToCachePath(it) }
            onResult(path)
        }

    /**
     * 注册多张图片选择器（Photo Picker 多选模式）— Fragment 版本。
     *
     * 在 Fragment.onCreate 中调用以注册 Launcher，之后通过 [imagePicker] 触发选择。
     * 功能与 [ComponentActivity.multiPhotoPicker] 一致，仅注册宿主不同。
     *
     * @param onResult   选择结果回调，参数为缓存文件路径列表
     * @return           可用于 [imagePicker] 的 ActivityResultLauncher
     */
    fun Fragment.multiPhotoPicker(
        onResult: (paths: List<String>) -> Unit
    ): ActivityResultLauncher<PickVisualMediaRequestWithMax> =
        registerForActivityResult(PickMultipleVisualMediaWithMax()) { result ->
            requireContext().clearPickedCache()
            // Android 14+ UI 层已限选，Android 13 及以下靠 take 截断兜底
            val paths = result.uris
                .mapIndexedNotNull { index, uri -> requireContext().uriToCachePath(uri, index) }
                .take(result.maxSelect)
            onResult(paths)
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
 * 最大选择数量从 [PickVisualMediaRequestWithMax.maxSelect] 动态读取，
 * 支持每次启动时传入不同的限制数量。
 */
private class PickMultipleVisualMediaWithMax :
    ActivityResultContract<PickVisualMediaRequestWithMax, PickMultipleVisualMediaResult>() {

    private val baseContract = ActivityResultContracts.PickMultipleVisualMedia()

    /** 保存每次 createIntent 时传入的 maxSelect，供 parseResult 携带到回调 */
    private var lastMaxSelect = Int.MAX_VALUE

    override fun createIntent(context: Context, input: PickVisualMediaRequestWithMax): Intent {
        lastMaxSelect = input.maxSelect
        val intent = baseContract.createIntent(context, input.request)
        // 只在Android 14+ 支持在 UI 层限制选择数量
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && input.maxSelect < Int.MAX_VALUE) {
            intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, input.maxSelect)
        }
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): PickMultipleVisualMediaResult {
        val uris = baseContract.parseResult(resultCode, intent)
        return PickMultipleVisualMediaResult(uris, lastMaxSelect)
    }
}
