# 会话结束时 MemoryCue 向量化（写入路径）

**Status:** `ready-for-agent`

## Parent

`.scratch/rag-memory-retrieval/PRD.md` — RAG-based MemoryCue Retrieval

## What to build

在会话结束时，每当 `MemoryCueService` 成功生成一条 COMPLETED 状态的 MemoryCue，立即异步调用 `EmbeddingService.indexAsync()` 将其向量化并存入 `InMemoryEmbeddingStore`。这是 RAG 管道的写入路径。

端到端行为：会话结束后，新生成的 MemoryCue 不仅写入 H2 的 `memory_cues` 表，还同时嵌入到向量存储中，为后续逐轮检索做好准备。索引过程异步进行，不阻塞会话结束流程。

## Acceptance criteria

### MemoryCueService 接入
- [ ] 注入 `EmbeddingService`
- [ ] 在每条 COMPLETED 状态的段 cue 保存后，调用 `embeddingService.indexAsync(cue.getId(), cue.getTopic(), cue.getSummary(), mode, userId)`
- [ ] `indexAsync()` 调用发生在 `memoryExecutor` 上由 `runAsync` 触发的段完成回调中（即与 `generateCue` 在同一个异步上下文中，避免额外线程切换）
- [ ] SEGMENT_FAILED 和 FIRST_CALL_FAILED 状态的 cue 不调用 `indexAsync()`

### 测试
- [ ] `MemoryCueServiceTest`：验证在无切分和存在切分两种情况下，每条 COMPLETED cue 都调用了 `embeddingService.indexAsync()`，且调用参数正确（cueId、topic、summary、mode、userId）
- [ ] `MemoryCueServiceTest`：删除所有标签合并相关的断言，替换为 `EmbeddingService.indexAsync()` 调用断言

### 构建验证
- [ ] `mvn compile` 通过
- [ ] `mvn test` 通过

## Blocked by

- `.scratch/rag-memory-retrieval/issues/01-remove-tags-subsystem.md`（共享 `MemoryCueService`，需在标签移除之后的代码基础上接入）
- `.scratch/rag-memory-retrieval/issues/02-rag-embedding-infrastructure.md`（需要 `EmbeddingService`）
