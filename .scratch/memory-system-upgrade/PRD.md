# PRD: 记忆系统升级 — 三轮注入 + MemoryCue 结构化记忆

**Status:** `ready-for-agent`

## Problem Statement

当前英语教练的记忆系统有两个弱点：

1. **记忆注入窗口过窄**：Topic Memory 和 Learning Profile 仅在每段新 Practice session 的**第一轮**（first turn）注入到 System Prompt 中。在 DAILY_TALK 等闲聊模式下，Leader 的第一句话通常是简单的"Hi"，Agent 来不及在回复中充分引用记忆内容。等到第二轮真正展开对话时，System Prompt 中的记忆已被清空，Agent "遗忘"了学生的历史话题和学习重点。

2. **缺少结构化记忆检索能力**：现有的 User Memory 是合并式聚合摘要（将每次会话 Report 与旧摘要合并为一个文本块），仅适用于注入 System Prompt。它无法按话题维度拆分、无法打标签、也无法在后续会话中根据关键词精准检索"关于旅游我们聊过什么"。

3. **记忆追溯缺失**：`user_memory` 表无法关联回产生该版本记忆的那段 Practice session，给调试和数据分析带来困难。

## Solution

三个改动协同升级记忆系统：

1. **三轮记忆注入**：将记忆注入窗口从第一轮扩展到前三轮（messageId ≤ 3），给 Agent 充足的机会自然地引入历史话题。

2. **结构化 MemoryCue 模块**：在会话结束时，由独立的 `MemoryCueAgent` 通过两步 LLM 调用将对话按话题拆分，每个话题生成 `(topic, summary, tags)` 三元组存入新表 `memory_cues`。这是一个与旧 User Memory **完全解耦、并存**的新模块，v1 仅存储，未来 v2 启用关键字检索。

3. **`user_memory` 增加 `session_id`**：每条 User Memory 记录现在知道它是由哪段 Practice session 触发生成的，便于追溯。

## User Stories

1. 作为一名英语学习者，当我开始一段新会话并在开头的闲聊中谈到之前聊过的话题时，Agent 应该自然地引用我的历史话题记忆（如"上次你提到的日本旅行后来怎么样了？"），即使我的第一句话只是简单的问候。
2. 作为一名英语学习者，在 DAILY_TALK 模式下前几轮闲聊时，Agent 就应该表现出它记得我之前的聊天内容，而不是到了第四轮才"想起来"。
3. 作为一名英语学习者，在 WORKPLACE_STANDUP 模式下，Agent 在前三轮就应该主动询问上次未完成的工作话题，延续对话的连贯性。
4. 作为一名英语学习者，在会话结束时，系统应自动将我本次对话涉及的话题进行结构化总结，按不同话题分别提取摘要和标签（如"旅游"标签包含我讨论的日本行程，"工作"标签包含我讲的演示文稿准备）。
5. 作为一名英语学习者，当一段对话包含多个不相关的话题时（如先聊旅游、再聊工作），系统应该能自动拆分为独立的记忆条目，而不是混在一起。
6. 作为一名英语学习者，当一段对话主题贯穿（如聊如何在旅游中兼顾远程工作），系统应将二者合并为一个综合记忆条目，标签同时包含"旅游"和"工作"。
7. 作为一名开发者，我需要能够根据 `session_id` 追溯某条 User Memory 记录是由哪段会话生成的，便于调试和数据分析。
8. 作为一名开发者，我可以追踪 MemoryCue 的生成状态——成功的记录有完整内容，失败的记录保留错误标记和段索引，方便排查 LLM 调用失败的原因。
9. 作为一名开发者，MemoryCue 的生成与 Report、Memory Merge 完全并行互不阻塞，不会因为话题拆分失败而影响会话结束的报表生成。
10. 作为系统，话题分割的第一步 LLM 调用如果失败，不影响 Report 和 Memory Merge 的正常完成，系统仅记录一条失败日志和失败标记。
11. 作为系统，一个 segment 的第二步 LLM 调用失败，不影响其他 segment 的成功存储——成功和失败的 MemoryCue 行各自独立入库。
12. 作为系统，不同 AgentMode 的 MemoryCue 数据相互隔离，WORKPLACE_STANDUP 的话题标签不会污染 DAILY_TALK 的记忆空间。

## Implementation Decisions

### 1. 记忆注入窗口从 1 轮扩展到 3 轮

- `ConversationAgent` 合并 `generateStream()` 和 `generateStreamFirstTurn()` 为一个方法，新增 `int messageId` 参数
- 内部判断 `messageId <= 3 && hasMemory` 决定是否注入 Topic Memory 和 Learning Profile
- `TurnProcessor` 移除 `isFirstTurn` 判断逻辑，仅将 `messageId` 透传给 `ConversationAgent`
- 判断依据：前端发来的 `messageId`（`MessageData.messageId`），而非历史消息列表的长度

