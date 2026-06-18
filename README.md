# XGBG-BridgeService

Android 桥接服务库 — 将 AppsFlyer 归因、Firebase Analytics、数数（ThinkingData）SDK、Google Play Billing、SmartRefreshLayout、XPopup 等第三方 SDK 统一封装为简洁的 Helper / Utils / Ext API，接入方只需一行初始化即可完成集成。

## 功能一览

| 功能 | 类 | 封装 SDK |
|------|-----|----------|
| 全局上下文 & 环境标记 | `ServiceHelper` | — |
| 归因 & 内购验证 | `AFHelper` | AppsFlyer SDK 6.18.0 |
| 事件上报 | `AEHelper` | ThinkingData (数数) SDK 3.4.2 |
| 事件上报 & 用户属性 | `FirebaseHelper` | Firebase Analytics (BOM 33.13.0) |
| Google Play 支付 | `GooglePay` | BillingClient 8.3.0 |
| 刷新文案国际化 | `SmartRefreshHelper` | SmartRefreshLayout 2.1.0 |
| 弹窗基类 | `XPopupBottom` 等 5 个 | XPopup 2.10.0 |
| 图片选择 | `MediaUtils` | Android Photo Picker |
| Gson 序列化 | `GsonExt` + `GsonHolder` | Gson 2.14.0 |
| 资源 & 尺寸扩展 | `ResourceExt` | — |
| 日期格式化 | `DateUtils` | — |
| 调试日志 | `LogX` | — |
| 国际化工具 | `Internation` | — |
| Assets 读取 | `AssetsUtils` | — |
| Google 广告 ID | `GoogleUtils` | — |

## 快速开始

### 1. 添加 JitPack 仓库

根 `settings.gradle`（Gradle 7+）：

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
        maven { url 'https://artifact.thinkingdata.io/release' }  // 数数 SDK
    }
}
```

### 2. 添加依赖

根 `build.gradle`：

```groovy
buildscript {
    dependencies {
        classpath 'com.google.gms:google-services:4.4.2'  // Firebase（如需 Firebase Analytics）
    }
}
```

app 模块 `build.gradle`：

```groovy
apply plugin: 'com.google.gms.google-services'  // Firebase（如需 Firebase Analytics）

dependencies {
    implementation 'com.github.linseml:XGBG-BridgeService:1.0.16'
}
```

### 3. Firebase 配置（仅 Firebase Analytics 需要）

从 [Firebase Console](https://console.firebase.google.com/) → 项目设置 → 下载 `google-services.json`，放入 app 模块根目录。

### 4. 初始化

⚠️ `ServiceHelper.init()` **必须最先调用**——后续所有 Helper 依赖此上下文。

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // ⚠️ 必须最先调用
        ServiceHelper.init(this, BuildConfig.DEBUG)

        // AppsFlyer 归因
        AFHelper.initSDK("你的 AppsFlyer Dev Key")
        AFHelper.startAppsFlyer(mapOf("channel" to "google_play"))
        AFHelper.startAFObservingTransactions()

        // 数数 (ThinkingData) 事件上报
        AEHelper.initSDK("你的数数 appId", "你的数数 serverUrl")
        AEHelper.setCommonProperties(JSONObject().apply {
            put("channel", "google_play")
        })

        // Firebase Analytics 事件上报
        FirebaseHelper.initSDK()
        FirebaseHelper.setCommonProperties(hashMapOf("channel" to "google_play"))

        // SmartRefreshLayout 刷新文案
        SmartRefreshHelper.initSmartRefreshLanguage()

        // Google Play Billing 支付
        GooglePay.init()
    }
}
```

---

## Helper API

### ServiceHelper — 全局上下文

```kotlin
ServiceHelper.init(application, BuildConfig.DEBUG)  // 初始化（必须最先调用）
ServiceHelper.getApplication()                       // 获取全局 Application
ServiceHelper.isDebug()                              // 是否 Debug 环境
```

### AFHelper — AppsFlyer 归因

```kotlin
AFHelper.initSDK("af_dev_key")                                     // 初始化 SDK + 归因回调
AFHelper.startAppsFlyer(mapOf("channel" to "google_play"))         // 启动上报 + 自定义参数
AFHelper.startAFObservingTransactions()                            // 监听内购 & 订阅交易自动上报
```

