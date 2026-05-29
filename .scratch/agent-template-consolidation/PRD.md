# PRD: 同步 Agent 模板模式合并

**Status:** `ready-for-agent`

## Problem Statement

当前 English Coach 的 4 个同步 Agent（CorrectionAgent、ReportAgent、MemoryAgent、MemoryCueAgent）存在严重的模板代码重复：

1. **结构完全相同的 4 个类**：每个 Agent 都执行「加载模板 → 替换占位符 →调用 LLM → 解析响应 → 错误处理」的相同骨架。CorrectionAgent（66 行）、ReportAgent（108 行）、MemoryAgent（33 行）、MemoryCueAgent（93 行）合计约 300 行，其中约 200 行是重复的模板调用逻辑。

2. **JSON 解析策略不一致**：CorrectionAgent 用 bracket-snippting + Jackson TypeReference、ReportAgent 用直接 Jackson Map + 字段提取器、MemoryAgent 无解析（纯 trim）、MemoryCueAgent 混用 regex + Jackson。四个类四种解析策略，没有统一的错误行为契约。

3. **LLM 调用日志缺少运行上下文**：当前 `LoggableChatModel` 包装器在模型层拦截，无法获取 sessionId、userId、agentType、mode 等运行时信息，导致日志表中这些字段全部为 null。ConversationAgent 通过手动注入补全了这些字段，但 4 个同步 Agent 的日志始终缺失上下文。

4. **MemoryAgent 职责退化**：原本承担 topicSummary 和 learningProfile 两项合并工作，但 topicSummary 合并已移除。现仅剩 learningProfile 合并，命名不再准确。

## Solution

抽取一个 `TaskRunner` 深模块，统一管理同步 Agent 的 LLM 调用生命周期。Agent 类保留——负责复杂业务逻辑（参数构建、JSON 解析、多任务编排），仅将「模板填充 + LLM 调用 + 日志记录 + 错误处理」四个横切关注点委托给 TaskRunner。

### 核心设计

```
TaskRunner
├── 注册表：Map<TaskName, TaskDefinition<?, ?>>
├── register(name, task) —— Agent 构造时注册任务
├── execute(name, params, ctx) —— 运行时按名调用
│   ├── paramBuilder(params) → 填充后的 prompt
│   ├── chatModel.chat(prompt) → 原始响应
│   ├── logService.saveAsync(ctx + 响应 + 元数据)
│   ├── parser(response) → 类型化结果
│   └── errorStrategy 处理异常
└── 依赖：ChatLanguageModel（原始，不再包装）+ LlmCallLogService
```

**Agent 改造后形状（以 CorrectionAgent 为例）：**

```java
@Component
public class CorrectionAgent {
    private final TaskRunner runner;

    public CorrectionAgent(TaskRunner runner, PromptLoader loader) {
        this.runner = runner;
        runner.register(TaskName.CORRECTION, TaskDefinition
                .<CorrectionParams, List<CorrectionData>>builder()
                .template(loader.load("correction.txt"))
                .paramBuilder(p -> Map.of("userInput", p.userInput()))
                .parser(this::parseJson)
                .errorStrategy(ErrorStrategy.SWALLOW)
                .build());
    }

    public List<CorrectionData> analyze(String userInput, TaskContext ctx) {
        return runner.execute(TaskName.CORRECTION,
                new CorrectionParams(userInput), ctx);
    }
}
```

Agent 保留复杂逻辑——`parseJson()` 方法内部使用 bracket-snippting + Jackson，但不会暴露到 TaskDefinition 接口上。

## User Stories

