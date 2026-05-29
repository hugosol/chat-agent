# 04 — MemoryAgent 重命名为 LearningAgent 并迁移到 TaskRunner

**Status:** `ready-for-agent`

## What to build

将 `MemoryAgent` 类重命名为 `LearningAgent`（更准确反映当前职责——仅负责 learningProfile 合并），同时迁移到 TaskRunner。

Agent 构造时注册 `MERGE_LEARNING` 任务：模板用 `memory-profile.txt`，`paramBuilder` 映射 `oldProfile` + `errorSummary` → `{oldLearningProfile}` + `{errorSummary}` 占位符（含空值→placeholder 逻辑），`parser` 为 `String::trim`，`errorStrategy` 用 `SWALLOW`。

新增内部 record `MergeLearningParams(String oldProfile, String errorSummary)`。`mergeProfile()` 签名增加 `TaskContext` 参数（sessionId/mode 可为 null——跨模式共享学习档案）。

`MemoryService` 中所有 `MemoryAgent` 引用更新为 `LearningAgent`，调用时传入 `TaskContext`。所有其他引用处同步更新类名和导包。

**过渡期注意**：同前面 Slice，双日志过渡期。

## Acceptance criteria

- [ ] 类重命名：`MemoryAgent.java` → `LearningAgent.java`
- [ ] `LearningAgent` 构造函数注入 `TaskRunner` + `PromptLoader` + `AppProperties`，不再注入 `ChatLanguageModel`
- [ ] 构造函数内调用 `runner.register(MERGE_LEARNING, TaskDefinition.builder()...build())`，模板中 `{profileMaxLength}` 静态占位符在构造时用 `AppProperties` 预填充
- [ ] 新增内部 record `MergeLearningParams(String oldProfile, String errorSummary)`
- [ ] `mergeProfile(String oldProfile, String errorSummary, TaskContext ctx)` 委托给 `runner.execute(TaskName.MERGE_LEARNING, params, ctx)`，SWALLOW 返回 null 时 MemoryService 已有 try-catch 兜底
- [ ] `paramBuilder` 中空值→placeholder 逻辑不变（oldProfile 为空时填入 placeholder 文本）
- [ ] `MemoryService` 中 `MemoryAgent` → `LearningAgent`：字段声明、构造函数参数、`generateSingle()` 内调用全部更新
- [ ] `MemoryService.generateSingle()` 构造并传入 `TaskContext(sessionId, userId, null)`（mode 为 null，跨模式共享）
- [ ] 旧 `MemoryAgentTest.java` → 新 `LearningAgentTest.java`，改为注入 `TaskRunner`（配合 StubModel），3 个已有用例全部通过：`mergeProfile_returnsTrimmedResponse`、`mergeProfile_includesAllInputsInPrompt`、`mergeProfile_handlesEmptyOldProfile`
- [ ] 全项目搜索 `MemoryAgent` 无残余引用（除 import 语句外）
- [ ] `mvn test` 中 LearningAgentTest + MemoryServiceTest 全部通过

## Blocked by

- [01 — TaskRunner 核心基础设施](./01-taskrunner-core.md)
