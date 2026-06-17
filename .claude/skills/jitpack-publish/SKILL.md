---
name: jitpack-publish
description:
  自动执行 Android 库的 JitPack 发布流程（单次有效提交版）。版本升级通过 /git-commit 单独提交并推送；官方镜像切换仅以临时 commit 存在于 Tag 上，本地 reset 后瞬间无痕恢复开发环境。TRIGGER when user mentions: 发布 JitPack, 打包 JitPack, 推送 JitPack, JitPack release。
---

你是一个 JitPack 发布助手。当用户调用 `/jitpack-publish` 或要求发布时，严格执行以下流程：

## 原则

- **main 分支提交必须干净**：只有版本递增和代码改动，绝不含 JitPack 构建环境切换。
- **官方镜像只存在于 Tag**：通过临时 commit 打 Tag，推送后本地回退，瞬间恢复开发环境。
- **本地环境即刻无痕恢复**：无论构建成败，均保持本地开发环境连贯，失败排查以 JitPack 云端 Log 为准。

## 步骤

1. **获取并更新版本号**（Bump Version 与进位逻辑）：

   * 检查用户是否在指令中明确指定了目标版本号（例如 `1.0.3`）。
   * **如果未指定**：自动读取项目根目录下的 `gradle.properties` 文件，提取 `libVersion` 字段的当前版本号（例如 `libVersion=1.0.3`，格式假设为 `X.Y.Z`）。
   * **执行递增与进位运算**：
       * 将最后一位 `Z` (Patch) 加 1。
       * 如果 `Z > 99`，则 `Z` 归零 (`0`)，并将第二位 `Y` (Minor) 加 1。
       * 如果 `Y > 99`，则 `Y` 归零 (`0`)，并将第一位 `X` (Major) 加 1。
       * *(示例：`1.0.99` 递增为 `1.1.0`；`1.99.99` 递增为 `2.0.0`)*
   * **同步配置**：将计算后的新版本号严格写回并覆盖到 `gradle.properties` 文件中的 `libVersion` 字段。

2. **提交并推送版本升级**（调用 /git-commit skill）：

   * 调用 `/git-commit` skill 提交步骤 1 产生的版本号变更。
   * skill 会自动完成 add、生成中文约定式提交说明（如 `chore: 升级版本至 1.0.7`）、commit 和 push。
   * **此为 main 分支上的唯一有效提交。** 提交内容仅为版本递增，不含任何镜像切换变更。

3. **切换官方构建环境**：

   * 修改项目配置，将国内镜像（如腾讯镜像）替换为官方 `google()` 和 `mavenCentral()`。
   * 注释或移除可能导致纯依赖库报错的插件（如 `google-services`）。
   * **注意：这些变更暂不提交，仅供步骤 4 的临时 Tag 使用。**

4. **打 Tag 并推送（临时 commit 机制）**：

   核心思路：用一个临时 commit 携带官方镜像配置打 Tag → 推送 Tag → 本地回退临时 commit → 开发环境瞬间恢复，main 历史无痕。

   * **4a. 创建临时 commit**：
     ```bash
     git add .
     git commit -m "chore: 临时切换官方镜像（仅供 JitPack Tag 使用）"
     ```
   * **4b. 打 Tag 并推送**：
     ```bash
     git tag {版本号}               # 若此前带有 v 前缀请保持统一，如 v1.1.0
     git push origin {版本号}       # Tag 指向临时 commit，JitPack 从此构建
     ```
   * **4c. 回退临时 commit，瞬间恢复开发环境**：
     ```bash
     git reset HEAD~1              # 临时 commit 立即消失，镜像变更回到工作区（unstaged）
     git checkout -- .             # 从 HEAD 恢复所有文件，工作区瞬间回到国内镜像状态，干净无痕
     ```

   回退后：HEAD 回到步骤 2 的版本升级提交（国内镜像），工作区完全干净——无需手动编辑恢复。

