# PRD: 闪卡（Flashcard）模块 —— 卡片录入 MVP

**Status:** `ready-for-agent`

## Problem Statement

Chat Agent 是一个 AI 英语口语练习工具。Learner 在 Practice session 中通过对话练习口语，Correction Agent 会标记语法、用词等错误。但目前 Learner 没有独立于对话的对词汇/表达进行复习的工具——遇到不熟悉的单词或想记住的表达，只能记在脑海中，无法系统化地回顾。

Learner 需要一个轻量的闪卡录入功能，可以在对话过程中随时打开，快速记录单词或表达（front/back），并打上标签分类，保存在数据库中供后续复习。

## Solution

在现有聊天页底部新增一个可折叠的闪卡录入面板，与 Debug 面板互斥。录入采用两阶段流：第一阶段极小面板（仅 front 输入框 + "继续"按钮），Learner 可以对照聊天记录找单词；第二阶段展开（back 输入框 + chip 式标签输入 + 保存按钮）。保存后面板自动关闭并短暂闪烁提示，数据通过 REST API 持久化到 H2。

卡片底层使用 FSRS-6（Free Spaced Repetition Scheduler v6）算法初始化调度状态，为后续复习功能预留基础。

闪卡模块与现有聊天功能完全解耦——独立的 JPA 实体、独立的 REST Controller、独立前端 JS 文件。不依赖 WebSocket，不依赖 Practice session。

## User Stories

1. 作为一名 Learner，我可以在对话过程中随时点击 header 上的按钮打开闪卡录入面板，把我的口语练习中遇到的不熟悉单词快速记录下来，不需要离开聊天页面。
2. 作为一名 Learner，我可以先输入卡片的正面（front，如单词或原文），此时面板保持极小的尺寸不遮挡聊天对话记录，让我可以一边看聊天记录一边录入。
3. 作为一名 Learner，点击"继续"后，面板展开到更大尺寸，我可以输入卡片的背面（back，如释义或校正文），以及任意数量的标签。
4. 作为一名 Learner，输入标签时，系统会根据我已创建过的标签提供自动补全建议，我可以从建议中选择已有的标签，也可以输入全新的标签名。
5. 作为一名 Learner，保存成功后，闪卡面板自动折叠关闭，底部出现一闪而过的"已保存"提示文字，我可以立即回到对话中继续练习。
6. 作为一名 Learner，闪卡面板和 Debug 日志面板互斥——展开闪卡面板时如果 Debug 面板处于展开状态会自动折叠，反之亦然，屏幕空间不会被两个面板同时占用。
7. 作为一名 Learner，我创建的卡片和标签自动归属于我的账户，其他 Learner 看不到我的卡片。
8. 作为一名开发者，FSRS-6 算法通过 8 个来自 py-fsrs（官方 Python 参考实现）的测试向量进行了精确验证，确保调度计算的正确性。
9. 作为一名开发者，卡片录入和标签创建的整个流程可以通过 E2E 测试（Playwright + H2）自动回归验证，不依赖 WireMock（因为闪卡模块不调 LLM）。

## Implementation Decisions

### 1. 闪卡模块与聊天功能完全解耦

