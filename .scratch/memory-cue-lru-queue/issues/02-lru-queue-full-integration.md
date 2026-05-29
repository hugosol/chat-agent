# MemoryCueQueue 全链路集成 — LRU 跨轮记忆迁移

**Status:** `ready-for-agent`

## Parent

[PRD: MemoryCueQueue — LRU 记忆上下文队列](../PRD.md)

## What to build

将 MemoryCueQueue 接入完整的 Turn 处理链路，实现跨轮记忆迁移。这是 MemoryCueQueue 功能的完整交付 —— 从 CoachState 生命周期到 System Prompt 注入的端到端行为。

**行为变化：**

- **首次 RAG 加载**（队列为空时）：search `topK + 1` 条（而非原来的 `topK` 条），作为"记忆余量"注入队列
- **后续 RAG 加载**（队列非空时）：search `topK` 条，命中已有的 cue 则 refresh 到 head（"又想起了"），新的 push 到 head，最久未访问的自动驱逐
- **空搜索保护**：RAG 返回 0 条时，队列保持原样不丢失已有记忆
- **注入格式**：编号列表 `[Memory Cues]\n1. [from yesterday] topic: summary\n2. [from just now] topic: summary\n...`，按 tail→head（旧→新）排列
- **字符串拼接消除**：不再使用 `", as well as, "` 拼接和拆分 MemoryCue 文本

**涉及的变更：**

| 层 | 变更 |
|---|------|
| CoachState | 新增 `MEMORY_CUE_QUEUE` channel，默认值 `null` |
| SessionService | `init()` 中创建 `new MemoryCueQueue(topK + 1)` 放入 initialState；新增 `getMemoryCueQueue(sessionId)` 访问器；注入 `AppProperties` |
| MemoryContent | 删除 `memoryCuesText` 和 `cueCreatedAts` 字段，新增 `List<CueMatch> cueMatches`；删除对应的构造函数重载；更新 `isEmpty()` |
| ConversationAgent | 删除 `applyTimeLabelsToCues()` 方法；新增 `formatMemoryCuesForPrompt(List<CueMatch> cues)` 生成编号列表；`buildSystemContent()` 从 `cueMatches` 读取替代 `memoryCuesText` |
| TurnProcessor | `resolveMemoryContext()`：队列为空时 search `topK + 1`，非空时 search `topK`；search 结果 push 到 queue 后 `getEntries()` 传入 MemoryContent；判断逻辑基于 `queue.isEmpty()` 而非 `messageId` |

**并发安全**：同一 Practice session 的 Turn 严格串行，无需额外同步。

## Acceptance criteria

- [ ] CoachState 包含 `MEMORY_CUE_QUEUE` channel，SessionService.init() 正确初始化
- [ ] Session Resume 后 MemoryCueQueue 通过 checkpoint 序列化自动恢复
- [ ] Session 结束时 MemoryCueQueue 随 `SessionService.remove()` 销毁
- [ ] messageId=1 时不调用 `embeddingService.search()`（仍使用 Topic Memory）
- [ ] messageId=2（首次 RAG，队列为空）时：`embeddingService.search(..., 3, ...)` 被调用；注入 3 条 MemoryCue
- [ ] messageId≥3（队列非空）时：`embeddingService.search(..., 2, ...)` 被调用
- [ ] RAG 返回空时队列不丢失已有记忆，队列内容保持不变
- [ ] 同一 cueId 再次命中时 refresh 到 head，队列中仅保留一个位置
- [ ] System Prompt 注入格式为编号列表（`[Memory Cues]\n1. [from ...] ...`），每条含时间标签
- [ ] `", as well as, "` 字符串拼接逻辑已完全移除
- [ ] `MemoryContent` 不再包含 `memoryCuesText` 和 `cueCreatedAts` 字段
- [ ] 新增 4 个 TurnProcessor 集成测试（详见 PRD Phase 3: IT1-IT4）
- [ ] `ConversationAgentTest` 适配新 MemoryContent 签名，验证编号列表格式
- [ ] `TurnProcessorTest` 现有测试适配新行为
- [ ] `mvn test` 通过
- [ ] `mvn verify` 通过（E2E 不回归）

## Blocked by

- [01-memory-cue-queue-tdd](./01-memory-cue-queue-tdd.md)
