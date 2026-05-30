# 03 — E2E 回归测试：完整录入流程验证 + H2 数据断言

**Status:** `ready-for-agent`

## Parent

[PRD: 闪卡模块 —— 卡片录入 MVP](../PRD.md)

## What to build

新增 `FlashcardIT` E2E 测试类，覆盖完整的闪卡录入流程——从点击面板按钮到 H2 数据库持久化验证。参照现有 `ChatAgentSessionIT` 模式编写，不依赖 WireMock（闪卡模块不调用 LLM）。

**E2ETestBase 改动：**

- 新增 `@Autowired CardRepository cardRepository` 和 `@Autowired TagRepository tagRepository`
- 可在基类中添加 `takeScreenshot(String name)` 已存在，无需改动
- WireMock Server 正常启动但无需注册任何闪卡相关 stub（`@BeforeEach` 中的 `WireMockStubs.registerAll(wireMockServer)` 继续为聊天测试服务，闪卡测试不受影响）

**FlashcardIT 测试类：**

- 继承 `E2ETestBase`，使用 e2e profile（`permit-all-paths: [/**]`，userId fallback `"anonymous"`，无认证障碍）
- 测试流程（单一测试方法，覆盖全流程）：

  1. 导航到 `index.html`
  2. 点击 header "闪卡"按钮 → 面板出现（`page.waitForSelector("#flashcardPanel:not(.collapsed)")`）
  3. 在 `#flashcardFront` 中输入 `"yesterday"` → 点击"继续"
  4. 面板 stage2 展开，在 `#flashcardBack` 中输入 `"昨天"`
  5. 在 `#flashcardTagInput` 中输入 `"daily"` → 回车 → chip 出现
  6. 在 `#flashcardTagInput` 中输入 `"time"` → 回车 → chip 出现
  7. 点击"保存"按钮
  8. 等待 `#flashcardToast` 出现（`page.waitForSelector("#flashcardToast:not(.hidden)")`）→ 验证文本包含"已保存"
  9. 等待面板自动折叠（`page.waitForSelector("#flashcardPanel.collapsed")`）
  10. 查询 `CardRepository`：验证存在一条 userId="anonymous"、front="yesterday"、back="昨天" 的 Card，FSRS 字段已初始化（stability=2.5, difficulty=0.0, state=0, reps=0, lapses=0）
  11. 查询 `TagRepository`：验证存在两条 Tag（name="daily" 和 "time"，userId="anonymous"），并通过 `card.getTags()` 验证 Card-Tag 的 ManyToMany 关联正确

- 测试结束后 `@AfterEach` 自动截图保存到 `target/e2e-screenshots/`

**不测试的部分（超出 MVP 范围）：**

- 标签自动补全下拉列表的 DOM 验证（autocomplete 为渐进增强，E2E 测试验证核心创建流程即可）
- 面板与 Debug 互斥（现有 E2E 测试中 Debug 面板默认关闭，互斥行为通过手动测试验证）
- 面板 `×` 关闭按钮
- 退格删除 chip

## Acceptance criteria

- [ ] `mvn verify` 通过：`FlashcardIT` 作为 failsafe 插件的一部分通过
- [ ] 测试中 `CardRepository.findById()` 返回的 Card 实体 FSRS 字段精确等于初始化值（stability=2.5, difficulty=0.0, state=0）
- [ ] 测试中 `card.getTags()` 包含两个 Tag（daily 和 time），Tag type 均为 null
- [ ] 测试不依赖 WireMock 注册任何新 stub
- [ ] `E2ETestBase` 新增的 `cardRepository` 和 `tagRepository` 不影响已有 5 个 IT 测试类

## Blocked by

- [02 — 前端闪卡面板](./02-frontend-panel.md)
