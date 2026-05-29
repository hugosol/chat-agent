# PRD: Unified MemoryCue Retrieval — Remove Topic Memory

**Status:** `ready-for-agent`

## Problem Statement

当前 English Coach 的记忆系统存在两个问题：

1. **Topic Memory 与 MemoryCue 功能重叠**：`TOPIC_SUMMARY` 是 Report 生成的宽泛单条摘要，仅在第 1 轮注入；MemoryCue 是按话题切分的结构化提示，从第 2 轮起通过 RAG 检索。两者提供相似的"上次聊了什么"信息，但控制流复杂——`userMemoryRounds` 阈值导致第 1 轮使用 User Memory，后续轮次使用 RAG MemoryCue，两个通路互斥切换。

2. **站会提示词预设"每天一次"的节奏**：`workplace_standup/description.txt` 中 `daily standup` 的措辞让 LLM 默认假设上次对话是昨天，当 Learner 在同一天进行多次快速站会（quick catch-up）时，Agent 会说"昨天我们讨论了..."而实际只过了一小时。时间感知应由 MemoryCue 自带的 `createTime` 驱动，而非角色描述暗示。

重新审视后，Topic Memory 可以被 MemoryCue 完全取代——加载最后一条会话的最后一个 MemoryCue 即可达到同样的"延续对话"效果，且结构更优（topic + summary 分离、自带时间戳）。

## Solution

**统一到 MemoryCue 单轨记忆**：删除 `TOPIC_SUMMARY` 相关的整个 User Memory 管道，将 MemoryCue 打造为唯一的跨会话记忆来源。前端报告不再展示冗余的话题摘要。

**RAG 优先 + 回退锚点**：每轮都执行 RAG 语义检索。第 1 轮若 RAG 返回空结果，自动加载最近一次会话的最后一条 MemoryCue 作为对话延续锚点，并附带时间上下文（`{lastConversation}` 占位符）。回退提示通过 MemoryCueQueue 的 LRU 机制至少存活 1 轮后自然淘汰。

**提示词时间感知**：去掉 `description.txt` 中的 `daily` 措辞，改为 `standup or quick catch-up`。当回退触发时，通过 `{lastConversation}` 占位符显式注入时间标签和行为引导，让 LLM 基于真实数据判断时间距离。

## User Stories

1. 作为 Learner，当我对 Agent 说 "hi" 开始新会话时，我希望 Agent 能够延续上次对话的话题（例如"上次提到的那个 pipeline 迁移怎么样了？"），而不需要我主动提起。
2. 作为 Learner，当我新会话的第 1 句话就提到了某个旧话题时（触发了 RAG 匹配），我希望 Agent 自动关联历史提示，而不是强行延续上次最后的话题。
3. 作为 Learner，当我一天内进行多次站会时，我希望 Agent 知道上次对话是"今天早些时候"而非"昨天"，从而说"你刚才说的那个部署完成了吗？"而不是"昨天我们讨论了部署"。
4. 作为 Learner，在同一次会话的多轮对话中，我希望历史话题提示通过 RAG 检索持续可见，而不是只有第 1 轮才被注入。
5. 作为开发者，我希望用一个统一的 RAG 检索通路取代 Topic Memory + MemoryCue 双轨切换逻辑，减少代码分支和维护成本。
6. 作为开发者，我希望 `MemoryType.TOPIC_SUMMARY` 枚举值和相关数据库列被彻底移除，消除"Topic Memory 到底是什么"的认知负担。
7. 作为一名 Learner，当我看会话报告时，我不希望看到话题摘要与 MemoryCue 重复——报告专注于评估和错误分析，话题回顾由 MemoryCue 独立承担。
8. 作为一名 Learner，我的 Learning Profile（学习错误模式档案）在对话第 1 轮仍然被注入，Agent 从一开始就了解我的弱点。
9. 作为一名开发者，我希望 `UserMemory` 重命名为 `UserLearningProfile`，明确它只存学习特征，话题记忆交给 MemoryCue 体系。
10. 作为一名开发者，`MemoryService` 重命名为 `LearningProfileService`，名称与其唯一职责（Learning Profile 的 LLM 合并写入）完全一致。

## Implementation Decisions

### 1. 删除 Topic Memory — 数据管道清理

