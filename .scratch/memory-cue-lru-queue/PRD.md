# PRD: MemoryCueQueue — LRU 记忆上下文队列

**Status:** `ready-for-agent`

## Problem Statement

当前 MemoryCue RAG 检索每轮独立执行，存在两个问题：

1. **无跨轮记忆迁移**：每个 Turn 独立 search top-K，结果直接注入 System Prompt，上一轮的相关记忆到了下一轮可能被完全替换。用户在持续聊"Japan trip"时，系统在第 3 轮命中了 `Travel: Planning trip to Japan`，但第 4 轮 search 返回新的 top-2 时这条记忆就被丢弃了——而用户仍在聊同一话题。人类记忆不是这样工作的——连续相关话题的记忆会"停留"在脑海中。

2. **字符串拼接脆弱**：当前用 `", as well as, "` 拼接多条 MemoryCue，然后在 `ConversationAgent` 中又按这个分隔符 split 回来加时间标签。如果某条 summary 原文恰好包含这个字符串，就会产生错误的拆分。这是靠巧合工作的代码。

## Solution

引入 **MemoryCueQueue**——一个容量为 `topK + 1` 的 LRU 有序集合，保存在 CoachState 中跨 Turn 存活：

1. **首次 RAG 加载**（队列为空时）：search `topK + 1` 条结果，按相关性低→高 push 到队列。低相关性坐在 tail（易被驱逐），高相关性坐在 head（受保护）。

2. **后续 RAG 加载**（队列非空时）：search `topK` 条结果，按相关性低→高逐个处理——已存在的 refresh 位置到 head，新条目 push 到 head。队列满时自动驱逐 tail（最久未访问）。

3. **去重 + 刷新**：同一 `cueId` 再次命中时，不占用新槽位，但刷新到 head（"又想起了这件事"）。保证每条 cue 在队列中只占一个位置。

4. **编号列表注入**：注入 System Prompt 时按 tail→head（旧→新）顺序生成编号列表，每条自带时间标签（`[from yesterday]`），无需字符串拆分。

### 队列行为示例

```
Turn 2（首次 RAG）: search(topK+1=3) → [A(0.6), B(0.75), C(0.9)]
  队列: A → B → C (tail→head)

Turn 3: search(topK=2) → [B(0.7), D(0.85)]
  B refresh + D push → A 驱逐
  队列: C → B → D

Turn 4: search(topK=2) → [E(0.8), D(0.9)]
  D refresh + E push → C 驱逐
  队列: B → D → E
```

### System Prompt 注入格式

```
[Memory Cues]
1. [from 3 days ago] Sprint Planning: Discussed login module sprint plan
2. [from yesterday] Travel: Planning trip to Japan
3. [from just now] Work Standup: Discussed API refactoring
```

## User Stories

1. 作为一名英语学习者，当我在 Turn 2 首次触发记忆检索时，Agent 应该加载 3 条最相关的历史记忆（而非 2 条），以便在后续轮次中有"记忆余量"自然迁移。

2. 作为一名英语学习者，当我在连续几轮围绕同一话题聊天时（如 Japan trip），上一轮触发的那条记忆应该在后续轮次中继续保留，而不会因为新 search 返回不同的 top-2 就被直接丢弃。

3. 作为一名英语学习者，当我的当前对话同时涉及多个历史话题时（如先聊旅行再聊工作），最久未引用的话题记忆应该自然"淡出"，被最新最相关的话题取代。

4. 作为一名英语学习者，当某条记忆在连续两轮都被 RAG 命中时，它不应该在队列中出现两次，而是保持在队列中并刷新到"最近想起"的位置。

5. 作为一名英语学习者，当我聊到一个完全全新的话题（RAG search 返回 0 条）时，已有记忆不应该被清空——它们只是暂时不被引用，下次聊回相关话题时仍然有效。

6. 作为一名英语学习者，在注入 System Prompt 的记忆列表中，最近想起来的话题应该排在编号末尾（靠近当前对话），LLM 自然给予更高注意力权重。

7. 作为一名英语学习者，每条注入的记忆都应带有时间标签（如 `[from yesterday]`），让 LLM 理解这些记忆来自不同时间点。

8. 作为一名开发者，MemoryCueQueue 应与 CoachState 共存亡——Session Resume 后自动恢复，Session 结束自动销毁，无需额外管理。

9. 作为一名开发者，当两条不同的 MemoryCue 具有相同的余弦相似度分数时，应按创建时间降序作为 tie-break，保证排序稳定性和可重现性。

10. 作为一名开发者，`MemoryContent` DTO 应携带 `List<CueMatch> cueMatches` 而非拼接后的字符串 + timestamp 列表，避免字符串拆分带来的脆弱性。

