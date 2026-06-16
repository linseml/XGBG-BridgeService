# XGBG-BridgeService

Android bridge service library for DDMH XGBG — 将 AppsFlyer 归因、Firebase Analytics、数数（ThinkingData）SDK、Google Play Billing、SmartRefreshLayout、XPopup 等第三方 SDK 统一封装为简洁的 Helper API，接入方只需一行初始化即可完成集成。

## Features

| 功能 | Helper | 封装的 SDK |
|------|--------|-----------|
| 归因 & 内购验证 | `AFHelper` | AppsFlyer SDK 6.18.0 |
| 事件上报 | `AEHelper` | ThinkingData (数数) SDK 3.4.2 |
| 事件上报 & 用户属性 | `FirebaseHelper` | Firebase Analytics (BOM 33.13.0) |
| Google Play 支付 | `GooglePay` | BillingClient 8.3.0 |
| 刷新文案国际化 | `SmartRefreshHelper` | SmartRefreshLayout 2.1.0 |
| 弹窗基类 | `XPopupBottom` 等 | XPopup 2.10.0 |
| 全局上下文 & 环境标记 | `AppHelper` | — |
| Gson / 资源 / 日期等扩展 | `GsonExt` / `ResourceExt` / `DateUtils` | Gson 2.14.0 |

## Quick Start

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

或根 `build.gradle`（旧版）：

```groovy
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
        maven { url 'https://artifact.thinkingdata.io/release' }
    }
}
```

### 2. 添加依赖

根 `build.gradle`：

```groovy
buildscript {
    dependencies {
        // Firebase google-services 插件（如需 Firebase Analytics）
        classpath 'com.google.gms:google-services:4.4.2'
    }
}
```

app 模块 `build.gradle`：

```groovy
apply plugin: 'com.google.gms.google-services'  // Firebase（如需 Firebase Analytics）

dependencies {
    implementation 'com.github.linseml:XGBG-BridgeService:1.0.2'
}
```

### 3. Firebase 配置（仅 Firebase Analytics 需要）

1. 从 [Firebase Console](https://console.firebase.google.com/) → 项目设置 → 下载 `google-services.json`
2. 将 `google-services.json` 放入 app 模块根目录（与 `build.gradle` 同级）

### 4. 初始化

在自定义 `Application.onCreate()` 中按以下顺序初始化：

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // ⚠️ 必须最先调用 — 后续所有 Helper 依赖此上下文
        AppHelper.init(this, BuildConfig.DEBUG)

        // AppsFlyer 归因
        AFHelper.initSDK("你的 AppsFlyer Dev Key")
        AFHelper.startAppsFlyer(mapOf("channel" to "google_play"))
        AFHelper.startAFObservingTransactions()

        // 数数 (ThinkingData) 事件上报
        AEHelper.initSDK("你的数数 appId", "你的数数 serverUrl")
        AEHelper.setCommonProperties(JSONObject().apply {
            put("channel", "google_play")
            put("version", "1.0.2")
        })

        // Firebase Analytics 事件上报
        FirebaseHelper.initSDK()
        FirebaseHelper.setCommonProperties(hashMapOf("channel" to "google_play", "version" to "1.0.2"))

        // SmartRefreshLayout 刷新文案
        SmartRefreshHelper.initSmartRefreshLanguage()

        // Google Play Billing 支付
        GooglePay.init()
    }
}
```

## Helper API

### AppHelper — 全局上下文

```kotlin
AppHelper.init(application, BuildConfig.DEBUG)  // 初始化（必须最先调用）
AppHelper.getApplication()                       // 获取全局 Application
AppHelper.isDebug()                              // 是否 Debug 环境
```

### AFHelper — AppsFlyer 归因

```kotlin
AFHelper.initSDK("af_dev_key")                                     // 初始化 SDK + 归因回调
AFHelper.startAppsFlyer(mapOf("channel" to "google_play"))         // 启动上报 + 附加自定义参数
AFHelper.startAFObservingTransactions()                            // 监听内购 & 订阅交易自动上报
```

### AEHelper — 数数 (ThinkingData) 事件上报

```kotlin
AEHelper.initSDK("appId", "serverUrl")                             // 初始化 SDK
AEHelper.track("event_name")                                       // 上报事件（无参数）
AEHelper.track("event_name", hashMapOf("key" to "value"))          // 上报事件（带参数）
AEHelper.setCommonProperties(jsonObject)                           // 设置公共属性（后续每条事件自动携带）
```

### FirebaseHelper — Firebase Analytics 事件上报

```kotlin
FirebaseHelper.initSDK()                                           // 初始化 SDK
FirebaseHelper.track("event_name")                                 // 上报事件（无参数）
FirebaseHelper.track("event_name", hashMapOf("key" to "value"))    // 上报事件（带 HashMap 参数）
FirebaseHelper.track("event_name", bundle)                         // 上报事件（带 Bundle 参数）
FirebaseHelper.setCommonProperties(bundle)                         // 设置公共事件属性（Bundle）
FirebaseHelper.setCommonProperties(hashMapOf("key" to "value"))    // 设置公共事件属性（HashMap）
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

// 拉起支付
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
GooglePay.restorePurchases(object : OnPayResultListener { ... })
```

### SmartRefreshHelper — SmartRefreshLayout 刷新文案

```kotlin
SmartRefreshHelper.initSmartRefreshLanguage()                      // 替换 ClassicsHeader/Footer 为中文文案
```

接入方如需自定义文案，在 app 模块的 `strings.xml` 中覆盖同名资源即可：

```xml
<string name="srl_header_pulling">自定义下拉文案</string>
```

## 依赖说明

| 依赖 | 方式 | 说明 |
|------|------|------|
| Gson 2.14.0 | `api` | 公开 API 暴露 `Gson` 类型（`GsonHolder.instance`） |
| XPopup 2.10.0 | `api` | 公开基类继承 XPopup 类型 |
| Billing 8.3.0 | `api` | 公开 API 暴露 `Purchase`/`ProductDetails` 类型 |
| AppsFlyer 6.18.0 | `implementation` | SDK 类型仅内部使用，接入方无需直接引用 |
| ThinkingData 3.4.2 | `implementation` | SDK 类型仅内部使用 |
| Firebase Analytics | `implementation` | SDK 类型仅内部使用 |
| SmartRefreshLayout 2.1.0 | `implementation` | SDK 类型仅内部使用（反射） |

## ProGuard / R8

库已通过 `consumerProguardFiles` 自动将混淆规则传递给接入方，无需手动配置。

如接入方启用 `minifyEnabled true`，库的 keep 规则会自动合并，确保公开 API 和第三方 SDK 不被混淆。

## Tech Stack

- Kotlin 2.0.21
- Android Library (compileSdk 35, minSdk 24, targetSdk 35)
- JVM Target 21
- DataBinding + ViewBinding
- JitPack 发布 (`com.ddmh:bridge-service:1.0.2`)

## License

Apache License 2.0