Report 不再生成 `topicSummary` 字段。`ReportResult` record 从 5 字段减为 4 字段（删除 `topicSummary`）。`ServerMessage.ReportData` 同步减为 4 字段。前端 `SESSION_REPORT` 不再携带话题摘要，报告弹窗中的 "Topic Summary" 区块删除。

`report.txt` JSON schema 中删除 `topicSummary` 字段。`memory-topic.txt` 文件彻底删除（确认无任何调用方——历史残留）。

### 2. MemoryType → LearningType 重命名

`MemoryType` 枚举重命名为 `LearningType`，删除 `TOPIC_SUMMARY` 值，仅保留 `LEARNING_PROFILE`。`UserMemory` 实体重命名为 `UserLearningProfile`，H2 表名从 `user_memory` 改为 `user_learning_profiles`。数据库列 `type` 保持不变，值从 `"TOPIC_SUMMARY"/"LEARNING_PROFILE"` 变为仅 `"LEARNING_PROFILE"`。

`MemoryService` 重命名为 `LearningProfileService`，`generateMemoryAsync()` 重命名为 `generateLearningProfileAsync()`，只保留 LEARNING_PROFILE 的 LLM 合并写入（原 TOPIC_SUMMARY 的直接写入分支删除）。`loadTopicCreatedAt()` 方法删除（仅服务于 Topic Memory 时间标签）。

`UserMemoryRepository` 重命名为 `UserLearningProfileRepository`。`CoachMessageHandler` 和 `SessionComplete` 中的 `MemoryService` 注入改为 `LearningProfileService`。

### 3. CoachState 通道清理

删除 `CoachState` 中的 `TOPIC_MEMORY` 通道（常量、schema 定义、`initialState()` 的 `topicMemory` 参数、`topicMemory()` 访问器）。通道总数从 7 减为 6。

`SessionService.init()` 不再从 H2 加载 Topic Memory。`getTopicMemory()` 方法删除。`CoachState.initialState()` 签名从 7 参数减为 6 参数。

### 4. 统一 RAG 检索 — 每轮执行

`TurnProcessor.resolveMemoryContext()` 完全重写：

- **每轮都执行 RAG 检索**：`EmbeddingService.search()` → push 结果到 `MemoryCueQueue`
- **第 1 轮回退**：若 RAG 结果 + 队列仍为空（说明用户说的内容未匹配到任何历史提示），从 H2 加载最近一次会话的最后一条 COMPLETED MemoryCue 作为锚点，推入队列，并设置 `lastConversationTimeLabel`
- Learning Profile 仅在第 1 轮传入 `MemoryContent`，后续轮次为 null
- 不再有 `messageId` 与 `userMemoryRounds` 的任何比较逻辑

### 5. MemoryContent DTO 重构

`MemoryContent` record 字段从 `(topicSummary, learningProfile, cueMatches, topicCreatedAt)` 改为 `(lastConversationTimeLabel, learningProfile, cueMatches)`。

- `lastConversationTimeLabel`：null = 不渲染 `{lastConversation}` 占位符；非 null = 渲染时间上下文
- `learningProfile`：第 1 轮传入，后续为 null
- `cueMatches`：RAG 检索结果或回退锚点提示（来自 `MemoryCueQueue.getEntries()`）

### 6. conversation-system.txt 模板重命名

`{topicSummary}` 占位符重命名为 `{lastConversation}`。新模板结构：

```
{Description}
Rules:
{Rules}

{lastConversation}

{memoryCues}

{learningProfile}

{activeEngagement}
```

### 7. ConversationAgent 新分支逻辑

三个占位符独立判断，不再互斥：

| 占位符 | 渲染条件 | 内容 |
|--------|---------|------|
| `{lastConversation}` | `lastConversationTimeLabel != null` | `"The last conversation was {timeLabel}. Pick up conversation naturally from where it left off."` |
| `{learningProfile}` | `learningProfile != null` | `"[Your Learning Profile]\n{text}"` |
| `{memoryCues}` | `cueMatches != null && !cueMatches.isEmpty()` | `"[Memory Cues]\n1. [from {label}] {topic}: {summary}\n..."` |
| `{activeEngagement}` | 以上三个**任一**非空 | Active engagement 指令文本 |

### 8. MemoryCueQueue 设计意图

`MemoryCueQueue` 是一个 FIFO + 去重的 LRU 有序集合，容量 = `topK + 1`。每轮 RAG 检索结果按插入时间追加到队尾；同 cueId 去重刷新；满容时淘汰队头（最早插入）。