11. 作为一名开发者，首次 RAG 加载使用 `topK+1` 还是 `topK` 应根据"队列是否为空"判断，而非依赖 `messageId`——中间可能有几轮 search 返回 0 条导致队列始终为空。

## Implementation Decisions

### 1. 新增深模块：`MemoryCueQueue`

自包含 LRU 逻辑的数据类，放在 `dto/` 包下。

**公开接口：**

- `MemoryCueQueue(int capacity)` — 构造，capacity = `topK + 1`
- `void push(List<CueMatch> newResults)` — 内部先按 score asc → createdAt desc 排序，然后逐个处理：已存在则 remove 旧位置后 add 到末尾（refresh），不存在则 add 到末尾，超容量时 remove(0)（驱逐 tail）。newResults 为空时无操作。
- `List<CueMatch> getEntries()` — 返回 tail→head 有序列表（不可变副本），供 `ConversationAgent` 迭代生成编号列表
- `boolean isEmpty()` — 队列是否为空

**内部状态：** `ArrayList<CueMatch>` — index 0 为 tail（最久未访问），last index 为 head（最近访问）。

**实现特性：**
- 实现 `java.io.Serializable`（支持 CoachState checkpoint 序列化）
- `CueMatch` 同步实现 `Serializable`
- `push()` 是原地 mutating（void 返回），CoachState 引用不变即可检测到

### 2. CueMatch 字段扩展

新增 `createdAt` 字段（`LocalDateTime`），用于：
- 分数相同时的 tie-break 排序
- 在 `ConversationAgent` 生成时间标签时使用

```java
public record CueMatch(
    String cueId,
    String topic,
    String summary,
    double score,
    LocalDateTime createdAt
) implements Serializable {}
```

### 3. MemoryContent DTO 调整

删除 `memoryCuesText` 和 `cueCreatedAts` 字段，新增 `cueMatches` 字段。

```java
public record MemoryContent(
    String topicSummary,
    String learningProfile,
    List<CueMatch> cueMatches,   // 替代 memoryCuesText + cueCreatedAts
    LocalDateTime topicCreatedAt
) {
    public boolean isEmpty();
}
```

### 4. ConversationAgent 新增格式化方法

新增私有方法 `formatMemoryCuesForPrompt(List<CueMatch> cues)`，负责将 MemoryCueQueue 的 entries 转换为编号列表文本（含时间标签）。不再依赖 `applyTimeLabelsToCues()` 的字符串拆分逻辑。

```java
private String formatMemoryCuesForPrompt(List<CueMatch> cues) {
    StringBuilder sb = new StringBuilder("[Memory Cues]\n");
    for (int i = 0; i < cues.size(); i++) {
        CueMatch cue = cues.get(i);
        String timeLabel = TimeLabel.computeLabel(cue.createdAt(), LocalDateTime.now());
        sb.append(i + 1).append(". [from ").append(timeLabel).append("] ")
          .append(cue.topic()).append(": ").append(cue.summary()).append("\n");
    }
    return sb.toString();
}
```

### 5. TurnProcessor 重构

`resolveMemoryContext()` 保持纯查询——读当前 queue + search 结果，计算新 MemoryContent，不写回。写回由调用方处理。

**首次/后续加载判断逻辑：**

| 条件 | search 的 topK | 说明 |
|------|---------------|------|
| 队列为空 | `topK + 1` | 首次 RAG 加载或队列在中间被清空后重新灌满 |
| 队列非空 | `topK` | 正常运行中 |

> 不基于 messageId 判断——中间可能有连续多轮 search 返回 0 条导致队列始终为空。

**流程：**

```
resolveMemoryContext():
  messageId ≤ userMemoryRounds 
    → MemoryContent(topicSummary, learningProfile, null, topicCreatedAt)
  
  messageId > userMemoryRounds:
    queue = sessionService.getMemoryCueQueue(sessionId)
    topK = queue.isEmpty() ? appProps.retrieval.topK + 1 : appProps.retrieval.topK
    results = embeddingService.search(userInput, mode, userId, topK, threshold)
    queue.push(results)  ← 原地 mutate
    → MemoryContent(null, null, queue.getEntries(), null)
```

### 6. CoachState 新增 Channel

```java
public static final String MEMORY_CUE_QUEUE = "memoryCueQueue";

// SCHEMA 中：
MEMORY_CUE_QUEUE, Channels.base(() -> null)
```

MemoryCueQueue 实例由 `SessionService.init()` 创建并放入 `initialState` map——`new MemoryCueQueue(topK + 1)`。SessionService 注入 `AppProperties`。

### 7. MemoryCueQueue 生命周期

