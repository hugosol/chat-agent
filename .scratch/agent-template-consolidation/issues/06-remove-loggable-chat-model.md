# 06 — 删除 LoggableChatModel 并解包装 ChatLanguageModel Bean

**Status:** `ready-for-agent`

## What to build

删除 `LoggableChatModel.java` 及其测试文件。修改 `LangChain4jConfig.chatLanguageModel()` 直接返回原始 `OpenAiChatModel`（不再包装）。日志能力已完全由 `TaskRunner` 内生提供。

**前置条件**：Slices 02-05 全部完成后，4 个 Agent 均不再直接调用 `chatModel.chat(prompt)`——所有 LLM 调用都通过 `TaskRunner.execute()` 完成，`TaskRunner` 注入的 `ChatLanguageModel` 不再需要 `LoggableChatModel` 包装层。

删除后，`llm_call_logs` 表中同步 Agent 的日志记录将只有 `TaskRunner` 写入的一条（含完整 `sessionId`/`userId`/`agentType`/`mode`），消除之前 Slices 02-05 迁移期间的"双日志"现象。

## Acceptance criteria

- [ ] 删除 `config/LoggableChatModel.java`
- [ ] 删除 `config/LoggableChatModelTest.java`（或移动到废弃目录）
- [ ] `LangChain4jConfig.chatLanguageModel()` 方法直接返回 `OpenAiChatModel` 实例，移除 `new LoggableChatModel(delegate, logService)` 包装
- [ ] `LangChain4jConfig` 中移除 `LlmCallLogService` 注入（如果仅用于 LoggableChatModel）
- [ ] `mvn compile` 通过，无编译错误
- [ ] `mvn test` 全部通过，无测试失败
- [ ] 全项目搜索 `LoggableChatModel` 无残余引用
- [ ] 验证：手动运行一次完整会话，查询 `llm_call_logs` 表确认同步 Agent 的 `agentType`/`sessionId`/`userId`/`mode` 字段不再为 null

## Blocked by

- [02 — CorrectionAgent 迁移到 TaskRunner](./02-correction-agent-migration.md)
- [03 — ReportAgent 迁移到 TaskRunner](./03-report-agent-migration.md)
- [04 — MemoryAgent 重命名为 LearningAgent 并迁移到 TaskRunner](./04-memory-agent-to-learning-agent.md)
- [05 — MemoryCueAgent 迁移到 TaskRunner](./05-memory-cue-agent-migration.md)
