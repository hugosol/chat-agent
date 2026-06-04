# PRD: 闪卡复习功能 —— FSRS-6 复习页面

**Status:** `ready-for-agent`

## Problem Statement

Chat Agent 的闪卡模块目前支持卡片录入、管理（CRUD）、标签组织和批量导入导出。Learner 可以创建大量的单词闪卡，但无法利用 FSRS-6 间隔重复算法进行系统化复习——所有卡片的 `cardState` 均为 0（New），`repeat()` 算法虽已完整实现却从未在用户流程中被调用。

Learner 需要一个与 Anki 体验一致的复习功能：定时定量地测试自己对已创建卡片的记忆，通过自我评分（Again/Hard/Good/Easy）驱动 FSRS-6 算法自动调整每张卡片的复习间隔，实现高效的间隔重复记忆。

## Solution

新增一个独立的复习页面（`/review/`），提供四种复习模式，将 FSRS-6 的 `repeat()` 算法暴露为 REST API，让 Learner 能够按 Deck（牌组）进行系统化的间隔重复复习。

核心体验：进入复习 → 选择 Deck 和模式 → 一张一张看卡片正面 → 点击翻面看背面 → 自我评分 → FSRS 自动调度 → 下一张。复习完成后显示本轮统计和下次最早到期时间。

复习功能与现有闪卡 CRUD 模块完全解耦：独立的 REST Controller、独立的 Service、独立的前端页面和 Vite bundle。依赖相同的 Card/Tag 实体和 FSRS 算法代码，但不复用 FlashcardController 和 FlashcardService。

## User Stories

1. 作为一名 Learner，我希望从 Header 导航栏点击"复习"进入一个独立的复习页面，而不是在聊天页面里操作，以便有一个专注的复习环境。
2. 作为一名 Learner，进入复习页面后，我可以看到所有可用的 Deck（牌组）列表，每个 Deck 显示名称和包含的卡片数量，方便我选择要复习的 Deck。
3. 作为一名 Learner，我可以在 Deck 选择页同一屏中选择复习模式：标准复习（混合新卡和到期复习卡）、仅复习（只看已学过的到期卡片）、仅新卡（只看还没学过的卡片）、速通（遍历 Deck 全部卡片，随机顺序，忽略到期时间）。
4. 作为一名 Learner，在标准复习或仅新卡模式下，我可以设定今日新卡上限（默认 20），控制每天引入多少张新卡片，避免一次性面对太多新内容。
5. 作为一名 Learner，我的每日新卡上限和"新的一天起点"偏好会自动保存，下次打开复习页面时不需要重新设置。如果我在同一天调低了上限且已学数量已超新上限，系统会弹出提示告知我今日不再引入新卡。
6. 作为一名 Learner，上次选择的 Deck 和模式会被记住，下次进入复习页面时自动回填，减少重复操作。
7. 作为一名 Learner，开始复习后，系统每次展示一张卡片的正面（Front），我可以边看边回忆。
8. 作为一名 Learner，点击卡片区域翻面后，我可以看到背面的释义（Back），并在 Front 和 Back 旁边各看到 🔊 按钮，点击即可听 TTS 发音。
9. 作为一名 Learner，翻面后底部出现四个评分按钮（Again 红色/ Hard 橙色/ Good 绿色/ Easy 蓝色），我根据自己实际的记忆程度点击对应的按钮，FSRS 算法会根据我的评分更新卡片的调度状态。
10. 作为一名 Learner，每评分完一张卡片，系统自动取下一张最早到期的卡片展示给我，并在底部状态栏实时显示"今日已复习 N 张"和"剩余 M 张"。
11. 作为一名 Learner，在标准复习和仅新卡模式下，如果今日新卡数已达上限，系统会弹出确认对话框告知我，确认后仍然可以继续复习到期卡片，只是不再引入新卡。
12. 作为一名 Learner，在仅复习模式下，我只会看到已学过且到期的卡片，不会看到任何新卡。
13. 作为一名 Learner，在仅新卡模式下，我只会看到从未学过的卡片，按创建顺序出现，受每日上限约束。
14. 作为一名 Learner，在速通模式下，系统会按随机顺序让我浏览 Deck 的全部卡片，不限制数量，不受 due 时间约束，但仍会正常更新 FSRS 状态。
15. 作为一名 Learner，当所有符合条件的卡片都复习完后，页面显示完成界面，展示本轮统计（复习 X 张，新学 Y 张），并告知"下一张卡片将在 X 小时后到期"。
16. 作为一名 Learner，复习过程中我可以通过顶部导航栏随时返回 Deck 选择页。
17. 作为一名 Learner，我的复习数据（FSRS 状态、首次复习日期）实时保存到数据库，即使页面刷新也不会丢失进度。
18. 作为一名 Learner，我之前通过 CSV 导入的卡片在导入时带有完整的 FSRS 状态（包括 `firstReviewDate`），导入后可以无缝进入复习流程。
19. 作为一名 Learner，我导出的 CSV 文件包含所有的 FSRS 状态字段（包括 `firstReviewDate`），确保备份和迁移的完整性。

