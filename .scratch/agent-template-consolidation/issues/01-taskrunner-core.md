# 01 — TaskRunner 核心基础设施

**Status:** `ready-for-agent`

## What to build

创建 TaskRunner 模块的 5 个新类，构成同步 Agent 的统一 LLM 调用执行引擎。不修改任何现有代码。

TaskRunner 是一个 `@Component`，注入原始 `ChatLanguageModel` 和 `LlmCallLogService`，内部持有 `Map<TaskName, TaskDefinition<?, ?>>` 注册表。Agent 在构造时通过 `runner.register(name, task)` 注册任务，运行时通过 `runner.execute(name, params, ctx)` 触发 LLM 调用。

`execute()` 内部流程：取 TaskDefinition → 调用 paramBuilder 填充模板 → `chatModel.chat(prompt)` → `logService.saveAsync()` 写入完整日志（含 sessionId/userId/agentType/mode）→ 调用 parser 解析 → 按 ErrorStrategy 处理异常。

**注意**：此 Slice 不修改 `LangChain4jConfig`，`ChatLanguageModel` bean 仍被 `LoggableChatModel` 包装。TaskRunner 通过 `LoggableChatModel` 调用 LLM，产生"双日志"（TaskRunner 有 metadata + LoggableChatModel 无 metadata），Slice 06 将删除 `LoggableChatModel` 消除双日志。

## Acceptance criteria

- [ ] `TaskName` 枚举定义 5 个值：`CORRECTION`、`REPORT`、`MERGE_LEARNING`、`CHAT_SWITCHES`、`GENERATE_MEMORY_CUE`
- [ ] `TaskDefinition<P, R>` Builder 模式构建不可变对象：`template`（String）、`paramBuilder`（`Function<P, Map<String, String>>`）、`parser`（`Function<String, R>`）、`errorStrategy`（ErrorStrategy）
- [ ] `TaskContext` record：`(String sessionId, String userId, String mode)`，三个字段均可为 null
- [ ] `ErrorStrategy` 枚举两个值：`SWALLOW`（捕获异常 → `log.warn()` → 返回 null）、`THROW`（原样传播）
- [ ] `TaskRunner` 为 `@Component`，注入 `ChatLanguageModel` + `LlmCallLogService`
- [ ] `TaskRunner.register(TaskName, TaskDefinition)` 存入注册表，同名重复注册抛 `IllegalStateException`
- [ ] `TaskRunner.execute(name, params, ctx)` 完整流程可运行，`agentType` 字段从 `TaskName.name()` 自动映射，`inputTokens`/`outputTokens` 保持 null
- [ ] LLM 调用异常（网络/API 错误）记 `log.error` + 日志 `status=ERROR`，解析异常（parser 抛异常）记 `log.warn`
- [ ] `TaskRunnerTest` 5 个用例通过：
  1. 成功执行：LLM 返回正常 → parser 成功 → 日志写入含完整 TaskContext
  2. 解析失败 + SWALLOW：LLM 正常但 parser 抛异常 → warn 日志 → 返回 null
  3. 解析失败 + THROW：parser 抛异常 → 异常冒泡给调用方
  4. LLM 网络失败：`chat()` 抛异常 → 日志 `status=ERROR` → SWALLOW 返回 null
  5. 未注册任务：`execute` 未注册的 TaskName → 抛 `IllegalStateException`

## Blocked by

None — 可立即开始