- 闪卡拥有独立的 JPA 实体（Card、Tag），与现有 Session、Message、ErrorRecord 等聊天实体无 FK 关联。
- 闪卡使用独立的 REST Controller，不通过 WebSocket 通信——这是代码库中首次引入 `@RestController`。
- 前端 JS 独立为 `flashcard.js` 文件，与 `app.js` 的聊天逻辑完全分离，通过 `window` 共享面板互斥状态。
- 共享同一个 `style.css`，新增样式块放在现有样式之后。
- 认证走 JSESSIONID cookie（与 WebSocket 一致），/api/** 不走 permit-all-paths（需要认证），CSRF 对 /api/** 禁用。

### 2. 标签（Tag）作为唯一的卡片组织方式

- Tag 是独立的 JPA 实体（id, name, type, userId），Card 与 Tag 通过 `@ManyToMany` 关联（join table `card_tags`）。
- Tag 有 type 字段（nullable String），为未来引入"Deck"概念预留——当 type 为特定值时，该 Tag 等价于一个牌组。
- MVP 阶段不创建 Deck，所有 Tag 的 type 为 null。未来在独立的 Tag 管理 UI 中设置 type。
- 创建卡片时携带的 tag 名如果不存在，后端自动 create（upsert）；已存在的 tag 直接关联。
- 没有独立的 POST /api/tags 端点——GET /api/tags 仅用于前端 tag 输入的 autocomplete 数据源。
- 未来增加"每张卡片至少有一个 Deck type 的 tag"约束仅需在 Service 层加校验，无需改 schema。

### 3. FSRS-6 算法集成

- 纯 Java 重写 FSRS-6（21 个默认参数常量，~150 行），无 JNI 依赖。
- FRSSchedular 是无状态纯函数——输入卡片当前状态 + 评分 → 输出新状态。序列化器与 JPA 实体之间用字段映射。
- 算法仅用于 MVP 的"初始化卡片状态"，测评函数（repeat）暂不暴露 REST 端点，为后续复习功能预留。
- 默认 FSRS-6 参数（w[0]...w[20]）硬编码为常量，未来可升级为数据库存储的 per-User 参数。
- 新卡片初始化状态：stability=2.5, difficulty=0.0, state=0 (New), due=now, reps=0, lapses=0, lastReview=null。

### 4. 两阶段录入 + chip 标签输入

- 阶段一：面板高度极小（~60px），仅一个 front 输入框 + "继续"按钮。Learner 可看着聊天对话区域中的内容填入单词。
- 阶段二：面板展开到 max-height ~70vh，容纳 back 输入框 + chip 标签输入区域 + "保存"按钮。
- Chip 输入：Learner 在标签输入框打字时，下方浮动一个匹配列表（数据来自 GET /api/tags），点击或回车确认后标签变为 chip 显示，支持退格或点 × 删除。
- 面板折叠时自动回到阶段一，清空所有输入。

### 5. REST API 约定

**POST /api/cards/add**
```
Request:  { "front": "yesterday", "back": "昨天", "tags": ["daily", "time"] }
Response: { "id": "uuid", "front": "yesterday", "back": "昨天", "tags": [...], "due": "..." }
```

**GET /api/tags**
```
Response: [{ "name": "daily", "type": null }, { "name": "time", "type": null }]
```

- userId 从 SecurityContext 获取（e2e profile 下 Principal 为 null → fallback "anonymous"）。
- Tag 自动 upsert：遍历请求中的 tag 名，按 name+userId 查已有记录，不存在的创建（type=null）。

### 6. 前端面板结构

- 面板 DOM 插入 `index.html`，位置在 Debug 面板之前（`<div id="flashcardPanel">`）。
- 面板样式：`position: fixed; bottom: 0; left: 0; right: 0; z-index: 201`，背景 `rgba(0,0,0,0.92)`。
- Header 行包含 "闪卡" toggle 按钮（折叠/展开）和 input 区域。
- Header 按钮放置在现有 header 右侧区域（靠近 "Corrections N" 按钮），与 Debug 面板的 header 按钮物理分离。
- 闪卡面板与 Debug 面板互斥：全局变量 `activePanel: null | 'debug' | 'flashcard'`，展开一个时自动折叠另一个。
- 保存成功后面板自动折叠，底部出现 CSS animation 闪烁提示文字（持续 ~2 秒后消失）。

### 7. 数据模型新增

```
┌─────────────┐     ┌──────────────────┐     ┌─────────────┐
│    Card      │     │   card_tags      │     │     Tag     │
│─────────────│     │──────────────────│     │─────────────│
│ id (PK)     │────▶│ card_id (FK)     │◀────│ id (PK)     │
│ userId      │     │ tag_id (FK)      │     │ name        │
│ front       │     └──────────────────┘     │ type (null) │
│ back        │                              │ userId      │
│ stability   │                              └─────────────┘
│ difficulty  │
│ state       │
│ due         │
│ reps        │
│ lapses      │
│ lastReview  │
│ createTime  │
│ updateTime  │
└─────────────┘
```

Card 和 Tag 均继承 `BaseEntity`（UUID id + createTime + updateTime）。

### 8. FSRS-6 版本与验证

| 属性 | 值 |
|------|-----|
| 版本 | FSRS-6 (The Algorithm · open-spaced-repetition/awesome-fsrs Wiki) |
| 参数个数 | 21 个 double (w[0]...w[20]) |
| 默认参数 | [0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796, 1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542] |
| 参考实现 | py-fsrs v6.3.1（官方 Python 实现，MIT License） |
| 测试向量来源 | `tests/test_basic.py` 中 5 个用例 + 自算 3 个覆盖率补全用例 |
| 测试框架 | JUnit 5, assertDouble with 1e-4 absolute tolerance |

8 个精确测试向量：

| # | 测试名 | 关键预期值 |
|---|--------|-----------|
| 1 | 13 次连续评分间隔 | [0,2,11,46,163,498,0,0,2,4,7,12,21] 天 |
| 2 | 6 次评分后精确状态 | stability ≈ 53.62691, difficulty ≈ 6.3574867 |
| 3 | 连续 Easy → Difficulty 下限 | 10 × Easy → D = 1.0 |
| 4 | 连续 Again → Stability 下限 | 1000 × Again → S ≥ STABILITY_MIN |
| 5 | Fuzz 确定性（Random seed） | seed(42) → 12 天; seed(12345) → 11 天 |
| 6 | Hard modifier (w[15]) | Hard 间隔 < Good 间隔 |
| 7 | Easy boost (w[16]) | Easy stability > Good stability |
| 8 | Same-day review (w[17-19]) | FSRS-6 独有修正公式验证 |

测试 #1-#5 的预期值直接移植自 py-fsrs；#6-#8 的预期值由 py-fsrs 在实施时一次性计算生成。

## Testing Decisions

### 测试原则

- 只测试外部行为（给定输入 → 预期输出或 DOM 状态），不测试实现细节。
- 单元测试不需要 Spring 上下文（纯 JUnit 5 for FSRS）。
- E2E 测试以 Learner 视角验证 DOM 变化 + H2 数据持久化。

### 单元测试：FsrsSchedulerTest

- 8 个测试用例覆盖全部 21 个 FSRS-6 参数和 4 种评分分支。
- 纯 JUnit 5，不需要 Spring Boot 启动，不需要 WireMock。
- 预期值来自 py-fsrs 精确计算（使用与官方代码完全相同的默认参数和 UTC 时间）。

### E2E 测试：FlashcardIT

- 参照现有 `ChatAgentSessionIT` 的模式：`extends E2ETestBase`，Playwright 操作 DOM，验证 H2 数据。
- 不需要 WireMock（闪卡不调 LLM），WireMock Server 在基类中启动但无需注册任何 stub。
- 使用 e2e profile（permit-all-paths: [/**]），userId fallback "anonymous"。
- 测试流程：导航到 index.html → 点击 "闪卡" 按钮 → 阶段一输入 front → 点击"继续" → 阶段二输入 back + 添加标签 → 点击"保存" → 等待闪烁提示文字出现 → 查询 CardRepository 验证卡片和标签已写入 H2。
- 在 E2ETestBase 中新增 `@Autowired CardRepository` 和 `@Autowired TagRepository`。

## Out of Scope

1. **闪卡复习功能** — 包括每日复习队列、评分按钮（Again/Hard/Good/Easy）、复习进度统计。
2. **Deck/Tag type 管理** — Tag 的 type 字段 MVP 保持 null，不提供设置 type 的 UI。
3. **卡片列表/编辑/删除** — MVP 只有创建功能，不支持查看已创建卡片列表或编辑删除。
4. **从 Practice session 的 Correction 自动生成闪卡** — 纯手动录入，不关联会话数据。
5. **FSRS 参数优化（Optimizer）** — 使用默认参数，不对 Learner 的复习日志进行参数学习。
6. **暴富文本/Markdown 卡片内容** — 卡片内容为纯文本。
7. **卡片跨设备同步** — 数据仅存本地 H2。

## Further Notes

- 闪卡录入是第一个使用 REST API 的功能，为未来更多 REST 资源（卡片列表、编辑、删除）建立了模式。
- Tag type 字段为空的设计使得未来引入 Deck 概念时无需迁移数据库 schema，仅需更新 Service 层校验逻辑。
- FSRS 调度状态字段（stability, difficulty, state, due 等）在创建卡片时自动初始化，前端完全无感——Learner 看不到任何 FSRS 概念，只看到 front/back/tags。
- 面板两阶段设计与移动端 390px 宽度兼容，chip 标签可自动换行。
