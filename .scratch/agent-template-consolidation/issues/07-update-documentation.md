# 07 — 更新架构文档和 AGENTS.md 反映 TaskRunner 设计

**Status:** `ready-for-agent`

## What to build

同步更新 `docs/architecture.md`、`AGENTS.md`，记录 TaskRunner 模块的引入、LoggableChatModel 的退役、MemoryAgent→LearningAgent 的重命名。可选新增 ADR。

## Acceptance criteria

### `docs/architecture.md`

- [ ] 决策日志新增条目 #40：TaskRunner 统一同步 Agent 模板调用模式（替代 LoggableChatModel，内生提供完整运行时上下文的 LLM 调用日志）
- [ ] 决策日志 #37（LLM 调用日志 + 文件日志）："同步 Agent 通过 `LoggableChatModel` 包装器透明拦截"改为"同步 Agent 通过 `TaskRunner` 统一管理 LLM 调用生命周期与日志"
- [ ] §八 会话历史管理策略「日志写入」行：`LoggableChatModel` 替换为 `TaskRunner.execute()`
- [ ] §八「日志清理」行：保留不变（清理逻辑未变）
- [ ] §十 项目文件结构 `agent/` 目录：`MemoryAgent.java` → `LearningAgent.java`；新增 `TaskRunner.java`、`TaskDefinition.java`、`TaskName.java`、`TaskContext.java`、`ErrorStrategy.java`
- [ ] §十 项目文件结构 `config/` 目录：移除 `LoggableChatModel.java` 行

### `AGENTS.md`

- [ ] Package 结构列表 `agent/`：`MemoryAgent` → `LearningAgent`；新增 `TaskRunner`、`TaskDefinition`、`TaskName`、`TaskContext`、`ErrorStrategy`
- [ ] "Two LLM beans" 段：移除 `LoggableChatModel` 提及，说明 `ChatLanguageModel` 仅由 `TaskRunner` 使用（不再有包装层）
- [ ] "LLM Call Log" 段：`LoggableChatModel` 引用替换为 `TaskRunner`，说明同步 Agent 的日志现在由 TaskRunner 内生写入含完整上下文字段

### 可选新增 ADR

- [ ] 如需，创建 `docs/adr/taskrunner-sync-agent-pattern.md`，记录设计动机、TaskName/TaskDefinition 模式、为何排除 ConversationAgent、如何新增同步 Agent

## Blocked by

- [06 — 删除 LoggableChatModel 并解包装 ChatLanguageModel Bean](./06-remove-loggable-chat-model.md)
