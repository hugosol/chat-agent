# 02 — CorrectionAgent 迁移到 TaskRunner

**Status:** `ready-for-agent`

## What to build

将 `CorrectionAgent.analyze()` 的内部实现从直接调用 `chatModel.chat(prompt)` 改为通过 `TaskRunner.execute(CORRECTION, params, ctx)`。

Agent 构造时注册 `CORRECTION` 任务：模板用 `correction.txt`，`paramBuilder` 映射 `userInput` → `{userInput}` 占位符，`parser` 指向私有 `parseJson()` 方法（bracket-snippting + Jackson TypeReference，逻辑不变），`errorStrategy` 用 `SWALLOW`。

新增内部 record `CorrectionParams(String userInput)`。`analyze()` 签名增加 `TaskContext` 参数，调用方（`CorrectionNode`）负责构造 `TaskContext`——从 `CoachState` 读取 `sessionId`/`userId`/`mode`。

`parseJson()` 保留为私有方法，内部逻辑不变（提取中括号 JSON、Jackson 反序列化、空列表兜底）。

**过渡期注意**：此 Slice 完成后，CorrectionAgent 的 LLM 调用会被双重记录（TaskRunner 含完整 metadata + LoggableChatModel 无 metadata），Slice 06 将消除后者。

## Acceptance criteria

- [ ] `CorrectionAgent` 构造函数注入 `TaskRunner` + `PromptLoader`，不再注入 `ChatLanguageModel`
- [ ] 构造函数内调用 `runner.register(CORRECTION, TaskDefinition.builder()...build())`
- [ ] 新增内部 record `CorrectionParams(String userInput)`
- [ ] `analyze(String userInput, TaskContext ctx)` 委托给 `runner.execute(TaskName.CORRECTION, new CorrectionParams(userInput), ctx)`，SWALLOW 返回 null 时转为空列表
- [ ] `parseJson(String response)` 保持为私有方法，bracket-snippting + Jackson TypeReference 逻辑不变
- [ ] `CorrectionNode.apply()` 从 `state.sessionId()`/`state.userId()`/`state.mode()` 构建 `TaskContext`，传给 `correctionAgent.analyze(userInput, ctx)`
- [ ] `CorrectionAgentTest` 改为注入 `TaskRunner`（配合 StubModel），6 个已有用例全部通过：`nullInputReturnsEmptyList`、`blankInputReturnsEmptyList`、`validJsonArrayReturnsCorrections`、`jsonWrappedInSurroundingTextIsExtracted`、`noBracketsReturnsEmptyList`、`invalidJsonReturnsEmptyList`
- [ ] `mvn test` 中 CorrectionAgentTest 全部通过

## Blocked by

- [01 — TaskRunner 核心基础设施](./01-taskrunner-core.md)
