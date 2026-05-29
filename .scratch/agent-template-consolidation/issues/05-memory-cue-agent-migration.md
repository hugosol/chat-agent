# 05 — MemoryCueAgent 迁移到 TaskRunner

**Status:** `ready-for-agent`

## What to build

将 `MemoryCueAgent` 的两个 LLM 调用（`detectSwitches` 和 `generateCue`）迁移到 TaskRunner。这是唯一注册两个 TaskDefinition 的 Agent。

Agent 构造时注册两个任务：
- `CHAT_SWITCHES`：模板 `memory-cue-split.txt`，paramBuilder 将 `List<MessageData>` 格式化为 `[MSG#N] USER: ...` 标签行，parser 用 regex `\\[\\s*\\d+(?:\\s*,\\s*\\d+)*\\s*]` 提取开关点列表，errorStrategy `SWALLOW`
- `GENERATE_MEMORY_CUE`：模板 `memory-cue-entry.txt`，paramBuilder 将消息分段 + 段索引映射为占位符，`{cueTopicMaxWords}` 和 `{cueSummaryMaxSentences}` 在构造时用 `AppProperties` 预填充，parser 用 Jackson 反序列化为 `CueResult(topic, summary)`，errorStrategy `SWALLOW`

新增内部 record `SwitchParams(List<MessageData> messages, AgentMode mode)` 和 `CueParams(List<MessageData> messages, AgentMode mode, int segmentIndex)`。两个公方法签名增加 `TaskContext` 参数，调用方 `MemoryCueService` 负责构造。

**过渡期注意**：同前面 Slice，双日志过渡期。Slice 06 将消除。

## Acceptance criteria

- [ ] `MemoryCueAgent` 构造函数注入 `TaskRunner` + `PromptLoader` + `AppProperties`，不再注入 `ChatLanguageModel`
- [ ] 构造函数内调用 `runner.register(CHAT_SWITCHES, ...)` 和 `runner.register(GENERATE_MEMORY_CUE, ...)`
- [ ] 新增内部 record：`SwitchParams(List<MessageData> messages, AgentMode mode)` 和 `CueParams(List<MessageData> messages, AgentMode mode, int segmentIndex)`
- [ ] `detectSwitches(List<MessageData> messages, AgentMode mode, TaskContext ctx)` 委托给 `runner.execute(CHAT_SWITCHES, params, ctx)`，SWALLOW 返回 null 时 MemoryCueService 语义等价于"无话题切换"
- [ ] `generateCue(List<MessageData> messages, AgentMode mode, int segmentIndex, TaskContext ctx)` 委托给 `runner.execute(GENERATE_MEMORY_CUE, params, ctx)`，SWALLOW 返回 null 时 MemoryCueService 已有 per-segment try-catch → 写 SEGMENT_FAILED
- [ ] 两个 parse 方法保留为私有：`parseSwitches(String response)`（regex 提取数组）、`parseCue(String response)`（Jackson `CueResult`）
- [ ] `MemoryCueService.generateCuesAsync()` 构造 `TaskContext(sessionId, userId, mode.name())` 传入两个调用
- [ ] `MemoryCueAgentTest` 改为注入 `TaskRunner`（配合 StubModel），7 个已有用例全部通过：`detectSwitches` 4 个（无开关、单个开关、多个开关、前导文本） + `generateCue` 2 个（解析 topic+summary、非法 JSON 抛异常）+ prompt 注入验证 1 个
- [ ] `mvn test` 中 MemoryCueAgentTest 全部通过

## Blocked by

- [01 — TaskRunner 核心基础设施](./01-taskrunner-core.md)
