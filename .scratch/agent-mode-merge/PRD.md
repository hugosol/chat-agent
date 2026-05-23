# PRD: 合并 Scenario 和 Persona 为统一的 AgentMode

**Status:** `ready-for-agent`

## Problem Statement

当前用户启动一个英语练习会话时，需要从两个独立的下拉框中选择**场景（Scenario）**和**角色（Persona）**，共计 2×2=4 种理论组合。实际上两个下拉框的自然组合固定（站会+同事、1-on-1+经理），交叉组合（站会+经理、1-on-1+同事）没有实际使用场景，反而增加了用户的心智负担和代码复杂度。同时，两个独立概念在提示词模板、会话状态、协议消息、数据持久化等层面都是分开管理的，导致以下问题：

1. **前端**：两个下拉框没有联动逻辑，用户可能选出不合理组合
2. **提示词模板**：3 个占位符（`{persona_description}`、`{persona_role}`、`{scenario}`）散布在单一模板中，难以按 Mode 定制内容
3. **代码层**：两个枚举类、两个 CoachState 频道、两个参数在调用链中传递，维护成本高
4. **扩展性**：将来增加新的对话模式时，需要在两个维度都做变更，容易遗漏

## Solution

将 `ScenarioType` 和 `PersonaType` 两个独立枚举合并为单一的 `AgentMode` 枚举。每个 `AgentMode` 代表一个完整的对话语境（角色 + 场景），前端只需一个下拉框选择。提示词模板拆分为 per-Mode 的 `description.txt` 和 `rules.txt` 文件，通过 `conversation-system.txt` 骨架模板组装，使得每个 Mode 可以独立定制其系统提示词。

## User Stories

1. 作为一名英语学习者，我想要从一个下拉框中选择练习模式，而不是在两个下拉框中分别选择场景和角色，以减少选择成本和避免不合理的组合。
2. 作为一名英语学习者，我启动会话后看到的对话行为应该与我选择的单一模式（如"站会"）完全匹配，无需担心场景和角色不协调。
3. 作为一名英语学习者，我在刷新页面后恢复会话时，UI 上应该正确显示我选择的模式，不丢失上下文。
4. 作为一名英语学习者，我在查看历史会话列表时，应该能看到每个会话对应的模式名称，便于回顾。
5. 作为一名开发者，我希望每个对话模式都有自己独立的 Description 和 Rules 模板文件，这样新增模式时只需添加文件夹和文件，无需修改核心 Agent 代码。
6. 作为一名开发者，我希望通过调整包装模板中 `{Description}` 和 `{Rules}` 的顺序来优化模型对不同信息的注意力分配，无需改代码。
7. 作为一名开发者，我希望 AgentMode 枚举的结构足够简单，只携带 displayName 和 templatePath 两个元数据字段，实际提示词内容完全由模板文件定义。
8. 作为一名开发者，我希望整个系统的类型安全不退化——Session 实体的 Mode 字段应使用枚举类型而非裸字符串。
9. 作为一个系统，当 WebSocket 客户端发送无效的 Mode 值时，应返回友好的错误消息而不是 Jackson 反序列化异常。
10. 作为一名测试工程师，现有的 E2E 测试和单元测试在重构后仍然能通过，覆盖模式启动、恢复、历史记录等核心流程。

## Implementation Decisions

### 1. 新枚举 `AgentMode` 的设计

- 枚举值暂仅一个：`WORKPLACE_STANDUP`（站会场景 + 同事角色），后续按需扩展
- 枚举字段仅两个：
  - `displayName`：前端下拉框显示文本（如 "Standup Meeting"）
  - `templatePath`：指向 per-Mode 模板文件夹路径（如 "workplace_standup"），用于 `PromptLoader` 加载对应模板文件
- 枚举本身不持有任何提示词内容，所有提示词内容存放在模板文件中

### 2. 提示词模板重构