1. 作为一名开发者，当新增一个同步 Agent 时，我只需要定义一个 TaskDefinition（模板、参数映射、解析器、错误策略）并注册到 TaskRunner，不需要重复编写 `chatModel.chat(prompt)` 调用和日志代码。
2. 作为一名开发者，当排查某次 LLM 调用日志时，我能在 `llm_call_logs` 表中看到完整的 sessionId、userId、agentType、mode 字段（不再为 null），直接定位到具体会话、具体 Agent 的调用记录。
3. 作为一名开发者，当修正 Agent 返回了解析失败的 LLM 响应时，错误被统一捕获并记录 warn 日志，不会静默吞掉。
4. 作为一名开发者，当查看代码时，我能在 Agent 构造器中一眼看到该 Agent 注册了哪些 LLM 任务（任务名 + 模板 + 策略），无需追踪分散的方法调用。
5. 作为一名开发者，当修改某个模板文件的占位符名称时，我只需要修改对应 Agent 的 paramBuilder，不用触碰 TaskRunner 代码。
6. 作为一名开发者，当需要为一个 Agent 添加第二个 LLM 任务时，只需再注册一个 TaskDefinition，无需复制模板调用代码。
7. 作为一名 Learner，这些变更是纯内部的架构重构，对我的使用体验完全透明——对话仍然流畅、修正仍然准确、报告仍然正常生成。
8. 作为一名开发者，删除 `LoggableChatModel` 后，代码中不再存在一个仅服务于 4 个 Agent 的薄包装层，横切关注点收敛到 TaskRunner。

## Implementation Decisions

### 1. TaskRunner —— 执行引擎

- 注入原始 `ChatLanguageModel`（不再通过 `LoggableChatModel` 包装）和 `LlmCallLogService`
- 持有内部注册表：`Map<TaskName, TaskDefinition<?, ?>>`
- `execute(name, params, ctx)` 内部流程：取 TaskDefinition → 调用 paramBuilder 填充模板 → `chatModel.chat()` → 单次 LLM 调用 → `logService.saveAsync()` 写入完整日志 → 调用 parser → 按 errorStrategy 处理异常
- LLM 调用异常（网络/API 错误）与解析异常分别处理：LLM 异常走 errorStrategy（同时日志 status=ERROR），解析异常走 errorStrategy（LLM 成功但输出格式不符合预期）
- `agentType` 字段从 `TaskName` 枚举名自动映射
- `inputTokens`/`outputTokens` 保持 null（同步 `chat()` 不返回 TokenUsage）

### 2. TaskName —— 任务名枚举

统一管理 5 个同步 LLM 任务的身份标识：

```java
enum TaskName {
    CORRECTION,           // CorrectionAgent: 分析单条用户输入，返回 List<CorrectionData>
    REPORT,               // ReportAgent: 基于完整对话 + 修正列表生成 ReportResult
    MERGE_LEARNING,       // LearningAgent (原 MemoryAgent): 合并学习档案 String → String
    CHAT_SWITCHES,        // MemoryCueAgent: 检测话题切换点，返回 List<Integer>
    GENERATE_MEMORY_CUE   // MemoryCueAgent: 总结单段对话，返回 CueResult(topic, summary)
}
```

枚举作为注册表 key，拼写错误编译报错，IDE 重构一键到位。

### 3. TaskDefinition —— 任务描述对象

Builder 模式构建，包含以下不可变字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `template` | `String` | 完整的模板文本（已预填完静态占位符，如 `{profileMaxLength}`、`{cueTopicMaxWords}`） |
| `paramBuilder` | `Function<P, Map<String, String>>` | 运行时参数 → 占位符键值对。key 为模板中花括号内的名称（不含花括号），value 为动态填充值 |
| `parser` | `Function<String, R>` | LLM 原始响应字符串 → 类型化结果。内部可使用 Jackson、regex 等任何方式。此函数可抛异常，TaskRunner 按 errorStrategy 处理 |
| `errorStrategy` | `ErrorStrategy` | 解析失败的策略 |

模板中的静态占位符（`{profileMaxLength}`、`{cueTopicMaxWords}`、`{cueSummaryMaxSentences}`）在 Agent 构造时从 `AppProperties` 预填充——Agent 加载模板后先用配置值 replace，再将最终模板传入 TaskDefinition。TaskRunner 只负责运行时占位符填充。

### 4. ErrorStrategy —— 错误策略枚举

两个值：

- `SWALLOW`：捕获异常 → `log.warn()` 打印完整异常信息 + 返回体原文 → 返回 null。调用方需判空。
- `THROW`：原样传播异常给调用方。

所有 5 个任务均使用 `SWALLOW`。ReportAgent 的 parser 内部已自愈（永远返回合法 ReportResult），Runner 层的 SWALLOW 仅作为 LLM 网络异常的安全网。