## Implementation Decisions

### 1. 复习模块架构：独立 Controller + Service + 页面

- 复习功能拥有独立的 `ReviewController`（`/api/review/*`），不与 `FlashcardController`（`/api/cards/*`）混在一起。
- 复习功能拥有独立的 `ReviewService`，包含 `getNextCard()`、`rateCard()`、`getStats()`、`getDecks()` 方法。
- Service 层中评分和取下一张卡完全解耦——`rateCard()` 只负责更新 FSRS 状态，`getNextCard()` 只负责查询。Controller 层进行功能拼接：评分后立即调取下一张，合并到一次 HTTP 响应中。
- 前端为独立 HTML 页面（`/review/`）+ 独立 Vite Library Mode IIFE bundle（`review-bundle.js` + `review-bundle.css`）。
- 复习页面不依赖 WebSocket，纯 REST API 通信。不依赖 ChatContext 或 useReducer。

### 2. REST API 设计

**GET /api/review/next?deckId=X&mode=STANDARD&limit=20**
```
Response: {
  "card": {
    "id": "uuid", "front": "yesterday", "back": "昨天",
    "tags": [...], "stability": 2.5, "difficulty": 0.0,
    "cardState": 0, "due": "...", "firstReviewDate": null
  } | null
}
```
- `mode` 参数控制过滤逻辑：STANDARD（新卡+到期卡混合）、REVIEW_ONLY（仅 `cardState != 0 && due <= now`）、NEW_ONLY（仅 `cardState = 0`）、CRAM（全 Deck 随机，忽略 due）。
- `limit` 参数仅对 STANDARD 和 NEW_ONLY 模式生效，控制当日还可引入多少新卡。
- 返回 `null` 表示没有更多符合条件的卡片，前端展示完成页面。

**POST /api/review/rate**
```
Request:  { "cardId": "uuid", "rating": "GOOD" }
Response: {
  "card": { ... } | null,
  "stats": { "reviewedToday": 12, "remaining": 5, "learnedToday": 3, "dailyLimit": 20 }
}
```
- 服务端自行获取 `Instant.now()`，不信任客户端时间。
- 评分成功后立即返回下一张卡片数据（复用 `getNextCard` 逻辑），减少一次 HTTP 往返。
- `stats` 附带最新统计，前端直接更新状态栏。

**GET /api/review/stats?deckId=X&mode=STANDARD&limit=20**
```
Response: {
  "reviewedToday": 12, "remaining": 5,
  "learnedToday": 3, "dailyLimit": 20,
  "nextDueAt": "2025-06-05T06:00:00Z"
}
```
- `learnedToday`：今日 `firstReviewDate >= 今日开始时间` 的卡片计数。
- 今日开始时间 = 用户偏好 `dayStartHour`（默认 6:00），按用户时区转换后计算 UTC 边界。
- `remaining`：`due <= now` 且 `cardState != 0` 的卡片数（不含当前正在复习的这张）。