### 2. MemoryCue 模块架构

#### 2.1 MemoryCue 表设计

- 新表 `memory_cues`，实体 `MemoryCue` 继承 `BaseEntity`（获取 `id`、`createTime`、`updateTime`）
- 字段：
  - `session_id`（String，NOT NULL，无 FK）：关联触发生成的 Practice session
  - `user_id`（String，NOT NULL）：Learner 数据隔离
  - `mode`（AgentMode 枚举，NOT NULL）：AgentMode 数据隔离
  - `segment_index`（int，NOT NULL）：对话拆分后的段索引（从 0 开始；-1 表示分割失败）
  - `topic`（String，nullable）：LLM 生成的话题名称（失败时为空或描述文字）
  - `summary`（String，nullable，无长度截断）：LLM 生成的话题摘要
  - `tags`（String = JSON `List<String>`，nullable）：自由文本标签列表，使用 `StringListConverter`（`AttributeConverter<List<String>, String>`）存取为 H2 JSON 列，后续可直接用 `JSON_CONTAINS` 查询
  - `status`（MemoryCueStatus 枚举，NOT NULL）：COMPLETED / SEGMENT_FAILED / FIRST_CALL_FAILED

#### 2.2 MemoryCueStatus 枚举

| 值 | 含义 |
|----|------|
| `COMPLETED` | segment 成功生成，有 topic、summary、tags |
| `SEGMENT_FAILED` | 第一次 LLM 调用成功分割，但该 segment 的第二次调用失败 |
| `FIRST_CALL_FAILED` | 话题切换检测（第一次 LLM 调用）失败，segment_index = -1 |

#### 2.3 MemoryCueAgent — 两步 LLM 调用

- 使用同步 `ChatLanguageModel` bean（与 MemoryAgent、ReportAgent 一致）
- 从 `PromptLoader` 加载两个 prompt 模板：`memory-cue-split.txt` 和 `memory-cue-entry.txt`
- 方法一 `detectSwitches(messages, mode)` → `List<Integer>` 切换点列表
  - 输入：全部 messages，每条前注入 `[MSG#N]` 行号标记
  - 输出：纯数字数组（如 `[]` 表示无切换，`[3]` 表示 MSG#3 后切换）
  - 代码侧做防御性 JSON 解析（提取 JSON 片段容错 LLM 额外文本）
  - 不依赖 mode 信息（mode 仅用于数据入库时的隔离）
- 方法二 `generateCue(messages, mode, segmentIndex)` → 结构化 JSON `{topic, summary, tags}`
  - 输入：该 segment 的 messages 子集（不注入行号标记）
  - 输出：三个字段均为英语，summary 不做长度截断，tags 不限数量

#### 2.4 MemoryCueService 流程

```
generateCuesAsync(sessionId, userId, mode, messages):
  1. 调用 agent.detectSwitches(messages, mode)
     
     失败 → 写一条 MemoryCue(status=FIRST_CALL_FAILED, segment_index=-1)
            + 打印日志，直接 return
     
     成功，无切换 → segments = [messages 全部]
     成功，有切换 → 按切换点切割为多个 segments
     
  2. segments.forEach((seg, i) → CompletableFuture.runAsync:
       agent.generateCue(seg, mode, i)
         成功 → 写 MemoryCue(status=COMPLETED)
         失败 → 写 MemoryCue(status=SEGMENT_FAILED)
     )
  
  3. 全部完成（CompletableFuture.allOf），不抛异常阻塞 onEndSession
```

- 异步执行，复用 `memoryExecutor` 线程池
- 第一步串行（先检测切换点），第二步各 segment 并行
- 与 `ReportAgent`、`MemoryService.generateMemoryAsync` 完全并行

#### 2.5 MemoryCueRepository

三个查询方法（v1 均可用于测试验证）：

- `findByUserIdAndMode(userId, mode)` — 按用户和模式查所有 MemoryCue
- `findBySessionId(sessionId)` — 按会话回溯
- 基础 CRUD 由 `JpaRepository<MemoryCue, String>` 提供

No FK constraints on `session_id` or `user_id`.

#### 2.6 StringListConverter

- 位置：`model/StringListConverter.java`
- 实现 `AttributeConverter<List<String>, String>`，使用 Jackson `ObjectMapper` 做 JSON 序列化/反序列化
- 空集合序列化为 `[]`

### 3. `user_memory` 增加 `session_id`

