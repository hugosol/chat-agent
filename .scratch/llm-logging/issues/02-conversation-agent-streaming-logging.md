# 02: ConversationAgent 流式调用日志记录

**Status:** `ready-for-agent`

## Parent

`.scratch/llm-logging/PRD.md` — LLM 调用日志 + 文件日志

## What to build

为 ConversationAgent 的流式 LLM 调用添加持久化日志记录，补全第 01 号 issue 覆盖同步 Agent 后剩下的唯一空白——流式调用无法通过 `ChatLanguageModel` 包装器拦截，需在 `TurnProcessor` 中手动注入。

`ConversationAgent` 新增 `buildPromptJson(List<Message> messages)` 方法：将构建好的消息列表序列化为 JSON 字符串（与 `generateStream()` 内部发送给 LLM 的消息列表一致，包括 system prompt）。

`TurnProcessor.processTurn()` 改动：在调用 `ConversationAgent.generateStream()` 前，调用 `buildPromptJson()` 获取 prompt JSON 并记录 `System.currentTimeMillis()` 作为 startTime。这两个值需要传入 `StreamingChatResponseHandler`。

`StreamingChatResponseHandler.onCompleteResponse()` 改动：在现有的 null guard 之后，计算 `durationMs = System.currentTimeMillis() - startTime`，提取 `tokenUsage.inputTokenCount()` 和 `tokenUsage.outputTokenCount()`，调用 `llmCallLogService.saveAsync(...)` 写入完整日志字段：

- `sessionId` — 从 TurnProcessor 获取
- `userId` — 从 SessionService.getUserId() 获取
- `agentType` — `"CONVERSATION"`
- `mode` — 当前 AgentMode.name()
- `model` — 从配置注入
- `requestPrompt` — buildPromptJson 的返回值
- `responseText` — onComplete 的完整响应文本
- `inputTokens` / `outputTokens` — 从 tokenUsage 提取
- `durationMs` — 计算值
- `status` — SUCCESS（若已通过 null guard）

`ConversationAgent` 的现有 DEBUG 日志 `"sending N messages"` 保持不动（仅作为控制台确认）。

## Acceptance criteria

- [ ] `ConversationAgent.buildPromptJson(List<Message>)` 返回与 `generateStream()` 内部发送给 LLM 相同的消息列表 JSON 字符串
- [ ] `TurnProcessor.processTurn()` 在 `generateStream()` 调用前捕获 prompt JSON 和 startTime
- [ ] `StreamingChatResponseHandler.onCompleteResponse()` 写入 `llm_call_logs` 表，字段：`sessionId` 非空、`userId` 非空、`agentType = "CONVERSATION"`、`mode` 非空、`inputTokens > 0`、`outputTokens > 0`、`durationMs > 0`
- [ ] `StreamingChatResponseHandler.onCompleteResponse()` 在 `response == null` 时不写入日志（不触发 NPE）
- [ ] ConversationAgent 现有 `log.debug("sending {} messages")` 保持不变
- [ ] `mvn test` 全部通过
- [ ] `mvn verify` 全部通过（E2E 使用 WireMock，日志写入 H2 内存库，不影响测试断言）

## Blocked by

- 01: LLM 调用日志 — 数据模型 + 同步 Agent 拦截（需要 `LlmCallLogService` 接口和 `LlmCallLog` 实体）
