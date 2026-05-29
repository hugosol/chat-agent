# MemoryCueQueue TDD — 核心数据结构和单元测试

**Status:** `ready-for-agent`

## Parent

[PRD: MemoryCueQueue — LRU 记忆上下文队列](../PRD.md)

## What to build

创建 `MemoryCueQueue` 类 —— 一个容量为 `topK + 1` 的 LRU 有序集合，用于跨 Turn 管理记忆上下文的生命周期。同时让 `CueMatch` 实现 `Serializable` 以支持 CoachState checkpoint 序列化。

MemoryCueQueue 的公开接口：

- `MemoryCueQueue(int capacity)` — 构造
- `void push(List<CueMatch> newResults)` — 内部先按 score asc → createdAt desc 排序，然后逐个处理：已存在同 cueId 则 remove 旧位置后 add 到末尾（refresh），不存在则 add 到末尾；超容量时 remove(0)（驱逐 tail）。空输入无操作。
- `List<CueMatch> getEntries()` — 返回 tail→head 有序列表（不可变副本）
- `boolean isEmpty()` — 队列是否为空

内部状态：`ArrayList<CueMatch>` — index 0 为 tail（最久未访问），last index 为 head（最近访问）。`push()` 是原地 mutating（void 返回），CoachState 通过同一引用检测变更。

`CueMatch` 已有全部 5 个字段（`cueId`, `topic`, `summary`, `score`, `createdAt`），只需添加 `implements Serializable`。

本阶段 Queue 尚未接入业务代码，是纯数据结构模块。

TDD 策略：11 个单元测试先于实现完成（Red-Green-Refactor）。只测试外部可观测行为（`push()` → `getEntries()` 的结果），不依赖内部实现细节。

## Acceptance criteria

- [ ] `CueMatch` 实现 `java.io.Serializable`
- [ ] `MemoryCueQueue` 实现 `java.io.Serializable`，4 个公开方法完整
- [ ] `push()` 的行为：内部排序（score asc → createdAt desc）、去重（同 cueId refresh 到 head）、驱逐（超容量 remove tail）、空输入无操作
- [ ] `getEntries()` 返回 tail→head 有序不可变副本
- [ ] 11 个单元测试全部通过（详见 PRD Phase 1 测试列表 T1-T11），覆盖：基本排序、满队列驱逐、去重刷新、纯重复无驱逐、空输入不变、不足 topK、乱序输入排序、空队列空输入、超容量 4 条、混合去重驱逐、分数 tie-break
- [ ] 新建测试文件 `src/test/java/com/hugosol/webagent/dto/MemoryCueQueueTest.java`
- [ ] `mvn test` 通过

## Blocked by

None — can start immediately