**GET /api/review/decks**
```
Response: [{ "id": "uuid", "name": "Daily English", "type": "deck", "cardCount": 45 }, ...]
```
- 返回用户的所有 Deck 标签（`Tag.type = "deck"`）及各自的卡片数。

### 3. 数据库变更

#### Card 实体新增字段
```
firstReviewDate  (Instant, nullable)
```
- 首次 `repeat()` 调用时写入当前日期（`now` 的日期部分）。
- 存量卡片（创建于该字段添加之前）的 `firstReviewDate` 为 NULL，在当前代码库中所有卡片状态均为 0（New），无需迁移。

#### UserPreferences 新实体
```
UserPreferences extends BaseEntity {
    String id;              // UUID (inherited)
    String userId;          // FK to User, unique
    int newCardDailyLimit;  // default 20
    int dayStartHour;       // 0-23, default 6
    String timezone;        // e.g. "Asia/Shanghai", default from browser Intl API
    String lastDeckId;      // convenience: last selected deck
    String lastMode;        // convenience: last selected mode
    Instant createTime;     // inherited
    Instant updateTime;     // inherited
}
```
- 与 User 一对一关系，通过 `userId` 关联。
- 默认值在 Java 层设置，不依赖数据库 DEFAULT 约束。
- `lastDeckId` 和 `lastMode` 是纯 UI 便利字段，用于记住上次选择。
- `timezone` 首次由前端传 `Intl.DateTimeFormat().resolvedOptions().timeZone`，后续可修改。

#### CSV 导入导出扩展
- 导出 CSV header 新增 `firstReviewDate` 列，格式为 ISO 日期（`2025-06-04`）。
- 导入 CSV 解析器 `CardCsvParser` 按 header 名称匹配该列，不存在或为空则设为 null（向后兼容存量 CSV）。
- `FsrsFields` 记录新增 `Instant firstReviewDate` 字段。

### 4. 复习四种模式

| 模式 | 查询逻辑 | 引入新卡 | 受上限约束 | 更新 FSRS |
|------|----------|:---:|:---:|:---:|
| **标准复习（STANDARD）** | `due <= now ORDER BY due ASC` | 是 | 是 | 是 |
| **仅复习（REVIEW_ONLY）** | `cardState != 0 AND due <= now ORDER BY due ASC` | 否 | 不适用 | 是 |
| **仅新卡（NEW_ONLY）** | `cardState == 0 ORDER BY createTime ASC` | 全是 | 是 | 是 |
| **速通（CRAM）** | `deckId` 全部卡片，随机顺序 | 全部 | 否 | 是 |

- 模式选择是会话级别的查询过滤器，不持久化到数据库。
- 速通模式同样调用 `repeat()` 更新 FSRS 状态——只是卡片出现顺序不受 `due` 约束。

### 5. 每日新卡上限逻辑

- 上限计数基准："今日" = 用户偏好的 `dayStartHour` 在用户时区下计算出的 UTC 时间边界。
  - 例：`dayStartHour=6, timezone=Asia/Shanghai` → 今日开始于 `前一天 22:00 UTC`。
- 新卡定义：`firstReviewDate >= 今日开始时刻` 的卡片。
- 进入复习时，如果"已学新卡数 >= 上限"（调低上限后），弹出确认对话框："今日新卡已达上限（已学 N / 上限 M），今日不再引入新卡。仍然继续复习吗？"——确认后以不引入新卡的方式继续（纯复习旧卡），不提供突破上限的选项。
- 上限提示适用于标准复习和仅新卡模式。仅复习和速通模式不触发。

### 6. 评分与 FSRS 调度

- 每次评分调用 `FsrsScheduler.repeat(cardState, rating, Instant.now(), aleaPrng)`。
- `now` 由服务端 `Instant.now()` 获取，不信任客户端时间。
- `repeat()` 返回值直接映射回 Card 实体字段（stability、difficulty、cardState、due、reps、lapses、lastReview）。
- 首次评分时同步写入 `firstReviewDate`（取 `now` 的日期部分）。
- 评分完立即 `CardRepository.save()`，实时持久化，保证刷新/崩溃不丢状态。

