# Design Rationale — 设计理念与深层机制

> 本文档记录代码中无法通过注释直接体现的设计决策——需要阅读完整实现逻辑才能理解的"为什么"。编码规范见 [conventions.md](conventions.md)，架构决策见 [architecture.md](architecture.md)。

---

## 一、记忆注入：MemoryCueQueue 的双轮存活机制

### 问题

系统需要在每轮对话中向 Agent 注入历史记忆，但不能一次性塞入所有相关记忆（会撑爆 token 窗口），也不能每轮都换一批全新的记忆（Agent 会丢失上下文连贯性）。

### 方案

`MemoryCueQueue` 是一个有容量的 LRU 有序集合，容量为 **`topK + 1`**（不是 `topK`）。多出来的 `+1` 槽位是**迁移缓冲区**——确保旧记忆在队列中至少存活两轮对话。

```
Round 1: queue 为空 → search topK+1=4 条，全部入队
         ┌───┬───┬───┬───┐
         │ A │ B │ C │ D │  (4 entries, capacity=4)
         └───┴───┴───┴───┘

Round 2: 用户提到新话题 → search topK=3 条新结果 (E,F,G)
         ┌───┬───┬───┬───┐
         │ A │ E │ F │ G │  (A 存活，因为首次多载了+1)
         └───┴───┴───┴───┘

Round 3: search topK=3 条新结果 (H,I,J)
         ┌───┬───┬───┬───┐
         │ H │ I │ J │ G │  (A 被驱逐，G 存活——每一批至少存活两轮)
         └───┴───┴───┴───┘
```

**关键细节：**

- 队列按插入顺序（FIFO），**不按相关度排序**。Agent 看到的是"记忆流"而非"搜索结果页"——编号列表按 tail→head（旧→新）排列。
- 同 cueId 的条目不会重复：push 时先 `removeIf` 旧条目再 `add` 新条目（刷新到队尾）。
- 队列跨 Turn 存活于 `ChatState` 中，由 `SessionService.getMemoryCueQueue(sessionId)` 管理。

### 两阶段加载策略

| 加载阶段 | 检索数量 | 原因 |
|---------|---------|------|
| 首次加载（队列空） | `topK + 1` | 多载一条建立跨轮缓冲区 |
| 后续加载 | `topK` | 补充满额，旧条目自然淘汰 |

### 为什么不是纯 LRU？

纯 LRU（最近访问排到队头）会导致 Agent 在不同话题间切换时记忆震荡。FIFO + 去重刷新（访问时只去重不改变顺序）提供了稳定的记忆流，让 Agent 感知到"刚才在聊什么→现在在聊什么"的时间线。

---

## 二、后备锚点：冷启动的对话钩子

### 问题

新会话的第一轮对话（RAG 检索为空——没有历史记忆）或话题完全切换时，Agent 没有任何上下文可以自然地延续对话。

### 方案

当 RAG 返回零结果且 `messageId == 1` 时，系统从 H2 加载最近一次已完成会话的最后一条 MemoryCue 作为**后备锚点**：

```
if (messageId == 1 && queue.isEmpty()) {
    fallback = memoryCueRepository.findTopByUserIdAndModeAndStatusOrderByCreateTimeDesc(...)
    queue.push(fallback)  // 注入锚点
    lastConversationTimeLabel = TimeLabel.computeLabel(...)
}
```

**注入到 System Prompt 的效果：**

```
[Last conversation: about 3 hours ago]
[Memory Cues]
1. [from about 3 hours ago] OAuth debugging: user was stuck on token refresh...
```

Agent 收到 `{activeEngagement}` 指令后，会自然地说出：_"So, did you manage to fix that OAuth token issue from last time?"_

### 生命周期

后备锚点的 `score = 0.0`（非语义匹配），约**一轮后**被驱逐——下一轮 RAG 返回真实匹配时，锚点作为最旧条目从队首移除。它提供了一次性的对话启动钩子，同时不会污染长期队列。

---

## 三、共享 detectSwitches：消除重复 LLM 成本

### 问题

`AssertionService` 和 `MemoryCueService` 都需要将会话对话切分为话题段。如果各自调用 `MemoryCueAgent.detectSwitches()`，同一会话会产生**两次相同的 LLM 调用**——这是（按 token 计）会话结束后成本最高的单次调用。

### 方案

`SessionComplete` 调用一次 `detectSwitches`，然后在两个 Service 之间共享结果：

```java
// SessionComplete.complete()
List<Integer> switchPoints = memoryCueAgent.detectSwitches(messages, mode, ctx);
List<List<MessageData>> segments = splitBySwitches(messages, switchPoints);

assertionService.generateAssertionsAsync(..., segments);  // 共享
memoryCueService.generateCuesAsync(..., segments);         // 共享
```

### 设计原则

当一个操作的结果被多个下游模块需要时，在**编排层**执行一次，结果共享。各 Service **禁止**内部各自调用（会产生重复 LLM 成本）。这是深模块原则的一个实例：调用方不需要知道 `detectSwitches` 在两个 Service 之间共享——这是 `SessionComplete` 的内部事务。

---

