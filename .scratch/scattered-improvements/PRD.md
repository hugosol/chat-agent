# PRD: 零星优化 — 上下文、词汇建议、日志格式

**Status:** `ready-for-agent`

## Problem Statement

当前系统存在四个独立的体验/维护问题：

1. **消息截断限制过严**：ConversationAgent 每次只发送最近 20 条消息给 LLM。虽然 128k token 上下文窗口远未触达（即使 200 轮会话也仅 ~18k input tokens），但 20 条的硬截断会导致长会话中 LLM"遗忘"早期对话内容，影响会话连贯性。
2. **词汇建议与纠错功能重叠**：ReportAgent 生成的 `vocabularySuggestions` 字段（"3-5 better words or phrases"）与 CorrectionAgent 的 `WORD_CHOICE` 类型纠错高度重复——后者已在每轮对话中逐个修正词汇问题并汇总到 `errorSummary`。该字段同时渗透到 MemoryAgent（Learning Profile 生成）、前端报告弹窗、数据库持久化等多个层级，增加了不必要的复杂度。
3. **日志时间单位过大**："Conversation close latency" 和 "MemoryCue + consolidation" 两条关键性能日志使用毫秒输出（如 `12345ms`），肉眼不易判断是 12 秒还是 120 秒，可读性差。
4. **文档过时**：移除 vocabularySuggestions 后，`CONTEXT.md`、`docs/architecture.md`、`README.md`、`docs/prd-persistent-memory.md` 中的相关描述将过时，需要同步更新以保持文档与代码一致。

## Solution

四个独立改动，互不依赖，可并行实施：

1. **移除 20 条历史消息截断**：ConversationAgent 发送全部历史消息给 LLM。保留已有的 `TokenTracker`（80% / 128k 警告）作为唯一上下文溢出防护。
2. **完整移除 vocabularySuggestions**：从 LLM prompt → 解析 → WebSocket 协议 → 前端 → MemoryAgent → 数据库整条链路删除该字段，不留任何残留引用。
3. **延迟日志改为秒**：`CoachMessageHandler.onEndSession()` 和 `MemoryCueService.generateCuesAsync()` 中的耗时日志从 `{}ms` 改为 `{}s`，保留一位小数。
4. **同步更新文档**：删除上述文档中所有 vocabularySuggestions 相关描述，更新 Report 字段列表、ER 图、前端布局描述。

## User Stories

1. 作为学习者，在超过 10 轮的练习会话中，我希望英语教练记得我在会话最初说过的内容（如我提到的项目名、同事名），使对话更自然连贯。
2. 作为学习者，在查看会话报告时，我不希望看到重复的词汇建议——每轮纠错侧边栏已经有 "WORD_CHOICE" 类型的修正，报告中的词汇建议与这些修正内容几乎完全重叠。
3. 作为开发者，在查看日志时，我希望 "Conversation close latency" 显示为 `12.3s` 而不是 `12345ms`，一眼就能判断延迟量级。
4. 作为开发者，在查看日志时，我希望 "Background task duration (MemoryCue + consolidation)" 也以秒为单位，与其他耗时日志格式保持一致。
5. 作为开发者，在维护 ConversationAgent 时，我不需要关心"20 条截断"这个魔法数字的合理性——实际 token 消耗远低于上下文窗口上限，硬截断是过度保守的早期设计。
6. 作为开发者，在阅读 CONTEXT.md 和 architecture.md 时，我希望文档描述与实际代码一致，不会看到已被移除的 vocabularySuggestions 功能。

## Implementation Decisions

### 1. 移除消息截断（仅靠 TokenTracker）

- ConversationAgent 的 `buildMessages()` 中 `history.size() - 20` 截断逻辑移除，改为遍历全部 `history`
- 不引入新的硬限制（如 50、100 条），不引入按 token 数动态截断
- 保留 `TokenTracker` 的 80% 警告机制：每轮 `processTurn()` 后通过 `isTokenWarning()` 检查，超过 80%（102,400 / 128,000 tokens）时向 Learner 发送 `TOKEN_WARNING` 消息
- 估算：200 轮工作场景会话约 18k input tokens，远低于 128k 上限；费用约 0.01 USD/token per M，200 轮累计约 1.8M input tokens ≈ $0.49

### 2. 完整移除 vocabularySuggestions

移除整条链路，不保留兼容层：