**设计理由**：
- **capacity = topK + 1**：多出的 1 个槽位作为 LRU 淘汰缓冲——避免刚检索到的结果立即被挤出队列
- **回退锚点存活周期**：第 1 轮回退的锚点提示从队尾入队。第 2 轮 RAG 结果追加在它之后。当容量溢出时，锚点位于队头最先被淘汰——正好存活 1 轮对话，给 LLM 一个找话题的起点，然后让路给真正相关的 RAG 检索结果
- **FIFO + 去重，非分数排序**：条目按插入时间排列。分数仅决定同一轮 push 批次内部的顺序

### 9. 站会提示词修正

`workplace_standup/description.txt`：

```
// Before:
during your daily standup.

// After:
during standup or quick catch-up.
```

去掉 "daily" 预设，让时间感知由 MemoryCue 的 `TimeLabel` 驱动。`daily_talk` 模式不需要修改——本来就是 casual chat，没有时间预设。

### 10. MemoryCueRepository 新增查询

```java
Optional<MemoryCue> findTopByUserIdAndModeAndStatusOrderByCreateTimeDesc(
    String userId, AgentMode mode, MemoryCueStatus status);
```

用于回退场景：加载最近一次会话的状态为 COMPLETED 的最新一条 MemoryCue。

### 11. `userMemoryRounds` 配置项删除

`AppProperties.memory.userMemoryRounds` 字段删除。`application.yml` 中 `app.memory.user-memory-rounds` 配置项删除。该阈值仅用于判定"前 N 轮注入 User Memory"，移除 Topic Memory 后无实际用途。

### 12. 不影响项

- WebSocket 协议消息类型不变（`SESSION_REPORT` 字段减少但消息类型保留）
- `LearningAgent.mergeProfile()` 完整保留（LEARNING_PROFILE 的 LLM 合并写入）
- `SessionComplete` 的 MemoryCue 生成管线不变
- `EmbeddingService` 的索引/检索逻辑不变
- `MemoryCueQueue` 的核心算法不变（仅使用场景从 "Round 2+ 检索" 改为 "Round 1+ 检索"）
- `TokenTracker` 不变
- `LlmCallLog` 不变
- `langgraph4j` 图结构不变

## Testing Decisions

### 测试原则

TDD 先行：先修改测试文件以反映新预期行为，再修改源码。测试验证外部可观察行为（WebSocket 消息、H2 数据、System Prompt 内容），不测试实现细节。

### 需要测试的模块

**单元测试 — 需修改：**

| 测试文件 | 变更类型 |
|---------|---------|
| `CoachStateTest.java` | 删除 `topicMemory()` 相关 4 个测试方法 |
| `ConversationAgentTest.java` | 更新 `MemoryContent` 构造为新三字段；新增 `{lastConversation}` 渲染测试（回退/非回退场景）；新增三条件独立渲染测试 |
| `TurnProcessorTest.java` | 删除 `getTopicMemory()` mock；新增 RAG 优先 + 回退流程测试（第 1 轮 RAG 命中/未命中、第 2+ 轮正常 RAG） |
| `MemoryServiceTest.java`→`LearningProfileServiceTest.java` | 删除 `TOPIC_SUMMARY` 相关 ~17 个测试用例；更新类名；更新 `LearningType.LEARNING_PROFILE` 引用 |
| `UserMemoryRepositoryTest.java`→`UserLearningProfileRepositoryTest.java` | `TOPIC_SUMMARY` → `LEARNING_PROFILE`；更新类名和表名 |
| `ReportAgentTest.java` | 删除 `topicSummary()` 的 3 个断言 |
| `CoachMessageHandlerTest.java` | `ReportResult` 构造函数去掉 topicSummary 参数；`ReportData` 构造函数参数减少；删除 `report().topicSummary()` 断言 |
| `SessionCompleteTest.java` | `FALLBACK_REPORT` 和 `ReportResult` 构造函数参数更新（3 处） |
| `SessionDbStoreTest.java` | `ReportResult` 构造函数参数更新（3 处） |
| `EntityMapperTest.java` | `ReportResult` 构造函数参数更新（2 处） |
| `ProtocolDispatcherTest.java` | `ServerMessage.ReportData` 构造函数参数更新 |

**E2E 测试 — 需修改：**

