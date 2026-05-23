# 05: Documentation Update

**Status:** `ready-for-agent`

## Parent

PRD: `.scratch/daily-talk-mode/PRD.md`

## What to build

更新所有项目文档以反映 DAILY_TALK 模式的加入和 Topic Memory 模式级隔离的语义变更。

### 变更清单

| 文件 | 变更内容 |
|------|---------|
| `CONTEXT.md` | Topic Memory 定义从 "across all past Practice sessions" 改为 "across all past Practice sessions of the same AgentMode"；AgentMode 示例加入 DAILY_TALK |
| `AGENTS.md` | 更新 AgentMode 相关描述，加入 DAILY_TALK 模式说明；更新 "3 Agent skills" 中关于模式的描述 |
| `README.md` | How to Use 表格加入 Daily Talk 模式引用；项目结构图中 `workplace_standup/` 后加入 `daily_talk/` 示例；TODO 中标记 DAILY_TALK 已完成 |
| `docs/architecture.md` | 决策日志新增第 34 项（DAILY_TALK 模式添加）；V1 范围从 "单 AgentMode" 更新为 "两个 AgentMode (WORKPLACE_STANDUP + DAILY_TALK)"；文件结构更新；Prompt 设计章节更新示例 |
| `docs/adr/` | 新建 `NNNN-mode-scoped-topic-memory.md`，记录 Topic Memory 模式隔离的决策（null = 跨模式共享，非 null = 模式隔离）和 trade-off |

### ADR 要点（`NNNN-mode-scoped-topic-memory.md`）

- **Context**：新增 DAILY_TALK 模式后，工作闲聊的 conversation topics 不应混入工作话题
- **Decision**：UserMemory 新增可空 mode 字段；TOPIC_SUMMARY 记 mode（隔离），LEARNING_PROFILE 记 null（共享）
- **Status**：Accepted
- **Consequences**：模式越多 UserMemory 行数越多，但隔离语义清晰；无需引入 MemoryType.TOPIC_SUMMARY_DAILY_TALK 等新类型

## Acceptance criteria

- [ ] `CONTEXT.md` Topic Memory 定义已更新为 mode-scoped
- [ ] `AGENTS.md` 包含 DAILY_TALK 模式描述
- [ ] `README.md` Daily Talk 出现在 How to Use 表格和项目结构图中
- [ ] `docs/architecture.md` 决策日志包含 #34（DAILY_TALK），V1 范围已更新
- [ ] `docs/adr/NNNN-mode-scoped-topic-memory.md` 文件存在，格式与现有 ADR 一致
- [ ] 所有 Markdown 文件无语法错误

## Blocked by

None — can start immediately（建议在 01-04 完成后执行，以反映最终实现状态）
