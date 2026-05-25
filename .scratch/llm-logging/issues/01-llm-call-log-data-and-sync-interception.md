# 01: LLM 调用日志 — 数据模型 + 同步 Agent 拦截

**Status:** `ready-for-agent`

## Parent

`.scratch/llm-logging/PRD.md` — LLM 调用日志 + 文件日志

## What to build

建立 LLM 调用日志的完整基础设施，并使其对同步 Agent 透明生效。

数据层：新建 `LlmCallLog` 实体（继承 `BaseEntity`，字段含 sessionId/userId/agentType/mode/model/requestPrompt/responseText/inputTokens/outputTokens/durationMs/status/errorMessage），对应 `llm_call_logs` 表。`LlmCallLogRepository` 提供基础 CRUD + 按 createTime 范围删除。`LlmCallLogService` 提供 `@Async saveAsync()` 异步写入和 `@PostConstruct cleanupOnStartup()` 启动时清理 3 天前记录。

基础设施：`AsyncConfig` 新增 `llmLogExecutor` 线程池（core=2, max=4）。

拦截层：新建 `LoggableChatModel` 实现 `ChatLanguageModel` 接口，代理所有调用到真实 `OpenAiChatModel`。拦截 `chat(String)` 方法：记录 startTime → 调用 delegate → 异步写日志（成功填充 status=SUCCESS + responseText；失败填充 status=ERROR + errorMessage）。在 `LangChain4jConfig.chatLanguageModel()` bean 中将返回值包装为 `LoggableChatModel`。

被覆盖的同步 Agent：CorrectionAgent、ReportAgent、MemoryAgent.mergeTopic、MemoryAgent.mergeProfile、MemoryCueAgent.detectSwitches、MemoryCueAgent.generateCue。这些调用的 sessionId/userId/agentType/mode/inputTokens/outputTokens 均为 null（包装器无上下文）。

`requestPrompt` 格式：`{"text":"prompt content"}`（JSON 字符串，为未来 PostgreSQL jsonb 预留兼容）。

LangChain4j 现有 `log-requests: true` / `log-responses: false` 配置保持不变。

## Acceptance criteria

- [ ] `LlmCallLog` 实体编译通过，继承 `BaseEntity`，字段与 PRD 表定义一致
- [ ] `LlmCallLogRepository` 提供 `findByCreateTimeBefore(LocalDateTime cutoff)` 方法
- [ ] `LlmCallLogService.saveAsync()` 标注 `@Async("llmLogExecutor")`，构建实体并 save
- [ ] `LlmCallLogService.cleanupOnStartup()` 标注 `@PostConstruct`，内部用 `CompletableFuture.runAsync` 异步执行 `DELETE FROM llm_call_logs WHERE create_time < :threeDaysAgo`，不阻塞启动
- [ ] `AsyncConfig` 新增 `llmLogExecutor` bean（core=2, max=4, 队列无界）
- [ ] `LoggableChatModel` 实现 `ChatLanguageModel`，`chat(String)` 拦截成功/失败路径并写入 `LlmCallLogService`
- [ ] `LoggableChatModel` 也代理 `ChatLanguageModel` 的其他接口方法（如 `generate(ChatMessage...)` 等），确保完整覆盖
- [ ] `LangChain4jConfig.chatLanguageModel()` bean 返回 `LoggableChatModel` 包装后的实例
- [ ] 同步 Agent 执行一次 LLM 调用后，`llm_call_logs` 表新增一条 SUCCESS 记录，`requestPrompt` 非空，`responseText` 非空，`durationMs > 0`
- [ ] LLM 调用失败时，`llm_call_logs` 表新增一条 ERROR 记录，`errorMessage` 非空，`responseText` 为 null
- [ ] `LlmCallLogService.saveAsync()` 不会阻塞 LLM 调用线程（异步执行验证）
- [ ] 启动时 3 天前的记录被清理，近期记录保留
- [ ] 3 个单元测试通过：`LlmCallLogRepositoryTest`、`LlmCallLogServiceTest`、`LoggableChatModelTest`
- [ ] `mvn test` 全部通过

## Blocked by

None — 可立即开始
