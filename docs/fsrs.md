# FSRS — 技术参考

本文档描述 Chat Agent 中 FSRS（Free Spaced Repetition Scheduler v6）模块的算法、配置、调度、优化和复习功能的完整技术细节。

> 术语表见 [CONTEXT.md](../CONTEXT.md) 中 Flashcard 相关条目。设计决策见 [docs/adr/scheduler-config-two-layer.md](adr/scheduler-config-two-layer.md)、[docs/adr/scheduler-static-to-instance.md](adr/scheduler-static-to-instance.md)、[docs/adr/fsrs-optimizer-pure-java.md](adr/fsrs-optimizer-pure-java.md)。测试清单见 [docs/tests.md](tests.md)。

---

## 一、算法概述

FSRS-6 是一个 21 参数（W[0..20]）的记忆间隔调度算法。核心思想：用**稳定性（Stability）**和**难度（Difficulty）**两个状态变量建模记忆强度，根据评分（Again/Hard/Good/Easy）更新状态并计算下一次复习间隔。

纯 Java 实现，位于 `com.hugosol.chatagent.flashcard` 包，零外部依赖。

### 1.1 状态变量

| 变量 | 含义 | 范围 |
|------|------|------|
| **Stability** (S) | 记忆稳定性，决定复习间隔 | [0.001, ∞) |
| **Difficulty** (D) | 卡片难度，影响稳定性变化率 | [1, 10] |

### 1.2 卡片状态（CardState）

| 状态 | 值 | 含义 |
|------|---|------|
| New | 0 | 创建后、首次复习前 |
| Learning | 1 | 初次接触，通过 learning steps 逐步推进 |
| Review | 2 | 已稳定，按稳定性计算间隔 |
| Relearning | 3 | Review 状态点 Again 后进入的重新学习期 |

### 1.3 核心公式

**遗忘曲线**（retrievability）:
```
R = (1 + factor × elapsed_days / S)^decay
  where factor = desired_retention^(1/decay) - 1
        decay = -w20
```

**难度更新**（nextDifficulty）:
```
D' = D + w6 × (rating - 3) × delta
  delta = constrain(w7 × exp((1 - D') / w7), linear damping)
```

**稳定性更新**（nextStability）:
- 遗忘（Again）: `S' = min(S / exp(w17 × w18), 硬性上限)`
- 回忆（Hard/Good/Easy）: `S' = S × (1 + exp(w8) × w15Penalty or w16Bonus)`

**短时记忆增强**（same-day review）:
```
S' = S × exp(w17 × w18_boost)
```

---

## 二、配置体系（两层模型）

FSRS 配置分为**系统管理**和**用户可配**两层，运行时通过 `FsrsSchedulerConfig.merge()` 合并。

### 2.1 系统层 — FsrsParameters

JPA 实体 `fsrs_parameters` 表，每用户一行。由 Optimizer 自动更新，用户不直接编辑。

| 字段 | 说明 |
|------|------|
| `w0..w20` | 21 个 FSRS 权重系数 |
| `enableShortTerm` | 同天复习加速开关（默认 true） |

默认权重（FSRS-6 标准）:
```
0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001,
1.8722, 0.1666, 0.796, 1.4835, 0.0614, 0.2629, 1.6483, 0.6014,
1.8729, 0.5425, 0.0912, 0.0658, 0.1542
```

### 2.2 用户层 — UserPreferences

JPA 实体 `user_preferences` 表，每用户一行。通过 Settings 页（`/settings`）配置。

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `learningSteps` | String | `"1m,10m"` | 学习阶段间隔（逗号分隔，支持 s/m/h/d） |
| `relearningSteps` | String | `"10m"` | 重学阶段间隔 |
| `desiredRetention` | Double | 0.9 | 目标正确率 [0.01, 0.99] |
| `maximumInterval` | Integer | 36500 | 最大间隔（天） |
| `enableFuzz` | Boolean | true | Fuzz 开关 |
| `shuffleDueCards` | Boolean | null | 到期卡片随机排序 |
| `newCardDailyLimit` | int | 20 | 每日新卡上限 |

nullable 字段为 null 时自动回退到 `FsrsSchedulerConfig` 默认值。

### 2.3 运行时配置 — FsrsSchedulerConfig