### AEHelper — 数数 (ThinkingData) 事件上报

```kotlin
AEHelper.initSDK("appId", "serverUrl")                             // 初始化 SDK
AEHelper.track("event_name")                                       // 上报事件（无参数）
AEHelper.track("event_name", hashMapOf("key" to "value"))          // 上报事件（带参数）
AEHelper.setCommonProperties(jsonObject)                           // 设置公共属性
```

### FirebaseHelper — Firebase Analytics 事件上报

```kotlin
FirebaseHelper.initSDK()                                           // 初始化 SDK
FirebaseHelper.track("event_name")                                 // 上报事件（无参数）
FirebaseHelper.track("event_name", hashMapOf("key" to "value"))    // 上报事件（HashMap）
FirebaseHelper.track("event_name", bundle)                         // 上报事件（Bundle）
FirebaseHelper.setCommonProperties(bundle)                         // 设置公共属性（Bundle）
FirebaseHelper.setCommonProperties(hashMapOf("key" to "value"))    // 设置公共属性（HashMap）
FirebaseHelper.setUserProperty("user_id", "12345")                 // 设置用户属性
FirebaseHelper.setUserId("player_12345")                           // 设置用户唯一标识
FirebaseHelper.setAnalyticsCollectionEnabled(true)                 // 数据收集开关（GDPR 合规）
```

### GooglePay — Google Play Billing 支付

```kotlin
GooglePay.init()                                                   // 初始化 BillingClient

// 查询商品详情
GooglePay.queryProductDetails(listOf("sku_gold"), ProductType.IAP) { details ->
    // details 包含商品名称、价格等信息
}

// 消耗型商品支付
GooglePay.launchPay(activity, "sku_gold", ProductType.IAP, object : OnPayResultListener {
    override fun onPurchaseSuccess(purchase: Purchase) {
        GooglePay.consumePurchase(purchase)        // 消耗型商品必须消耗
    }
    override fun onPurchaseError(code: Int, message: String) { /* 处理失败 */ }
    override fun onPurchaseCancelled() { /* 用户取消 */ }
})

// 非消耗型 / 订阅确认（否则 3 天后自动退款）
GooglePay.acknowledgePurchase(purchase)

// 恢复订阅
GooglePay.restorePurchases(object : OnPayResultListener {  })
```

### SmartRefreshHelper — 刷新文案国际化

```kotlin
SmartRefreshHelper.initSmartRefreshLanguage()
```

接入方如需自定义文案，在 app 模块 `strings.xml` 中覆盖同名资源即可：

```xml
<string name="srl_header_pulling">自定义下拉文案</string>
```

### MediaUtils — 图片选择

封装 Android Photo Picker，将选中图片 Uri 复制到缓存目录并返回本地路径；兼容 Android 14+ 多选数量限制，低版本通过截断兜底；每次选图前自动清理旧缓存。

```kotlin
// 1. 在 Activity.onCreate 中注册 Launcher
private val singlePicker = singlePhotoPicker { path ->
    path?.let { showToast("选中: $it") }
}

private val multiPicker = multiPhotoPicker(maxSelect = 3) { paths ->
    showToast("选中 ${paths.size} 张")
}

// 2. 在需要触发选择时调用 imagePicker()
singlePicker.imagePicker()   // 单选
multiPicker.imagePicker()    // 多选（最多3张）
```

---

## 扩展 API

### ResourceExt — 全局 Context & 资源快捷访问

```kotlin
application          // 全局 Application 实例
context              // 全局 Application Context
res                  // 全局 Resources 对象

// 资源访问扩展
Context.color(id)    // 获取颜色 Int
Context.string(id)   // 获取字符串
Context.stringArray(id) // 获取字符串数组
Context.drawable(id) // 获取 Drawable
Context.dimenPx(id)  // 获取尺寸像素值
Context.integer(id)  // 获取整数值

// 尺寸转换
16.dp()              // dp → px（Int）
14.sp()              // sp → px（Int）
dp2px(16f)           // dp → px（Float）
px2dp(48f)           // px → dp

// 屏幕信息
getScreenWidth()     // 屏幕宽度（px）
getScreenHeight()    // 屏幕高度（px）
context.getStatusBarHeight()          // 状态栏高度
activity.getStatusBarHeightDynamic {} // 动态状态栏高度（支持沉浸式）
context.getNavigationBarHeight()      // 导航栏高度

// 键盘控制
view.hideKeyboard()
editText.showKeyboard()
```

