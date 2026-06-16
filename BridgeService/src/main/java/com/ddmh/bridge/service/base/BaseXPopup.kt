package com.ddmh.bridge.service.base

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModel
import com.ddmh.bridge.service.ext.hideKeyboard
import com.ddmh.bridge.service.interfaces.OnPopupLifecycleListener
import com.lxj.xpopup.core.BottomPopupView
import com.lxj.xpopup.core.BubbleAttachPopupView
import com.lxj.xpopup.core.CenterPopupView
import com.lxj.xpopup.core.DrawerPopupView
import com.lxj.xpopup.core.PositionPopupView
import java.lang.reflect.ParameterizedType

/**
 * 底部弹出弹窗
 *
 * 适用于从屏幕底部滑入的弹窗，如列表选择器、编辑面板等。
 *
 * @param VM ViewModel 类型，上界为 AndroidX ViewModel，
 *           子类可传入各项目自己的 BaseViewModel。
 *           若 ViewModel 有 start() 等初始化方法，请在 onSetup() 中自行调用。
 * @param VB ViewBinding 类型，对应弹窗的布局 Binding 类
 * @param contentViewResId 弹窗布局资源 ID
 * @param context 上下文
 */
abstract class XPopupBottom<VM : ViewModel, VB : ViewDataBinding>(
    private val contentViewResId: Int,
    context: Context
) : BottomPopupView(context) {

    /**
     * ViewBinding 实例，在 onCreate 中自动绑定
     */
    lateinit var mBinding: VB

    /**
     * ViewModel 实例，声明了 VM 类型时通过反射自动创建。
     * 若子类的 BaseViewModel 有 start() 等初始化方法，请在 onSetup() 中自行调用。
     */
    lateinit var mViewModel: VM

    /**
     * 触摸弹窗外部时是否自动关闭软键盘
     */
    protected var dismissKeyboardOnTouchOutside = false

    /**
     * 弹窗生命周期状态回调
     */
    private var popupLifecycleListener: OnPopupLifecycleListener? = null

    /**
     * 设置弹窗生命周期状态回调
     *
     * @param callback 回调接口，不需要时不设置即可
     * @return 自身引用，支持链式调用
     */
    fun setPopupLifecycleListener(callback: OnPopupLifecycleListener?): XPopupBottom<VM, VB> {
        this.popupLifecycleListener = callback
        return this
    }

    /**
     * 弹窗创建回调
     * 自动完成 ViewBinding 绑定、ViewModel 初始化、执行 onSetup
     */
    final override fun onCreate() {
        super.onCreate()
        mBinding = XPopupDelegate.bindViewBinding(javaClass, popupImplView)
        initViewModel()
        onSetup()
    }

    /**
     * 反射解析 ViewModel 类型并创建实例
     * 弹窗不需要 ViewModelProvider 的跨配置保持机制，
     * 直接手动创建实例即可，保证每个弹窗有独立的 ViewModel。
     *
     * 注意：基类仅创建 ViewModel 实例，不再自动调用 start() 等初始化方法。
     * 若子类的 BaseViewModel 有 start() 等方法，请在 onSetup() 中自行调用。
     */
    private fun initViewModel() {
        val vmClass = XPopupDelegate.resolveViewModelClass(javaClass) ?: return
        @Suppress("UNCHECKED_CAST")
        mViewModel = (vmClass as Class<VM>).getDeclaredConstructor().newInstance()
    }

    /**
     * 返回子类传入的布局资源 ID
     */
    final override fun getImplLayoutId(): Int = contentViewResId

    /**
     * 子类必须实现的初始化方法，数据、监听器等逻辑在此统一处理。
     *
     * 若子类的 ViewModel 有 start() 等初始化方法，建议在此方法开头调用：
     * ```
     * override fun onSetup() {
     *     mViewModel.start()   // ← 如需初始化 ViewModel，在此调用
     *     // ... 其他初始化逻辑
     * }
     * ```
     */
    protected abstract fun onSetup()

    /**
     * 触摸事件分发
     * 开启 [dismissKeyboardOnTouchOutside] 时：
     * - 点击弹窗内部非输入框区域 → 关闭键盘（不关闭弹窗）
     * - 点击弹窗外部区域 → 交给 XPopup 默认行为（关闭弹窗时自然收起键盘）
     *
     * 原理：ACTION_DOWN 时记录弹窗边界，ACTION_UP 时对比判断。
     * 如果按下时在弹窗内部，说明用户意图与弹窗交互，不应因键盘收起导致的
     * 弹窗位置变化而被 XPopup 误判为"外部点击"。
     */
    private var downPopupRect = Rect()

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (dismissKeyboardOnTouchOutside) {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    popupImplView.getGlobalVisibleRect(downPopupRect)
                }

                MotionEvent.ACTION_UP -> {
                    val touchX = ev.rawX.toInt()
                    val touchY = ev.rawY.toInt()
                    val isInsidePopup = downPopupRect.contains(touchX, touchY)

                    if (isInsidePopup) {
                        val focused = findFocus()
                        if (focused is EditText) {
                            val editRect = Rect()
                            focused.getGlobalVisibleRect(editRect)
                            // 点击位置不在输入框内 → 关闭键盘，消费事件
                            if (!editRect.contains(touchX, touchY)) {
                                focused.clearFocus()
                                focused.hideKeyboard()
                                return true
                            }
                        }
                        // 输入框已获得焦点且点击在输入框内 → 交给默认处理
                    }
                    // 按下时在弹窗外部 → 交给 XPopup 处理（关闭弹窗）
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onShow() {
        super.onShow()
        popupLifecycleListener?.onShow()
    }

    override fun beforeShow() {
        super.beforeShow()
        popupLifecycleListener?.beforeShow()
    }

    override fun dismiss() {
        super.dismiss()
        popupLifecycleListener?.dismiss()
    }

    override fun beforeDismiss() {
        super.beforeDismiss()
        popupLifecycleListener?.beforeDismiss()
    }
}

/**
 * 位置弹出弹窗
 *
 * 适用于指定位置弹出的提示条，如顶部/底部通知等。
 * 继承自 XPopup 框架的 PositionPopupView，支持自定义偏移量。
 *
 * 注意：PositionPopupView 构造函数中会调用 getImplLayoutId()，
 * 因此子类必须直接覆写 getImplLayoutId()（而非通过构造参数传递布局 ID），
 * 以绕过 Kotlin 初始化顺序问题。
 *
 * @param VM ViewModel 类型，上界为 AndroidX ViewModel，
 *           子类可传入各项目自己的 BaseViewModel。
 *           若 ViewModel 有 start() 等初始化方法，请在 onSetup() 中自行调用。
 * @param VB ViewBinding 类型，对应弹窗的布局 Binding 类
 * @param context 上下文
 */
abstract class XPopupPosition<VM : ViewModel, VB : ViewDataBinding>(
    context: Context
) : PositionPopupView(context) {

    /**
     * ViewBinding 实例，在 onCreate 中自动绑定
     */
    lateinit var mBinding: VB

    /**
     * ViewModel 实例，声明了 VM 类型时通过反射自动创建。
     * 若子类的 BaseViewModel 有 start() 等初始化方法，请在 onSetup() 中自行调用。
     */
    lateinit var mViewModel: VM

    /**
     * 弹窗生命周期状态回调
     */
    private var popupLifecycleListener: OnPopupLifecycleListener? = null

    /**
     * 设置弹窗生命周期状态回调
     *
     * @param callback 回调接口，不需要时不设置即可
     * @return 自身引用，支持链式调用
     */
    fun setPopupLifecycleListener(callback: OnPopupLifecycleListener?): XPopupPosition<VM, VB> {
        this.popupLifecycleListener = callback
        return this
    }

    /**
     * 弹窗创建回调
     * 自动完成 ViewBinding 绑定、ViewModel 初始化、执行 onSetup
     */
    final override fun onCreate() {
        super.onCreate()
        mBinding = XPopupDelegate.bindViewBinding(javaClass, popupImplView)
        initViewModel()
        onSetup()
    }

    /**
     * 反射解析 ViewModel 类型并创建实例
     * 弹窗不需要 ViewModelProvider 的跨配置保持机制，
     * 直接手动创建实例即可，保证每个弹窗有独立的 ViewModel。
     *
     * 注意：基类仅创建 ViewModel 实例，不再自动调用 start() 等初始化方法。
     * 若子类的 BaseViewModel 有 start() 等方法，请在 onSetup() 中自行调用。
     */
    private fun initViewModel() {
        val vmClass = XPopupDelegate.resolveViewModelClass(javaClass) ?: return
        @Suppress("UNCHECKED_CAST")
        mViewModel = (vmClass as Class<VM>).getDeclaredConstructor().newInstance()
    }

    /**
     * 子类必须实现的初始化方法，数据、监听器等逻辑在此统一处理。
     *
     * 若子类的 ViewModel 有 start() 等初始化方法，建议在此方法开头调用：
     * ```
     * override fun onSetup() {
     *     mViewModel.start()   // ← 如需初始化 ViewModel，在此调用
     *     // ... 其他初始化逻辑
     * }
     * ```
     */
    protected abstract fun onSetup()

    /**
     * 子类必须覆写此方法返回布局资源 ID。
     * 由于 PositionPopupView 构造函数中会调用 getImplLayoutId()，
     * 子类应直接覆写并返回硬编码常量（如 R.layout.xxx），
     * 而不能依赖构造参数或属性（Kotlin 初始化顺序问题）。
     */
    abstract override fun getImplLayoutId(): Int

    override fun onShow() {
        super.onShow()
        popupLifecycleListener?.onShow()
    }

    override fun beforeShow() {
        super.beforeShow()
        popupLifecycleListener?.beforeShow()
    }

    override fun dismiss() {
        super.dismiss()
        popupLifecycleListener?.dismiss()
    }

    override fun beforeDismiss() {
        super.beforeDismiss()
        popupLifecycleListener?.beforeDismiss()
    }
}

/**
 * 居中弹出弹窗
 *
 * 适用于屏幕中央弹出的提示框、确认对话框等。
 *
 * @param VM ViewModel 类型，上界为 AndroidX ViewModel，
 *           子类可传入各项目自己的 BaseViewModel。
 *           若 ViewModel 有 start() 等初始化方法，请在 onSetup() 中自行调用。
 * @param VB ViewBinding 类型，对应弹窗的布局 Binding 类
 * @param contentViewResId 弹窗布局资源 ID
 * @param context 上下文
 */
abstract class XPopupCenter<VM : ViewModel, VB : ViewDataBinding>(
    private val contentViewResId: Int,
    context: Context
) : CenterPopupView(context) {

    /**
     * ViewBinding 实例，在 onCreate 中自动绑定
     */
    lateinit var mBinding: VB

    /**
     * ViewModel 实例，声明了 VM 类型时通过反射自动创建。
     * 若子类的 BaseViewModel 有 start() 等初始化方法，请在 onSetup() 中自行调用。
     */
    lateinit var mViewModel: VM

    /**
     * 弹窗生命周期状态回调
     */
    private var popupLifecycleListener: OnPopupLifecycleListener? = null

    /**
     * 设置弹窗生命周期状态回调
     *
     * @param callback 回调接口，不需要时不设置即可
     * @return 自身引用，支持链式调用
     */
    fun setPopupLifecycleListener(callback: OnPopupLifecycleListener?): XPopupCenter<VM, VB> {
        this.popupLifecycleListener = callback
        return this
    }

    /**
     * 弹窗创建回调
     * 自动完成 ViewBinding 绑定、ViewModel 初始化、执行 onSetup
     */
    final override fun onCreate() {
        super.onCreate()
        mBinding = XPopupDelegate.bindViewBinding(javaClass, popupImplView)
        initViewModel()
        onSetup()
    }

    /**
     * 反射解析 ViewModel 类型并创建实例
     * 弹窗不需要 ViewModelProvider 的跨配置保持机制，
     * 直接手动创建实例即可，保证每个弹窗有独立的 ViewModel。
     *
     * 注意：基类仅创建 ViewModel 实例，不再自动调用 start() 等初始化方法。
     * 若子类的 BaseViewModel 有 start() 等方法，请在 onSetup() 中自行调用。
     */
    private fun initViewModel() {
        val vmClass = XPopupDelegate.resolveViewModelClass(javaClass) ?: return
        @Suppress("UNCHECKED_CAST")
        mViewModel = (vmClass as Class<VM>).getDeclaredConstructor().newInstance()
    }

    /**
     * 返回子类传入的布局资源 ID
     */
    final override fun getImplLayoutId(): Int = contentViewResId

    /**
     * 子类必须实现的初始化方法，数据、监听器等逻辑在此统一处理。
     *
     * 若子类的 ViewModel 有 start() 等初始化方法，建议在此方法开头调用：
     * ```
     * override fun onSetup() {
     *     mViewModel.start()   // ← 如需初始化 ViewModel，在此调用
     *     // ... 其他初始化逻辑
     * }
     * ```
     */
    protected abstract fun onSetup()

    override fun onShow() {
        super.onShow()
        popupLifecycleListener?.onShow()
    }

    override fun beforeShow() {
        super.beforeShow()
        popupLifecycleListener?.beforeShow()
    }

    override fun dismiss() {
        super.dismiss()
        popupLifecycleListener?.dismiss()
    }

    override fun beforeDismiss() {
        super.beforeDismiss()
        popupLifecycleListener?.beforeDismiss()
    }
}

/**
 * 气泡依附弹窗
 *
 * 适用于锚定在某个 View 旁边的气泡弹窗，如排序筛选、快捷操作菜单等。
 * 继承自 XPopup 框架的 BubbleAttachPopupView。
 *
 * @param VM ViewModel 类型，上界为 AndroidX ViewModel，
 *           子类可传入各项目自己的 BaseViewModel。
 *           若 ViewModel 有 start() 等初始化方法，请在 onSetup() 中自行调用。
 * @param VB ViewBinding 类型，对应弹窗的布局 Binding 类
 * @param contentViewResId 弹窗布局资源 ID
 * @param context 上下文
 */
abstract class XPopupAttach<VM : ViewModel, VB : ViewDataBinding>(
    private val contentViewResId: Int,
    context: Context
) : BubbleAttachPopupView(context) {

    /**
     * ViewBinding 实例，在 onCreate 中自动绑定
     */
    lateinit var mBinding: VB

    /**
     * ViewModel 实例，声明了 VM 类型时通过反射自动创建。
     * 若子类的 BaseViewModel 有 start() 等初始化方法，请在 onSetup() 中自行调用。
     */
    lateinit var mViewModel: VM

    /**
     * 弹窗生命周期状态回调
     */
    private var popupLifecycleListener: OnPopupLifecycleListener? = null

    /**
     * 设置弹窗生命周期状态回调
     *
     * @param callback 回调接口，不需要时不设置即可
     * @return 自身引用，支持链式调用
     */
    fun setPopupLifecycleListener(callback: OnPopupLifecycleListener?): XPopupAttach<VM, VB> {
        this.popupLifecycleListener = callback
        return this
    }

    /**
     * 弹窗创建回调
     * 自动完成 ViewBinding 绑定、ViewModel 初始化、执行 onSetup
     */
    final override fun onCreate() {
        super.onCreate()
        mBinding = XPopupDelegate.bindViewBinding(javaClass, popupImplView)
        initViewModel()
        onSetup()
    }

    /**
     * 反射解析 ViewModel 类型并创建实例
     * 弹窗不需要 ViewModelProvider 的跨配置保持机制，
     * 直接手动创建实例即可，保证每个弹窗有独立的 ViewModel。
     *
     * 注意：基类仅创建 ViewModel 实例，不再自动调用 start() 等初始化方法。
     * 若子类的 BaseViewModel 有 start() 等方法，请在 onSetup() 中自行调用。
     */
    private fun initViewModel() {
        val vmClass = XPopupDelegate.resolveViewModelClass(javaClass) ?: return
        @Suppress("UNCHECKED_CAST")
        mViewModel = (vmClass as Class<VM>).getDeclaredConstructor().newInstance()
    }

    /**
     * 返回子类传入的布局资源 ID
     */
    final override fun getImplLayoutId(): Int = contentViewResId

    /**
     * 子类必须实现的初始化方法，数据、监听器等逻辑在此统一处理。
     *
     * 若子类的 ViewModel 有 start() 等初始化方法，建议在此方法开头调用：
     * ```
     * override fun onSetup() {
     *     mViewModel.start()   // ← 如需初始化 ViewModel，在此调用
     *     // ... 其他初始化逻辑
     * }
     * ```
     */
    protected abstract fun onSetup()

    override fun onShow() {
        super.onShow()
        popupLifecycleListener?.onShow()
    }

    override fun beforeShow() {
        super.beforeShow()
        popupLifecycleListener?.beforeShow()
    }

    override fun dismiss() {
        super.dismiss()
        popupLifecycleListener?.dismiss()
    }

    override fun beforeDismiss() {
        super.beforeDismiss()
        popupLifecycleListener?.beforeDismiss()
    }
}

/**
 * 侧边抽屉弹窗
 *
 * 适用于从屏幕左侧或右侧滑入的抽屉式弹窗，如筛选面板、设置菜单等。
 *
 * @param VM ViewModel 类型，上界为 AndroidX ViewModel，
 *           子类可传入各项目自己的 BaseViewModel。
 *           若 ViewModel 有 start() 等初始化方法，请在 onSetup() 中自行调用。
 * @param VB ViewBinding 类型，对应弹窗的布局 Binding 类
 * @param contentViewResId 弹窗布局资源 ID
 * @param context 上下文
 */
abstract class XPopupDrawer<VM : ViewModel, VB : ViewDataBinding>(
    private val contentViewResId: Int,
    context: Context
) : DrawerPopupView(context) {

    /**
     * ViewBinding 实例，在 onCreate 中自动绑定
     */
    lateinit var mBinding: VB

    /**
     * ViewModel 实例，声明了 VM 类型时通过反射自动创建。
     * 若子类的 BaseViewModel 有 start() 等初始化方法，请在 onSetup() 中自行调用。
     */
    lateinit var mViewModel: VM

    /**
     * 弹窗生命周期状态回调
     */
    private var popupLifecycleListener: OnPopupLifecycleListener? = null

    /**
     * 设置弹窗生命周期状态回调
     *
     * @param callback 回调接口，不需要时不设置即可
     * @return 自身引用，支持链式调用
     */
    fun setPopupLifecycleListener(callback: OnPopupLifecycleListener?): XPopupDrawer<VM, VB> {
        this.popupLifecycleListener = callback
        return this
    }

    /**
     * 弹窗创建回调
     * 自动完成 ViewBinding 绑定、ViewModel 初始化、执行 onSetup
     */
    final override fun onCreate() {
        super.onCreate()
        mBinding = XPopupDelegate.bindViewBinding(javaClass, popupImplView)
        initViewModel()
        onSetup()
    }

    /**
     * 反射解析 ViewModel 类型并创建实例
     * 弹窗不需要 ViewModelProvider 的跨配置保持机制，
     * 直接手动创建实例即可，保证每个弹窗有独立的 ViewModel。
     *
     * 注意：基类仅创建 ViewModel 实例，不再自动调用 start() 等初始化方法。
     * 若子类的 BaseViewModel 有 start() 等方法，请在 onSetup() 中自行调用。
     */
    private fun initViewModel() {
        val vmClass = XPopupDelegate.resolveViewModelClass(javaClass) ?: return
        @Suppress("UNCHECKED_CAST")
        mViewModel = (vmClass as Class<VM>).getDeclaredConstructor().newInstance()
    }

    /**
     * 返回子类传入的布局资源 ID
     */
    final override fun getImplLayoutId(): Int = contentViewResId

    /**
     * 子类必须实现的初始化方法，数据、监听器等逻辑在此统一处理。
     *
     * 若子类的 ViewModel 有 start() 等初始化方法，建议在此方法开头调用：
     * ```
     * override fun onSetup() {
     *     mViewModel.start()   // ← 如需初始化 ViewModel，在此调用
     *     // ... 其他初始化逻辑
     * }
     * ```
     */
    protected abstract fun onSetup()

    override fun onShow() {
        super.onShow()
        popupLifecycleListener?.onShow()
    }

    override fun beforeShow() {
        super.beforeShow()
        popupLifecycleListener?.beforeShow()
    }

    override fun dismiss() {
        super.dismiss()
        popupLifecycleListener?.dismiss()
    }

    override fun beforeDismiss() {
        super.beforeDismiss()
        popupLifecycleListener?.beforeDismiss()
    }
}

// ============== 共享工具 ==============

/**
 * XPopup 基类共享逻辑
 * 负责 ViewBinding 反射绑定、ViewModel 泛型解析
 */
private object XPopupDelegate {

    /**
     * 反射调用 ViewBinding 的 bind 方法完成布局绑定
     *
     * @param VB 具体的 ViewBinding 子类类型
     * @param startClass 子类的 Class 对象，用于解析泛型类型参数
     * @param popupImplView 弹窗内容视图
     * @return 绑定完成的 ViewBinding 实例
     */
    @Suppress("UNCHECKED_CAST")
    fun <VB : ViewDataBinding> bindViewBinding(
        startClass: Class<*>,
        popupImplView: View
    ): VB {
        val vbClass = resolveViewBindingClass(startClass)
            ?: throw IllegalStateException(
                "Could not resolve ViewBinding type for ${startClass.name}. " +
                        "Declare it as generic parameter, e.g.: " +
                        "XPopupBottom<MyVM, MyBinding>(..., context)"
            )
        val bindMethod = vbClass.getMethod("bind", View::class.java)
        return bindMethod.invoke(null, popupImplView) as VB
    }

    /**
     * 沿继承链向上查找，解析 VM 泛型类型
     *
     * 支持中间子类的情况，会一直向上查找直到找到 XPopup 基类为止。
     * 当 VM 泛型参数直接为 ViewModel 本身时返回 null（表示不需要创建 ViewModel），
     * 只有子类声明了具体的 ViewModel 子类时才返回对应的 Class。
     *
     * @param startClass 子类的 Class 对象
     * @return VM 对应的 Class，未声明具体子类时返回 null
     */
    fun resolveViewModelClass(startClass: Class<*>): Class<*>? {
        var clazz: Class<*> = startClass
        while (true) {
            val genericSuper = clazz.genericSuperclass
            if (genericSuper is ParameterizedType) {
                val rawType = genericSuper.rawType as Class<*>
                if (isXPopupSubclass(rawType)) {
                    val typeArg = genericSuper.actualTypeArguments[0]
                    // 若 VM 泛型参数直接为 ViewModel 本身，说明子类未声明具体 ViewModel，
                    // 返回 null 表示不需要创建 ViewModel 实例
                    if (typeArg is Class<*> && typeArg != ViewModel::class.java) {
                        return typeArg
                    }
                }
            }
            val superClazz = clazz.superclass ?: break
            clazz = superClazz
        }
        return null
    }

    /**
     * 沿继承链向上查找，解析 VB 泛型类型
     *
     * @param startClass 子类的 Class 对象
     * @return VB 对应的 Class，未声明时返回 null
     */
    fun resolveViewBindingClass(startClass: Class<*>): Class<*>? {
        var clazz: Class<*> = startClass
        while (true) {
            val genericSuper = clazz.genericSuperclass
            if (genericSuper is ParameterizedType) {
                val rawType = genericSuper.rawType as Class<*>

                if (isXPopupSubclass(rawType)) {
                    return genericSuper.actualTypeArguments[1] as? Class<*>
                }
            }
            val superClazz = clazz.superclass ?: break
            clazz = superClazz
        }
        return null
    }

    /**
     * 判断是否为 XPopup 系列基类
     */
    private fun isXPopupSubclass(clazz: Class<*>): Boolean {
        return clazz == XPopupBottom::class.java ||
                clazz == XPopupPosition::class.java ||
                clazz == XPopupCenter::class.java ||
                clazz == XPopupAttach::class.java ||
                clazz == XPopupDrawer::class.java
    }
}