不可变 record，合并两层配置后传递给 `FsrsScheduler` 构造函数。

```java
public record FsrsSchedulerConfig(
    double[] weights,           // 21个, clone-defensive
    double desiredRetention,    // 0.9
    Duration[] learningSteps,   // [1m, 10m]
    Duration[] relearningSteps, // [10m]
    int maximumInterval,        // 36500
    boolean enableFuzz,         // true
    boolean enableShortTerm     // true
)
```

配置由 `FsrsConfigService.getConfig(userId)` 提供，通过 Caffeine 缓存（24h 过期），`@Cacheable` + `@CacheEvict` 管理。

---

## 三、调度器设计（FsrsScheduler）

位于 `flashcard/FsrsScheduler.java`，**实例类**，构造函数接受 `FsrsSchedulerConfig`。

### 3.1 公共方法

| 方法 | 说明 |
|------|------|
| `createInitState(now)` *(static)* | 创建全新卡片状态：S=2.5, D=0, state=New, lapses=0 |
| `enchantCard(now)` | New→Learning 过渡：state=1, step=0, hasStability=false |
| `repeat(state, rating, now, fuzzSource)` | **核心调度函数**：完整 4 状态机 |
| `preview(state, now)` | 返回 4 种评分的 CardState（无 fuzz），用于 UI 预览 |
| `reschedule(logs, now)` | 从 ReviewLog 序列重放重建卡片状态（Optimizer 后用） |
| `retrievability(state, now)` | 当前记忆概率 |
| `forgettingCurve(days, S, decay)` *(static)* | 纯数学遗忘曲线 |

### 3.2 repeat() 状态机

```
┌─────────┐   enchant    ┌──────────┐  last step GOOD   ┌────────┐
│  New(0) │ ────────────→ │ Learning │ ────────────────→ │ Review │
└─────────┘               │   (1)    │                    │  (2)   │
                           └──────────┘                    └───┬────┘
                                │ AGAIN→step=0                 │
                                │ (reset to first step)        │ AGAIN
                                │                              ↓
                                │                        ┌───────────┐
                                │  last step GOOD        │Relearning │
                                └───────────────────────→│    (3)    │
                                                          └───────────┘
                                                               │ GOOD→Review
                                                               │ AGAIN→step=0
```

**Learning 阶段**: 按 `learningSteps[]` 逐步推进。AGAIN 重回 step=0，GOOD 前进一个 step（最后一步→Review），EASY 直接跳 Review。

**Relearning 阶段**: 按 `relearningSteps[]` 推进，逻辑同 Learning。全通过后回 Review。

**Fuzz**: 仅对 Review 状态且间隔 ≥ 2.5 天的卡片应用。使用 `AleaPrng` 确定性 PRNG 以保证跨实现一致性。三级 fuzz 区间：`[2.5, 7)` × 0.15, `[7, 20)` × 0.1, `[20, ∞)` × 0.05。

### 3.3 lapses 语义

FSRS 中 lapses 是**连续犯错计数**：在 Review 状态点 Again 时 +1，成功晋升（Good/Easy）后重置为 0。这与 SM-2 的累计 lapses 不同。

---

## 四、优化器设计（FsrsOptimizer）

位于 `flashcard/FsrsOptimizer.java`，纯 Java 实现，使用手动 Adam + 中心有限差分梯度（h=1e-4），与 py-fsrs 算法对齐。

### 4.1 超参数

| 参数 | 值 | 说明 |
|------|---|------|
| 初始学习率 | 4e-2 | Adam η |
| β1 / β2 | 0.9 / 0.999 | Adam 动量 |
| 训练轮数 | 5 | 全量数据 shuffle 5 轮 |
| 批次大小 | 512 | 非同日 review 条目 |
| 最大序列长度 | 64 | 每卡最多取 64 条 review log |
| 梯度步长 | 1e-4 | 有限差分 h |
| 最小数据量 | 512 | 非同日 review 不足则跳过优化 |
| 学习率调度 | Cosine annealing | 0.5η × (1 + cos(π·t/tMax)) |

### 4.2 优化流程（FsrsOptimizeService）

