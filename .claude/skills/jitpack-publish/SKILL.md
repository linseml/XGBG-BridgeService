---
name: jitpack-publish
description:
  自动执行 Android 库的 JitPack 发布流程。包含自动读取和递增 gradle.properties 中的 libVersion（支持满百进位）、切换官方镜像并打 Tag 推送、轮询构建状态直至成功或失败并输出原因，解决国内镜像源导致的 JitPack 构建失败问题，避免构建时序冲突。TRIGGER when user mentions: 发布 JitPack, 打包 JitPack, 推送 JitPack, JitPack release。
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

* 提交代码：`git add .`，commit message 需遵循 `/git-commit` skill 的约定式提交规范（`<type>: <description>`），根据实际 diff 生成说明（例如 `chore: bump version to 1.0.4 and switch to official Gradle mirror for JitPack release`）。
* 创建标签：`git tag {版本号}` (若带有 v 前缀请保持统一，如 `v1.1.0`)。
* **推送标签至远端**：`git push origin {版本号}` （此步骤确保 JitPack 抓取到的绝对是带有官方镜像的干净代码快照）。

4. **恢复本地开发环境**：

* 将镜像重新改回腾讯镜像，并恢复之前注释的插件。
* 提交代码：`git add .`，commit message 遵循约定式提交规范，此时 diff 只有镜像回切一项，如 `chore: 切回腾讯 Gradle 镜像（本地开发用）`。
* 推送主分支：`git push origin main`。

5. **触发构建并轮询状态（发布验证核心步骤）**：

* 自动调用 JitPack API 触发构建：
  `curl -s "https://jitpack.io/api/builds/com.github.{用户名}.{仓库名}/{版本号}"`
* **轮询构建状态**：每隔 **10 秒** 查询一次 JitPack 构建状态，最多轮询 **30 次**（总计 5 分钟）：
  * 调用 `curl -s "https://jitpack.io/api/builds/com.github.{用户名}.{仓库名}/{版本号}"` 获取构建状态 JSON。
  * 从返回的 JSON 中提取本次版本 `{版本号}` 对应的状态字段值。
  * **状态判断**：
    - `"ok"` → 构建成功，立即停止轮询，输出成功结果。
    - `"Error"` → 构建失败，立即停止轮询，并**获取失败原因**：
      * 调用 `curl -s "https://jitpack.io/api/builds/com.github.{用户名}.{仓库名}/{版本号}/log"` 获取构建日志。
      * 从日志中提取关键错误信息（如编译错误、依赖缺失、插件冲突等），整理后告知用户。
    - 其他值或版本号未出现在返回 JSON 中 → 构建仍在进行中，继续轮询。
  * 超过 30 次轮询仍未得到 `"ok"` 或 `"Error"` → 视为超时，告知用户构建时间过长，建议手动查看。

6. **输出最终结果**：

* **构建成功时**：
  ```
  ==================== JitPack 发布成功 ====================
  发布版本: {版本号} (已自动递增并更新至 gradle.properties)
  Tag 推送状态: 成功
  环境恢复状态: 成功 (已切回国内镜像并推送到 main)
  JitPack 构建状态: ✅ 成功
  依赖引用: implementation 'com.github.{用户名}.{仓库名}:{版本号}'
  ========================================================
  ```

* **构建失败时**：
  ```
  ==================== JitPack 发布失败 ====================
  发布版本: {版本号} (已自动递增并更新至 gradle.properties)
  Tag 推送状态: 成功
  环境恢复状态: 成功 (已切回国内镜像并推送到 main)
  JitPack 构建状态: ❌ 失败
  失败原因: {从构建日志中提取的关键错误信息摘要}
  💡 建议: 请根据上述失败原因排查问题，修复后重新执行 /jitpack-publish 发布新版本。
  ========================================================
  ```

* **构建超时时**：
  ```
  ==================== JitPack 发布超时 ====================
  发布版本: {版本号} (已自动递增并更新至 gradle.properties)
  Tag 推送状态: 成功
  环境恢复状态: 成功 (已切回国内镜像并推送到 main)
  JitPack 构建状态: ⏳ 构建超时（5 分钟内未完成）
  👉 请前往 https://jitpack.io/#{用户名}/{仓库名}/{版本号} 手动查看构建进度。
  ========================================================
  ```
