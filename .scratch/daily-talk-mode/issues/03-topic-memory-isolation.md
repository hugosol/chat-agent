# 03: Mode-Scoped Topic Memory Isolation

**Status:** `ready-for-agent`

## Parent

PRD: `.scratch/daily-talk-mode/PRD.md`

## What to build

在 `UserMemory` 实体新增可空的 `mode` 字段（`@Enumerated(EnumType.STRING)`, `AgentMode` 类型），实现 Topic Memory 的模式级隔离和 Learning Profile 的跨模式共享：

- `TOPIC_SUMMARY` 类型记录：`mode` = 当前 AgentMode（隔离），不同模式的话题记忆互不污染
- `LEARNING_PROFILE` 类型记录：`mode` = null（跨模式共享），同一个 Learner 的学习档案保持一致

涉及全链路变更：

- **实体层**：`UserMemory.java` 新增 `mode` 字段
- **仓库层**：`UserMemoryRepository.java` 查询方法变更为 `findTopByUserIdAndTypeAndModeOrderByVersionDesc`，mode 参数支持 null 查询
- **服务层**：`MemoryService.java` 所有公开方法（`loadLatestContent`、`generateMemoryAsync`、`generateSingle`）新增 `mode` 参数；`loadLatestContent` 中 mode 为 null 时查 null（共享），非 null 时查特定 mode（隔离）；`generateMemoryAsync` 生成 TOPIC_SUMMARY 时传入 mode，生成 LEARNING_PROFILE 时传 null
- **会话层**：`SessionService.init()` 加载记忆时传入当前 mode；`CoachMessageHandler.onEndSession()` 生成记忆时传入当前 mode
- `MemoryAgent.mergeTopic()` 无需改动（合并逻辑与模式无关）
- `CoachState.initialState()` 已有 `topicMemory` 和 `learningProfile` 参数，无需变更签名

## Acceptance criteria

- [ ] `UserMemory` 实体包含可空 `AgentMode mode` 字段，`mvn compile` 通过
- [ ] `UserMemoryRepository.findTopByUserIdAndTypeAndModeOrderByVersionDesc` 支持 null mode 查询
- [ ] `MemoryService.loadLatestContent(userId, type, mode)` 签名新增 mode 参数
- [ ] `MemoryService.generateMemoryAsync(userId, report, mode)` 签名新增 mode 参数
- [ ] `SessionService.init()` 调用 `loadLatestContent` 时传入当前 AgentMode
- [ ] `CoachMessageHandler.onEndSession()` 调用 `generateMemoryAsync` 时传入当前 AgentMode
- [ ] 结束一个 WORKPLACE_STANDUP 会话后，`user_memory` 表中 TOPIC_SUMMARY 记录的 `mode` 列为 `WORKPLACE_STANDUP`，LEARNING_PROFILE 记录的 `mode` 列为 `NULL`
- [ ] 结束一个 DAILY_TALK 会话后，TOPIC_SUMMARY 的 `mode` 为 `DAILY_TALK`，LEARNING_PROFILE 的 `mode` 为 `NULL`
- [ ] `mvn test` 全部通过（含 MemoryServiceTest 等受影响的单元测试）
- [ ] `mvn verify` 全部通过（现有 E2E 测试不受影响，H2 断言包含 mode 字段）

## Blocked by

- `01-prompt-generalization` — 骨架模板通用化对模式隔离无直接依赖，但 01 是当前 PRD 的基础变更，建议先完成