1. **触发**: `POST /api/fsrs/optimize` (手动) 或 `@Scheduled(cron: 每周日 03:00)` (自动)
2. **异步执行**: `@Async("optimizerExecutor")`，进度通过 `taskId` 轮询
3. **损失比较**: 仅当优化后 loss < 默认参数 loss 时才保存
4. **参数保存**: 写入 `FsrsParameters` 表，清除 `fsrsConfig` 缓存
5. **重调度**: 调用 `ReviewService.rescheduleAllCards(userId)`，异步重放所有 ReviewLog

### 4.3 API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/fsrs/optimize` | 启动优化，返回 `{taskId, status}` |
| `GET` | `/api/fsrs/optimize/status?taskId=` | 轮询进度，返回 `{progress, result?}` |

---

## 五、复习流程（Review 模块）

### 5.1 API 端点（ReviewController）

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/review/decks` | 列出所有牌组（type="deck" 的 Tag）及卡片计数 |
| `GET` | `/api/review/stats?deckId=&mode=` | 复习统计（已复习、剩余、今日学习、次日到期） |
| `GET` | `/api/review/start?deckId=&mode=` | 开始复习：按 mode 选卡，返回首卡+preview+stats |
| `POST` | `/api/review/next` | 评分当前卡（cardId/rating/mode/deckId），返回下一卡+preview+stats |
| `POST` | `/api/cards/{cardId}/forget` | 重置单卡 FSRS 状态到 New，删除 ReviewLog |
| `POST` | `/api/cards/forget?deckId=` | 批量重置整个牌组 |
| `GET` | `/api/user/preferences` | 获取用户偏好 |
| `PUT` | `/api/user/preferences` | 保存用户偏好（含验证） |

### 5.2 复习模式

| 模式 | 选卡策略 |
|------|---------|
| `STANDARD` | 先到期卡（due ≤ now），再新卡（state=0）不超过每日上限 |
| `REVIEW_ONLY` | 仅到期卡 |
| `NEW_ONLY` | 仅新卡，不超过每日上限 |
| `CRAM` | 从牌组中随机选卡，无状态过滤，无上限 |

`shuffleDueCards=true` 时到期卡按随机顺序而非时间顺序。

### 5.3 评分预览

`POST /api/review/next` 和 `GET /api/review/start` 的响应中包含 `preview` 字段，展示 4 种评分（Again/Hard/Good/Easy）对应的 `CardState`（含 due 日期）。Preview 不含 fuzz，前端用于渲染"Good · 约15天后"级别的提示。

### 5.4 复习统计（ReviewStats）

| 字段 | 计算方式 |
|------|---------|
| `reviewedToday` | `COUNT(lastReview >= todayStart)` |
| `learnedToday` | `COUNT(firstReviewDate >= todayStart)` |
| `remaining` | `COUNT(state ≠ 0 AND due ≤ now) + remainingNewQuota` |
| `dailyLimit` | 从 `UserPreferences.newCardDailyLimit` 读取 |
| `nextDueAt` | `MIN(due WHERE due > now)` |

`todayStart` 根据用户 timezone + dayStartHour 偏好计算。

### 5.5 ReviewLog

每次 `rateCard()` 创建一条 `ReviewLog` 记录：

| 字段 | 说明 |
|------|------|
| `rating` | AGAIN/HARD/GOOD/EASY |
| `stateBefore/After` | 评分前后的 CardState（stability/difficulty/state/step 快照） |
| `scheduledDays` | 计划间隔天数 |
| `elapsedDays` | 实际间隔天数 |
| `firstReview` | 是否为首次复习 |

---

## 六、Flashcard 模块完整架构

### 6.1 REST API（FlashcardController）

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/cards/add` | 创建卡片（front/back/tagIds），FSRS 自动初始化 |
| `GET` | `/api/cards?search=&deckId=&page=&size=` | 分页列表，支持搜索和牌组筛选 |
| `PUT` | `/api/cards/{id}` | 编辑卡片 |
| `DELETE` | `/api/cards/{id}` | 删除卡片 |
| `GET` | `/api/tags?type=` | 列出标签（可过滤 type） |
| `POST` | `/api/tags` | 创建标签 |
| `PUT` | `/api/tags/{id}` | 编辑标签 |
| `DELETE` | `/api/tags/{id}` | 删除标签 |
| `POST` | `/api/cards/import?tagId=` | CSV 批量导入 |
| `GET` | `/api/cards/export?tagId=` | CSV 批量导出 |