### 7. 前端架构

#### 页面路由（非 SPA）
- URL：`/review/` → `src/main/resources/static/review/index.html`
- React 入口：`src/main/frontend/src/entry/review-entry.tsx`
- Vite 构建：`vite.config.review.ts`，输出 `review-bundle.js` + `review-bundle.css` 到 `static/shared/`
- 与 chat-bundle、manage-bundle、header-bundle 共享 React CDN 加载方式

#### 组件树与三阶段状态机
```
ReviewApp (useState: stage)
├─ stage === "deck-picker"
│   └─ DeckPicker
│       ├─ DeckCard × N          (deck 列表 + 卡片数)
│       ├─ ModeSelector          (4 个 radio，单选)
│       ├─ DailyLimitInput       (仅 STANDARD / NEW_ONLY 可见)
│       └─ StartButton           (点击前校验)
├─ stage === "reviewing"
│   └─ ReviewPage
│       ├─ TopBar                (← 返回, Deck名)
│       ├─ CardDisplay
│       │   ├─ FrontText + 🔊    (翻面后显示TTS)
│       │   └─ BackText + 🔊     (翻面后显示TTS)
│       ├─ StatsBar              (已复习N | 剩余M | 新卡X/Y)
│       └─ RatingButtons         (Again/Hard/Good/Easy, 翻面后出现)
└─ stage === "complete"
    └─ CompletePage              (统计摘要 + 下次到期时间 + 返回按钮)
```
- 三阶段切换纯前端 `useState`，无 URL 路由变化。
- 翻面交互：点击卡片区域切换 `flipped: boolean` 状态。

#### TTS 按钮
- 翻面后 Front 和 Back 各出现 🔊 按钮。
- 复用现有 `src/main/frontend/src/shared/tts.ts` 的 `speakText()` 函数，`lang="en-US"`, `rate=0.95`。
- 前端先调用 `englishOnly()` 判断文本是否包含英文，仅含英文时显示按钮。

#### 评分按钮颜色
| 按钮 | 颜色 | 语义 |
|------|------|------|
| Again | `#e74c3c` 红色 | 忘记了，重新来 |
| Hard | `#e67e22` 橙色 | 努力后想起来了 |
| Good | `#27ae60` 绿色 | 正常答对 |
| Easy | `#3498db` 蓝色 | 很简单 |

#### Header 导航更新
- Header 导航栏（`header-bundle.js`）新增"复习"链接，指向 `/review/`。
- 现有 ManagePageIT 的 `manageNavSidebar()` 测试需将导航链接数从 2 更新为 3。

### 8. 应用配置

在 `application.yml` 中不新增配置——每日新卡上限和"新的一天起点"存入 `UserPreferences` 表，由用户自行管理，不做全局硬编码。

唯一与配置相关的：`UserPreferences.dayStartHour` 的默认值 6 在 Java 代码中定义（实体字段默认值）。

## Testing Decisions

### 测试原则

- 只测试外部行为（给定输入 → 预期输出或 DOM 状态），不测试实现细节。
- 单元测试（Java）：`ReviewService` 的纯逻辑测试，mock Repository 层。
- 单元测试（前端 Vitest）：组件渲染测试，mock `global.fetch`。
- E2E 测试（Playwright）：以 Learner 视角验证完整 DOM 交互流程 + H2 数据断言。

### 现有测试的回归调整

| 测试文件 | 所需变更 |
|----------|---------|
| `FlashcardIT.java` | Card 断言新增 `firstReviewDate` 为 null（新创建卡片未经复习） |
| `FlashcardBatchIT.java` | CSV header 和数据行新增 `firstReviewDate` 列，验证往返一致性 |
| `ManagePageIT.java` | `manageNavSidebar()` 导航链接数从 2 → 3（新增"复习"入口） |

### 新增 E2E 测试：ReviewIT