各任务 SWALLOW 后的 fallback 语义：

| 任务 | SWALLOW 返回 | 调用方行为 |
|------|-------------|-----------|
| CORRECTION | null → 转为空列表 | 前端不显示修正卡片 |
| REPORT | parser 自愈，Runner 不触发 | 前端正常展示（部分字段可能为空） |
| MERGE_LEARNING | null | MemoryService 已有 try-catch 兜底 |
| CHAT_SWITCHES | null | 语义等价于"无话题切换"，功能不退化 |
| GENERATE_MEMORY_CUE | null | MemoryCueService 已有 per-segment try-catch → 写 SEGMENT_FAILED |

### 5. TaskContext —— 运行时上下文

```java
public record TaskContext(String sessionId, String userId, String mode) {}
```

- `sessionId`：MERGE_LEARNING 时可能为 null（MemoryService 调用时可选）
- `mode`：MERGE_LEARNING 时可能为 null（跨模式共享学习档案）
- 三个字段覆盖 `llm_call_logs` 表中所有运行时上下文字段

### 6. Agent 类保留与改造

每个 Agent 类公方法签名增加 `TaskContext` 参数。内部实现从直接调 `chatModel.chat()` 改为 `runner.execute(taskName, params, ctx)`。

**CorrectionAgent（66 行 → ~40 行）**：
- 构造时注册 `CORRECTION` 任务
- `parseJson()` 保留为私有方法（bracket-snippting + Jackson TypeReference）
- 新增 `CorrectionParams`（内部 record，仅含 userInput）

**ReportAgent（108 行 → ~75 行）**：
- 构造时注册 `REPORT` 任务
- `paramBuilder` 封装 `buildConversationText()` + `buildErrorsText()` 逻辑
- `parseReport()` 保留为私有方法（Map 提取 + getString/getInt）
- `ReportResult` record 保持不变，为 3 个外部调用方（SessionStore、MemoryService、EntityMapper）保持稳定接口
- 新增 `ReportParams`（内部 record，含 messages + corrections）

**MemoryAgent → LearningAgent（33 行 → ~25 行）**：
- 类重命名，更准确反映当前职责
- 构造时注册 `MERGE_LEARNING` 任务
- 参数映射逻辑（空值→placeholder）保留在 paramBuilder 中
- 所有引用处更新类名和导包

**MemoryCueAgent（93 行 → ~75 行）**：
- 构造时注册 `CHAT_SWITCHES` 和 `GENERATE_MEMORY_CUE` 两个任务
- `detectSwitches()` 和 `generateCue()` 方法保留，内部改为 `runner.execute()`
- 两个 parse 方法保留为私有方法
- 新增 `SwitchParams` 和 `CueParams` 两个内部 record
- `AppProperties` 依赖保留（用于构造函数预填静态占位符）

### 7. ConversationAgent 不纳入

ConversationAgent 使用 `StreamingChatLanguageModel`（流式回调），调用模式与同步 Agent 根本不同——无单一返回字符串、无单一解析步骤。它是项目中最深的一个 Agent，保持独立。

### 8. LoggableChatModel 删除

- 删除 `config/LoggableChatModel.java`（76 行）
- `LangChain4jConfig.chatLanguageModel()` 取消包装，直接返回 `OpenAiChatModel`
- 日志能力由 TaskRunner 内生提供，写入完整上下文字段

### 9. 变更文件清单

