# 01: FsrsScheduler.preview() + ReviewService.previewCard()

**Status:** `ready-for-agent`

## 范围

在 FsrsScheduler 新增 `preview()` 公开方法，在 ReviewService 新增 `previewCard()` 方法。

## 实现内容

### FsrsScheduler.preview()
- 签名：`public Map<Rating, CardState> preview(CardState card, Instant now)`
- 对 AGAIN/HARD/GOOD/EASY 四种评分各调用一次 `repeat(card, rating, now, null)`
- `null` fuzzSource → 确定性输出，不含 fuzz
- 返回 `EnumMap<Rating, CardState>`（有序、类型安全）
- 纯计算方法，不依赖任何外部服务

### ReviewService.previewCard()
- 签名：`public Map<Rating, CardState> previewCard(Card card, Instant now)`
- 从 Caffeine 缓存获取 `FsrsSchedulerConfig getConfig(card.getUserId())`
- `new FsrsScheduler(config)` 构建 Scheduler
- 复用现有的 `buildCardState(card)` 逻辑（从 rateCard 提取为私有方法）
- 调用 `scheduler.preview(cardState, now)` 返回结果

### Code cleanup
- 将 `rateCard()` 中构建 CardState 的逻辑提取为 `buildCardState(Card card)` 私有方法，供 `rateCard()` 和 `previewCard()` 共用

## 依赖
P0 完成后的 FsrsScheduler 实例化 + Caffeine 缓存

## 验证
- 单元测试追加到 FsrsSchedulerTest
- 集成测试追加到 ReviewServiceTest