## 四、Assertion Manager 为何串行处理

### 问题

Assertion Manager 阶段需要将新提取的断言与历史断言合并。如果并行处理，两条新断言可能同时尝试与**同一条**旧断言合并，产生冗余的合并结果。

### 方案

Manager 使用**串行循环**（`for` 循环，非 `parallelStream`）逐条处理新断言：

```java
// AssertionService.manage()
for (MemoryAssertion newAssertion : newAssertions) {
    // 1. Search top-3 similar old assertions
    // 2. Per-candidate Judge LLM → YES/NO
    // 3. For each YES: Merge LLM → soft-delete old → insert merged + lineage edge
}
```

串行化保证了每个 merge 操作的**原子性**：在第一条新断言完成 merge（包括写入 `assertion_lineage` 边）之前，第二条不会开始搜索。如果 Manager 是并行的，两条相似的断言（例如来自同一会话中不同话题段的"过去时"和"过去时疑问形式"）可能同时匹配到同一条历史断言，并各自生成一个合并版本——产生重复。

### 权衡

串行化带来了性能成本（N 条断言需要 N 轮搜索→判断→合并）。V1 中单会话通常仅有 3–8 条新断言，这个代价是可以接受的。如果未来扩展到多 group，可能需要引入**搜索前分配所有权**的机制（先分配每条新断言"负责"哪些旧断言，再并行处理）。

---

## 五、为什么是 1 节点图（而非原始设计的 5 节点）

### 原始设计

```
START → conversation → correction → merge → report → memory → END
```

### 实际问题

流式对话（token-by-token 推送到 WebSocket）**不适合** StateGraph 的同步节点模型：
- LangGraph 节点是同步函数：`Map<String,Object> → Map<String,Object>`
- 流式输出需要**逐步回调**（`onPartialResponse(String token)`），无法在一个同步节点内完成
- 强制塞进图节点会导致 WebSocket 推送延迟和复杂的状态管理

### 最终架构

```
┌────────────────────────────────────────────────┐
│ Service 层 (TurnProcessor)                     │
│  • ConversationAgent.generateStream()          │  ← 流式对话 + 记忆注入
│  • 并行调用 graph.stream()  → CorrectionNode   │  ← 纠错
│  • 回调管理、token 计数、null guard             │
├────────────────────────────────────────────────┤
│ LangGraph 层                                   │
│  START → correction → END (1 node)             │  ← 状态容器 + Checkpoint
│  ChatState 6 个通道                             │
└────────────────────────────────────────────────┘
```

图仅保留其核心功能：**状态容器**（跨 Turn 保持 messages、corrections、mode、userId）和 **Checkpoint**（页面刷新恢复）。其他所有功能（流式对话、同步 Agent、记忆注入、会话结束管线）统一由 Service 层管理。

---

## 六、日语模式跳过全部记忆和纠错管线

### 问题

日语模式（JAPANESE_BUSINESS）的定位是商务日语练习，与英语模式有本质差异：
- Correction 的五类错误（GRAMMAR、CHINGLISH 等）对日语不适用
- MemoryCue 和 Assertion 的 prompt 是英文写的，注入日语内容会产生语义混乱
- LearningProfile 的合并逻辑基于英语错误类型

### 方案

在 `TurnProcessor` 和 `SessionComplete` 中用简单的 guard 跳过：

```java
// TurnProcessor.processTurn()
if (mode == AgentMode.JAPANESE_BUSINESS) {
    // 跳过 Correction 节点
}

// TurnProcessor.resolveMemoryContext()
if (mode == AgentMode.JAPANESE_BUSINESS) {
    return new MemoryContent(null, null, List.of());  // 无记忆注入
}

// SessionComplete.complete()
if (mode != AgentMode.JAPANESE_BUSINESS) {
    // 跳过全部三条异步管线
}
```

### 为什么不是每个 Service 内部检查？

Guard 放在编排层（TurnProcessor / SessionComplete）而非各 Service 内部，使得每个 Service 保持**模式无关**——Service 不需要知道"日语模式"的存在。未来新增模式时，只需在编排层添加判断，Service 代码不变。

---

## 七、会话断开后的状态保留策略

### 问题

用户可能关闭浏览器标签（WebSocket 断开），然后重新打开页面。系统需要支持**无缝恢复**——用户看到完整的对话历史、纠错结果、token 用量。

### 方案

WebSocket 断开时**只移除 `sessionToWs` 映射**，**不调用 `removeSession()`**：

```
断开连接 → sessionToWs.remove(sessionId)  // 仅解绑
         → ChatState 保留在 activeStates  // 不释放
         → TokenTracker 保留               // 不重置

重新连接 → RESUME_SESSION → 校验 userId → sessionToWs.put(newWsId)
         → 从 MemorySaver Checkpoint 恢复 ChatState
         → 返回完整 messages + corrections + tokenUsage
```

### 为什么不在断连时释放？

- 如果释放，恢复时需要从 H2 重新加载所有消息、纠正、状态——这是一个重操作
- ChatState 在内存中保留了完整的运行时状态（包括 MemoryCueQueue、pending corrections）
- Checkpoint（MemorySaver）提供了页面刷新的恢复能力