- 新增 `session_id` 列（String，nullable，无 FK）
- `UserMemory` 构造器新增 `sessionId` 参数（最小改动：同时保留旧构造器用于测试）
- `MemoryService.generateSingle()` 在 `generateMemoryAsync()` 调用时传入 sessionId
- 旧数据行 `session_id` 为 NULL，H2 `ddl-auto=update` 自动添加列

### 4. ConversationAgent 方法合并

- 删除 `generateStream()` 和 `generateStreamFirstTurn()` 两个公开方法
- 新公开方法签名：
  ```
  generateStream(List<MessageData> history, AgentMode mode,
                 String topicSummary, String learningProfile,
                 int messageId, StreamingChatResponseHandler handler)
  ```
- 内部判断 `boolean injectMemory = messageId <= 3 && (!topicSummary.isBlank() || !learningProfile.isBlank())`
- 私有方法 `generate(...)` 和 `buildSystemContent(...)` 逻辑不变

### 5. TurnProcessor 简化

- 移除 `isFirstTurn` 变量
- 不再做 `isFirstTurn && hasMemory` 条件分支
- 直接调用 `conversationAgent.generateStream(historySnapshot, finalMode, topicSummary, learningProfile, messageId, handler)`
- 所有注入判断逻辑下沉到 `ConversationAgent`

### 6. `CoachMessageHandler.onEndSession()` 并行流

```
onEndSession():
  ├─ executor:
  │    reportAgent.generate() → ReportResult
  │    → memoryService.generateMemoryAsync(userId, report, mode, sessionId)
  │
  ├─ executor（与上并行）:
  │    memoryCueService.generateCuesAsync(sessionId, userId, mode, messages)
  │
  └─ 主线程（等 report 完成）:
       sessionStore.completeSession(sessionId, messages, corrections, report)
       → 发送 SESSION_REPORT 到 WS
       sessionService.remove(sessionId)
```

### 7. 线程池扩展

- `AsyncConfig.memoryExecutor`：core 2→4, max 4→8
- 同时运�行 MemoryCue 第一步 + 第二步（最多 3 segment 并行）+ Report + Topic Memory Merge + Learning Profile Merge

### 8. AgentMode 隔离

- `MemoryCue` 的 `mode` 字段使用 `@Enumerated(EnumType.STRING) AgentMode`
- LLM prompt 中不传 mode 信息（mode 无关话题分割和摘要生成）
- mode 在数据库查询层做隔离

### 9. 文档更新

- `CONTEXT.md`：新增 `MemoryCue`、`Memory Cue Split`、`Memory Cue Entry` 术语
- `AGENTS.md`：补充记忆系统新行为（三轮注入 + MemoryCue 模块）
- 新建 `docs/adr/dual-memory-system.md`：记录双轨记忆系统架构决策
- 同步更新`README.MD`和`architecture.md`

### 10. WireMock E2E 测试桩

- 新增两个 E2E marker：
  - `E2E_MARKER_MEMORY_CUE_SPLIT`：匹配第一次 LLM 调用（话题切换检测）
  - `E2E_MARKER_MEMORY_CUE_ENTRY`：匹配第二次 LLM 调用（topic/summary/tags）
- 新增 WireMock 响应文件：
  - `memory-cue-split-two-switch.json`：返回 `[3]`（MSG#3 后切换）
  - `memory-cue-entry-success.json`：返回标准 `{topic, summary, tags}` JSON
- 测试仅验证：两段 → 两条 COMPLETED 记录

### 11. Prompt 文件

- `src/main/resources/prompts/memory-cue-split.txt`：话题切换检测 prompt，占位符 `{messages}`
- `src/main/resources/prompts/memory-cue-entry.txt`：segment 摘要生成 prompt，占位符 `{segment}`
- E2E 测试用 prompt 文件（放在 `src/test/resources/prompts/`）以 `E2E_MARKER_*` 开头

### 12. 影响范围：变更文件清单

| 操作 | 文件 |
|---|---|
| 新建 | `model/MemoryCue.java`、`model/MemoryCueStatus.java`、`model/StringListConverter.java` |
| 新建 | `repository/MemoryCueRepository.java` |
| 新建 | `agent/MemoryCueAgent.java` |
| 新建 | `service/MemoryCueService.java` |
| 新建 | `memory-cue-split.txt`（main + test）、`memory-cue-entry.txt`（main + test） |
| 新建 | WireMock 响应文件 |
| 新建 | `docs/adr/dual-memory-system.md` |
| 修改 | `ConversationAgent.java`、`TurnProcessor.java` |
| 修改 | `CoachMessageHandler.java`、`MemoryService.java` |
| 修改 | `UserMemory.java`、`SessionService.java` |
| 修改 | `AsyncConfig.java` |
| 修改 | `CONTEXT.md`、`AGENTS.md`、`WireMockStubs.java` |
| 测试 | `MemoryCueAgentTest.java`、`MemoryCueServiceTest.java`、`MemoryCueRepositoryTest.java`、`EnglishCoachMemoryCueIT.java` |
| 更新 | `ConversationAgentTest.java`、`MemoryServiceTest.java`、`TurnProcessorTest.java`（如有） |