- **创建**：`SessionService.init()` — 随 Practice session 启动
- **写入**：每轮 `TurnProcessor.processTurn()` 调用 `resolveMemoryContext()` → 内部 push
- **读取**：`ConversationAgent.buildSystemContent()` 从 `MemoryContent.cueMatches()` 读取
- **销毁**：`SessionService.remove()` — 随 Practice session 结束
- **恢复**：checkpoint snapshot/restore 自动通过 Java 序列化，无需额外代码

### 8. 配置（无新增项）

复用现有 `top-k` 配置，通过 `topK + 1` 推导首次加载数量和队列容量。

```yaml
app:
  memory:
    user-memory-rounds: 1
    retrieval:
      top-k: 2
      similarity-threshold: 0.5
```

### 9. 并发安全

同一 Practice session 的 Turn 严格串行（前端上一个 Turn 完成后才发下一个 `USER_INPUT`）。无需额外同步。

### 10. 文档更新

| 文档 | 操作 | 内容 |
|------|------|------|
| `CONTEXT.md` | 修改 | ① 新增 `MemoryCueQueue` 术语 —— LRU 有序集合，容量 topK+1，跨 Turn 存活于 CoachState，去重时刷新位置，注入时按 tail→head 编号列出<br>② 更新 `MemoryCue Retrieval` —— "top-2 matches" 改为 "通过 MemoryCueQueue 管理：首次加载 topK+1 条，后续 topK 条，LRU 去重驱逐"<br>③ 更新 `MemoryContent` 字段描述 |
| `docs/architecture.md` | 修改 | 5 处：决策 #36/#39、第四节生命周期描述、第八节 RAG 检索描述、第十节 DTO 列表（新增 MemoryCueQueue） |
| `AGENTS.md` | 修改 | 第 62 行："injecting top-2 matches" 改为 "injecting via MemoryCueQueue (capacity topK+1, LRU dedup, numbered list output)" |
| `docs/adr/memory-cue-lru-queue.md` | 新建 | ADR 记录 LRU 队列替代 flat top-K 的决策：理由（模拟人类记忆迁移）、替代方案（flat top-K vs LRU）、trade-off（复杂度 vs 连贯性） |

### 11. 实现顺序

```
Phase 1: CueMatch implements Serializable
       → MemoryCueQueue 类 + 11 个单元测试 → 实现
Phase 2: MemoryContent 改字段 + ConversationAgent.formatMemoryCuesForPrompt()
       → ConversationAgentTest 适配
Phase 3: CoachState + SessionService 整合 → TurnProcessor 重构
       → 4 个集成测试
Phase 4: 跑 mvn verify 验证 E2E 不回归
Phase 5: 文档更新（CONTEXT.md, architecture.md, AGENTS.md, ADR）
```

## Testing Decisions

### 测试原则

- TDD 开发：Red-Green-Refactor。MemoryCueQueue 的 11 个单元测试在实现之前完成。
- 只测试外部可观测行为（`push()` → `getEntries()` 的结果），不测试内部实现（不依赖 ArrayList 内部细节）。
- 集成测试用 Mock（参考现有 `TurnProcessorTest` 模式），不引入 WireMock。

### Phase 1: MemoryCueQueue 单元测试（11 个）

新建文件 `src/test/java/com/hugosol/webagent/dto/MemoryCueQueueTest.java`

| # | 测试名 | 已有队列 | push() 输入 | 期望 getEntries() | 验证点 |
|---|--------|---------|------------|-------------------|--------|
| T1 | `pushAllNew_toEmpty_sortedByScoreAsc` | `[]` | `[A(0.6), B(0.75), C(0.9)]` | `[A, B, C]` | 基本排序：按 score 升序排列 |
| T2 | `pushAllNew_toFull_evictsOldest` | `[A, B, C]` | `[D(0.7), E(0.85)]` | `[C, D, E]` | 驱逐最旧：A 和 B 被推出，C 幸存 |
| T3 | `pushMixed_refreshesExisting` | `[A, B, C]` | `[D(0.7), B(0.9)]` | `[C, D, B]` | 去重刷新：B 从中间 refresh 到 head；A 被驱逐 |
| T4 | `pushAllExisting_refreshOnly_noEviction` | `[A, B, C]` | `[A(0.6), C(0.9)]` | `[B, A, C]` | 全重复无驱逐：3 条都在，B 在 tail（未被 touch），C 在 head |
| T5 | `pushEmpty_unchanged` | `[A, B, C]` | `[]` | `[A, B, C]` | 空输入不变：队列完全不受影响 |
| T6 | `pushFewerThanTopK` | `[A, B, C]` | `[D(0.8)]` | `[B, C, D]` | 不足 topK：只 push 1 条，A 被驱逐 |
| T7 | `pushUnsortedInput_sortsInternally` | `[]` | `[Z(0.95), X(0.5), Y(0.7)]` | `[X, Y, Z]` | 内部排序：乱序输入，输出按 score 升序 |
| T8 | `pushEmpty_toEmpty_staysEmpty` | `[]` | `[]` | `[]`, `isEmpty()==true` | 空队列空输入：保持空 |
| T9 | `pushFourAllNew_capacityThree_evictsFirst` | `[]` | `[W(0.4), X(0.5), Y(0.6), Z(0.8)]` | `[X, Y, Z]` | 超容量 4 条：W 在循环中被驱逐 |
| T10 | `pushTwo_toFull_oneDup_oneNew` | `[A, B, C]` | `[B(0.7), D(0.85)]` | `[C, B, D]` | 混合去重 + 驱逐：B refresh，D new，A 驱逐 |
| T11 | `pushScoreTie_stableSort_byCreatedAtDesc` | `[]` | `[A(0.8, 5/28), B(0.8, 5/27), C(0.8, 5/29)]` | `[B(5/27), A(5/28), C(5/29)]` | 分数相同按 createdAt 降序（旧在前，新在后） |

