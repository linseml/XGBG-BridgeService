---
description: Android 开发规范
paths:
  - "**/src/main/**/*.kt"
  - "**/src/main/**/*.java"
---

# Android 开发规范

## 弹窗基类使用

- `XPopupBottom<VM, VB>`：底部弹出，构造参数 `(contentViewResId, context)`
- `XPopupCenter<VM, VB>`：居中弹出，构造参数 `(contentViewResId, context)`
- `XPopupAttach<VM, VB>`：依附弹窗，构造参数 `(contentViewResId, context)`
- `XPopupDrawer<VM, VB>`：侧边抽屉，构造参数 `(contentViewResId, context)`
- `XPopupPosition<VM, VB>`：自定义位置，无 contentViewResId，子类需重写 `getImplLayoutId()`
- 所有弹窗通过 `mBinding` 访问视图，`mViewModel` 访问 ViewModel（基类自动初始化）
- 生命周期钩子：`onSetup()` — onCreate 中 VB/VM 初始化完成后调用，替代传统 `initView/initListener` 模式
- **禁止编造基类方法名**：生成代码前必须先读取项目实际的基类实现

## Activity 规范

- 新建 Activity 后必须在 `AndroidManifest.xml` 注册
- 必须提供 `companion object { fun start(context: Context) }` 静态启动方法

## RecyclerView

- 使用 SmartRefreshLayout 包裹 RecyclerView 实现下拉刷新
- 布局文件命名 `item_{功能}.xml`

## ViewModel + LiveData

- ViewModel 中使用 `MutableLiveData`，对外暴露 `LiveData`
- 协程请求在 `viewModelScope.launch` 中执行
- Activity 获取 ViewModel：`by viewModels<XxxViewModel>()`（`import androidx.activity.viewModels`）

## SDK 初始化

- 宿主应用通过 `ServiceHelper.init()` 初始化 SDK
- `ServiceHelper` 提供 `isDebug()`、应用信息等全局配置
- Debug 模式下 `LogX` 自动启用日志输出

## 图片选择

- 使用 `MediaUtils.singlePhotoPicker()` 和 `multiPhotoPicker()` 代替系统 Intent 拑图
- 适配 Android 14+ 的 `PickMultipleVisualMediaWithMax` 限制选择数量