## Testing Decisions

### 测试原则

- TDD 开发：Red-Green-Refactor，所有新增代码先写单元测试
- 只测试外部可观测行为，不测试内部实现细节
- E2E 测试不过度复杂——unit test 覆盖所有 edge case，E2E 仅验证 happy path

### 需要新建的单元测试

1. **`MemoryCueAgentTest`**：
   - `detectSwitches`：无切换返回空列表、单切换返回正确索引、多切换返回多数索引
   - `detectSwitches`：LLM 返回额外文本（防御性解析）
   - `generateCue`：返回正确的 `{topic, summary, tags}` JSON 结构
   - `generateCue`：LLM 返回非法 JSON 时抛异常（Service 层处理）

2. **`MemoryCueServiceTest`**：
   - 无切换场景 → 1 次 detect + 1 次 generateCue + 1 行 COMPLETED
   - 有切换场景 → 1 次 detect + N 次 generateCue + N 行 COMPLETED
   - 第一次调用失败 → 1 行 FIRST_CALL_FAILED，不触发第二次调用
   - 某个 segment 失败 → 同行 COMPLETED + failed 行 SEGMENT_FAILED

3. **`MemoryCueRepositoryTest`**：
   - `findByUserIdAndMode`：mode 隔离验证
   - `findBySessionId`：session 关联验证
   - 基础 `save` / `findById` 验证

### 需要更新的单元测试

4. **`ConversationAgentTest`**：
   - messageId=1 → memory 注入（验证 system prompt 包含 `[Conversation Memory]`）
   - messageId=3 → memory 注入
   - messageId=4 → memory 不注入（验证 system prompt 不包含注入文本）
   - 无 memory 数据 → messageId=1 也不注入

5. **`MemoryServiceTest`**：验证 `generateMemoryAsync` 传入 sessionId

### 需要新建的 E2E 测试

6. **`EnglishCoachMemoryCueIT`**：
   - 启动会话 → 3 轮对话（模拟话题切换 at MSG#3）→ 结束会话
   - 验证 `memory_cues` 表有两行 COMPLETED，对应不同 topic 和 tags

### 参考已有测试模式

- Agent 单元测试参考 `MemoryAgentTest.java`
- Service 单元测试参考 `MemoryServiceTest.java`
- Repository 测试参考 `UserMemoryRepositoryTest.java`
- E2E 测试参考 `EnglishCoachMemoryIT.java`（Playwright + WireMock 场景状态机）

## Out of Scope

- **MemoryCue 关键字检索**：v1 仅存储结构化记忆，不实现检索功能。`memory_cues` 表设计已预留 JSON 列用于未来 `JSON_CONTAINS` 查询，但本 PRD 不涉及任何检索逻辑或 UI。
- **旧 User Memory 系统替换**：新的 MemoryCue 与旧 Topic Memory 并存。后续如果 MemoryCue 验证成功，可能统一为一个系统，但这是未来决策。
- **Segment 生成失败后的重试**：`SEGMENT_FAILED` 行仅记录，不自动重试。
- **前端 UI 变更**：暂无任何前端改动（无检索入口、无记忆显示 UI）。
- **数据库迁移工具**：继续使用 Hibernate `ddl-auto=update`，不引入 Flyway/Liquibase。

## Further Notes

- 当前 `CorrectionAgent` 和 `ReportAgent` 不受影响——它们与 MemoryCue 模块完全解耦。
- `ConversationAgent` 合并方法后需同步删除 `generateStreamFirstTurn` 的所有调用方和测试引用。
- `CoachState` 的 `TOPIC_MEMORY` 和 `LEARNING_PROFILE` 频道保持不变——会话初始时仍然从 DB 加载最新版本到内存，只是在轮次中使用条件变为 `messageId <= 3` 而非"仅第一轮"。
- Prompt 模板文件（`memory-cue-split.txt`、`memory-cue-entry.txt`）的具体 prompt 文案由开发者自行设计，LLM 输出格式在 prompt 中约定。
- 所有新增 LLM 调用均复用 `AsyncConfig` 的 `memoryExecutor`（core=4, max=8, CallerRunsPolicy）。