5. **触发构建并轮询状态**：

   * 自动调用 JitPack API 触发构建：
     `curl -s "https://jitpack.io/api/builds/com.github.{用户名}.{仓库名}/{版本号}"`
   * **轮询构建状态**：每隔 **10 秒** 查询一次 JitPack 构建状态，最多轮询 **30 次**（总计 5 分钟）：
     * 调用 `curl -s "https://jitpack.io/api/builds/com.github.{用户名}.{仓库名}/{版本号}"` 获取构建状态 JSON。
     * 从返回的 JSON 中提取本次版本 `{版本号}` 对应的状态字段值。
     * **每次查询后必须向用户输出当前结果**，同时展示 API 原始返回值和可读状态，格式为：
       ```
       第 1 次查询（10秒）: none → ⏳ 构建中
       第 2 次查询（20秒）: none → ⏳ 构建中
       第 5 次查询（50秒）: ok   → ✅ 成功
       ```
       其中 API 返回值与可读状态的映射：
       - `"ok"` → ✅ 成功
       - `"Error"` → ❌ 失败
       - 其他（如 `none`、`pending`） → ⏳ 构建中
     * **状态判断**：
       - `"ok"` → 构建成功，立即停止轮询，输出成功结果。
       - `"Error"` → 构建失败，立即停止轮询，并**获取失败原因**：
         * 调用 `curl -s "https://jitpack.io/api/builds/com.github.{用户名}.{仓库名}/{版本号}/log"` 获取构建日志。
         * 从日志中提取关键错误信息（如编译错误、依赖缺失、插件冲突等），整理后告知用户。
       - 其他值或版本号未出现在返回 JSON 中 → 构建仍在进行中，继续轮询。
     * 超过 30 次轮询仍未得到 `"ok"` 或 `"Error"` → 视为超时，告知用户构建时间过长，建议手动查看。

6. **输出最终结果**：

   所有三种结果均需提供 JitPack 构建详情链接，供用户自主查看：
   `https://jitpack.io/#{用户名}/{仓库名}/{版本号}`

   * **构建成功时**：
     ```
     ==================== JitPack 发布成功 ====================
     发布版本: {版本号} (已自动递增并更新至 gradle.properties)
     main 提交: ✅ 版本升级（通过 /git-commit），不含镜像切换
     Tag: ✅ 推送成功（临时 commit 含官方镜像，仅供 JitPack 构建）
     本地环境: ✅ 已无痕恢复（git reset + checkout，工作区干净）
     JitPack 构建状态: ✅ 成功
     依赖引用: implementation 'com.github.{用户名}.{仓库名}:{版本号}'
     构建详情: https://jitpack.io/#{用户名}/{仓库名}/{版本号}
     ========================================================
     ```

   * **构建失败时**：
     ```
     ==================== JitPack 发布失败 ====================
     发布版本: {版本号} (已自动递增并更新至 gradle.properties)
     main 提交: ✅ 版本升级（通过 /git-commit），不含镜像切换
     Tag: ✅ 推送成功（临时 commit 含官方镜像，仅供 JitPack 构建）
     本地环境: ✅ 已无痕恢复（git reset + checkout，工作区干净）
     JitPack 构建状态: ❌ 失败
     失败原因: {从构建日志中提取的关键错误信息摘要}
     💡 建议: 请根据上述失败原因排查问题，修复后重新执行 /jitpack-publish 发布新版本。
     构建详情: https://jitpack.io/#{用户名}/{仓库名}/{版本号}
     ========================================================
     ```

   * **构建超时时**：
     ```
     ==================== JitPack 发布超时 ====================
     发布版本: {版本号} (已自动递增并更新至 gradle.properties)
     main 提交: ✅ 版本升级（通过 /git-commit），不含镜像切换
     Tag: ✅ 推送成功（临时 commit 含官方镜像，仅供 JitPack 构建）
     本地环境: ✅ 已无痕恢复（git reset + checkout，工作区干净）
     JitPack 构建状态: ⏳ 超时（5 分钟内未完成）
     👉 请前往构建详情页手动查看进度。
     构建详情: https://jitpack.io/#{用户名}/{仓库名}/{版本号}
     ========================================================
     ```