| 操作 | 文件 | 说明 |
|------|------|------|
| **新建** | `agent/TaskRunner.java` | 执行引擎：注册表 + execute + 日志 + 错误处理 |
| **新建** | `agent/TaskDefinition.java` | 任务描述对象，Builder 模式 |
| **新建** | `agent/TaskName.java` | 任务名枚举（5 个值） |
| **新建** | `agent/TaskContext.java` | 运行时上下文 record |
| **新建** | `agent/ErrorStrategy.java` | 错误策略枚举（SWALLOW / THROW） |
| **新建** | 单元测试 × 5+ | TaskRunnerTest、CorrectionAgentTest、ReportAgentTest、LearningAgentTest、MemoryCueAgentTest |
| **修改** | `agent/CorrectionAgent.java` | 内部迁移到 TaskRunner |
| **修改** | `agent/ReportAgent.java` | 内部迁移到 TaskRunner |
| **修改** | `agent/MemoryAgent.java` → `agent/LearningAgent.java` | 重命名 + 内部迁移 |
| **修改** | `agent/MemoryCueAgent.java` | 内部迁移到 TaskRunner |
| **删除** | `config/LoggableChatModel.java` | 死代码，被 TaskRunner 取代 |
| **修改** | `config/LangChain4jConfig.java` | 取消 LoggableChatModel 包装，直接返回原始模型 |
| **修改** | `graph/nodes/CorrectionNode.java` | 适配调整（如果需要传递 TaskContext） |
| **修改** | `websocket/CoachMessageHandler.java` | ReportAgent 调用增加 TaskContext 参数 |
| **修改** | `service/MemoryService.java` | LearningAgent 重命名引用 + TaskContext 参数 |
| **修改** | `service/MemoryCueService.java` | MemoryCueAgent 调用增加 TaskContext 参数 |
| **修改** | 所有现有测试文件 | 适配新的 Agent 签名和 Stub 模式 |

## Testing Decisions

### 测试原则

- 只测试 Agent 外部可观测行为：给定输入 + TaskContext → 期望输出
- TaskRunner 单独测试：模拟 ChatLanguageModel，验证日志被调用、异常被按策略处理、paramBuilder 模板填充正确
- 每个 Agent 测试保留现有测试用例，仅调整依赖注入方式（注入 StubRunner + TaskRunner 替代 StubChatModel）
- StubChatModel 从 4 个测试文件消除——TaskRunner 持有 ChatLanguageModel，测试时注入 StubModel

### 新建测试

1. **TaskRunnerTest**：
   - 成功执行：LLM 返回正常 → parser 成功 → 日志写入含完整 TaskContext
   - 解析失败 + SWALLOW：LLM 正常但 parser 抛异常 → warn 日志 → 返回 null
   - 解析失败 + THROW：parser 抛异常 → 异常冒泡
   - LLM 网络失败：chat() 抛异常 → 日志 status=ERROR → 按策略处理
   - 注册表：register 后 execute 正确路由、未注册任务抛 IllegalStateException

2. **CorrectionAgentTest**（6 个现有用例保留）：
   - nullInputReturnsEmptyList、blankInputReturnsEmptyList、validJsonArrayReturnsCorrections、jsonWrappedInSurroundingTextIsExtracted、noBracketsReturnsEmptyList、invalidJsonReturnsEmptyList
   - 注入方式改为注入 TaskRunner + StubModel

3. **ReportAgentTest**（7 个现有用例保留）：
   - 同上，注入方式改为注入 TaskRunner + StubModel

4. **LearningAgentTest**（3 个现有用例保留）：
   - mergeProfile_returnsTrimmedResponse、mergeProfile_includesAllInputsInPrompt、mergeProfile_handlesEmptyOldProfile

5. **MemoryCueAgentTest**（7 个现有用例保留）：
   - detectSwitches 4 个 + generateCue 2 个 + prompt 注入验证 1 个

### 参考已有测试模式

- Agent 测试参考现有 `CorrectionAgentTest.java`（StubChatModel 模式）
- Service 测试参考 `MemoryServiceTest.java`
- E2E 测试无需修改（WireMock 在 HTTP 层拦截，Agent 内部重构对 E2E 透明）

## Out of Scope

- ConversationAgent 流式调用纳入 TaskRunner（不同的接缝，使用 StreamingChatLanguageModel）
- TaskRunner 支持 LLM 调用重试
- 前端 debug 栏静默提示（TaskRunner SWALLOW 时后端 log.warn 已足够调试）
- langgraph4j 图形结构重构（CorrectionNode 保留，仅内部改用 TaskRunner-powered CorrectionAgent）
- Agent 参数对象的参数对象在 dto 包 vs agent 内部包的标准化（本次不作为强制规范）
- 数据库迁移（不变更表结构，仅增删代码）
- E2E 测试修改（现有 E2E 通过 WireMock mock HTTP 层，Agent 内部重构对 E2E 透明）

