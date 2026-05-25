# PRD: LLM 调用日志 + 文件日志

**Status:** `ready-for-agent`

## Problem Statement

当前英语教练的日志系统存在三个痛点：

1. **无文件日志**：所有日志仅输出到控制台。本地调试时难以回溯——控制台窗口关闭后日志全部丢失，排查问题时无法对比多次运行的日志记录。
2. **无持久化 LLM 调用记录**：每次 LLM 调用（对话生成、语法纠正、报告生成、记忆合并、记忆切分）的 prompt、response、token 用量、耗时等信息仅在 stdout 打印（且分布不均——部分 agent 在 INFO 级别打印完整内容，部分仅 DEBUG 打印一行长度）。无法在事后查询"某次纠正花了多少 token"、"某次记忆合并的 prompt 是什么"。
3. **日志级别不一致**：`ReportAgent` 和 `MemoryAgent` 在 INFO 级别打印完整 prompt + response，控制台刷屏严重；`ConversationAgent` 仅一句 DEBUG `"sending N messages"`。

## Solution

两个独立的模块协同解决：

1. **数据库 LLM 调用日志**：新建 `llm_call_logs` 表，记录每次 LLM 调用的 prompt、response、token 用量、耗时、状态。同步 Agent 通过 `ChatLanguageModel` bean 包装器透明拦截；ConversationAgent（流式）通过 `TurnProcessor` 手动注入。写入异步执行（独立线程池），不阻塞 LLM 调用。启动时自动清理 3 天前的记录。

2. **文件日志**：仅 `local` profile 激活 logback file appender——控制台保持 INFO，文件写 DEBUG。按天滚动，保留 3 天。同时将 `ReportAgent` 和 `MemoryAgent` 的日志从 INFO 降级为 DEBUG。

## User Stories

1. 作为一名开发者，在本地调试时，我希望所有业务日志（DEBUG 级别）自动写入 `./logs/` 目录，按天滚动，保留 3 天，以便我回顾历史运行日志。
2. 作为一名开发者，当控制台刷屏时，我希望文件日志包含所有 DEBUG 信息，而控制台仅显示 INFO 及以上，保持终端清爽。
3. 作为一名开发者，我希望每次 LLM 调用的完整 prompt 和 response 自动持久化到数据库，以便在事后通过 SQL 查询定位问题。
4. 作为一名开发者，当 ConversationAgent 的流式调用失败或返回异常内容时，我希望能看到它构建的完整 system prompt + message list（JSON 格式），而不是仅一句 "sending N messages"。
5. 作为一名开发者，当 ReportAgent 生成报告质量不佳时，我希望直接在数据库中找到它的 prompt 和 response，而不需要翻控制台日志。
6. 作为一名开发者，当 MemoryMerge 结果不理想时，我希望能查询上一次记忆合并的输入和输出，定位瓶颈。
7. 作为一名开发者，当 MemoryCue 的话题切分逻辑错误时，我希望能看到 `detectSwitches` 的完整对话文本和 LLM 返回的切分点数组。
8. 作为一名开发者，我希望知道每次 LLM 调用的耗时（毫秒），以便发现性能瓶颈。
9. 作为一名开发者，我希望每次 ConversationAgent 调用记录包含 token 用量（input/output），以便统计成本。（同步 Agent 的 ChatLanguageModel.chat() 返回 String 不含 TokenUsage，因此同步调用的 token 字段为 null）
10. 作为一名开发者，我希望 LLM 调用记录不会因为写入数据库而阻塞主业务线程（所有 DB 写入异步执行）。
11. 作为一个系统，超过 3 天的 LLM 调用记录应在启动时自动清理，防止数据库无限增长。
12. 作为一名开发者，在生产环境下文件日志不激活（仅 local profile），避免磁盘 I/O 开销。
13. 作为系统，现有的 LangChain4j HTTP 层 `log-requests: true` / `log-responses: false` 配置保持不变，控制台依然能看到 HTTP 调用确认。

## Implementation Decisions

### 1. 数据库表 `llm_call_logs`

新独立表，与现有业务实体无耦合。实体继承 `BaseEntity` 获取 `id`（UUID PK）和 `createTime`。

