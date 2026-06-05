# PRD: 闪卡复习评分预览 —— 按钮显示间隔时间

**Status:** `ready-for-agent`

## Problem Statement

Learner 在复习闪卡时看到四个评分按钮（Again / Hard / Good / Easy），但不知道每个按钮的实际后果。选 Again 是 1 分钟后再见还是 1 天后？选 Good 是 3 天后还是 30 天后？当前 Learner 只能凭感觉点击，无法根据间隔信息做出理性判断——例如"这张卡我还有点犹豫，Hard 才 2 天后见面，那我选 Hard 加强记忆"或"这张卡很熟了，Easy 能推到 60 天后"。

没有间隔预览，FSRS 算法对 Learner 是一个黑盒，四个评分按钮无法被正确地当作"复习节奏调控工具"使用。

## Solution

在 Learner 翻出卡片背面后，每个评分按钮旁边显示该评分对应的**预计下次复习时间**（如"Good · 约 15 天后"）。预览使用确定性间隔（不含 fuzz 随机扰动），让 Learner 在评分前看到每个选项的实质后果。

同时将 `preview()` 作为 `FsrsScheduler` 的公开方法，返回四种评分对应的完整 `CardState`，供前端灵活展示。

## User Stories

1. 作为一名 Learner，翻出卡片背面后，我看到 Again/Hard/Good/Easy 四个按钮旁各自显示预计的复习间隔（如"1 分钟"、"2 天"、"15 天"、"60 天"），从而理解每个评分对复习节奏的影响。
2. 作为一名 Learner，当我犹豫"该评 Hard 还是 Good"时，我对比两者的间隔差异（如 Hard=3 天 vs Good=15 天），选择最能反映我实际掌握程度的评分。
3. 作为一名 Learner，当我完全忘记一张卡（Again），我看到间隔显示"1 分钟"——这清楚告诉我卡片会进入短间隔重新学习，而不是推到数天后。
4. 作为一名 Learner，对一张非常熟悉的卡片，我看到 Easy=60 天，放心地选择 Easy，知道它短期内不会再被安排。
5. 作为一名 Learner，预览显示的间隔不含随机扰动（fuzz），与实际复习可能有 ±1-2 天偏差，但这不影响我做出正确的评分选择。
6. 作为一名 Learner，每张卡片翻出后都自动显示四个评分的预览间隔（不需要额外点击），信息在当前界面直接可见。
7. 作为一名开发者，`FsrsScheduler.preview()` 是独立的公开方法，不依赖任何 Service，可在单元测试中独立验证。
8. 作为一名开发者，预览使用与当前 Learner 相同的 Scheduler 配置（W[21]、learning_steps、desired_retention 等），确保预览和实际复习一致。
9. 作为一名开发者，现有的 E2E 测试通过 `data-testid` 选择评分按钮，按钮文本增加间隔信息不会破坏任何 E2E 测试。

## Implementation Decisions

### 1. FsrsScheduler.preview() 方法

在 `FsrsScheduler` 上新增实例方法：

```
preview(CardState card, Instant now) → Map<Rating, CardState>
```

- 对每种 Rating（AGAIN/HARD/GOOD/EASY）调用 `repeat(card, rating, now, null)`
- `null` fuzzSource → 不应用 fuzz，输出确定性间隔
- 使用当前 Scheduler 实例的 `FsrsSchedulerConfig`（包含该 Learner 的个性化参数）
- 纯计算方法，无副作用，不依赖任何 Service

### 2. 预览不含 fuzz

预览展示确定性间隔。fuzz 只在 Learner 实际评分时由 `ReviewService.rateCard()` 应用。Fuzz 偏差：短间隔（<3 天）无影响，中等间隔（几周）偏差 ±1-2 天，长间隔（数月）偏差 ±20 天。Learner 看到"约 15 天"足以做决策，精确的 fuzz 值无额外价值。

与 Anki 一致——Anki 评分按钮旁显示的也是确定性间隔。

### 3. ReviewService.previewCard() 方法

新增 Service 层方法隔离 Scheduler 调用：

```
previewCard(Card card, Instant now) → Map<Rating, CardState>
```

- 从 Caffeine 缓存获取该 Learner 的 `FsrsSchedulerConfig`
- 构建 `CardState`（复用现有 rateCard 中的 `buildCardState` 逻辑）
- 调用 `scheduler.preview(cardState, now)`
- 使用缓存的 config，与实际复习一致

### 4. API 响应嵌入 preview 字段

在已有的 `GET /api/review/start` 和 `POST /api/review/next` 响应中新增 `preview` 字段。

改造前响应：
```
{ "card": {...}, "stats": {...} }
```

改造后响应（card 非 null 时）：
```
{
  "card": {...},
  "stats": {...},
  "preview": {
    "AGAIN": { "stability": ..., "difficulty": ..., "state": ..., "step": ..., "due": "2026-06-05T...", ... },
    "HARD":  { ... },
    "GOOD":  { ... },
    "EASY":  { ... }
  }
}
```