### GsonExt — JSON 序列化 / 反序列化

```kotlin
val json = user.toJson()                    // 序列化 → JSON 字符串
val user: User? = json.toBean<User>()       // 反序列化 → 对象（null 安全）
```

### LogX — 调试日志

```kotlin
LogX.d("tag", "message")                   // Debug 日志（Release 自动静默）
LogX.e("tag", "error info")                // 错误日志
// 自动附加调用者信息：线程名、类.方法(文件:行号)
```

---

## Utils API

### DateUtils — 日期格式化

```kotlin
DateUtils.nowDate      // 当前日期 "yyyy-MM-dd"
DateUtils.nowTime      // 当前时间 "yyyy-MM-dd HH:mm:ss"
DateUtils.nowDateTime  // 当前时分秒 "HH:mm:ss"

DateUtils.parseStamp(timeStamp, "yyyy-MM-dd")  // 时间戳 → 格式化字符串
"2025-01-01".parseDate("yyyy-MM-dd", "MM/dd")  // 格式转换扩展
```

### Internation — 国际化工具

```kotlin
Internation.getSystemCountryCode("US")   // 系统国家码（仅返回受支持的国家）
Internation.getSystemLanguageTag("en-US") // 系统语言标签（中文回退为默认值）
```

### AssetsUtils — Assets 文件读取

```kotlin
AssetsUtils.readJson(context, "config.json")  // 读取 Assets 下的 JSON 文件
```

### GoogleUtils — Google 广告 ID

```kotlin
// 协程方式获取（IO 线程）
val adId = GoogleUtils.fetchGoogleAID()
```

---

## 弹窗基类

| 基类 | 继承 | 用途 |
|------|------|------|
| `XPopupBottom<VM, VB>` | BottomPopupView | 底部滑入弹窗 |
| `XPopupCenter<VM, VB>` | CenterPopupView | 展中弹窗 |
| `XPopupPosition<VM, VB>` | PositionPopupView | 位置弹窗（通知等） |
| `XPopupAttach<VM, VB>` | BubbleAttachPopupView | 锚点气泡弹窗 |
| `XPopupDrawer<VM, VB>` | DrawerPopupView | 侧边抽屉弹窗 |

所有基类提供：
- `mBinding: VB` — 自动 ViewBinding
- `mViewModel: VM` — 自动 ViewModel（通过泛型反射创建）
- `onSetup()` — 子类初始化抽象方法（Template Method 模式）
- `setPopupLifecycleListener()` — 可选生命周期回调（链式调用）

---

## 依赖说明

| 依赖 | 方式 | 版本 | 说明 |
|------|------|------|------|
| Gson | `api` | 2.14.0 | 公开 API 暴露 `Gson` 类型 |
| XPopup | `api` | 2.10.0 | 公开基类继承 XPopup 类型 |
| Billing | `api` | 8.3.0 | 公开 API 暴露 `Purchase`/`ProductDetails` 类型 |
| SmartRefreshLayout | `api` | 2.1.0 | 接入方直接使用刷新组件 |
| AppsFlyer | `api` | 6.18.0 | 接入方可直接使用归因 API |
| ThinkingData | `implementation` | 3.4.2 | SDK 类型仅内部使用 |
| Firebase Analytics | `implementation` | BOM 33.13.0 | SDK 类型仅内部使用 |

## ProGuard / R8

库已通过 `consumerProguardFiles` 自动传递混淆规则，接入方无需手动配置。

## Tech Stack

- Kotlin 2.0.21 · Android Library (compileSdk 35, minSdk 24, targetSdk 35)
- JVM Target 21 · DataBinding + ViewBinding
- JitPack 发布：`com.github.linseml:XGBG-BridgeService:1.0.16`

## License

Apache License 2.0