| 测试文件 | 变更类型 |
|---------|---------|
| `EnglishCoachMemoryIT.java` | 删除 `TOPIC_SUMMARY` 的 H2 断言；更新验证逻辑为仅验证 `LEARNING_PROFILE` |
| `DailyTalkIT.java` | 删除 `TOPIC_SUMMARY` 的 H2 断言；删除前端报告 "Topic Summary" 文本匹配 |

**E2E 测试 — 资源文件：**

| 文件 | 变更类型 |
|------|---------|
| `test/resources/wiremock/report.json` | 删除 JSON 中的 `topicSummary` 键值对 |
| `test/resources/wiremock/daily-report.json` | 删除 JSON 中的 `topicSummary` 键值对 |
| `test/resources/prompts/memory-topic.txt` | **删除**（主线同名文件已删除） |

### 测试先例

- E2E 测试：参照 `EnglishCoachMemoryIT.java` 的模式（Playwright + WireMock，DOM 等待断言，H2 查询验证）
- 单元测试：参照 `MemoryServiceTest.java` —— Mock Repository，验证异步写入行为和参数传递

## Out of Scope

- Learning Profile 在多轮对话中的持久注入（当前仅第 1 轮，后续不注入）
- MemoryCue 过期/淘汰机制（当前无过期逻辑，`queue.isEmpty()` 安全网保留但永不触发）
- `user_memory`/`user_learning_profiles` 表的旧数据迁移（`ddl-auto: update` 创建新表，旧数据孤岛。Learning Profile 可在下次会话结束时通过 LLM 合并重新生成）
- `coach_session` 表的 `topicSummary` 列迁移（该列从未存在——`SessionReport` 实体不包含 `topicSummary` 字段）
- AgentMode 的新增/修改（仅修改提示词文本，不新增 mode）
- 前端 UI 排版变更（仅删除报告弹窗中的一行，不影响布局结构）

## Further Notes

### 文档变更计划

本次重构需要在 4 份文档中同步更新：

**README.md — 12 处变更**：架构图、Agent 表格、记忆注入描述、项目结构中的 `memory-topic.txt` 和 `MemoryAgent.java` 删除、`{topicSummary}` → `{lastConversation}`、`app.memory.user-memory-rounds` 配置项删除、V2 Roadmap 更新。

**AGENTS.md — 5 处变更**：CoachState 通道数 7→6、`MemoryService` → `LearningProfileService`、`UserMemory` → `UserLearningProfile`、仓库列表更新、记忆注入段完全重写、删除 "Topic Memory is a direct write"。

**CONTEXT.md — 15 处变更**：重写 "Memory and Long-Term Context" 术语表——删除 Topic Memory 条目、重写 MemoryCueQueue（新增容量设计理由和 LRU 存活周期说明）、更新 MemoryContent 字段列表、更新 Memory Injection 定义、更新关系描述、更新示例对话。MemoryCueQueue 设计意图文档化：
- capacity = topK + 1 的 LRU 缓冲理由
- 回退锚点提示至少存活 1 轮后通过 FIFO 自然淘汰
- 排序规则：FIFO + 去重，非分数排序

**docs/architecture.md — 22 处变更**：决策日志新增一行（MemoryCueQueue LRU 淘汰设计）、CoachState schema 更新、每轮对话流程重写、MemoryService → LearningProfileService 更新、数据模型图 UserMemory → UserLearningProfile、枚举定义 MemoryType → LearningType。

### 实施顺序

按 TDD 原则，6 个阶段：

1. **底层数据结构**：LearningType 重命名、UserLearningProfile 实体、LearningProfileService、ReportResult 去 topicSummary、MemoryContent 重构、MemoryCueRepository 新查询
2. **逻辑与控制流**：CoachState 通道清理、SessionService 简化、TurnProcessor 重写、ConversationAgent 新分支、conversation-system.txt 模板、SessionComplete/CoachMessageHandler 依赖更新
3. **配置与提示词**：删除 userMemoryRounds、修改 standup description.txt、删除 memory-topic.txt
4. **前端**：删除报告弹窗 Topic Summary 区块
5. **单元测试**：16 个测试文件先行修改
6. **E2E 测试**：4 个测试文件 + 3 个资源文件更新

**总计影响范围**：~19 个源文件 + ~16 个测试文件 + ~6 个资源文件 + 4 个文档文件。
