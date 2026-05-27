# 01: LLM Call Log Schema Split

**Status:** `ready-for-agent`

## Parent

`.scratch/memory-optimization/PRD.md`

## What to build

`llm_call_logs` 表新增 `system_prompt` 和 `chat_history` 两个独立 CLOB 列，将当前混在一起的 `request_prompt` blob 拆分为结构化字段，方便按 system prompt / chat history 维度独立查询和分析。

**同步 Agent（Correction、Report、Memory、MemoryCue）：**
通过 `LoggableChatModel` 包装器拦截。当前 `chat()` 方法将 prompt 包装为 `{"text":"..."}` JSON 后存入 `request_prompt`。改为：原始 prompt 文本同时存入新增的 `system_prompt` 字段，`chat_history` 为 null。`request_prompt` 保留不动。

**流式 Agent（Conversation）：**
在 `TurnProcessor.onCompleteResponse()` 回调中，当前通过 `conversationAgent.buildPromptJson()` 构建的 JSON 消息数组（`[{"role":"system",...},{"role":"user",...},...]`）整体存入 `request_prompt`。改为：解析该 JSON 数组，提取 role 为 `system` 的消息内容存入 `system_prompt`，其余 user/assistant 消息列表序列化为 JSON 数组存入 `chat_history`。`request_prompt` 保留不动。

**不改动项：**
- `request_prompt` 列保留，旧数据不动
- 同步 Agent 日志的 `sessionId`、`userId`、`agentType`、`mode` 继续为 null（不补全）
- `LlmCallLogService.saveAsync()` 方法签名不变（新增字段通过 entity setter 在调用方设置）

## Acceptance criteria

- [ ] `LlmCallLog` entity 新增 `systemPrompt`（CLOB）和 `chatHistory`（CLOB）字段，`requestPrompt` 保留
- [ ] `LoggableChatModel.chat()` 中将原始 prompt 存入 `system_prompt`（不再包装 JSON），`chat_history` 为 null
- [ ] `TurnProcessor.onCompleteResponse()` 中解析 `buildPromptJson()` JSON 数组，system 消息 → `system_prompt`，其余 → `chat_history`（序列化为 JSON 数组字符串）
- [ ] H2 表 `llm_call_logs` 自动新增两列（`spring.jpa.hibernate.ddl-auto: update`）
- [ ] 旧记录 `system_prompt` 和 `chat_history` 为 null，`request_prompt` 中数据完整可查
- [ ] `mvn test` 通过（现有单元测试不受影响）
- [ ] `mvn verify` 通过（E2E 测试不受影响——日志表变更对外部行为不可见）

## Blocked by

None — can start immediately

## User stories covered

#1, #2, #3
