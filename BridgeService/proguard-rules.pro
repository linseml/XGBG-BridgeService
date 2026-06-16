# BridgeService Library ProGuard/R8 Rules
# 保持所有公开 API 不被混淆，确保使用者能正常调用

# ========================
# 基类
# ========================
-keep class com.ddmh.bridge.service.BaseApplication { *; }
-keep class com.ddmh.bridge.service.base.BaseVMActivity { *; }
-keep class com.ddmh.bridge.service.base.BaseVMFragment { *; }
-keep class com.ddmh.bridge.service.base.BaseViewModel { *; }

# ========================
# Helper 单例对象
# ========================
-keep class com.ddmh.bridge.service.helper.AEHelper { *; }
-keep class com.ddmh.bridge.service.helper.AFHelper { *; }
-keep class com.ddmh.bridge.service.helper.AppHelper { *; }
-keep class com.ddmh.bridge.service.helper.FirebaseHelper { *; }
-keep class com.ddmh.bridge.service.helper.GooglePay { *; }

# ========================
# Utils 单例对象
# ========================
-keep class com.ddmh.bridge.service.utils.DateUtils { *; }
-keep class com.ddmh.bridge.service.utils.GsonHolder { *; }
-keep class com.ddmh.bridge.service.utils.LogX { *; }
-keep class com.ddmh.bridge.service.utils.MediaUtils { *; }

# ========================
# Kotlin 顶层函数和扩展
# ========================
# Kotlin 顶层函数编译为以文件名+Kt命名的类
-keep class com.ddmh.bridge.service.ext.GsonExtKt { *; }
-keep class com.ddmh.bridge.service.ext.ResourceExtKt { *; }

# ========================
# 第三方 SDK 混淆规则
# ========================

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
# Gson 与泛型 - 防止擦除类型信息
-keep class * {
    <fields>;
    public <methods>;
}

# AppsFlyer SDK
-keep class com.appsflyer.** { *; }
-dontwarn com.appsflyer.**

# Google Install Referrer
-keep class com.android.installreferrer.** { *; }
-dontwarn com.android.installreferrer.**

# AppsFlyer Purchase Connector
-keep class com.appsflyer.purchase.** { *; }
-dontwarn com.appsflyer.purchase.**

# ThinkingAnalytics (数数) SDK
-keep class cn.thinkingdata.android.** { *; }
-dontwarn cn.thinkingdata.android.**
