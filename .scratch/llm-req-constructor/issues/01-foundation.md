# 01: Foundation — LlmTaskDefinition + LlmReqConstructor + CorrectionAgent

**Status:** `ready-for-agent`

## Parent

`.scratch/llm-req-constructor/PRD.md`

## What to build

创建 `LlmTaskDefinition` 和 `LlmReqConstructor` 两个新组件，迁移 `CorrectionAgent` 作为首个消费者，打通完整的 `register → execute → generate(List<ChatMessage>) → 日志` 端到端链路。

`LlmTaskDefinition` 持有 systemTemplate、userTemplate、paramBuilder、parser、errorStrategy 五个字段。`LlmReqConstructor` 提供 `register` 和 `execute`（3 参）两个公开方法。内部流程：注册表查找 → paramBuilder 填占位符 → fillTemplate 分别填 system/user 模板 → 组装 `[SystemMessage, UserMessage]` → `ChatLanguageModel.generate()` → 日志（13 参重载，填充 systemPrompt/chatHistory/tokenUsage）→ parser → 返回。

`correction.txt` 模板在 `---USER---` 处拆分，system 部分保留纯指令，user 部分为 `"User's utterance: {userInput}"`。`CorrectionAgent` 注册 `LlmTaskDefinition` 时传入两部分模板和共享的 `paramBuilder`。

## Acceptance criteria

- [ ] `LlmTaskDefinition` 通过 builder 模式构造，5 字段齐全
- [ ] `LlmReqConstructor.register(TaskName, LlmTaskDefinition)` 写入 ConcurrentHashMap
- [ ] `LlmReqConstructor.execute(CORRECTION, params, ctx)` → `generate(List<ChatMessage>)` → 返回解析结果
- [ ] 日志 `saveAsync` 调用 13 参重载，systemPrompt 不为 null，chatHistory 不为 null，inputTokens/outputTokens 从 tokenUsage 提取
- [ ] `correction.txt` 模板拆分后 SystemMessage 不含 `{userInput}`，UserMessage 不含 `You are an English coach...`
- [ ] `CorrectionAgent` 现有测试全部通过（适配 mock 后）
- [ ] E2E `correction.txt` 测试模板同步拆分，标记关键词在 system 部分

## Blocked by

None — can start immediately.