- 新建 per-Mode 模板目录：`src/main/resources/prompts/{templatePath}/`
  - `description.txt`：包含身份声明 + 场景描述（如 "You are a friendly teammate at a software company in a daily standup meeting..."）
  - `rules.txt`：行为约束规则（如回复长度、纠错方式、语气风格等）
- 现有 `conversation-system.txt` 退化为纯骨架模板，只保留两个占位符加动态区块：
  ```
  {Description}

  {Rules}

  {topicSummary}
  {learningProfile}
  {activeEngagement}
  ```
- 静态的 `ACTIVE_ENGAGEMENT_TEXT` 常量保留在代码中，不属于任何 Mode 特定内容

### 3. 模板加载策略

- `ConversationAgent` 构造时遍历所有 `AgentMode.values()`，通过 `PromptLoader.load(templatePath + "/description.txt")` 和 `"rules.txt"` 预加载到 `EnumMap<AgentMode, String>` 中
- 请求时 O(1) 查 Map 取值，不做文件 IO
- `conversation-system.txt` 骨架模板仍由构造时一次性加载
- 模板加载和替换逻辑完全限定在 `ConversationAgent` 内部，外部调用者只传 `AgentMode`

### 4. 数据库变更

- `Session` 实体：
  - 新增 `@Enumerated(EnumType.STRING) private AgentMode mode` 字段，替换原有的 `ScenarioType scenario` + `String persona`
  - 列类型仍为 `VARCHAR`，不改变数据库结构，仅列名和语义变更
- 不编写迁移脚本，接受已有 H2 数据丢失（当前为开发环境，数据通过 `sessionStore.completeSession()` 写入）

### 5. CoachState 频道变更

- 删除 `SCENARIO` 和 `PERSONA` 两个频道
- 增加 `MODE` 频道：`Channels.base(() -> "")`，存储 AgentMode 枚举名（字符串）
- `SessionService` 删除 `getScenario()` 和 `getPersona()` 方法，新增 `getMode()` 方法

### 6. WebSocket 协议变更

客户端消息 `START_SESSION`：
```json
{ "type": "START_SESSION", "mode": "WORKPLACE_STANDUP" }
```
（原 `scenario` + `persona` 合并为 `mode`）

服务端消息字段同步简化：

| 消息类型 | 旧字段 | 新字段 |
|---|---|---|
| `SESSION_STARTED` | `scenario`, `persona` | `mode` |
| `SESSION_RESUMED` | `scenario`, `persona` | `mode` |
| `SessionSummary` | `scenario` | `mode` |

### 7. 协议层类型策略

- `ClientMessage.StartSession` 的 `mode` 字段使用 `String` 类型（非枚举）
- 校验工作由 `CoachMessageHandler` 通过 `AgentMode.valueOf()` 完成，返回友好的错误消息
- 保持协议层与领域模型的解耦

### 8. CoachMessageHandler 变更

- `onStartSession()`：从 `msg.mode()` 读取并进行 `AgentMode.valueOf()` 校验，默认值简化为 `WORKPLACE_STANDUP`
- `onResumeSession()`：调用 `sessionService.getMode()` 替代原先的 `getScenario()` + `getPersona()`
- `onLoadHistory()`：`SessionSummary` 构造使用 `s.getMode().name()`

### 9. TurnProcessor 和 ConversationAgent 调用链

- `TurnProcessor.processTurn()` 从 `SessionService` 只取 `mode` 一个值（而非 scenario + persona）
- `ConversationAgent` 方法签名为：
  - `generateStream(history, AgentMode mode, handler)`
  - `generateStreamFirstTurn(history, AgentMode mode, topicSummary, learningProfile, handler)`
  - `buildSystemContent()` 改为接收 `AgentMode mode`，从 pre-loaded Map 取 Description 和 Rules 替换占位符

### 10. 前端变更

