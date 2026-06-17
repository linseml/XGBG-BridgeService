package com.ddmh.bridge.service.ext

//noinspection SuspiciousImport
import android.R
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat
import com.ddmh.bridge.service.helper.ServiceHelper


/**
 * 全局 Application 实例，通过 [ServiceHelper.getApplication] 获取。
 * 可在任何位置使用，无需依赖 Activity 或 Fragment 生命周期。
 */
val application: Application
    get() = ServiceHelper.getApplication()

/**
 * 全局 Application Context，通过 [ServiceHelper.getApplication] 获取。
 * 可在任何位置使用，避免直接传递 Context 的麻烦。
 */
val context: Context
    get() = ServiceHelper.getApplication()

/**
 * 全局 Resources 对象，等效于 [context].[android.content.Context.getResources]。
 * 用于在非 Activity/Fragment 场景下访问资源。
 */
val res: Resources
    get() = context.resources

/**
 * 从资源 ID 获取颜色值（Int）。
 *
 * @param id 颜色资源 ID，如 `R.color.red`
 * @return 对应的颜色 Int 值
 */
fun Context.color(id: Int) = ContextCompat.getColor(this, id)

/**
 * 从资源 ID 获取字符串。
 *
 * @param id 字符串资源 ID，如 `R.string.app_name`
 * @return 对应的字符串
 */
fun Context.string(id: Int) = resources.getString(id)

/**
 * 从资源 ID 获取字符串数组。
 *
 * @param id 字符串数组资源 ID，如 `R.array.planets`
 * @return 对应的字符串数组
 */
fun Context.stringArray(id: Int) = resources.getStringArray(id)

/**
 * 从资源 ID 获取 Drawable 对象。
 *
 * @param id Drawable 资源 ID，如 `R.drawable.ic_launcher`
 * @return 对应的 [android.graphics.drawable.Drawable]，可能为 null
 */
fun Context.drawable(id: Int) = ContextCompat.getDrawable(this, id)

/**
 * 从资源 ID 获取尺寸值（像素），等价于 `getDimensionPixelSize`。
 *
 * @param id 尺寸资源 ID，如 `R.dimen.spacing_medium`
 * @return 转换后的像素值（Int），向下取整保证为整数像素
 */
fun Context.dimenPx(id: Int) = resources.getDimensionPixelSize(id)

/**
 * 从资源 ID 获取整数值。
 *
 * @param id 整数资源 ID，如 `R.integer.max_count`
 * @return 对应的整数值
 */
fun Context.integer(id: Int) = resources.getInteger(id)

/**
 * 从资源 ID 获取颜色值，委托给 [Context.color]。
 *
 * @param id 颜色资源 ID
 * @return 对应的颜色 Int 值
 */
fun View.color(id: Int) = context.color(id)

/**
 * 从资源 ID 获取字符串，委托给 [Context.string]。
 *
 * @param id 字符串资源 ID
 * @return 对应的字符串
 */
fun View.string(id: Int) = context.string(id)

/**
 * 从资源 ID 获取字符串数组，委托给 [Context.stringArray]。
 *
 * @param id 字符串数组资源 ID
 * @return 对应的字符串数组
 */
fun View.stringArray(id: Int) = context.stringArray(id)

/**
 * 从资源 ID 获取 Drawable，委托给 [Context.drawable]。
 *
 * @param id Drawable 资源 ID
 * @return 对应的 Drawable，可能为 null
 */
fun View.drawable(id: Int) = context.drawable(id)

/**
 * 从资源 ID 获取尺寸像素值，委托给 [Context.dimenPx]。
 *
 * @param id 尺寸资源 ID
 * @return 转换后的像素值（Int）
 */
fun View.dimenPx(id: Int) = context.dimenPx(id)

/**
 * 从资源 ID 获取整数值，委托给 [Context.integer]。
 *
 * @param id 整数资源 ID
 * @return 对应的整数值
 */
fun View.integer(id: Int) = context.integer(id)

/**
 * 获取屏幕高度（像素）。
 *
 * @return 屏幕高度像素值
 */
fun getScreenHeight(): Int = res.displayMetrics.heightPixels

/**
 * 获取屏幕宽度（像素）。
 *
 * @return 屏幕宽度像素值
 */
fun getScreenWidth(): Int = res.displayMetrics.widthPixels

/**
 * 获取 LayoutInflater，通过 LAYOUT_INFLATER_SERVICE 系统服务获取。
 * 避免每次手动调用 [Context.getSystemService]。
 */
inline val Context.layoutInflater: LayoutInflater
    get() = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

/**
 * 使用全局 [context] 的 LayoutInflater 布局化一个 XML 布局。
 * 适用于在非 Activity/Fragment 中需要 inflate 布局的场景。
 *
 * @param layoutId 布局资源 ID，如 `R.layout.item_card`
 * @param parent   可选的父 ViewGroup，用于提供 LayoutParams；不影响 attach 行为
 * @param attachToParent 是否将 inflate 结果直接添加到 [parent]，默认 false
 * @return 布局化后的 [View] 根视图
 */
