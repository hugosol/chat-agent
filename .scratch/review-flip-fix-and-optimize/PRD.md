# PRD: Review Page Flip Bug Fix, TTS Layout Refinement & /review/next Query Optimization

**Status:** `ready-for-agent`

## Problem Statement

### Bug: 点击卡片外部空白区域导致评分按钮出现但卡片未翻转

在复习页面中，`.content` 区域（flex 居中容器）绑定了点击翻转事件。当 Learner 点击卡片外部的空白区域时，ReviewPage 的 `flipped` 状态被设为 `true`，评分按钮（RatingButtons）出现——但 CardDisplay 内部的独立 `flipped` 状态未变化，卡片仍显示 "Tap to reveal"（未翻面）。

根因：ReviewPage 和 CardDisplay 各自维护了独立的 `flipped` state，且 `.content` 的 onClick 和 CardDisplay 内部 `.card` 的 onClick 是两个互不同步的翻转入口。只有当用户恰好点击到卡片本身时，两个事件的冒泡才使两个 `flipped` 同时变为 true。

### Layout: TTS 按钮位置不合理

当前 TTS 按钮始终存在于卡片内部的 `.cardFront` 行（正面文字右侧）。Learner 翻面前就有 TTS 按钮干扰视觉焦点，且翻面后 TTS 按钮与 back 面的 TTS 按钮堆叠在同一卡片内，不够清晰。

### Performance: /review/next 端点存在冗余查询

每次评分后调用 `/review/next`，流程为 `rateCard` → `getNextCard` → `computeReviewStats`，各自独立调用 `userPreferencesService.get()`、独立计算 `todayStart`，且在 STANDARD 模式下对 `learnedToday` 执行了两次相同的 `COUNT` 查询。STANDARD 模式的 `computeRemaining` 对 due 和新卡分别做一次 `COUNT`，可以合并。

## Solution

### 前端

- **消除双 `flipped` 状态**：`flipped` 上提到 ReviewPage 为唯一真相源，CardDisplay 变为受控组件（接收 `flipped` prop + `onFlip` 回调）
- **删除 `.content` 的 onClick**：点击卡片外部空白区域不再触发任何事件。仅 `.card` 本身可点击翻转
- **TTS 按钮布局调整**：未翻转时，front 文字的 TTS 按钮渲染在卡片下方（`.cardArea` 底部）；翻转后，该按钮隐藏，front 文字行内的 TTS 按钮出现。back 文字的 TTS 按钮保持不变

### 后端

- **新增 `ReviewService.getNextCardAndStats`**：合并 `getNextCard` 和 `computeReviewStats` 为一个方法，内部共享 `learnedToday` 计数和 `todayStart` 计算，消除 `COUNT` 重复查询和 `computeTodayStart` 重复计算。`processNextCard` 和 `startReview` 端点改用此方法
- **新增 `CardRepository.countDueAndNewByDeckId`**：将 STANDARD 模式 `computeRemaining` 中的两个独立 `COUNT`（`countDueCardsByTagsId` + `countByTagsIdAndCardState`）合并为一个 JPQL 查询
- `userPreferencesService.get()` 重复调用保持现状：Caffeine 缓存命中是纳秒级内存操作，修改的代码侵入性（需穿透 `FsrsConfigService` 并影响 `previewCard` 和 `rescheduleAllCards`）远超收益

## User Stories

1. 作为一名 Learner，在复习页面看到卡片正面时，点击卡片周围的空白区域**不应该**有任何反应——卡片不会翻转，评分按钮也不会出现，以便我不会误操作。
2. 作为一名 Learner，只有当我**直接点击卡片本身**时，卡片才会翻开显示背面和评分按钮，符合我的直觉预期。
3. 作为一名 Learner，卡片未翻转时，我看到一个 TTS 发音按钮在卡片下方（不在卡片内部），点击即可听到正面单词的发音，视觉上更简洁。
4. 作为一名 Learner，卡片翻转后，卡片下方的 TTS 按钮消失，改为在正面文字旁边出现 🔊 按钮，我仍然可以在看到背面的同时发音正面单词。
5. 作为一名 Learner，翻转后的背面文字旁边的 TTS 按钮保持不变，我仍然可以听到背面释义的发音。
6. 作为一名 Learner，每完成一次评分，系统以更少的数据库查询返回下一张卡片和统计数据，响应速度更快（尤其是在卡片数量大的 Deck 中）。
7. 作为一名 Learner，`/review/start` 端点同样受益于优化后的合并查询，进入复习时加载首张卡片更快。

