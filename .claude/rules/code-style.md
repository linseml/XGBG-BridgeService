---
description: 代码风格与命名规范
paths:
  - "**/*.kt"
  - "**/*.java"
---

# 代码风格

## 语言

- 新代码统一使用 Kotlin，禁止新增 Java 文件
- 优先使用 data class 表示数据模型
- 优先使用 Kotlin 标准库和扩展函数

## 格式

- 使用 4 空格缩进
- 命名遵循 Android 官方规范

## 强制规则

- 使用 ViewBinding（`mBinding`），禁止 `findViewById`
- 使用 `LogX`，禁止 `android.util.Log`
- 使用 `GsonHolder.gson` 或 `GsonExt`（`toJson()/toBean()`），禁止直接 `new Gson()`
- 使用 `ResourceExt` 扩展（`dp()/sp()/color()/string()` 等），禁止硬编码数值
- 协程使用 `viewModelScope` 或 `lifecycleScope`，禁止裸 `GlobalScope`

## import 完整性

生成的代码必须包含所有必要的 import 语句：
- `android.view.View`（使用 View.VISIBLE/View.GONE 时）
- `android.content.Intent`（页面跳转时）

## 命名规范

| 类型 | 格式 | 示例 |
|---|---|---|
| 弹窗 | `{功能}Popup` | `PayResultPopup` |
| Helper | `{功能}Helper` | `AFHelper`、`FirebaseHelper` |
| 工具类 | `{功能}Utils` | `DateUtils`、`MediaUtils` |
| 接口 | `On{功能}Listener` | `OnPayResultListener` |
| Bean | `{功能}Bean` | `PayResultBean` |
| 弹窗布局 | `popup_{功能}.xml` | `popup_pay_result.xml` |
