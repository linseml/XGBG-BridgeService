---
description: 安全规范
paths: []
---

# 安全规范

- 图片处理必须防范 OOM，做好 Bitmap 回收
- 颜色禁止硬编码，定义在 `colors.xml`
- 字符串禁止硬编码，定义在 `strings.xml`
- AppsFlyer SDK 和 Firebase SDK 初始化参数禁止硬编码，通过 `ServiceHelper` 配置传入
- 发布到 JitPack 的 SDK 不得暴露宿主应用敏感信息（token、用户 ID 等）
