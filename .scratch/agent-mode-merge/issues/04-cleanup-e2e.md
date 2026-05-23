# 04: 旧代码清理 + E2E 测试更新

**Status:** `ready-for-agent`

## Parent

PRD: `.scratch/agent-mode-merge/PRD.md`

## What to build

彻底删除 `ScenarioType.java` 和 `PersonaType.java` 两个枚举类，移除全项目中所有对它们的 import 引用。从 `Session` 实体中删除旧的 `scenario` 和 `persona` 字段，从 `CoachState` 中删除 `SCENARIO` 和 `PERSONA` 频道，从 `SessionService` 中删除 `getScenario()` 和 `getPersona()` 方法，从 `ClientMessage.StartSession` 和 `ServerMessage` 相关 record 中删除旧的 scenario/persona 字段。更新 E2E 测试：`EnglishCoachSessionIT` 中 `session.getScenario()` 断言改为 `session.getMode()`，E2E 测试中的模式下拉选择步骤简化；`EnglishCoachResumeIT` 适配新的 session resume payload。确保 `mvn compile` + `mvn test` + `mvn verify` 全部通过。

## Acceptance criteria

- [ ] `ScenarioType.java` 文件已删除
- [ ] `PersonaType.java` 文件已删除
- [ ] 全项目无 `import.*ScenarioType` 或 `import.*PersonaType` 引用（可通过 `rg "ScenarioType|PersonaType" --type java` 验证为 0 结果）
- [ ] `Session` 实体无 `scenario` 和 `persona` 字段，仅保留 `mode` 字段
- [ ] `CoachState` 无 `SCENARIO` 和 `PERSONA` 频道定义
- [ ] `CoachState.initialState()` 签名无 scenario/persona 参数 — 只有 `mode`
- [ ] `SessionService` 无 `getScenario()` 和 `getPersona()` 方法
- [ ] `ClientMessage.StartSession` 无 `scenario`/`persona` 字段
- [ ] `ServerMessage.SessionStarted` 和 `SessionResumed` 无 `scenario`/`persona` 字段
- [ ] `mvn compile` 通过
- [ ] `mvn test` 全部通过（所有 6 个受影响的测试类）
- [ ] `mvn verify` 全部通过（EnglishCoachSessionIT + EnglishCoachResumeIT）
- [ ] E2E 测试中 `session.getMode()` 正确返回 `AgentMode.WORKPLACE_STANDUP`

## Blocked by

- `03-frontend-dropdown-merge` — 前端已适配，确保全链路一致
