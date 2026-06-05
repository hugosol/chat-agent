# PRD: 卡片 Reschedule —— 从复习历史重建 FSRS 状态

**Status:** `ready-for-agent`

## Problem Statement

Learner 修改 FSRS 参数（如 learning_steps、desired_retention）后，已有卡片的 FSRS 状态仍基于旧参数计算。如果不做干预，每张卡片需要等到被再次复习时才能自然过渡到新参数——对于一个有数百张卡片、平均间隔数周乃至数月的 Deck，全部收敛可能需要数月。

Learner 需要一种机制：用当前 Scheduler 参数即时重算所有复习过的卡片状态，使参数变更立即生效于全部卡片。

## Solution

在 `FsrsScheduler` 上新增 `reschedule()` 公开方法——输入卡片的全部 ReviewLog 历史，用当前 Scheduler 参数从头模拟所有复习，返回"如果一直用当前参数"的卡片状态。Service 层提供 `rescheduleAllCards()` 异步编排方法，在优化器完成后自动触发。

## User Stories

1. 作为一名 Learner，优化器为我计算出更精准的 W[21] 参数后，全部卡片自动用新权重重算——我不用做任何额外操作。
2. 作为一名 Learner，reschedule 过程中不影响我的正常复习——它在后台异步执行。
3. 作为一名 Learner，从未被复习过的全新卡片不受 reschedule 影响——它们本来就在正确状态。
4. 作为一名 Learner，当我修改 learning_steps 或 desired_retention 时，已有卡片不会立即 reschedule，而是在下次复习时自然过渡到新参数——因为我当时的评分是基于旧间隔的，用新参数重放没有意义。
5. 作为一名开发者，`FsrsScheduler.reschedule()` 是纯计算方法，不含 fuzz 随机扰动——相同输入始终产生相同输出。
6. 作为一名开发者，reschedule 的起始状态使用 `enchantCard()`（原 `initNewCard`），模拟卡片从首次复习时开始的历史重放。
7. 作为一名开发者，ReviewLog 为空时 reschedule 返回 `createInitState`（New 状态）。
8. 作为一名开发者，`enchantCard` 是新命名——替代原 `initNewCard`，更准确地表达"为首次复习做准备"而非"创建新卡片"。

## Implementation Decisions

### 1. FsrsScheduler.reschedule() 方法

纯计算方法，不接受 Spring 依赖：

```
reschedule(List<ReviewLog> reviewLogs, Instant now) → CardState
```

流程：
1. 若 `reviewLogs` 为空 → 返回 `createInitState(now)`（全新卡片）
2. 按 `reviewedAt` 升序排序
3. 以第一条 ReviewLog 的时间为起点调用 `enchantCard(firstReviewTime)`（进入 Learning 状态）
4. 逐条遍历 ReviewLog，每次调用 `repeat(card, log.rating, log.reviewedAt, null)`（null = 无 fuzz）
5. 返回最终 CardState

### 2. 不含 fuzz

Reschedule 传递 `null` fuzzSource。Fuzz 是实际复习时的随机扰动——reschedule 计算的是参数变更后的确定性基准状态。实际复习时 fuzz 仍然正常生效（在 rateCard 中），不与 reschedule 叠加。

### 3. 命名变更：initNewCard → enchantCard

原 `initNewCard` 方法重命名为 `enchantCard(Instant now)`：

| 原名 | 新名 | 含义 |
|------|------|------|
| `initNewCard(now)` | `enchantCard(now)` | 准备首次复习（Learning, step=0, hasStability=false） |
| `createInitState(now)` | 不变 | 创建全新卡片（state=0, stability=2.5, 从未复习） |

`enchantCard` 避免与 `createInitState` 语义冲突——前者是"赋予复习能力"，后者是"创建空白卡片"。

### 4. ReviewService.rescheduleAllCards() 编排

- `@Async("optimizerExecutor")` 异步执行
- 流程：
  1. 查询该 userId 下有 ReviewLog 的所有卡片
  2. 按 cardId 分组加载 ReviewLog
  3. 每卡调用 `scheduler.reschedule(reviewLogs, now)`
  4. 更新 Card 的 FSRS 字段（stability/difficulty/cardState/step/due/reps/lapses/lastReview）
  5. 批量 `cardRepository.saveAll(cards)`
- 无 ReviewLog 的卡片跳过（它们状态正确）
- 完成后清除 Caffeine 缓存（`cacheManager.getCache("fsrsConfig").evict(userId)`）

### 5. 触发点与适用性

Reschedule 的核心前提：用户的评分在参数变化前后仍可被视为"相同强度的记忆信号"。这仅在参数变化幅度很小的场景下成立。

