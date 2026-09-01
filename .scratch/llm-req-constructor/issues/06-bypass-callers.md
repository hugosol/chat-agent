# 06: 旁路调用点 — CardEnhanceService + LlmReplayController

**Status:** `ready-for-agent`

## Parent

`.scratch/llm-req-constructor/PRD.md`

## What to build

将两个绕过 TaskRunner 的直接调用点纳入 `LlmReqConstructor` 统一管道。`CardEnhanceService.generateSceneSummary()` 从 `chatLanguageModel.chat(prompt)` 改为 `llmReqConstructor.chat(messages, ctx, agentType, modelType)`——System 部分为角色声明（`"你是电影台词分析助手..."`），User 部分为台词上下文。`LlmReplayController./llm-replay` 从直接 `chatModel.chat(prompt)` 改为使用 `llmReqConstructor.chat()`，适配新的结构化消息格式。

`LlmCallLogServiceTest` 新增对 `systemPrompt` 和 `chatHistory` 字段的断言。

## Acceptance criteria

- [ ] `CardEnhanceService.generateSceneSummary()` 走 `LlmReqConstructor.chat()` 无 Agent 路径
- [ ] CardEnhanceService 的 LLM 调用被记录到 `llm_call_logs` 表
- [ ] `LlmReplayController./llm-replay` 使用 `LlmReqConstructor.chat()` 回放
- [ ] `LlmCallLogServiceTest` 新增 systemPrompt/chatHistory 不为 null 的断言
- [ ] `ChatLanguageModel.chat(prompt)` 的 E2E mock 响应正常

## Blocked by

- `01-foundation`
