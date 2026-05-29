# ADR: MemoryCueQueue — LRU 记忆上下文队列

**Date:** 2026-05-29
**Status:** Accepted

## Context

MemoryCue RAG 检索每轮独立执行 flat top-K search，结果直接注入 System Prompt。每一轮的结果与前一轮无关联。这导致两个问题：

1. **无跨轮记忆迁移**：用户持续聊同一话题时（如 Japan trip），第 N 轮命中的那条 `Travel: Planning trip to Japan` 在第 N+1 轮可能被新返回的 top-2 完全替换——即使用户仍在聊旅行。人类记忆不是这样工作的——连续相关话题的记忆会"停留"。

2. **字符串注入脆弱**：多条 MemoryCue 用 `", as well as, "` 拼接后注入 prompt，再加时间标签时又按此分隔符 split 回来。如果某条 summary 原文包含相同字符串，解析就错了。

## Decision

引入 **MemoryCueQueue**——容量为 `topK + 1` 的 LRU 有序集合，保存在 CoachState 中跨 Turn 存活：

- **数据结构**：`ArrayList<CueMatch>` 模拟 LRU，index 0 为 tail（最久未访问），last index 为 head（最近访问）
- **首次加载**：队列为空时 search `topK + 1` 条，按相关性低→高 push（低相关先被驱逐）
- **后续加载**：队列非空时 search `topK` 条，去重 refresh + 新条目 push
- **去重**：相同 `cueId` 不占新槽位，但刷新到 head
- **驱逐**：容量满时 remove(0)，逐出最久未访问的 tail
- **注入格式**：按 tail→head（旧→新）生成编号列表，每条自带时间标签，不再使用字符串拼接/拆分

### 队列行为示意

```
Turn 2（空队列）: search(3) → [A(0.6), B(0.75), C(0.9)]
  队列: A → B → C

Turn 3: search(2) → [B(0.7), D(0.85)]
  B refresh + D push → A 驱逐
  队列: C → B → D

Turn 4: search(2) → [E(0.8), D(0.9)]
  D refresh + E push → C 驱逐
  队列: B → D → E
```

### System Prompt 输出

```
[Memory Cues]
1. [from 3 days ago] Sprint Planning: Discussed login module sprint plan
2. [from yesterday] Travel: Planning trip to Japan
3. [from just now] Work Standup: Discussed API refactoring
```

## Alternatives Considered

### Flat top-K 每轮独立注入（原方案）

- **优点**：简单，无状态
- **缺点**：无记忆迁移，上一轮的高质量命中在下一轮被丢弃；字符串拼接/拆分脆弱

### LRU 队列（选定方案）

- **优点**：模拟人类记忆的"连续相关话题被反复想起"行为；编号列表天然隔离时间标签；去重避免重复信息
- **缺点**：增加一个有状态数据结构；需要序列化支持 checkpoint

### FIFO 队列

- **优点**：简单
- **缺点**：已存在的条目再次命中时不能刷新位置——同一 cue 可能被连续逐出又推入，浪费容量

## Consequences

### Positive

- 跨轮记忆连贯性：连续聊同一话题时，相关 MemoryCue 会"停留"在队列中
- 注入格式干净：编号列表 + 时间标签，无字符串拆分脆弱性
- LRU 自然遗忘：最久未被引用的话题自动淡出
- 无配置新增：复用 `top-k` 推导首次加载数量和容量

### Negative

- 增加有状态组件（需序列化以支持 checkpoint）
- 队列行为在首次加载和后续加载时使用不同 topK，需要理解性成本
- 分数相同时需 tie-break 保证排序稳定性

## Configuration

无需新增配置项。复用现有：

```yaml
app:
  memory:
    retrieval:
      top-k: 2              # 后续加载数量
      # 首次加载: top-k + 1 = 3
      # 队列容量: top-k + 1 = 3
```
