# GooglePay 实现方案

## 需求
- 支持内购（一次性商品：消耗型 + 非消耗型）和订阅购买
- 支付结果通过接口回调回传给接入方
- 仅客户端消耗（consume 消耗型 / acknowledge 非消耗型 & 订阅）

## 设计

### 1. 新建回调接口 — `OnPayResultListener`

路径: `interfaces/OnPayResultListener.kt`

```kotlin
interface OnPayResultListener {
    fun onPurchaseSuccess(purchase: Purchase)       // 购买成功
    fun onPurchaseError(code: Int, message: String)  // 购买失败
    fun onPurchaseCancelled()                         // 用户取消
}
```

- 接入方实现此接口来接收支付结果
- 所有方法无默认实现，强制接入方处理所有场景

### 2. 改造 `GooglePay`

路径: `helper/GooglePay.kt`

核心功能模块：

**a) 初始化 BillingClient**
```kotlin
fun init()
```
- 创建 BillingClient 并建立连接
- BillingClient 连接成功后自动查询支持的购买类型
- 连接断开时自动重连

**b) 查询商品详情**
```kotlin
fun querySkuDetails(skuIds: List<String>, skuType: String, callback: ((List<SkuDetails>) -> Unit)?)
```
- skuType: BillingClient.SkuType.INAPP (内购) / BillingClient.SkuType.SUBS (订阅)
- 查询成功后通过 callback 返回 SkuDetails 列表（可选，供接入方展示价格等）

**c) 拉起支付（核心方法）**
```kotlin
fun launchPay(skuId: String, skuType: String, listener: OnPayResultListener)
```
- 先查询 SkuDetails，再通过 BillingFlowParams 拉起 Google Play 支付界面
- skuType 区分内购 / 订阅
- listener 接收支付结果

**d) 消耗/确认购买**
```kotlin
fun consumePurchase(purchase: Purchase)       // 消耗型商品 — consumeAsync
fun acknowledgePurchase(purchase: Purchase)   // 非消耗型/订阅 — acknowledgeAsync
```
- 消耗型商品必须 consume 后才能再次购买
- 非消耗型商品和订阅必须 acknowledge，否则 3 天后 Google 自动退款

**e) 查询已购买商品（补单/恢复购买）**
```kotlin
fun queryPurchases(skuType: String, callback: ((List<Purchase>) -> Unit)?)
fun restorePurchases(listener: OnPayResultListener?)  // 订阅恢复
```

**f) PurchasesUpdatedListener**
- 内部实现 BillingClient 的购买更新监听
- 根据购买结果分发到当前注册的 OnPayResultListener
- 自动处理消耗/确认（可选），或交给接入方在 onPurchaseSuccess 中手动调用

### 3. 生命周期管理
- BillingClient 在 `init()` 时创建并连接
- 支持多次调用 launchPay（每次可更换 listener）
- 保存当前活跃的 listener，在 purchasesUpdated 中回调
- 连接断开自动重连

### 4. 日志
- 全部使用 LogX.d(TAG, ...) / LogX.e(TAG, ...) 输出关键节点日志
- 关键节点：初始化、连接状态变化、查询商品、拉起支付、购买结果、消耗/确认

## 文件清单

| 文件 | 操作 | 说明 |
|---|---|---|
| `interfaces/OnPayResultListener.kt` | 新建 | 支付结果回调接口 |
| `helper/GooglePay.kt` | 重写 | Google Play Billing 完整实现 |

## 不涉及的改动
- AFHelper / AEHelper / AppHelper / Constant — 不改动
- build.gradle — billing:8.3.0 依赖已存在，无需新增
