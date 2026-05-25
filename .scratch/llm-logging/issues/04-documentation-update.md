# 04: 文档更新 — LLM 日志 + 文件日志

**Status:** `ready-for-agent`

## Parent

`.scratch/llm-logging/PRD.md` — LLM 调用日志 + 文件日志

## What to build

为 LLM 调用日志和文件日志两项新功能更新项目文档（4 个文件），具体变更：

**AGENTS.md**：
- Quick Reference 表格中新增 `logback-spring.xml` 行：文件日志（local profile 激活，`./logs/` 目录）
- Environment 表格新增一行：| `LOG_DIR` | `./logs/` | 文件日志输出目录（仅 local profile）|
- Key Facts 下方新增 **"Logging"** 小节，包含：
  - **LLM 调用日志**：`llm_call_logs` 表记录每次 LLM API 调用（prompt、response、token 用量、耗时），通过 `LlmCallLogService` 异步写入，启动时自动清理 3 天前记录
  - **文件日志**：仅 `local` profile 激活 `logback-spring.xml` 的 file appender，控制台 INFO、文件 DEBUG，按天滚动保留 3 天
  - **日志级别**：`ReportAgent` 和 `MemoryAgent` 的 prompt/response 打印降级为 DEBUG 级别

**CONTEXT.md**：
- 术语表中新增 **LLM Call Log**：每次 LLM API 调用的持久化记录，包含 prompt、response、token 用量和耗时，存储在 `llm_call_logs` 表中，用于调试和成本追踪

**docs/architecture.md**：
- 决策日志新增第 37 项：**"LLM 调用日志 + 文件日志"** —— 内容："新建 `llm_call_logs` 表持久化每次 LLM 调用的完整上下文（prompt/response/tokens/duration）。同步 Agent 通过 `LoggableChatModel` 包装器透明拦截，ConversationAgent 通过 `TurnProcessor` 手动注入。写入异步执行不阻塞业务。启动时自动清理 3 天前记录。新增 `logback-spring.xml`，仅 local profile 启用文件日志（DEBUG 级别，按天滚动）。"
- Section 六（数据模型）ER 图中新增 `llm_call_logs` 表，列出核心字段：`id`、`session_id`、`agent_type`、`model`、`request_prompt`、`response_text`、`input_tokens`、`output_tokens`、`duration_ms`、`status`
- Section 十（项目结构）新增以下文件到结构树：
  - `model/LlmCallLog.java`
  - `repository/LlmCallLogRepository.java`
  - `service/LlmCallLogService.java`
  - `config/LoggableChatModel.java`
  - `src/main/resources/logback-spring.xml`
- Section 八（会话生命周期）补充说明：LLM 调用日志在每次 API 调用时异步写入，与会话生命周期并行

**README.md**：
- Tables 列表中增加 `llm_call_logs` — "LLM API 调用记录（prompt, response, tokens, duration）"
- Testing 节下方新增 **"Logging"** 小节：
  - **文件日志**：`mvn spring-boot:run -Dspring-boot.run.profiles=local` → `./logs/english-coach.YYYY-MM-DD.log`（DEBUG 级别，保留 3 天，控制台保持 INFO）
  - **LLM 调用日志**：H2 控制台 `http://localhost:8080/h2-console` → `SELECT * FROM llm_call_logs ORDER BY create_time DESC`
- Project Structure 树中新增与架构文档一致的 5 个新文件

## Acceptance criteria

- [ ] `AGENTS.md` Quick Reference 区有 logback 说明、Environment 表格有效果说明、Key Facts 下有 "Logging" 小节
- [ ] `CONTEXT.md` 术语表中 "LLM Call Log" 术语已添加，定义准确
- [ ] `docs/architecture.md` 决策日志有 #37、ER 图有 `llm_call_logs`、项目结构有 5 个新文件、会话生命周期有日志层说明
- [ ] `README.md` Tables 列表有 `llm_call_logs`、有 "Logging" 小节（含文件路径和 SQL 查询示例）、结构树有 5 个新文件
- [ ] 所有文档中的信息与代码实现一致（表名、字段名、路径无误）

## Blocked by

None — 可立即开始（但建议在所有代码实现完成后执行，以确保文档与实际代码一致）
