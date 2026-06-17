---
name: jitpack-publish
description:
  自动执行 Android 库的 JitPack 发布流程（单次有效提交版）。版本升级通过 /git-commit 单独提交并推送；官方镜像切换仅以临时 commit 存在于 Tag 上，本地 reset 后瞬间无痕恢复开发环境。步骤 2 完成后所有操作免确认直接执行。TRIGGER when user mentions: 发布 JitPack, 打包 JitPack, 推送 JitPack, JitPack release。
---

你是一个 JitPack 发布助手。当用户调用 `/jitpack-publish` 或要求发布时，严格执行以下流程：

## 原则

- **main 分支提交必须干净**：只有版本递增和代码改动，绝不含 JitPack 构建环境切换。
- **官方镜像只存在于 Tag**：通过临时 commit 打 Tag，推送后本地回退，瞬间恢复开发环境。
- **本地环境即刻无痕恢复**：无论构建成败，均保持本地开发环境连贯，失败排查以 JitPack 云端 Log 为准。
- **步骤 2 完成后免确认**：镜像切换、打 Tag、轮询等后续操作均为自动化流程，无需用户逐条确认。

## 步骤

1. **获取并更新版本号**（Bump Version 与进位逻辑）：

   * 检查用户是否在指令中明确指定了目标版本号（例如 `1.0.3`）。
   * **如果未指定**：自动读取项目根目录下的 `gradle.properties` 文件，提取 `libVersion` 字段的当前版本号（例如 `libVersion=1.0.3`，格式假设为 `X.Y.Z`）。
   * **执行递增与进位运算**：
       * 将最后一位 `Z` (Patch) 加 1。
       * 如果 `Z > 99`，则 `Z` 归零 (`0`)，并将第二位 `Y` (Minor) 加 1。
       * 如果 `Y > 99`，则 `Y` 归零 (`0`)，并将第一位 `X` (Major) 加 1。
       * *(示例：`1.0.99` 递增为 `1.1.0`；`1.99.99` 递增为 `2.0.0`)*
   * **同步配置**：使用 `sed` 或 `Edit` 工具将新版本号写回 `gradle.properties` 的 `libVersion` 字段。两者均匹配白名单（`Bash(sed *)` / `Edit(gradle.properties)`），免确认。

2. **提交并推送版本升级**（调用 /git-commit skill）：

   * 调用 `/git-commit` skill 提交步骤 1 产生的版本号变更。
   * skill 会自动完成 add、生成中文约定式提交说明（如 `chore: 升级版本至 1.0.7`）、commit 和 push。
   * **此为 main 分支上的唯一有效提交。** 提交内容仅为版本递增，不含任何镜像切换变更。
   * **此步骤完成后，后续所有操作免确认直接执行。**

3. **切换官方构建环境**：

   * 使用 `sed` 命令修改项目配置（匹配 `Bash(sed *)` 白名单，免确认），将国内镜像（如腾讯镜像）替换为官方 `google()` 和 `mavenCentral()`。
   * 使用 `sed` 命令注释或移除可能导致纯依赖库报错的插件（如 `google-services`）。
   * **不要用 Edit 工具**——Edit 工具不在白名单中，会触发用户确认。
   * **注意：这些变更暂不提交，仅供步骤 4 的临时 Tag 使用。**

4. **打 Tag 并推送（临时 commit 机制，单条命令执行）**：

   核心思路：用一个临时 commit 携带官方镜像配置打 Tag → 推送 Tag → 本地回退临时 commit → 开发环境瞬间恢复，main 历史无痕。

   **必须合并为一条 Bash 命令执行，中间不停顿、不等待确认**：

   ```bash
   git add . && \
   git commit -m "chore: 临时切换官方镜像（仅供 JitPack Tag 使用）" && \
   git tag {版本号} && \
   git push origin {版本号} && \
   git reset HEAD~1 && \
   git checkout -- . && \
   git status
   ```

   - 若此前 Tag 带有 v 前缀请保持统一（如 `v1.1.0`）。
   - 执行完毕后 HEAD 回到步骤 2 的版本升级提交（国内镜像），工作区完全干净。

5. **触发构建并逐次轮询状态（每次查询单独 Bash 调用，醒目输出）**：

   **不要用 Monitor，不要用 while 循环脚本。** 原因：
   - **Monitor**：事件只作为后台通知推送，不会醒目显示在终端主对话区，用户看不到中间查询结果。
   - **while 循环脚本用 Bash**：缓冲全部输出直到脚本结束才一次性返回，中间查询结果同样看不到。

   **必须逐次调用 Bash，每次一条命令，醒目输出格式化结果。** 命令以 `sleep 3 && curl` 开头，匹配白名单 `Bash(sleep *)`，免确认直接执行：

   ```bash
   sleep 3 && curl -s "https://jitpack.io/api/builds/com.github.{用户名}.{仓库名}/{版本号}"
   ```

   **每次查询后的处理流程**：

   - 读取 curl 返回的 JSON，提取 `status` 字段值。
   - **醒目输出格式化结果**（直接写在回复文本中，确保用户在终端主对话区能看到）：
     ```
     第 {N} 次查询 | {HH:MM:SS} | {status} → {✅ 成功 / ❌ 失败 / ⏳ 构建中}
     ```
   - 状态映射：`"ok"` → ✅ 成功，`"Error"` → ❌ 失败，其他 → ⏳ 构建中
   - **如果 `ok` 或 `Error`**：停止轮询，进入步骤 6。
     - 如果 `Error`：额外调用 `curl -s "https://jitpack.io/api/builds/com.github.{用户名}.{仓库名}/{版本号}/log"` 获取构建日志，提取关键错误信息。
   - **如果是其他值**：立即发起下一次查询（sleep 3 + curl）。
   - **无限轮询**：没有次数上限，持续查询直到出结果。

6. **输出最终结果**：

   根据步骤 5 的脚本输出判断最终状态（只有成功或失败两种），提供 JitPack 构建详情链接：
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
