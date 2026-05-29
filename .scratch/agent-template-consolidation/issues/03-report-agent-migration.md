# 03 — ReportAgent 迁移到 TaskRunner

**Status:** `ready-for-agent`

## What to build

将 `ReportAgent.generate()` 的内部实现从直接调用 `chatModel.chat(prompt)` 改为通过 `TaskRunner.execute(REPORT, params, ctx)`。

Agent 构造时注册 `REPORT` 任务：模板用 `report.txt`，`paramBuilder` 封装 `buildConversationText()` + `buildErrorsText()` 逻辑（将 `List<MessageData>` + `List<CorrectionData>` 映射为 `{fullConversation}` + `{allCorrections}` 占位符），`parser` 指向私有 `parseReport()` 方法（Jackson Map + getString/getInt 提取器，逻辑不变），`errorStrategy` 用 `SWALLOW`。

新增内部 record `ReportParams(List<MessageData> messages, List<CorrectionData> corrections)`。`generate()` 签名增加 `TaskContext` 参数。调用方 `CoachMessageHandler.onEndSession()` 负责构造 `TaskContext`。

`ReportResult` record 保持稳定，3 个外部调用方（SessionStore、MemoryService、EntityMapper）无需修改。

**过渡期注意**：同 Slice 02，双日志过渡期。Slice 06 将消除。

## Acceptance criteria

- [ ] `ReportAgent` 构造函数注入 `TaskRunner` + `PromptLoader` + `ObjectMapper`，不再注入 `ChatLanguageModel`
- [ ] 构造函数内调用 `runner.register(REPORT, TaskDefinition.builder()...build())`
- [ ] 新增内部 record `ReportParams(List<MessageData> messages, List<CorrectionData> corrections)`
- [ ] `generate(List<MessageData> messages, List<CorrectionData> allCorrections, TaskContext ctx)` 委托给 `runner.execute(TaskName.REPORT, new ReportParams(messages, allCorrections), ctx)`
- [ ] `parseReport(String response)` 保持为私有方法，Jackson Map 提取 + getString/getInt + fallback 逻辑不变
- [ ] `ReportResult` record 签名和字段不变（5 个字段：overallAssessment, topicSummary, errorSummary, fluencyScore, keyTakeaway）
- [ ] `CoachMessageHandler.onEndSession()` 构造 `TaskContext(sessionId, userId, mode)` 并传入 `reportAgent.generate(messages, corrections, ctx)`
- [ ] `ReportAgentTest` 改为注入 `TaskRunner`（配合 StubModel），7 个已有用例全部通过：valid JSON 解析所有字段、缺失字段默认值、非法 JSON fallback 为 raw response、CORRECTION 消息过滤、errors 格式化、空 corrections placeholder、部分 JSON 使用默认值
- [ ] `mvn test` 中 ReportAgentTest 全部通过
- [ ] `SessionStore`、`MemoryService`、`EntityMapper` 中 `ReportResult` 引用无需修改（接口稳定）

## Blocked by

- [01 — TaskRunner 核心基础设施](./01-taskrunner-core.md)