| 层级 | 变更 |
|------|------|
| LLM Prompt | `report.txt` 删除 `"vocabularySuggestions"` 行；`memory-profile.txt` 删除 `Vocabulary Suggestions` 行和 `{vocabularySuggestions}` 占位符 |
| Agent 解析 | `ReportAgent.ReportResult` record 移除 `vocabularySuggestions` 字段；`parseReport()` 不再提取该键 |
| WebSocket 协议 | `ServerMessage.ReportData` record 移除字段；`CoachMessageHandler.onEndSession()` 构造 ReportData 去掉对应参数 |
| Memory | `MemoryAgent.mergeProfile()` 签名去掉第三个参数 `vocabularySuggestions`；`MemoryService` 调用处去掉该实参 |
| 持久化 | `SessionReport` 实体移除 `vocabularySuggestions` 字段、getter、setter、`@Column` 注解；`EntityMapper.buildReport()` 移除 `setVocabularySuggestions()` 调用 |
| 前端 | `app.js` 的 `showReport()` 函数移除 Vocabulary Suggestions 那一行 DOM 构造 |
| 测试 | `ReportAgentTest` 移除相关断言；`MemoryAgentTest.mergeProfile` 调用去掉第三个参数；`MemoryServiceTest` 所有 `mergeProfile` mock 调用去掉第三个参数；E2E WireMock stub 文件 `report.json` / `daily-report.json` 移除该字段 |

**数据库迁移**：由于 `spring.jpa.hibernate.ddl-auto: update` 只加不删，移除实体字段后 H2 表中的 `vocabulary_suggestions` 列保留为孤儿列——不读写、不报错。不做 `ALTER TABLE DROP COLUMN`，旧列无害留存。

**冗余性分析**：`vocabularySuggestions` 与 `CorrectionAgent.WORD_CHOICE` 类型纠错功能重叠——后者已在每轮逐条修正词汇问题并在 `errorSummary` 中按类型汇总。移除 vocabularySuggestions 不丢失任何信息。

### 3. 延迟日志格式

- `CoachMessageHandler.onEndSession()`: `log.info("Conversation close latency: {}s", String.format("%.1f", elapsed / 1000.0))`
- `MemoryCueService.generateCuesAsync()`: `log.info("Background task duration (MemoryCue + consolidation): {}s", String.format("%.1f", elapsed / 1000.0))`
- 使用 `String.format("%.1f", ...)` 控制一位小数

### 4. 文档同步更新

代码改造完成后，同步更新以下文档中所有 `vocabularySuggestions` 相关描述：

| 文件 | 变更 |
|------|------|
| `CONTEXT.md` | Report 术语定义：删除 "vocabulary suggestions" 字段描述 |
| `docs/architecture.md` | (a) Section 五 ReportAgent prompt 描述：删除 "3. Vocabulary Suggestions" 项，后续编号 4→3, 5→4；(b) Section 六 ER 图：SessionReport 移除 `vocabulary` 列；(c) Section 九 前端布局：删除 "Vocabulary Suggestions" 行 |
| `README.md` | Agent 表格 ReportAgent 行：删除 "vocabulary suggestions" 输出描述 |
| `docs/prd-persistent-memory.md` | (a) Memory 输入表：删除 Learning Profile 行中的 `vocabularySuggestions`；(b) MemoryAgent 接口描述：`mergeProfile` 签名去掉第三个参数；(c) memory-profile.txt 模板描述：删除 "and vocabulary suggestions" |

### 5. 变更文件清单

**改动 1 — 消息截断（2 文件）**

| 操作 | 文件 | 说明 |
|------|------|------|
| 修改 | `agent/ConversationAgent.java` | `buildMessages()` 移除 `history.size() - 20` 截断 |
| 修改 | `agent/ConversationAgentTest.java` | 删除 `historyTruncatedToLast20()` 测试方法 |

**改动 2 — vocabularySuggestions（15 文件）**

| 操作 | 文件 | 说明 |
|------|------|------|
| 修改 | `prompts/report.txt` | 删除 vocabularySuggestions 行 |
| 修改 | `prompts/memory-profile.txt` | 删除 Vocabulary Suggestions 行和占位符 |
| 修改 | `agent/ReportAgent.java` | ReportResult record 移除字段；parseReport 移除提取 |
| 修改 | `agent/MemoryAgent.java` | mergeProfile() 签名去掉第三个参数 |
| 修改 | `protocol/ServerMessage.java` | ReportData record 移除字段 |
| 修改 | `websocket/CoachMessageHandler.java` | 构造 ReportData 去掉对应参数 |
| 修改 | `service/MemoryService.java` | mergeProfile() 调用去掉第三个实参 |
| 修改 | `service/EntityMapper.java` | buildReport() 移除 setVocabularySuggestions() |
| 修改 | `model/SessionReport.java` | 移除字段、getter、setter、@Column |
| 修改 | `static/app.js` | showReport() 移除 Vocabulary Suggestions DOM |
| 修改 | `agent/ReportAgentTest.java` | 移除 vocabularySuggestions 断言 |
| 修改 | `agent/MemoryAgentTest.java` | mergeProfile 调用去掉第三个参数 |
| 修改 | `service/MemoryServiceTest.java` | 所有 mergeProfile mock 调用去掉第三个参数 |
| 修改 | `test/resources/wiremock/report.json` | 删除字段 |
| 修改 | `test/resources/wiremock/daily-report.json` | 删除字段 |