- `preview` 字段仅在 `card` 非 null 时存在（复习完毕 card=null 时不返回）
- 每个评分返回完整 CardState，前端自行提取 `due` 计算间隔、提取 `state` 判断状态变化

### 5. 前端 RatingButtons 组件

组件新增 `preview` prop（从 API 响应提取）：

```
RatingButtons props: {
  preview: Record<RatingValue, CardState> | null
  onRate: (rating: RatingValue) => void
}
```

- 按钮文本：原有标签 + 间隔描述，如 `"Good · 约15天后"`
- 间隔从 `due - now` 计算，前端格式化为人类可读：`"<1分钟"`, `"10分钟"`, `"2天"`, `"3个月"`
- `preview` 为 null 时不显示间隔（退化到当前行为）
- 不改变 data-testid（`rating-again`、`rating-hard`、`rating-good`、`rating-easy`）

### 6. ReviewPage 状态管理

- `GET /api/review/start` 响应中提取 `preview` → 存入 React state
- `POST /api/review/next` 响应中提取新卡片的 `preview` → 更新 state
- 传给 `RatingButtons` 作为 props

### 7. 卡片状态处理

`preview()` 对不同卡片状态的行为：

| 卡片状态 | preview 行为 |
|---------|-------------|
| New（state=0） | 内部调用 `initNewCard(now)` 再计算四个评分，展示首次学习的间隔 |
| Learning（state=1） | 展示当前学习步的四种评分结果，如 Again→重置步骤、Good→毕业到 Review |
| Review（state=2） | 展示标准 Review 间隔，Again→进入 Relearning、Hard/Good/Easy→Review 间隔 |
| Relearning（state=3） | 展示重学步的四种评分结果 |

### 8. 向后兼容

- `preview` 字段是新增字段，前端需做判空处理
- 现有不传 `preview` 的 API 调用（如有）不会崩溃
- 按钮 `data-testid` 不变

## Testing Decisions

### What makes a good test
- 测试外部行为（preview 返回 4 个不同 CardState、间隔正确、不含 fuzz），不测试内部梯度或 repeat 实现细节
- E2E 不依赖按钮文本选择器（继续使用 data-testid）
- 单元测试使用默认 FsrsSchedulerConfig，不依赖 DB

### Test seams

**单元测试（FsrsSchedulerTest 扩展）**
- `preview_returnsFourOutcomes`：返回 Map 包含 4 个 Rating 键
- `preview_ratingsProduceDifferentDue`：四种评分的 due 各不相同（验证有实际区分）
- `preview_noFuzz_repeatable`：同一输入两次调用返回相同结果（确定性）
- `preview_newCard_learningStateCorrect`：新卡预览中 Again 导致 step=0，Good 导致毕业

**集成测试（ReviewServiceTest 扩展）**
- `previewCard_usesCachedConfig`：验证使用缓存的 FsrsSchedulerConfig
- `previewCard_returnsFourOutcomes`：返回完整 Map

**集成测试（ReviewControllerTest 扩展）**
- `startReview_includesPreview`：验证响应中 `preview` 字段存在且有 4 个条目
- `nextReview_includesPreview`：验证 next 接口也包含 preview

**E2E 测试（ReviewIT）**
- 现有 9 个场景：**零改动**（使用 data-testid，不依赖按钮文本）
- 可选新增：验证评分按钮上显示间隔文字

### Prior art
- `FsrsSchedulerTest`（12 个单元测试）— 同文件追加 preview 测试
- `ReviewServiceTest`（23 个单元测试）— 同文件追加 previewCard 测试
- `ReviewIT`（9 个 E2E 场景）— 已有 data-testid 保护，无需改动

## Out of Scope

- **repeat() 预览模式**作为独立的 ts-fsrs 风格 `repeat()` 重载返回四个结果——当前用 `preview()` 命名更明确
- **fuzz 范围展示**（如"13-17 天"而非"约 15 天"）——精度增加但前端复杂度提升，Learner 决策价值低
- **自定义间隔格式化**——前端统一用简写格式（"15天"/"1分钟"），不做国际化多语言
- **预览缓存**——preview 随卡片数据一起返回，不做独立缓存
- **next_state/next_interval 公开**——无已知调用方，暂不做

## Further Notes

### 依赖关系
- **依赖 P0 完成**：FsrsScheduler 实例化后才有 `preview()` 方法；Caffeine 缓存确保使用正确的 SchedulerConfig
- **不依赖 P1/P2/P3/P4**：预览功能独立于学习步 UI 和优化器

### 与 P2（L1 改进）的关系
- `repeat()` 预览是 P2 的核心功能之一
- 原 P2 还包含 `next_state/next_interval`（决定先不做）和 `JSON 序列化`（未讨论）
- 此 PRD 已将 P2 中预览功能独立出来

### 之前决定先不做的 L1 功能
- `next_state` 和 `next_interval`：当前无已知调用方，private→public 可随时做
- `retrievability` 和 `forgettingCurve`：已在 P0 中一并改为 public
- `STATE_NEW` 常量：已在 P0 中加入