## Further Notes

- 迁移后 `StubChatModel` 内联类从 4 个测试文件消除——改为通过 TaskRunner 注入 StubModel，减少测试样板
- `LoggableChatModel` 删除后，无其他代码直接使用 `ChatLanguageModel`（仅 TaskRunner）
- TaskName 枚举新增值时，需要同步在受影响的 Agent 中注册对应 TaskDefinition；编译器和运行时注册检查会双重保证
- 各 Agent 的 `paramBuilder` 是纯函数（P → Map<String, String>），天然支持单元测试（不依赖 LLM）
- `CorrectionNode` 是唯一通过 langgraph4j 图节点间接调用 Agent 的场景——CorrectionNode 需要从 CoachState 获取 sessionId/userId/mode 构造 TaskContext

## Implementation Plan

7 个垂直切片的 issue 已发布到 `.scratch/agent-template-consolidation/issues/`，按依赖顺序排列：

| # | Issue | 类型 | 阻塞于 | 说明 |
|---|-------|------|--------|------|
| 01 | [TaskRunner 核心基础设施](issues/01-taskrunner-core.md) | AFK | — | 新建 5 个类 + TaskRunnerTest |
| 02 | [CorrectionAgent 迁移](issues/02-correction-agent-migration.md) | AFK | 01 | 迁移 + CorrectionNode 适配 |
| 03 | [ReportAgent 迁移](issues/03-report-agent-migration.md) | AFK | 01 | 迁移 + CoachMessageHandler 适配 |
| 04 | [MemoryAgent → LearningAgent](issues/04-memory-agent-to-learning-agent.md) | AFK | 01 | 重命名 + 迁移 + MemoryService 适配 |
| 05 | [MemoryCueAgent 迁移](issues/05-memory-cue-agent-migration.md) | AFK | 01 | 双任务注册 + MemoryCueService 适配 |
| 06 | [删除 LoggableChatModel](issues/06-remove-loggable-chat-model.md) | AFK | 02-05 | 删除包装层 + Bean 解包装 + 消除双日志 |
| 07 | [更新文档](issues/07-update-documentation.md) | AFK | 06 | 更新 architecture.md + AGENTS.md + 可选 ADR |

### 执行顺序

```
01 (TaskRunner 核心) ──┬── 02 (CorrectionAgent)
                       ├── 03 (ReportAgent)
                       ├── 04 (LearningAgent)
                       └── 05 (MemoryCueAgent)
                              │
                              ▼
                       06 (删除 LoggableChatModel)
                              │
                              ▼
                       07 (文档更新)
```

Slices 02-05 相互独立，可并行执行；但推荐按 02→03→04→05 顺序逐一完成（每个完成后运行 `mvn test` 验证）。

### 过渡期双日志说明

Slices 02-05 完成后、Slice 06 完成前，同步 Agent 的 LLM 调用会被双重记录：
- **TaskRunner**：写入含完整 `sessionId`/`userId`/`agentType`/`mode` 的日志（正确的记录）
- **LoggableChatModel**：写入 metadata 全 null 的日志（过渡期残留）

此双日志现象在 Slice 06 删除 `LoggableChatModel` 后自动消失。无功能退化。

### 文档更新计划（Slice 07）

| 文件 | 改动 |
|------|------|
| `docs/architecture.md` | 决策日志新增 #40（TaskRunner 模式）；#37 修正 LoggableChatModel→TaskRunner；§八日志写入行修正；§十 agent/ 目录更新（+TaskRunner 系列、MemoryAgent→LearningAgent）；§十 config/ 删除 LoggableChatModel |
| `AGENTS.md` | Package 列表更新 agent/（+TaskRunner 系列、MemoryAgent→LearningAgent）；"Two LLM beans" 段移除 LoggableChatModel；"LLM Call Log" 段修正 |
| `docs/adr/taskrunner-sync-agent-pattern.md`（可选） | 新建 ADR：设计动机、TaskName/TaskDefinition 模式、排除 ConversationAgent 原因、新增 Agent 指南 |