### Phase 3: TurnProcessor 集成测试（4 个）

扩展现有文件 `src/test/java/com/hugosol/webagent/service/TurnProcessorTest.java`

| # | 测试名 | messageId | Mock 设置 | 验证点 |
|---|--------|-----------|----------|--------|
| IT1 | `messageIdOne_usesTopicMemory_notRagSearch` | 1 | topicMemory="travel", learningProfile="past tense" | `embeddingService.search()` never called；MemoryContent 的 topicSummary/learningProfile 非空 |
| IT2 | `messageIdTwo_firstRagLoad_searchesTopKPlusOne` | 2 | search 返回 `[A, B, C]`（3 条） | `embeddingService.search(... , 3, ...)` 被调用；MemoryContent.cueMatches 有 3 个条目 |
| IT3 | `messageIdTwo_firstRagLoad_emptyQueue_stillSearches` | 2 | search 返回 `[]` | search 调用但 MemoryContent.cueMatches 为空 |
| IT4 | `messageIdThree_subsequentRagLoad_searchesTopK` | 3 | 队列已有 `[A, B, C]`；search 返回 `[D, E]`（2 条） | `embeddingService.search(... , 2, ...)`；MemoryContent.cueMatches 为 `[C, D, E]` |

### 需要更新的现有测试

| 文件 | 变更 |
|------|------|
| `ConversationAgentTest.java` | 适配新 `MemoryContent(cueMatches)` 签名；验证编号列表格式 |
| `TurnProcessorTest.java` | 现有 `messageIdFive_callsRagSearch` 等测试适配新行为：首次/后续判断改为基于队列 isEmpty |
| `EmbeddingServiceTest.java` | 不需要变更（search 接口不变） |

### 参考已有测试模式

- `TurnProcessorTest.java` — Mockito + 回调 Stub 模式（`TurnProcessorTest` 的 `StubCallback`）
- `EmbeddingServiceTest.java` — 自定义 Stub 模式（`StubEmbeddingModel`）
- `ConversationAgentTest.java` — System Prompt 内容断言

## Out of Scope

- **前端 UI 变更**：Agent 侧的 MemoryCueQueue 和编号列表注入是纯后端行为，前端无感知。
- **旧 User Memory 系统替换**：User Memory（Topic Memory + Learning Profile）保持不变，仅在第一轮注入。
- **MemoryCueQueue 跨 Session 持久化**：队列生命周期 = Practice session 生命周期。Session 结束时销毁，下一段 Session 重新从空队列开始。
- **多用户并发**：单用户场景下 Turn 严格串行，不加锁。
- **应用配置项新增**：不新增 `application.yml` 配置项，复用 `top-k` 推导。

## Further Notes

- 当前 `user-memory-rounds: 1` 意味着第一轮用 User Memory，第二轮即开始 RAG MemoryCue 检索。首次 RAG 加载在 messageId=2 时发生。
- `CueMatch.createdAt` 字段已在当前代码中存在（由 `EmbeddingService.search()` 返回），无需修改 `EmbeddingService`。
- 删除 `ConversationAgent.applyTimeLabelsToCues()` 方法——由 `formatMemoryCuesForPrompt()` 替代。
- `MemoryContent` 删除两个字段后需同步删除对应的构造函数重载。
- 现有的 `conversation-system.txt` 模板保持不变——`{memoryCues}` 占位符依然存在，只是内容格式从 `", as well as, "` 拼接变为编号列表。