### 权衡

服务器重启会丢失所有未结束的会话（MemorySaver 是内存实现）。这是 V1 的已知限制，V2 计划迁移到 Redis/Postgres checkpoint。

### 多标签协调

`sessionToWs` 是一对一映射。Tab B 执行 RESUME_SESSION → `put` 覆盖 Tab A 绑定。Tab A 重新激活 → Page Visibility API 触发自动 RESUME_SESSION → 全量重建 DOM。`sendSynced()` 保证异步线程的 WebSocket 写入安全。

---

## 八、`enabled` 软删除：保留演化链完整性

### 问题

`memory_assertions` 表需要支持去重合并——当两条断言合并时，旧的断言应该"消失"（不再出现在检索结果中），但其 ID 必须保留（因为 `assertion_lineage` 边引用了它）。

### 方案

使用 `enabled` 字段（BOOLEAN, DEFAULT true）实现软删除：

```sql
-- 合并时
UPDATE memory_assertions SET enabled = false WHERE id = ?;  -- 旧断言
INSERT INTO memory_assertions ... (enabled = true);         -- 合并结果
INSERT INTO assertion_lineage (parent_id, child_id, operation = 'MERGE');
```

- `enabled = false` 的断言从 embedding store 中移除（不参与 Manager 搜索）
- 递归 CTE 可以追溯完整演化链：`WITH RECURSIVE ... FROM assertion_lineage WHERE child_id = ?`
- 用户表同样使用 `enabled = false` 保留学习数据引用完整性

### 为什么不硬删除？

硬删除会破坏 `assertion_lineage` 的外键约束（或产生孤立边）。软删除保留了**数据谱系**——对于审计和调试记忆系统行为至关重要。

---

## 九、ConversationAgent 的启动时预加载

### 问题

每次会话开始时，ConversationAgent 需要加载 per-Mode 的 prompt 模板（`description.txt`、`rules.txt`、`conversation-system.txt`）。如果每次请求都从磁盘读取，会产生不必要的 I/O。

### 方案

在所有 `AgentMode.values()` 上使用 **`EnumMap`** 在构造时预加载所有模板：

```java
// ConversationAgent 构造时
for (AgentMode mode : AgentMode.values()) {
    modeDescriptions.put(mode, promptLoader.load(path + "/description.txt"));
    modeRules.put(mode, promptLoader.load(path + "/rules.txt"));
    modeTemplates.put(mode, promptLoader.loadIfExists(path + "/conversation-system.txt", fallback));
}

// 运行时 O(1) 查取
String description = modeDescriptions.get(mode);
```

**效果**：零 I/O 的 prompt 组装。每个请求仅做字符串替换（`{Description}`、`{Rules}` 等占位符），不涉及文件系统。

---

## 十、FSRS 的确定性 PRNG

### 问题

FSRS-6 算法使用 fuzz（随机扰动）防止卡片在相同日期聚集。如果使用 `java.util.Random`，不同实现（如 Python、JavaScript）会产生不同结果，导致跨平台复习数据无法互通。

### 方案

使用 **Alea PRNG**（Johannes Baagøe 算法）——一种确定性的伪随机数生成器，给定相同 seed 产生完全相同的序列。这是在 Anki 生态系统中用于跨实现一致性的同一算法。

### 为什么这很重要？

如果未来需要将复习数据导入 Anki（或与其他 FSRS 实现同步），确定性的 fuzz 保证了相同输入产生相同的调度结果——无需重新计算。

---

## 十一、纠错超时保护

### 问题

纠错是异步执行的（与流式对话并行）。如果纠错 LLM 调用挂起（网络问题），而用户已经结束会话，系统需要确保不会永远等待。

### 方案

`SessionService.waitForPendingCorrections(sessionId, 10_000)` ——在发送 `SESSION_REPORT` 之前等待所有 pending correction 完成，**超时 10 秒**：

```java
// ChatMessageHandler.onEndSession()
sessionService.waitForPendingCorrections(sessionId, 10_000);
// 超时后强制继续——报告不包含超时纠错的结果
```

同时，`pendingCorrections.remove(sessionId)` 取出并清空——即使部分 future 未完成，也不会泄漏到后续会话。

---

## 十二、降级报告哨兵值

### 问题

Report Agent 的 LLM 调用可能失败（API 错误、网络超时）。如果直接抛异常，用户看到的是空白页或错误消息，体验很差。

### 方案

返回**降级报告**——一个包含哨兵值 `fluencyScore = -1` 的有效 ReportResult 对象：

```java
// SessionComplete.complete()
try {
    reportResult = reportAgent.generate(...)
} catch (Exception e) {
    reportResult = new ReportResult(..., -1, ...)  // 降级
}
```

前端条件渲染：
- `fluencyScore == -1` → 隐藏评分行，显示"报告生成失败，请查看下方总结"
- 正常值 → 显示完整报告

### 为什么不返回 null？

null 需要每个调用方都检查，容易遗漏。降级对象携带完整结构，只是标记了一个字段为"不可用"——调用方无需改变处理逻辑。
