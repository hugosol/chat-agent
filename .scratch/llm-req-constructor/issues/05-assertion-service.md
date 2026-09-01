# 05: AssertionService — XML + exampleMessages + executeRaw

**Status:** `ready-for-agent`

## Parent

`.scratch/llm-req-constructor/PRD.md`

## What to build

迁移 `AssertionService` 全部 4 个 task（EXTRACT_TOPICS、EXTRACT_STATE、JUDGE_SAME、MERGE_ASSERTION）到 `LlmReqConstructor`。这是最复杂的迁移——覆盖三个 API 变体：

- EXTRACT_TOPICS：`executeRaw`（原始响应，自备重试）+ `exampleMessages`（2 组 few-shot 示例对，XML 输入 → JSON 数组输出）
- EXTRACT_STATE：`execute` 3 参 + XML 格式对话数据
- JUDGE_SAME：`execute` 3 参 + `exampleMessages`（3 组 few-shot 示例对，Statement A/B → YES/NO）
- MERGE_ASSERTION：`execute` 3 参

`extractTopicsWithRetry` 从 `runner.requestRaw` 改为 `llmReqConstructor.executeRaw`。`exampleMessages` 构建为 `List<ChatMessage>`（`UserMessage(示例输入) → AiMessage(期望输出) → UserMessage(下一示例输入) → AiMessage(...)`）。`LlmReqConstructor` 内部组装时将 exampleMessages 插入到 user 数据之前。

4 个 assertion 模板全部拆分。`extract-topics.txt` 和 `extract-state.txt` 的 system 部分含 `{groupName}` `{groupDescription}` `{topic}` 等上下文占位符。

## Acceptance criteria

- [ ] EXTRACT_TOPICS 走 `executeRaw`，`extractTopicsWithRetry` 3 次重试逻辑不变
- [ ] EXTRACT_STATE、JUDGE_SAME、MERGE_ASSERTION 走 `execute` 3 参
- [ ] `exampleMessages` 静态列表正确插入到 UserMessage 之前
- [ ] `judge-same.txt` 的 3 组示例移到 Java 代码，模板只保留系统指令
- [ ] `extract-topics.txt` 的 2 组示例移到 Java 代码
- [ ] 对话数据以 XML 格式发送（与 Slice 4 一致）
- [ ] `AssertionServiceTest` 全部通过（`@Mock TaskRunner` → `@Mock LlmReqConstructor`）
- [ ] 4 个 E2E assertion 模板同步拆分，标记关键词在 system 部分

## Blocked by

- `01-foundation`