| 触发场景 | reschedule？ | 理由 |
|---------|-------------|------|
| **优化器完成（P4）** | ✅ 自动 | W[21] 微调 <5%，间隔几乎不变，评分大概率一致 |
| **改 learning_steps（P1）** | ❌ 不做 | "1 分钟后的 Good" ≠ "1 天后的 Good"——评分不可靠 |
| **改 desired_retention（P1）** | ❌ 不做 | 间隔变化 ~20%，重放无意义 |
| **手动触发** | ✅ 可选 | Learner 自己承担近似误差 |

**P1 不 reschedule 时卡片如何过渡：**
- `repeat()` 已有步数越界保护：`step >= learningSteps.length → 直接毕业到 Review`，不会崩溃或卡住
- Review 状态卡片：新参数在下次 rateCard 时影响**下一次**间隔计算
- 代价：全量收敛慢（需数周至数月），但数学上自然、正确

### 6. 线程池

使用 `optimizerExecutor`（P4 引入的线程池）。Reschedule 轻量（秒级），与优化器（分钟级）共享线程池——两者不同时触发（优化器先执行，reschedule 在完成后触发）。

### 7. FsrsScheduler API 变更（影响 P0）

P0 中原 `initNewCard` 重命名为 `enchantCard`，新增 `reschedule` 方法：

```
FsrsScheduler 实例方法:
  enchantCard(Instant now)          → CardState    (原 initNewCard)
  repeat(CardState, Rating, Instant, DoubleSupplier) → CardState
  reschedule(List<ReviewLog>, Instant) → CardState   (新增)
  retrievability(CardState, Instant) → double       (新公开)
  forgettingCurve(double, double, double) → double  (新静态)

FsrsScheduler 静态方法:
  createInitState(Instant now)      → CardState    (不变)
```

## Testing Decisions

### What makes a good test
- 测试外部行为（输入 ReviewLog → 输出 CardState），不测试内部 repeat 实现
- 确定性验证（相同输入 → 相同输出）
- 边界：空日志、单条日志、多条日志

### Test seams

**单元测试（FsrsSchedulerTest 追加 5 个）**

| 测试 | 验证 |
|------|------|
| `reschedule_emptyLogs_returnsCreateInitState` | 无历史 → New 状态 |
| `reschedule_singleReview_updatesState` | 1 条日志 → 卡片脱离 New |
| `reschedule_multipleReviews_accumulatesState` | 10 条日志 → 累积 stability/difficulty |
| `reschedule_deterministic_noFuzz` | 同一日志两次 → 相同结果 |
| `reschedule_differentConfig_producesDifferentDue` | 不同 desiredRetention → 终点 due 不同 |

**集成测试（ReviewServiceTest 追加 2 个）**

| 测试 | 验证 |
|------|------|
| `rescheduleAllCards_processesCardsWithReviewLogs` | mock 3 卡有日志 → 每张都 save |
| `rescheduleAllCards_skipsCardsWithoutReviewLogs` | 无日志卡 → 设为 createInitState |

**现有测试影响**

| 测试类 | 影响 |
|--------|------|
| `FsrsSchedulerTest`（12 个） | `initNewCard` 调用改为 `enchantCard`——纯重命名，断言不变 |
| `ReviewServiceTest`（23 个） | 无影响（reschedule 是新增方法） |
| `ReviewIT`（9 个） | 零影响（reschedule 是后台行为） |
| `ManagePageIT`、`FlashcardIT` 等 | 零影响 |

### Prior art
- `FsrsSchedulerTest` 已有 repeat() 系列测试 — reschedule 内部依赖 repeat()，测试模式一致
- `ReviewServiceTest` 已有 @Async 相关的 mock 模式（若 Future 需要）

## Out of Scope

- **手动 reschedule 触发 UI**：当前仅有程序化触发（优化完成）。管理页面"重新计算全部卡片"按钮在后续迭代中补充
- **reschedule 进度报告**：无 UI 端点展示 reschedule 进度（轻量操作，秒级完成）
- **部分 reschedule**（仅重算某个 Deck）：当前只支持全量 `rescheduleAllCards`，按 Deck 范围延后
- **Reschedule 与 forget 的交互**：forget 删除 ReviewLog，reschedule 读取 ReviewLog——两者自然协调（forget 后的卡片 reschedule 返回 New）

## Further Notes

### 依赖关系
- **依赖 P0 完成**：FsrsScheduler 实例化后才能调用 reschedule
- **被 P4 消费**：优化完成后自动触发
- **P1 不触发 reschedule**：改 learning_steps / desired_retention 后卡片自然过渡（见触发点与适用性章节）
- **命名变更同步**：P0 PRD 和 Issue 04 中 `initNewCard` 需改为 `enchantCard`

### 性能
- 单卡 10 条 ReviewLog → 10 次 repeat() → ~10μs
- 100 卡 × 10 条 → ~1ms + DB 读写
- 异步执行，不阻塞 HTTP 请求