**改动 3 — 日志格式（2 文件）**

| 操作 | 文件 | 说明 |
|------|------|------|
| 修改 | `websocket/CoachMessageHandler.java` | onEndSession 耗时改为秒 |
| 修改 | `service/MemoryCueService.java` | generateCuesAsync 耗时改为秒 |

**改动 4 — 文档更新（4 文件）**

| 操作 | 文件 | 说明 |
|------|------|------|
| 修改 | `CONTEXT.md` | Report 术语定义删除 vocabulary suggestions |
| 修改 | `docs/architecture.md` | 删除 ReportAgent prompt、ER 图、前端布局中的 vocabularySuggestions 引用 |
| 修改 | `README.md` | ReportAgent 描述删除 vocabulary suggestions |
| 修改 | `docs/prd-persistent-memory.md` | 删除 MemoryAgent mergeProfile 签名和模板中的 vocabularySuggestions 引用 |

## Testing Decisions

### 测试原则

- 改动 1 和 2 涉及逻辑删除，重点是确保删除后现有测试仍通过（不出现编译错误、Mock 参数不匹配、断言字段缺失）
- 改动 3 不引入测试（日志格式变更通过人工 review 验证）

### 测试修改

| 测试文件 | 变更 |
|----------|------|
| `ConversationAgentTest.java` | 删除 `historyTruncatedToLast20()`（该测试验证的是"超出 20 条时截断"，截断逻辑被移除后该测试失效） |
| `ReportAgentTest.java` | 移除 mock JSON 中的 `vocabularySuggestions` 字段；移除 `parseReport()` 中的 `vocabularySuggestions()` 断言和缺失键回退空字符串断言 |
| `MemoryAgentTest.java` | `mergeProfile_returnsTrimmedResponse`, `mergeProfile_includesAllInputsInPrompt`, `mergeProfile_handlesEmptyOldProfile` 三个测试：调用合并 `mergeProfile(oldProfile, errorSummary)`（去掉第三个参数） |
| `MemoryServiceTest.java` | Mock 验证：`mergeProfile(eq(...), eq(...))`（去掉 `eq("vocab")` 和 `anyString()` 第三个参数） |

### 不新增测试

- 移除截断逻辑不新增"全部发送"测试——现有 `buildMessagesWithCorrectStructure` 测试已覆盖正常消息构建路径（在 20 条以内场景下验证，移除截断后这些测试仍然有效）
- vocabularySuggestions 移除后，`ReportAgentTest` 的 JSON 解析测试覆盖缺失键回退逻辑，移除字段后该测试直接删除
- E2E 测试不涉及 vocabularySuggestions 的 DOM 断言（已确认 E2E Java 测试中无 `vocabularySuggestions` 引用）

## Out of Scope

- 按 token 数动态截断历史消息
- vocabularySuggestions 的数据库列清理（孤儿列无害，不做 `ALTER TABLE`）
- 其他 Agent（CorrectionAgent、MemoryCueAgent）的日志格式调整
- E2E 测试重录（WireMock stub 仅删除冗余字段，不影响场景状态机）
- 前端 report modal 的样式调整（仅删除一行内容，布局自适应）
- TokenTracker 阈值调整

## Further Notes

- 四个改动互不依赖，可独立实施和 review
- 改动 1 是行为变更（LLM 收到更多上下文），但不影响 TokenTracker 的警告机制
- 改动 2 不影响会话报告的其他字段（overallAssessment、topicSummary、errorSummary、fluencyScore、keyTakeaway 保持不变）
- 改动 4（文档更新）应在代码改造完成后执行，确保文档描述与实际行为一致
- `ConversationAgent.buildPromptJson()`（用于 LLM 调用日志）与 `buildMessages()` 共用同一截断逻辑，移除后日志记录的 prompt 也将包含全部历史
