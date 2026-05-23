# 01: AgentMode 领域模型 + 提示词模板系统

**Status:** `ready-for-agent`

## Parent

PRD: `.scratch/agent-mode-merge/PRD.md`

## What to build

创建 `AgentMode` 枚举（仅 `WORKPLACE_STANDUP`，携带 `displayName` 和 `templatePath` 两个元数据字段），新建 per-Mode 模板文件（`description.txt` + `rules.txt`）和重构后的 `conversation-system.txt` 骨架模板。重构 `ConversationAgent`：构造时遍历所有 `AgentMode.values()` 预加载模板到 `EnumMap<AgentMode, String>`，`buildSystemContent()` 改用 `{Description}` 和 `{Rules}` 两个占位符替代原有的三个（`{persona_description}`、`{persona_role}`、`{scenario}`）。`TurnProcessor` 内部将旧的 scenario+persona 映射到 AgentMode 再传给 ConversationAgent。旧的 `ScenarioType` 和 `PersonaType` 保留不动，协议层不变。

## Acceptance criteria

- [ ] `AgentMode` 枚举编译通过，`WORKPLACE_STANDUP.displayName` 返回 `"Standup Meeting"`，`templatePath` 返回 `"workplace_standup"`
- [ ] `src/main/resources/prompts/workplace_standup/description.txt` 和 `rules.txt` 存在，内容合理
- [ ] `conversation-system.txt` 退化为骨架：`{Description}\n\n{Rules}\n\n{topicSummary}\n{learningProfile}\n{activeEngagement}`
- [ ] `ConversationAgent` 构造时预加载所有 Mode 模板到 EnumMap，不含文件 IO 在请求路径中
- [ ] `ConversationAgent.generateStream()` 和 `generateStreamFirstTurn()` 内部接收 `AgentMode` 参数（签名从 `(history, scenario, persona, ...)` 变为 `(history, AgentMode, ...)`）
- [ ] `buildSystemContent()` 替换 `{Description}` 和 `{Rules}` 占位符，不再引用 `{persona_description}`、`{persona_role}`、`{scenario}`
- [ ] `TurnProcessor.processTurn()` 从 SessionService 读取 scenario+persona 并映射到 AgentMode，传给 ConversationAgent
- [ ] `mvn test -Dtest=ConversationAgentTest` 通过（断言从 3 个占位符改为 2 个，PersonaType/ScenarioType 参数改为 AgentMode）
- [ ] `ScenarioType.java` 和 `PersonaType.java` 未被删除或修改
- [ ] `mvn compile` 通过（全量编译无错误）

## Blocked by

None — can start immediately
