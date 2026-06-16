---
name: jitpack-publish
description:
  自动执行 Android 库的 JitPack 发布流程。包含自动读取和递增 gradle.properties 中的 libVersion（支持满百进位）、切换官方镜像并打 Tag 推送，解决国内镜像源导致的 JitPack 构建失败问题，避免构建时序冲突。TRIGGER when user mentions: 发布 JitPack, 打包 JitPack, 推送 JitPack, JitPack release。
---

你是一个 JitPack 发布助手。当用户调用 `/jitpack-publish` 或要求发布时，严格执行以下流程：

## 步骤

1. **获取并更新版本号（Bump Version 与进位逻辑）**：

* 检查用户是否在指令中明确指定了目标版本号（例如 `1.0.3`）。
* **如果未指定**：自动读取项目根目录下的 `gradle.properties` 文件，提取 `libVersion` 字段的当前版本号（例如
  `libVersion=1.0.3`，提取出 `1.0.3`，格式假设为 `X.Y.Z`）。
* **执行递增与进位运算**：
    * 将最后一位 `Z` (Patch) 加 1。
    * 如果 `Z > 99`，则 `Z` 归零 (`0`)，并将第二位 `Y` (Minor) 加 1。
    * 如果 `Y > 99`，则 `Y` 归零 (`0`)，并将第一位 `X` (Major) 加 1。
    * *(示例：`1.0.99` 递增为 `1.1.0`；`1.99.99` 递增为 `2.0.0`)*
* **同步配置**：将计算后的新版本号严格写回并覆盖到 `gradle.properties` 文件中的 `libVersion` 字段。

2. **切换官方构建环境**：

* 修改项目配置，将国内镜像（如腾讯镜像）替换为官方 `google()` 和 `mavenCentral()`。
* 注释或移除可能导致纯依赖库报错的插件（如 `google-services`）。

3. **提交快照并打 Tag（防冲突核心步骤）**：

* 提交代码：`git add .` 并在 commit message 中注明 `chore: prepare for JitPack release v{版本号}`。
* 创建标签：`git tag {版本号}` (若带有 v 前缀请保持统一，如 `v1.1.0`)。
* **推送标签至远端**：`git push origin {版本号}` （此步骤确保 JitPack 抓取到的绝对是带有官方镜像的干净代码快照）。

4. **恢复本地开发环境**：

* 将镜像重新改回腾讯镜像，并恢复之前注释的插件。
* 提交代码：`git add .` 并在 commit message 中注明 `chore: restore environment after release`。
* 推送主分支：`git push origin main`。

5. **触发与结果输出**：

* 通知用户代码及 Tag 已成功推送。
* （可选）自动调用 JitPack API 预热构建：
  `curl -s "https://jitpack.io/api/builds/com.github.{用户名}.{仓库名}/{版本号}"`
* 用以下格式输出结果：
  ```
  ==================== JitPack 发布就绪 ====================
  发布版本: {版本号} (已自动递增并更新至 gradle.properties)
  Tag 推送状态: 成功
  环境恢复状态: 成功 (已切回国内镜像并推送到 main)
  👉 请前往 JitPack 官网查看构建进度或等待依赖生效。
  ========================================================
  ```