| 字段 | 类型 | 说明 |
|------|------|------|
| `sessionId` | VARCHAR(36) 可空 | Practice session UUID。ConversationAgent 手动注入；其他 Agent（通过包装器拦截）为 null |
| `userId` | VARCHAR 可空 | Learner 标识。ConversationAgent 手动注入；其他 null |
| `agentType` | VARCHAR 可空 | 枚举值：CONVERSATION / CORRECTION / REPORT / MEMORY_TOPIC / MEMORY_PROFILE / MEMORY_CUE_SPLIT / MEMORY_CUE_ENTRY。ConversationAgent 手动注入；其他 null |
| `mode` | VARCHAR 可空 | AgentMode 枚举名。ConversationAgent 手动注入；其他 null |
| `model` | VARCHAR(100) | 模型名，从配置注入 |
| `requestPrompt` | CLOB | 统一 JSON 格式。同步 Agent：`{"text":"prompt content"}`；ConversationAgent：消息列表 JSON |
| `responseText` | CLOB 可空 | LLM 完整响应文本。流式在 onComplete 时填入 |
| `inputTokens` | INT 可空 | Prompt token 用量（仅 ConversationAgent 可获取） |
| `outputTokens` | INT 可空 | Completion token 用量（仅 ConversationAgent 可获取） |
| `durationMs` | BIGINT | 调用耗时，System.currentTimeMillis() 差值 |
| `status` | VARCHAR(20) | SUCCESS 或 ERROR |
| `errorMessage` | VARCHAR 可空 | 异常信息 |

### 2. 写入策略：混合方案

#### 2.1 同步 Agent — ChatLanguageModel 包装器

- 新建 `LoggableChatModel` 实现 `ChatLanguageModel` 接口，代理所有调用到真实 `OpenAiChatModel`
- 在 `LangChain4jConfig.chatLanguageModel()` bean 中包装返回值
- 拦截 `chat(String)` 方法：记录 startTime → 调用 delegate → 异步写日志（成功/失败）
- 覆盖 Agent：CorrectionAgent、ReportAgent、MemoryAgent.mergeTopic、MemoryAgent.mergeProfile、MemoryCueAgent.detectSwitches、MemoryCueAgent.generateCue
- `sessionId`、`userId`、`agentType`、`mode`、`inputTokens`、`outputTokens` 均为 null

#### 2.2 流式 Agent — ConversationAgent 手动注入

- `ConversationAgent` 新增 `buildPromptJson(...)` 方法：序列化消息列表为 JSON
- `TurnProcessor.processTurn()` 在调用 `generateStream()` 前：
  - 调用 `buildPromptJson()` 获取 prompt JSON
  - 记录 `startTime`
- 在 `StreamingChatResponseHandler.onCompleteResponse()` 中：
  - 计算 duration，提取 tokenUsage
  - 调用 `llmCallLogService.saveAsync(...)` 传入完整字段（sessionId、userId、agentType=CONVERSATION、mode、tokens）

### 3. 异步写入 + 启动清理

`LlmCallLogService`：
- `@Async("llmLogExecutor")` 方法 `saveAsync(...)` — 构建实体并 save
- `@PostConstruct` 方法 `cleanupOnStartup()` — 启动时异步执行 `DELETE FROM llm_call_logs WHERE create_time < :threeDaysAgo`（内部用 CompletableFuture.runAsync，不阻塞启动）

`AsyncConfig` 新增 `llmLogExecutor` 线程池：core=2, max=4。

### 4. 文件日志配置

- 新建 `src/main/resources/logback-spring.xml`
- `<springProfile name="local">` 包裹 file appender
- Console appender 全局生效（INFO 级别）
- File appender：`./logs/english-coach.%d{yyyy-MM-dd}.log`，DEBUG 级别，`TimeBasedRollingPolicy`，`maxHistory=3`
- Pattern：`%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n`

### 5. 日志级别调整

- `ReportAgent`：`log.info` → `log.debug`（4 处）
- `MemoryAgent`：`log.info` → `log.debug`（4 处）

### 6. 文档更新

- **`AGENTS.md`**：
  - Quick Reference 区增加 `logback-spring.xml`（local profile 启用文件日志）
  - Environment 表格新增日志目录行
  - 新增 "Logging" 小节：LLM 调用日志（`llm_call_logs` 表）、文件日志（仅 local profile）
