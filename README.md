# XGBG-BridgeService

Android bridge service library for DDMH XGBG.

## Features

- AppsFlyer 归因集成
- Google Pay 支持
- Firebase 集成
- ThinkingAnalytics (数数) SDK 集成
- Base ViewModel / Activity / Fragment 基类
- Gson / Resource / Date 等常用扩展工具

## Usage

### Add JitPack repository

In your root `build.gradle`:

```groovy
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

Or in `settings.gradle` (Gradle 7+):

```groovy
dependencyResolutionManagement {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

### Add dependency

```groovy
implementation 'com.github.YOUR_GITHUB_USERNAME:XGBG-BridgeService:1.0.0'
```

implementation 'com.github.Leesin:XGBG-BridgeService:1.0.0'

## License

Apache License 2.0