## Implementation Decisions

### 前端：状态归属

- **`flipped` 上提到 ReviewPage**，CardDisplay 不再维护独立 `flipped`。CardDisplay 新增两个 props：
  - `flipped: boolean` — 受控，决定是否显示背面
  - `onFlip?: () => void` — 点击卡片时回调
- `key={card.id}` 重挂载机制不变：换卡时 React 自动卸载旧 CardDisplay 并挂载新实例，`flipped` 回到初始值 `false`
- **`editing` 状态**保持在 CardDisplay 内部（仅影响 textarea 渲染），通过现有 `onEditingChange` 回调通知 ReviewPage 禁用评分按钮
- **`.content` 的 onClick 移除**，该 div 仅保留布局职责（flex 居中）

### 前端：TTS 按钮渲染位置

- CardDisplay 内部：front TTS（`data-testid="tts-btn-front"`）仅在 `flipped && hasEnglishFront` 时渲染于 `.cardFront` 行内
- CardDisplay 内部：back TTS（`data-testid="tts-btn-back"`）仅在 `flipped && hasEnglishBack` 时渲染于 `.cardBack` 行内（不变）
- CardDisplay 内部：卡片下方 TTS（新 `data-testid="tts-btn-below"`）在 `.card` 元素外部、`.cardArea` 内部渲染，条件为 `!flipped && hasEnglishFront`

### 后端：DTO 定义

新增 `NextCardAndStats` record（`dto/` 包下）：

```java
public record NextCardAndStats(Optional<Card> card, ReviewStats stats) {}
```

### 后端：ReviewService 新方法

```java
public NextCardAndStats getNextCardAndStats(String deckId, String mode, String userId) {
    Instant now = Instant.now();
    UserPreferences prefs = preferencesService.get(userId);
    Instant todayStart = computeTodayStart(prefs);
    long learnedToday = cardRepository.countByTagsIdAndFirstReviewDateGreaterThanEqual(deckId, todayStart, userId);
    
    Optional<Card> card = getNextCardInternal(deckId, mode, userId, prefs, now, todayStart, learnedToday);
    ReviewStats stats = computeStatsInternal(deckId, mode, userId, prefs, now, todayStart, learnedToday);
    
    return new NextCardAndStats(card, stats);
}
```

- `getNextCard` 原有 public 方法抽取为 `getNextCardInternal`（package-private），原有签名保留为委托方法（`/review/start` 的独立调用方不变）
- `computeReviewStats` 原有 public 方法保留不动（`/review/stats` 独立调用方不变）
- `isNewCardLimitExceeded` 改为接受 `learnedToday` 参数，消除内部 COUNT 调用

### 后端：CardRepository 新查询

```java
@Query("SELECT COUNT(CASE WHEN c.cardState <> 0 AND c.due <= :now THEN 1 END), " +
       "COUNT(CASE WHEN c.cardState = 0 THEN 1 END) " +
       "FROM Card c JOIN c.tags t WHERE t.id = :deckId AND c.userId = :userId")
Object[] countDueAndNewByDeckId(@Param("deckId") String deckId, @Param("now") Instant now, @Param("userId") String userId);
```

`computeRemaining` 中 STANDARD 分支改为调用此方法，从 `Object[]` 解构两个 count。

### 后端：Controller 改动

`ReviewController.processNextCard`：
```java
reviewService.rateCard(request.cardId(), rating, request.mode(), Instant.now(), userId, request.deckId());
var result = reviewService.getNextCardAndStats(request.deckId(), request.mode(), userId);
var card = result.card();
var stats = result.stats();
// previewCard 调用不变
```

