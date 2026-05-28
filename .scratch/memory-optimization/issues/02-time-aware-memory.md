# 02: Time-Aware Memory

**Status:** `ready-for-agent`

## Parent

`.scratch/memory-optimization/PRD.md`

## What to build

Topic Memory 和 RAG MemoryCues 注入 System Prompt 时自动添加 `[from yesterday]` 风格的时间标签前缀，让 Agent 感知每条记忆的时效性，从而自然地问"你昨天提到的那个 pipeline 迁移完成了吗？"而不是泛泛地说"你之前提过 pipeline 迁移"。

**新建 `TimeLabel` 枚举**（`com.hugosol.webagent.model.TimeLabel`），9 个常量按声明顺序从小到大匹配：

| 常量 | 标签 | 最大时长 |
|------|------|---------|
| `JUST_NOW` | `"just now"` | 5 分钟 |
| `A_FEW_MINUTES_AGO` | `"a few minutes ago"` | 1 小时 |
| `EARLIER_TODAY` | `"earlier today"` | 12 小时 |
| `YESTERDAY` | `"yesterday"` | 48 小时 |
| `A_FEW_DAYS_AGO` | `"a few days ago"` | 7 天 |
| `ABOUT_A_WEEK_AGO` | `"about a week ago"` | 14 天 |
| `A_FEW_WEEKS_AGO` | `"a few weeks ago"` | 30 天 |
| `ABOUT_A_MONTH_AGO` | `"about a month ago"` | 60 天 |
| `A_WHILE_AGO` | `"a while ago"` | 365 天（含 >365 天兜底） |

静态方法 `computeLabel(LocalDateTime eventTime, LocalDateTime referenceTime)` 按声明顺序匹配第一个 `elapsed ≤ maxDuration` 的条件，返回对应标签字符串。

**实体层：**
- `UserMemory` 和 `MemoryCue` 各新增 `@Transient getTimeLabel(LocalDateTime referenceTime)` 方法，调用 `TimeLabel.computeLabel()`

**注入逻辑：**
- `ConversationAgent.buildSystemContent()` 中：Topic Memory 单条前加 `[from yesterday] ` 前缀；RAG MemoryCues 逐条拼接前加各自的时间标签前缀
- `MemoryContent` DTO 新增 `topicCreatedAt` 和 `cueCreatedAts` 字段，用于传递时间戳
- `TurnProcessor.processTurn()` 构造 `MemoryContent` 时从 `SessionService` 获取 `topicCreatedAt`，从 `EmbeddingService.search()` 返回值中获取各 cue 的 `createdAt`
- `CueMatch` DTO 新增 `createdAt` 字段

**测试：**
- `TimeLabelTest` 单元测试：覆盖每个阈值的边界值（刚好 5 分钟、5 分 1 秒、1 小时、365 天+1 秒等）
- `EnglishCoachMemoryCueIT` E2E 测试：验证注入的 System Prompt 中包含时间标签（通过检查 llm_call_logs 或 WireMock 请求体）

## Acceptance criteria

- [ ] `TimeLabel` 枚举定义完成，9 个常量 + `computeLabel()` 静态方法
- [ ] `TimeLabelTest` 单元测试覆盖所有阈值边界（≥10 个用例）
- [ ] `UserMemory.getTimeLabel()` 和 `MemoryCue.getTimeLabel()` `@Transient` 方法可用
- [ ] `MemoryContent` DTO 携带 `topicCreatedAt`（`LocalDateTime`）和 `cueCreatedAts`（`List<LocalDateTime>`）
- [ ] `CueMatch` DTO 新增 `createdAt` 字段，`EmbeddingService.search()` 返回时填充
- [ ] Topic Memory 注入格式：`[from yesterday] Learner discussed Q4 deliverables...`
- [ ] RAG MemoryCue 注入格式：`[from yesterday] Python CI: discussed migration... , as well as, [from last week] Q4 deliverables: prioritizing backend...`
- [ ] `SessionService` 提供获取 topic memory `createdAt` 的方法
- [ ] `mvn test` 全部通过
- [ ] `EnglishCoachMemoryCueIT` E2E 通过（验证时间标签存在于 System Prompt 中）

## Blocked by

None — can start immediately

## User stories covered

#4, #5, #6