参照 `FlashcardIT` 和 `FlashcardBatchIT` 的模式（extends `E2ETestBase`，Playwright + H2 断言，不依赖 WireMock）。测试场景：

1. **Deck 选择 + 模式选择 + 上限设置** — 验证 Deck 列表渲染、模式 radio 切换、上限输入框的条件显示、开始按钮。
2. **标准复习：翻面 → 评分 → 下一张** — 翻面前只显示 Front，翻面后显示 Back + TTS + 评分按钮；评分 GOOD 后更新 FSRS 状态和 `firstReviewDate`。
3. **连续评分 + 统计刷新** — 评分后 StatsBar 更新 `reviewedToday` 和 `remaining`。
4. **队列耗尽 → 完成页** — 所有卡片复习完后显示统计和"下一张将在 X 小时后"提示。
5. **仅复习模式** — 不出现 `cardState=0` 的卡片。
6. **仅新卡模式** — 只出现 `cardState=0` 的卡片。
7. **速通模式** — 随机顺序，全 Deck 遍历。
8. **上限提示弹窗** — 已学数 ≥ 上限 → 确认 → 继续但不引入新卡。
9. **UserPreferences 持久化** — 设置上限后刷新页面，偏好仍然生效。

### E2ETestBase 扩展

- Autowire `UserPreferencesRepository`。
- 可选：新增 helper 方法 `navigateToReview()`、`flipCard()`、`rateCard(rating)`。

### 前端单元测试（Vitest）

- `DeckPicker.test.tsx`：Deck 列表渲染、模式切换、上限输入显示/隐藏、开始按钮状态。
- `ReviewPage.test.tsx`：翻面交互、评分按钮点击 → `fetch` 调用、统计刷新。
- `CompletePage.test.tsx`：统计数据显示、下次到期时间、返回按钮。
- `RatingButtons.test.tsx`：四个按钮渲染、颜色、点击回调。

### 应用配置

`application-e2e.yml` 无需新增配置——复习功能不需要调 LLM，所有偏好从 `UserPreferences` 表读取。

## Out of Scope

1. **FSRS 参数（21 个 W 值）用户可调** — 使用硬编码默认参数，不做用户级或 Deck 级参数调整。
2. **目标留存率（Desired Retention）用户可调** — 使用硬编码 0.9，不暴露设置入口。
3. **统计图表/进度趋势** — 不做日期维度的复习量图表或正确率趋势。
4. **复习提醒/推送通知** — 无浏览器通知或邮件提醒。
5. **Deck 级别的每日复习上限** — 上限仅在会话选择时设定，不做 per-Deck 的独立上限。
6. **跨设备同步** — 复习状态仅存本地 H2。
7. **每日新卡上限的"跨会话计数"持久化（新增独立表）** — 通过 `firstReviewDate` 字段实现，不加新的计数表。
8. **不同日子不同的复习上限** — 不做"周一三五 20 张，周末 50 张"等规则。
9. **自动刷新到期卡片** — 到达完成页后不会自动检测新到期卡片，需要用户手动返回重新进入。

## Further Notes

- 复习页面是代码库中第三个独立 HTML 页面（after Chat 和 Manage），延续了"非 SPA 多页面 + Vite Library Mode IIFE bundle"的架构传统。
- `ReviewController` 是代码库中第二个 `@RestController`，建立了后端 API 层的"按业务域拆分 Controller"模式。
- `firstReviewDate` 字段的设计避免了为"每日新卡上限"增加独立的计数表——利用已有的 `repeat()` 调用点自然写入，查询时直接过滤即可。
- `UserPreferences` 实体开启了用户级偏好持久化的模式，未来可扩展更多偏好项（如 TTS 语速、主题等）。
- 速通模式虽忽略 `due`，但仍调用 `repeat()` 更新 FSRS 状态——这保证了学习效果，也避免了"速通一轮后回到原始 due"的体验问题。
- 复习模块不涉及 langgraph、WebSocket、或 LLM 调用——它是 100% 的 REST + JPA + React 功能。
