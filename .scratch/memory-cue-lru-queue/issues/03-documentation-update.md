# 文档更新 — CONTEXT.md, architecture.md, AGENTS.md, ADR

**Status:** `ready-for-agent`

## Parent

[PRD: MemoryCueQueue — LRU 记忆上下文队列](../PRD.md)

## What to build

更新项目文档以反映 MemoryCueQueue 的新设计和术语。

**具体变更：**

| 文件 | 操作 | 内容 |
|------|------|------|
| `CONTEXT.md` | 修改 | ① 新增 `MemoryCueQueue` 术语：LRU 有序集合，容量 topK+1，跨 Turn 存活于 CoachState，去重时刷新位置，注入时按 tail→head 编号列出；② 更新 `MemoryCue Retrieval` 描述："top-2 matches" 改为 "通过 MemoryCueQueue 管理：首次加载 topK+1 条，后续 topK 条，LRU 去重驱逐"；③ 更新 `MemoryContent` 字段描述 |
| `docs/architecture.md` | 修改 | 5 处：决策 #36/#39、第四节生命周期描述、第八节 RAG 检索描述、第十节 DTO 列表（新增 MemoryCueQueue 条目） |
| `AGENTS.md` | 修改 | "injecting top-2 matches" 改为 "injecting via MemoryCueQueue (capacity topK+1, LRU dedup, numbered list output)" |
| `docs/adr/memory-cue-lru-queue.md` | 新建 | ADR 记录 LRU 队列替代 flat top-K 的决策：理由（模拟人类记忆迁移）、替代方案（flat top-K vs LRU）、trade-off（复杂度 vs 连贯性） |

## Acceptance criteria

- [ ] `CONTEXT.md` 包含 MemoryCueQueue 术语定义
- [ ] `CONTEXT.md` 中 MemoryCue Retrieval 描述已更新
- [ ] `docs/architecture.md` 所有相关决策描述已更新
- [ ] `AGENTS.md` 中 RAG 描述已更新
- [ ] `docs/adr/memory-cue-lru-queue.md` 已创建，内容覆盖决策理由、替代方案、trade-off

## Blocked by

- [02-lru-queue-full-integration](./02-lru-queue-full-integration.md)