- **`CONTEXT.md`**：
  - 新增术语 **LLM Call Log**：每次 LLM API 调用的持久化记录，包含 prompt、response、token 用量和耗时，存储在 `llm_call_logs` 表中，用于调试和成本追踪
- **`docs/architecture.md`**：
  - 决策日志新增第 37 项："LLM 调用日志 + 文件日志"
  - Section 六（数据模型）ER 图中新增 `llm_call_logs` 表
  - Section 十（项目结构）新增 5 个新文件
  - Section 八（会话生命周期）补充日志层说明
- **`README.md`**：
  - Tables 列表增加 `llm_call_logs`
  - Testing 节下方新增 "Logging" 小节：文件日志路径 + LLM 调用日志表
  - Project Structure 树新增 5 个新文件

### 7. 变更文件清单

| 操作 | 文件 | 说明 |
|------|------|------|
| 新建 | `model/LlmCallLog.java` | JPA 实体，继承 BaseEntity |
| 新建 | `repository/LlmCallLogRepository.java` | JPA Repository |
| 新建 | `service/LlmCallLogService.java` | `@Async save()` + `@PostConstruct cleanup()` |
| 新建 | `config/LoggableChatModel.java` | ChatLanguageModel 包装器，拦截 chat(String) |
| 新建 | `src/main/resources/logback-spring.xml` | 文件日志配置（local profile 激活） |
| 新建 | 单元测试 × 3 | LoggableChatModelTest、LlmCallLogServiceTest、LlmCallLogRepositoryTest |
| 修改 | `config/LangChain4jConfig.java` | chatLanguageModel() bean 包装为 LoggableChatModel |
| 修改 | `config/AsyncConfig.java` | 新增 llmLogExecutor 线程池 |
| 修改 | `agent/ConversationAgent.java` | 新增 buildPromptJson() 方法 |
| 修改 | `service/TurnProcessor.java` | ConversationAgent 日志：计时 + prompt 捕获 + onComplete 写 DB |
| 修改 | `agent/ReportAgent.java` | log.info → log.debug |
| 修改 | `agent/MemoryAgent.java` | log.info → log.debug |
| 修改 | `AGENTS.md` | 新增 Logging 小节 |
| 修改 | `CONTEXT.md` | 新增 LLM Call Log 术语 |
| 修改 | `docs/architecture.md` | 决策 #37 + ER 图 + 项目结构 |
| 修改 | `README.md` | 新增 Logging 小节 + 更新结构树 |

## Testing Decisions

### 测试原则

- 只测试外部可观测行为
- 包装器测试：LLM 调用被记录到日志表，duration 准确
- 文件日志不写单元测试（logback 配置通过人工验证）

### 新建测试

1. **LoggableChatModelTest**：成功调用 → DB 有 SUCCESS 记录、prompt/response 完整、duration > 0；失败调用 → DB 有 ERROR 记录、errorMessage 非空
2. **LlmCallLogServiceTest**：saveAsync 写入可查；cleanup 删 3 天前记录，保留近期记录
3. **LlmCallLogRepositoryTest**：基础 save/findById，按 createTime 范围删除

### 参考已有测试模式

- Service 测试参考 `MemoryServiceTest.java`
- Repository 测试参考 `UserMemoryRepositoryTest.java`

## Out of Scope

- 生产环境文件日志
- LLM 日志查询 UI
- LLM 响应内容质量分析
- MemoryCue 生成失败重试
- 数据库迁移工具（继续用 `ddl-auto=update`）
- E2E 测试（E2E 用 WireMock 不调真实 LLM）

## Further Notes

- `LoggableChatModel` 仅包装 `ChatLanguageModel`（同步），不包装 `StreamingChatLanguageModel`
- `requestPrompt` 使用 JSON 格式，为未来迁移 PostgreSQL `jsonb` 预留兼容
- 清理在每次启动时触发一次。每天约 200 条记录，不需要 @Scheduled 定时任务
- 同步 Agent 的 `chat(String)` 返回 String 不含 TokenUsage，因此同步调用的 `inputTokens`/`outputTokens` 为 null
- 现有 LangChain4j `log-requests: true` 保持不变
