# 05: ReviewService 适配 + 洗牌查询

**Status:** `ready-for-agent`

## 范围

改造 `ReviewService.rateCard()` 使用新的实例化 Scheduler，并在 `getNextCard()` 中实现洗牌功能。同时改造 `CardRepository` 新增洗牌查询方法。

## 实现内容

### ReviewService.rateCard() 改造
- 新增注入 `FsrsParametersRepository`
- rateCard 流程：
  1. 读取 Card（不变）
  2. 构建 FsrsSchedulerConfig（通过 Cache → 见 Issue 06）
  3. 构建 CardState：新卡(cardState==0) 用 `initNewCard()`，已有卡用 CardState record 构造（不变）
  4. `new FsrsScheduler(config).repeat(inputState, rating, now, aleaPrng::next)`（替代 static 调用）
  5. 更新 Card 字段 + 创建 ReviewLog（不变）
- 移除对 `FsrsScheduler.repeat()` 静态方法的直接调用

### ReviewService.getNextCard() — 洗牌功能
- 新增读取 `UserPreferences.shuffleDueCards`
- STANDARD 模式：
  - 洗牌 ON → 调用 `findRandomDueCardByDeckId` + `findRandomNewCardByDeckId`
  - 洗牌 OFF → 保持现有 `findFirstDueCardByDeckId` + `findFirstNewCardByDeckId`
- REVIEW_ONLY 模式：
  - 洗牌 ON → 调用 `findRandomDueCardByDeckId`
  - 洗牌 OFF → 保持现有 `findFirstDueCardByDeckId`
- NEW_ONLY 模式：
  - 洗牌 ON → 调用 `findRandomNewCardByDeckId`
  - 洗牌 OFF → 保持现有 `findFirstNewCardByDeckId`
- CRAM 模式：已是随机，不变

### CardRepository 新增查询
- `findRandomDueCardByDeckId(deckId, now)` — 原生查询 `SELECT * FROM cards c JOIN card_tags ct ... WHERE due <= :now ORDER BY RAND() LIMIT 1`
- `findRandomNewCardByDeckId(deckId)` — 原生查询 `SELECT * FROM cards c JOIN card_tags ct ... WHERE c.card_state = 0 ORDER BY RAND() LIMIT 1`
- 同样方法用于 due 和新卡的 ID 查询版本（Issue 讨论过但最终选了 B 方案的原生 RAND）

### FlashcardService / CardBatchService
- `createInitState()` 保持为 `FsrsScheduler.createInitState()` 静态调用 — 无改动
- 如果用到了 `FsrsScheduler.initNewCard()` 则改为 `new FsrsScheduler(defaults).initNewCard()`

### 测试适配
- `ReviewServiceTest`：mock FsrsParametersRepository；验证洗牌 ON/OFF 时调用正确的 Repository 方法
- 新增测试：shuffleDueCards=true 时验证随机查询被调用

## 依赖
- Issue 03（UserPreferences 新增字段，含 shuffleDueCards）
- Issue 04（FsrsScheduler 实例化改造）
- Issue 06（Caffeine 缓存，用于获取 config）

## 验证
- `mvn test` 通过
- ReviewServiceTest 验证 rateCard 使用新 Scheduler
- 手动测试（启动 local profile，复习时观察洗牌行为）