所有端点通过 JSESSIONID cookie 认证，userId 从 `SecurityContext` 提取。

### 6.2 CSV 格式

导入导出的 CSV 包含 front、back 及完整 FSRS 状态：

```
front,back,stability,difficulty,cardState,due,reps,lapses,lastReview
"hello","你好",2.5,0.0,New,,0,0,
```

cardState 使用可读文本（New/Learning/Review/Relearning），非数字编码。Parser 按列名称匹配，兼容列序变化和多余列。

### 6.3 数据模型

```
Card ──ManyToMany── card_tags ──ManyToMany── Tag
  │                                              │
  │ userId                                       │ userId
  │ front                                        │ name
  │ back                                         │ type ("deck" → 牌组)
  │ stability/difficulty/cardState/due/...
  │ firstReviewDate

ReviewLog ── FK ──→ Card
  │ rating, stateBefore/After, elapsedDays, scheduledDays
  │ userId

FsrsParameters ── userId (unique)
  │ w0..w20, enableShortTerm

UserPreferences ── userId (unique)
  │ newCardDailyLimit, learningSteps, relearningSteps,
  │ desiredRetention, maximumInterval, enableFuzz, shuffleDueCards,
  │ dayStartHour, timezone, lastDeckId, lastMode
```

### 6.4 服务层依赖

```
FlashcardController → FlashcardService → CardRepository, TagRepository
                                       → CardBatchService → CardCsvParser

ReviewController → ReviewService → CardRepository, ReviewLogRepository
                                 → FsrsConfigService → FsrsParametersRepository
                                                     → UserPreferencesService

FsrsOptimizeController → FsrsOptimizeService → FsrsOptimizer
                                             → FsrsParametersRepository
                                             → ReviewService.rescheduleAllCards
```

### 6.5 线程池

| 池名 | 核心/最大 | 用途 |
|------|----------|------|
| `optimizerExecutor` | 2/4 | FSRS 优化 + reschedule |
| `llmRequestExecutor` | 4/8 | review 中的 LLM 调用（不涉及纯 FSRS） |

---

## 七、测试覆盖

### 7.1 单元测试

| 测试类 | 内容 |
|--------|------|
| `FsrsSchedulerTest` | repeat() 4 状态机 + preview/reschedule/retrievability 12+ |
| `FsrsSchedulerConfigTest` | merge() 合并逻辑 + parseSteps() |
| `FsrsOptimizerTest` | 优化器 loss 计算 + 梯度验证（12,580 真实 log 数据） |
| `AleaPrngTest` | PRNG 确定性验证 |
| `FlashcardServiceTest` | 创建/查询/更新/删除 + tag 属于权验证 |
| `ReviewServiceTest` | rateCard/forgetCard/getNextCard/computeStats 26+ |
| `CardBatchServiceTest` | 导入/导出 CSV + 校验 + 错误处理 |
| `ReviewControllerTest` | MockMvc 端点测试 |
| `FlashcardControllerTest` | MockMvc 端点测试 |

### 7.2 E2E 测试

| 测试类 | 内容 |
|--------|------|
| `FlashcardIT` | 两阶段面板 → chip 标签 → H2 数据验证 |
| `FlashcardBatchIT` | 导出 CSV → 删卡 → 导入 CSV → FSRS 状态还原 |
| `ReviewIT` | 9 场景：牌组/模式选择、翻面评分、stats bar、完成页、4 模式、每日上限 |
| `ManagePageIT` | 管理页 CRUD + 搜索/排序/牌组筛选/分页/forget |

---

## 八、与 py-fsrs / ts-fsrs 的兼容性

- 算法对齐 FSRS-6 标准，`repeat()` 状态机与 py-fsrs `Scheduler.review_card()` 等价
- Rating pyValue 映射（AGAIN=1, HARD=2, GOOD=3, EASY=4）与 py-fsrs 一致
- Fuzz 使用自研 `AleaPrng` 替代 py-fsrs 的 `random.uniform()`，确保确定性
- Optimizer 交叉验证通过 12,580 条真实 Anki ReviewLog：优化后 loss < 默认 loss
- 唯一差异：Card 状态比 py-fsrs 多一个 New(0) 状态（py-fsrs 新卡直接 Learning）
