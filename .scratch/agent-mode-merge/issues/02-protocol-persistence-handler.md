# 02: 协议层 + 持久化层 + Handler 统一

**Status:** `ready-for-agent`

## Parent

PRD: `.scratch/agent-mode-merge/PRD.md`

## What to build

将 scenario+persona 两个独立维度在协议、持久化、状态管理、Handler 层面统一为单一的 `mode` 字段。`CoachState` 增加 `MODE` 频道（`Channels.base(() -> "")`），`Session` 实体新增 `@Enumerated(EnumType.STRING) private AgentMode mode` 字段。`ClientMessage.StartSession` 增加 `mode` 字段（String 类型，协议层不解耦），`ServerMessage.SessionStarted`/`SessionResumed`/`SessionSummary` 的 `scenario`+`persona` 字段统一改为 `mode`。`CoachMessageHandler` 在 `onStartSession()` 通过 `AgentMode.valueOf()` 校验 mode，默认值 `WORKPLACE_STANDUP`；`onResumeSession()` 调用 `sessionService.getMode()` 替代旧的 getScenario()+getPersona()；`onLoadHistory()` 中 `SessionSummary` 使用 `s.getMode().name()`。`SessionService` 新增 `getMode()`、删除 `getScenario()` 和 `getPersona()`，`init()` 参数从 `(sessionId, scenario, persona, userId, wsId)` 改为 `(sessionId, mode, userId, wsId)`。`SessionStore.createSession()` 签名从 `(ScenarioType, String, String)` 改为 `(AgentMode, String)`。`TurnProcessor` 直接从 `SessionService.getMode()` 取 AgentMode，不再内部做 scenario+persona 映射。

## Acceptance criteria

- [ ] `CoachState` 有 `MODE` 频道（`Channels.base(() -> "")`），`initialState()` 参数从 `(sessionId, scenario, persona, ...)` 改为 `(sessionId, mode, ...)`
- [ ] `CoachState.SCENARIO` 和 `PERSONA` 频道**仍保留**（本 slice 不断后兼容，Slice 4 再删除）
- [ ] `Session` 实体有 `@Enumerated(EnumType.STRING) private AgentMode mode` 字段，旧的 `scenario`/`persona` 字段**仍保留**
- [ ] `ClientMessage.StartSession` 有 `String mode` 字段，旧的 `scenario`/`persona` **仍保留**
- [ ] `ServerMessage.SessionStarted(sessionId, mode)`、`SessionResumed(sessionId, mode, ...)`、`SessionSummary(id, mode, ...)` — 字段名为 `mode`
- [ ] `CoachMessageHandler.onStartSession()` 从 `msg.mode()` 读取，进行 `AgentMode.valueOf()` 校验，无效值返回 ERROR 消息（如 `"Invalid mode: xxx. Available: [WORKPLACE_STANDUP]"`）
- [ ] `CoachMessageHandler.onResumeSession()` 调用 `sessionService.getMode()` 获取 mode
- [ ] `CoachMessageHandler.onLoadHistory()` 中 SessionSummary 构造使用 `mode` 字段
- [ ] `SessionService.getMode()` 存在且工作正常，`getScenario()`/`getPersona()` **暂保留**
- [ ] `SessionStore.createSession()` 接受 `AgentMode` 参数
- [ ] `TurnProcessor.processTurn()` 调用 `getMode()` 直接获取 AgentMode，不再做映射
- [ ] `mvn test -Dtest=CoachMessageHandlerTest,SessionStoreTest,SessionAuditingTest,ProtocolDispatcherTest` 全部通过
- [ ] `mvn compile` 通过

## Blocked by

- `01-agentmode-templates` — 需要 AgentMode 枚举和 ConversationAgent 新签名