fun inflateLayout(
    @LayoutRes layoutId: Int,
    parent: ViewGroup? = null,
    attachToParent: Boolean = false
): View {
    return context.layoutInflater.inflate(layoutId, parent, attachToParent)
}

/**
 * 将 dp 值转换为像素值（Int），内部委托给 [dp2px]。
 * 可直接在 Number 上调用，如 `16.dp()`、`0.5f.dp()`。
 *
 * @return 转换后的像素值（Int），四舍五入到整数
 */
fun Number.dp(): Int = dp2px(toFloat())

/**
 * 将 sp 值转换为像素值（Int），内部委托给 [sp2px]。
 * 可直接在 Number 上调用，如 `14.sp()`、`1.5f.sp()`。
 *
 * @return 转换后的像素值（Int），四舍五入到整数
 */
fun Number.sp(): Int = sp2px(toFloat())

/**
 * 将 dp 值转换为像素值（Int）。
 * 使用当前屏幕 density 进行换算，结果四舍五入。
 *
 * @param dpValue 要转换的 dp 值
 * @return 转换后的像素值（Int）
 */
fun dp2px(dpValue: Float): Int {
    return (dpValue * res.displayMetrics.density + 0.5f).toInt()
}

/**
 * 将 dp 值转换为像素值（Float），不四舍五入，保留浮点精度。
 * 适用于需要精确像素计算的场景（如动画、渐变等）。
 *
 * @param dpValue 要转换的 dp 值
 * @return 转换后的像素值（Float）
 */
fun dp2pxF(dpValue: Float): Float {
    return dpValue * res.displayMetrics.density
}

/**
 * 将像素值转换为 dp 值（Int）。
 * 使用当前屏幕 density 进行反向换算，结果四舍五入。
 *
 * @param pxValue 要转换的像素值
 * @return 转换后的 dp 值（Int）
 */
fun px2dp(pxValue: Float): Int {
    return (pxValue / res.displayMetrics.density + 0.5f).toInt()
}

/**
 * 将 sp 值转换为像素值（Int）。
 * 使用当前屏幕 scaledDensity（考虑字体缩放）进行换算，结果四舍五入。
 *
 * @param spValue 要转换的 sp 值
 * @return 转换后的像素值（Int）
 */
fun sp2px(spValue: Float): Int {
    return (spValue * res.displayMetrics.scaledDensity + 0.5f).toInt()
}

/**
 * 将像素值转换为 sp 值（Int）。
 * 使用当前屏幕 scaledDensity 进行反向换算，结果四舍五入。
 *
 * @param pxValue 要转换的像素值
 * @return 转换后的 sp 值（Int）
 */
fun px2sp(pxValue: Float): Int {
    return (pxValue / res.displayMetrics.scaledDensity + 0.5f).toInt()
}

/**
 * 静态获取状态栏高度（像素），不会随沉浸式/刘海屏等动态变化。
 * 通过反射查找系统内部资源 `status_bar_height` 获取高度值。
 * 适用于只需一次获取固定高度的简单场景；
 * 若需要响应沉浸式、刘海屏、横竖屏等动态变化，请使用 [getStatusBarHeightDynamic]。
 *
 * @return 状态栏高度像素值；若无法获取则返回 0
 */
@SuppressLint("InternalInsetResource")
fun Context.getStatusBarHeight(): Int {
    val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
    return if (resourceId > 0) {
        resources.getDimensionPixelSize(resourceId)
    } else {
        0
    }
}

/**
 * 获取导航栏（底部虚拟按键栏）的高度（像素）。
 * 通过反射查找系统内部资源 `navigation_bar_height` 获取高度值。
 * 在没有导航栏的设备（如手势导航模式）上返回 0。
 *
 * @return 导航栏高度像素值；若设备无导航栏则返回 0
 */
@SuppressLint("InternalInsetResource")
fun Context.getNavigationBarHeight(): Int {
    val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
    return if (resourceId > 0) {
        resources.getDimensionPixelSize(resourceId)
    } else {
        0
    }
}

/**
 * 动态获取状态栏高度（支持沉浸式/刘海屏/横竖屏变化）
 * 默认绑定到当前 Activity 的根布局
 */
fun Activity.getStatusBarHeightDynamic(callback: (Int) -> Unit) {
    val rootView = window.decorView.findViewById<View>(R.id.content)
    rootView.setOnApplyWindowInsetsListener { _, insets ->
        val statusBarHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            insets.getInsets(WindowInsets.Type.statusBars()).top
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetTop
        }
        callback(statusBarHeight)
        insets
    }
    rootView.requestApplyInsets()
}


fun View.hideKeyboard() {
    val imm =
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(windowToken, 0)
}

fun EditText.showKeyboard() {
    val imm =
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)

}