# 01: FsrsScheduler.reschedule() + ReviewService.rescheduleAllCards()

**Status:** `ready-for-agent`

## 范围

在 FsrsScheduler 新增 `reschedule()` 纯计算方法，在 ReviewService 新增 `rescheduleAllCards()` 异步编排方法。同步将 `initNewCard` 重命名为 `enchantCard`。

## 实现内容

### FsrsScheduler 变更

**重命名：initNewCard → enchantCard**
- 方法签名不变：`public CardState enchantCard(Instant now)`
- 语义不变：返回 Learning 状态（step=0, hasStability=false）
- 所有现有调用方同步改名（ReviewService.rateCard()）

**新增：reschedule()**
- 签名：`public CardState reschedule(List<ReviewLog> reviewLogs, Instant now)`
- 逻辑：
  1. `reviewLogs` 为空 → 返回 `createInitState(now)`
  2. 按 `reviewedAt` 升序排序
  3. `CardState card = enchantCard(reviewLogs.get(0).getReviewedAt())`
  4. for each log: `card = repeat(card, log.getRating(), log.getReviewedAt(), null)`
  5. 返回最终 card
- null fuzzSource → 确定性输出

### ReviewService 新增

**rescheduleAllCards(userId)**
- `@Async("optimizerExecutor")` 异步
- 流程：
  1. 查询该 userId 下所有有 ReviewLog 的卡片（`cardRepository.findCardsWithReviewLogs(userId)` 或通过 ReviewLogRepository 反查卡片 ID）
  2. 按 cardId 分组加载 ReviewLog
  3. 通过 Caffeine 缓存获取 `FsrsSchedulerConfig getConfig(userId)`
  4. `new FsrsScheduler(config)` 构建实例
  5. 每卡：`scheduler.reschedule(logs, Instant.now())` → 更新 Card FSRS 字段 → 收集到列表
  6. `cardRepository.saveAll(cards)` 批量保存
  7. `cacheManager.getCache("fsrsConfig").evict(userId)` 清除缓存

### CardRepository 扩展（若需要）
- 新增查询：`findCardsWithReviewLogs(userId)` — 返回该 userId 下有 ReviewLog 的所有卡片
- 或通过 ReviewLogRepository 获取去重 cardId 列表，再用 CardRepository.findById 逐张加载

### 命名同步（ReviewService）
- `rateCard()` 中 `FsrsScheduler.initNewCard(now)` → `scheduler.enchantCard(now)`

## 依赖
- P0 完成后的 FsrsScheduler 实例化 + Caffeine 缓存
- P4 完成后 `optimizerExecutor` 线程池可用
- P1 不触发 reschedule（见 PRD 触发点与适用性章节）

## 验证
- FsrsSchedulerTest 新增 5 个 reschedule 测试
- ReviewServiceTest 新增 2 个 rescheduleAllCards 测试
- FsrsSchedulerTest 中 `initNewCard` 调用改为 `enchantCard`（纯命名）