- 合并两个 `<select>` 为一个，仅包含 `WORKPLACE_STANDUP` 选项
- `sendStart()` 发送 `{ type: "START_SESSION", mode: els.modeSelect.value }`
- `resetUI()` 中禁用逻辑同步简化

### 11. 旧代码清理

- 完整删除 `ScenarioType.java` 和 `PersonaType.java` 两个枚举类
- 删除所有对其的 import 引用

### 12. 影响范围：变更文件清单

| 操作 | 文件 |
|---|---|
| 新建 | `AgentMode.java`（枚举）、`description.txt`（模板）、`rules.txt`（模板） |
| 修改 | `ConversationAgent.java`、`Session.java`、`CoachState.java`、`SessionService.java`、`ClientMessage.java`、`ServerMessage.java`、`CoachMessageHandler.java`、`TurnProcessor.java`、`SessionStore.java`、`index.html`、`app.js`、`conversation-system.txt`、`MemoryAgent.java`（仅 import 路径）、`ReportAgent.java`（仅 import 路径） |
| 删除 | `ScenarioType.java`、`PersonaType.java` |
| 测试变更 | `ConversationAgentTest.java`、`CoachMessageHandlerTest.java`、`SessionStoreTest.java`、`SessionAuditingTest.java`、`EnglishCoachSessionIT.java`、`ProtocolDispatcherTest.java` |

## Testing Decisions

### 测试原则

- 只测试外部可观测行为，不测试内部实现细节（如 EnumMap 缓存、模板加载顺序）
- 利用已有测试框架：JUnit 5 + AssertJ + Mockito（单元测试）、Playwright + WireMock（E2E 测试）

### 需要更新的测试

1. **`ConversationAgentTest`**：断言从 3 个占位符替换改为 2 个；`PersonaType`/`ScenarioType` 参数改为 `AgentMode`
2. **`CoachMessageHandlerTest`**：`START_SESSION`/`SESSION_STARTED` 消息断言中的 `scenario`+`persona` 改为 `mode`
3. **`SessionStoreTest`**：`createSession()` 调用参数从 `(ScenarioType, String, String)` 改为 `(AgentMode, String)`
4. **`SessionAuditingTest`**：Session 构造参数变更
5. **`EnglishCoachSessionIT`**：`session.getScenario()` 断言改为 `session.getMode()`；E2E 测试中的模式下拉选择步骤简化
6. **`ProtocolDispatcherTest`**：消息 JSON 中的字段变更

### 不需要新增的测试

- `AgentMode` 枚举本身无需单元测试（纯数据类）
- 模板文件加载不需要独立测试（由 Agent 单元测试间覆盖）

## Out of Scope

- 新增第二个 `AgentMode` 值（如 1-on-1 + Manager）——等本 PRD 实施完成后再扩展
- 前端历史记录页面的实现（`LOAD_HISTORY` / `SESSION_HISTORY` 在前端尚未接入，本次仅保持协议兼容）
- `CorrectionAgent`、`ReportAgent`、`MemoryAgent` 的模式感知（这些 Agent 目前与 Mode 无关）
- 模板热加载或动态刷新能力
- 数据库迁移工具或 flyway/liquibase 集成

## Further Notes

- 当前 `CorrectionAgent`、`ReportAgent`、`MemoryAgent` 不引用 ScenarioType 或 PersonaType，它们在此次重构中几乎不受影响
- `SessionSummary` 的 `scenario` 字段改为 `mode` 后，前端暂未使用此字段，无破坏性影响
- 模板文件的 UTF-8 编码和换行符格式需保持一致（CRLF for Windows），不影响 `PromptLoader` 的 `StandardCharsets.UTF_8` 读取
- `CoachMessageHandler` 中 `onStartSession()` 的默认 Mode 从 `WORKPLACE_STANDUP` + `TEAM_COLLEAGUE` 简化为仅 `WORKPLACE_STANDUP`
