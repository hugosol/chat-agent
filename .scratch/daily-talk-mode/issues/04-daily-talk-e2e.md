# 04: Daily Talk E2E Test

**Status:** `ready-for-agent`

## Parent

PRD: `.scratch/daily-talk-mode/PRD.md`

## What to build

新建 `DailyTalkIT.java`（`@ActiveProfiles("e2e")`），对标 `EnglishCoachSessionIT.java` 的结构和模式，覆盖 DAILY_TALK 完整会话流程。需要新建配套的 WireMock stubs 和 mock 回复文件，以及测试用的 prompt 覆盖文件。

### 测试覆盖场景

1. **启动**：发送 `START_SESSION { mode: "DAILY_TALK" }` → 验证 `SESSION_STARTED` 回传 `mode = DAILY_TALK`
2. **2-3 轮对话**：发送 USER_INPUT → 验证 mock SSE stream 到达（回复内容体现教学风格：如教地道表达、补充词汇）
3. **纠正结果**：验证 sidebar 中出现 correction bubble
4. **结束会话**：发送 END_SESSION → 验证 report modal 展示
5. **H2 持久化断言**：
   - `Session.mode = DAILY_TALK`
   - Messages / ErrorRecords / SessionReport 正确写入
   - `UserMemory` 的 TOPIC_SUMMARY 记录 `mode` 字段为 `DAILY_TALK`
   - `UserMemory` 的 LEARNING_PROFILE 记录 `mode` 字段为 `NULL`

### 新增文件

- `src/test/java/com/hugosol/webagent/e2e/DailyTalkIT.java`
- WireMock stubs（JSON Path 匹配 DAILY_TALK 关键词）
- Mock 回复文件：conversation SSE stream + correction JSON + report JSON
- `src/test/resources/prompts/` 下测试 prompt 覆盖（如 conversation-system 测试版本需调整）

## Acceptance criteria

- [ ] `DailyTalkIT.java` 编译通过，`@ActiveProfiles("e2e")` 正确配置
- [ ] WireMock stubs 使用 JSON Path 匹配 DAILY_TALK 关键词，场景状态机正常轮转
- [ ] Mock conversation SSE 回复包含教学风格内容（teach expression / suggest vocabulary / cultural note）
- [ ] Mock correction JSON 包含至少 1 条纠正项
- [ ] Mock report JSON 包含 `overallAssessment`、`errorSummary`、`fluencyScore`
- [ ] DOM 等待逻辑覆盖：input bar 可见 → streaming 开始/结束 → correction bubble 增加 → report modal 可见
- [ ] H2 断言验证 `session.mode = DAILY_TALK`
- [ ] H2 断言验证 `UserMemory.mode = DAILY_TALK`（TOPIC_SUMMARY）和 `NULL`（LEARNING_PROFILE）
- [ ] `@AfterEach` 截图保存到 `target/e2e-screenshots/`
- [ ] `mvn verify -Dtest=DailyTalkIT` 通过（或 `mvn verify` 全部 E2E 通过）

## Blocked by

- `02-daily-talk-mode-core` — 需要 AgentMode.DAILY_TALK 枚举值和模板文件
- `03-topic-memory-isolation` — 需要 UserMemory.mode 字段用于 H2 断言