`ReviewController.startReview`：同 pattern，替换原有的 `getNextCard` + `computeReviewStats` 双调用。

## Testing Decisions

### 测试缝（从小到粗）

| 层级 | 缝 | 位置 | 验证什么 |
|------|-----|------|---------|
| Component (Vitest) | CardDisplay props | `CardDisplay.test.tsx` | `flipped` prop 控制翻转；TTS 按钮在 `flipped=false` 时不出现 |
| Component (Vitest) | ReviewPage 点击行为 | `ReviewApp.test.tsx`（新增场景） | 点击卡片外部不触发翻转 |
| API (MockMvc) | `getNextCardAndStats` mock | `ReviewControllerTest.java` | Controller 正确使用合并方法 |
| Service (Mockito) | `getNextCardAndStats` 逻辑 | `ReviewServiceTest.java`（新增） | 各模式下的 card + stats 返回；learnedToday 一致性 |
| Repository (DataJpa) | `countDueAndNewByDeckId` | `CardRepositoryIsolationTest.java`（新增） | COUNT 正确性、userId 隔离 |
| E2E (Playwright) | 完整复习流程 | `ReviewIT.java` | 点击卡片翻转 → 评分 → 下一张；TTS 按钮可见性 |

### 好测试的原则

- **测试行为，不测试实现**：验证“点击空白区域无反应”而不是“`onClick` 被移除”
- **使用 `data-testid`**（前端）和 **mock 验证**（后端）作为断言锚点
- **不测试默认值**：`UserPreferences` 缓存命中不是测试目标
- **每个测试覆盖一个分支**：STANDARD / REVIEW_ONLY / NEW_ONLY / CRAM 四种模式各一个

### 需要改动的测试文件

| 文件 | 改动类型 |
|------|---------|
| `CardDisplay.test.tsx` | 所有测试加 `flipped`/`onFlip` props；TTS 可见性断言改为基于 `flipped` |
| `ReviewControllerTest.java` | `startReview` 和 `nextReview` 测试的 mock 从双方法改为 `getNextCardAndStats` |
| `ReviewServiceTest.java` | 新增 `getNextCardAndStats` 的 6~8 个测试 |
| `CardRepositoryIsolationTest.java` | 新增 `countDueAndNewByDeckId` 的 3 个测试 |

### 不需要改动的测试

- `ReviewIT.java` — 所有 E2E 场景通过 `data-testid="flip-card-btn"` 点击卡片本身，不受 `.content` onClick 移除影响
- `ReviewServiceTest.java` 现有 `rateCard_*`、`getNextCard_*`、`computeReviewStats_*` 测试 — 方法签名不变
- `ReviewApp.test.tsx` — 当前仅验证 `.app` div 存在

## Out of Scope

- 不引入新的 UI 动画或过渡效果
- 不修改 RatingButtons 的布局或交互逻辑
- 不修改 StatsBar 的显示逻辑
- 不修改编辑背面文字的 PATCH 流程
- 不修改 DeckPicker 或 CompletePage
- 不修改 `FsrsConfigService`、`FsrsParametersService` 或 `UserPreferencesService` 的缓存策略
- 不新增 E2E 测试场景（现有 ReviewIT 已覆盖核心流程）

## Further Notes

- `CardDisplay` 内部仍有独立的 `editing` state —— 这与 `flipped` 不同，`editing` 是纯 CardDisplay 内部 UI 状态（textarea 内容），不需要上提到 ReviewPage。唯一的交互点是 `onEditingChange` 回调（用于禁用评分按钮），已在现有流程中工作正常
- `flipped` 和 `editing` 是互斥的：编辑模式下不需要关心翻转状态（编辑区替代了背面显示），因此不存在 `flipped` 优先还是 `editing` 优先的竞态问题
- `/review/start` 端点也改用 `getNextCardAndStats`，确保两个端点的一致性
- 如果未来需要从后端驱动 TTS 行为（如服务端语音合成），此 PRD 不阻塞——TTS 按钮的渲染位置变化不涉及 API 变更
