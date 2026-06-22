本项目为 Android SDK 库项目（BridgeService），使用 Kotlin + MVVM + DataBinding/ViewBinding + XPopup 架构。

## 项目配置

| 配置项 | 值 |
|---|---|
| compileSdk | 35 |
| minSdk | 24 |
| targetSdk | 35 |
| Kotlin | 2.0.21 |
| AGP | 8.7.3 |
| Gradle | 8.11.1 |
| JitPack 版本 | 1.0.16 |

## 构建命令

```bash
# 构建 Debug/Release
./gradlew assembleDebug
./gradlew assembleRelease

# 清理
./gradlew clean
```

## 架构

基于 Kotlin 的 Android 库项目，采用 2 模块 Gradle 结构：

```
XGBG-BridgeService/
├── app/                    # Demo 宿主应用（com.ddmh.xgbg.service）
│   └── App.kt              # 继承 BaseApplication
│   └── MainActivity.kt     # 简单 Demo 页面
│
└── BridgeService/          # 核心 SDK 库模块（com.ddmh.bridge.service）
    ├── base/               # BaseApplication + XPopup 五种弹窗基类
    ├── ext/                # ResourceExt.kt、GsonExt.kt 扩展函数
    ├── helper/             # ServiceHelper、GooglePay、AFHelper、AEHelper、FirebaseHelper、SmartRefreshHelper
    ├── interfaces/         # OnPayResultListener、OnPopupLifecycleListener
    └── utils/              # LogX、GsonHolder、AssetsUtils、DateUtils、GoogleUtils、Internation、MediaUtils
```

**模块依赖关系：**
- `:app` → `:BridgeService`

### 模块职责

- **app**：Demo 宿主应用，仅包含 App 和 MainActivity，用于演示和调试 SDK 功能。
- **BridgeService**：核心 SDK 库模块，发布到 JitPack。提供支付（Google Pay + AppsFlyer）、数据分析（AppsFlyer/Firebase/ThinkingAnalytics）、弹窗（XPopup 五种基类）、工具类等能力，供宿主应用集成。

## 关键依赖

| 依赖 | 版本 | 作用域 | 用途 |
|---|---|---|---|
| XPopup | 2.10.0 | api | 弹窗框架，提供五种弹窗基类 |
| SmartRefreshLayout | 2.1.0 | api | 下拉刷新框架 |
| AppsFlyer SDK | 6.18.0 | api | 归因与数据分析 |
| Google Billing | 8.3.0 | api | Google Play 内购支付 |
| Firebase BOM | 33.13.0 | implementation | Firebase 数据分析 |
| ThinkingAnalytics | 3.4.2 | implementation | 数数数据分析 |
| Gson | 2.14.0 | api | JSON 序列化/反序列化 |
| Purchase Connector | 2.2.0 | implementation | AppsFlyer 支付连接 |
| Install Referrer | 2.2 | implementation | Google Play 安装来源追踪 |

## 项目约定

### 基类

项目不包含 BaseActivity/BaseFragment/BaseDialogFragment，而是基于 XPopup 提供五种弹窗基类：

| 基类 | 父类 | 用途 |
|---|---|---|
| `XPopupBottom<VM, VB>` | BottomPopupView | 底部弹出弹窗 |
| `XPopupCenter<VM, VB>` | CenterPopupView | 居中弹出弹窗 |
| `XPopupAttach<VM, VB>` | BubbleAttachPopupView | 依附弹窗 |
| `XPopupDrawer<VM, VB>` | DrawerPopupView | 侧边抽屉弹窗 |
| `XPopupPosition<VM, VB>` | PositionPopupView | 自定义位置弹窗 |

通用约定：
- ViewBinding 通过 `mBinding` 访问（基类自动绑定）
- ViewModel 通过 `mViewModel` 访问（基类反射初始化）
- 生命周期钩子：`protected abstract fun onSetup()` — 在 onCreate 中 ViewBinding/ViewModel 初始化完成后调用
- `XPopupPosition` 无 contentViewResId 构造参数，子类需重写 `getImplLayoutId()`

`BaseApplication`：`open class BaseApplication : Application()`，仅 override `onCreate()`，无额外钩子。

### 工具类

| 工具类 | 用途 |
|---|---|
| `LogX` | 日志工具，所有方法通过 `ServiceHelper.isDebug()` 控制，格式 `LogX-->{tag}`，自动注入调用者信息 |
| `GsonHolder` | 单例 Gson 实例（`serializeNulls()` + `disableHtmlEscaping()`） |
| `AssetsUtils` | 读取 assets 目录 JSON 文件 |
| `DateUtils` | 日期格式化：`nowDate`、`nowTime`、`parseStamp()`、`String.parseDate()` |
| `GoogleUtils` | 异步获取 Google Advertising ID |
| `Internation` | 获取系统国家代码（`getSystemCountryCode()`）和语言标签（`getSystemLanguageTag()`） |
| `MediaUtils` | Photo Picker：`singlePhotoPicker()`、`multiPhotoPicker()` |
| `ServiceHelper` | SDK 初始化入口与全局配置（debug 模式、应用信息等） |

### 扩展函数

| 文件 | 关键能力 |
|---|---|
| `ResourceExt.kt` | `application`/`context`/`res` 全局属性；`color()/string()/drawable()/dimenPx()` 等 Context/View 扩展；`dp2px()/px2dp()/dp()/sp()` 单位转换；屏幕尺寸、状态栏/导航栏高度 |
| `GsonExt.kt` | `T?.toJson()` 序列化、`String?.toBean<T>()` 反序列化、`typeOf<T>()` TypeToken |

### 命名规范

| 类型 | 格式 | 示例 |
|---|---|---|
| 弹窗 | `{功能}Popup` | `PayResultPopup` |
| Helper | `{功能}Helper` | `AFHelper`、`FirebaseHelper` |
| 工具类 | `{功能}Utils` | `DateUtils`、`MediaUtils` |
| 接口 | `On{功能}Listener` | `OnPayResultListener` |
| 弹窗布局 | `popup_{功能}.xml` | `popup_pay_result.xml` |
