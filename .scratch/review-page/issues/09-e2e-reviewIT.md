# 09: E2E 回归测试 — ReviewIT（9 个场景）

## Parent

[PRD: 闪卡复习功能](../PRD.md)

## What to build

新建 `ReviewIT.java` E2E 测试类（extends `E2ETestBase`，登录绕过、H2 断言、Playwright DOM 等待）。覆盖 9 个场景：从 Deck 选择到完成页的完整复习流程、四种模式各一个场景、上限弹窗、UserPreferences 持久化。每个测试中通过 Repository 直接 seed 测试数据（Card + Tag），复习完成后验证 H2 中 FSRS 字段和 `firstReviewDate` 的正确性。扩展 `E2ETestBase` 自动装配 `UserPreferencesRepository`。

参考现有 `FlashcardIT` 和 `FlashcardBatchIT` 的模式——不依赖 WireMock（复习不调 LLM）。全部 DOM 等待使用 `page.waitForFunction()` 而非 WebSocket 帧拦截。`@AfterEach` 自动截图。

## Acceptance criteria

- [ ] `ReviewIT` 新建，`extends E2ETestBase`，`@ActiveProfiles("e2e")`
- [ ] `E2ETestBase` 新增 `@Autowired UserPreferencesRepository`
- [ ] `@BeforeEach` seed 数据：创建 Deck 标签 + 3 张新卡（cardState=0）+ 2 张已学到期卡（cardState=2, due<=now）+ 1 张未到期卡（due>now）
- [ ] 场景 1 — Deck 选择 + 模式选择 → 开始：导航到 `/review/`，验证 Deck 列表、模式 radio、上限输入框条件显示、开始按钮
- [ ] 场景 2 — 标准复习：翻面前只显示 Front，翻面后显示 Back + TTS + 评分按钮；评分 GOOD → 下一张出现；H2 验证 stability/difficulty/cardState/firstReviewDate 更新
- [ ] 场景 3 — 连续评分 + 统计刷新：评分后 StatsBar 实时更新 "已复习" 和 "剩余"
- [ ] 场景 4 — 队列耗尽 → 完成页：所有到期卡片评完后，显示 CompletePage + "下一张将在约 X 小时后" + 返回按钮
- [ ] 场景 5 — 仅复习模式：不出现 cardState=0 的卡片
- [ ] 场景 6 — 仅新卡模式：只出现 cardState=0 的卡片
- [ ] 场景 7 — 速通模式：全 Deck 遍历，验证最后 H2 中所有卡片 cardState 已变更（即调用了 repeat）
- [ ] 场景 8 — 上限弹窗：先 seed UserPreferences（newCardDailyLimit=0, learnedToday=0→但在开始前已手动测了 0 张变 0→上限提示），弹出确认 → 继续且不引入新卡
- [ ] 场景 9 — UserPreferences 持久化：设置上限 → 刷新页面 → 进入复习 → 上限值仍然生效
- [ ] 所有测试方法 `@AfterEach` 自动截图到 `target/e2e-screenshots/`
- [ ] `mvn verify -Dtest=ReviewIT` 通过

## Blocked by

- #01-#08: 所有功能就绪后才能跑 E2E
