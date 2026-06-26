# Assertion Extraction — 结构化断言存储替代 Blob 摘要

MemoryCue 的 summary 字段是覆盖多个独立事实的自由文本 blob，导致无法字段级去重/更新。我们决定引入 MemoryAssertion 作为新的记忆存储单元，将每条独立事实存为独立行，配合 embedding 语义检索 + LLM 驱动的合并管线实现结构化收束。V1 仅写入端，MemoryCue 检索端保持不变。

## Considered Alternatives

1. **检索时去重（不改存储）。** 在 MemoryCueQueue 层面按 topic 相似度只保留最新 Cue。丢弃旧信息（"曾经卡住过"这个历史丢失），且无法表达状态变更。
2. **生成时合并（不改存储）。** 每个新 Cue 生成前先检索旧 Cue，让 LLM 产出"增量更新"文本。每次生成多一次 LLM 检索调用，但粒度问题未解决。
3. **检索时 LLM 合并。** 多条 Cue 注入前先跑一次 LLM 合并。每轮对话多一次 LLM 调用，延迟增加。
4. **断言提取（选定）。** 从存储层改变粒度。代价是约 15 次 LLM 调用/会话（vs MemoryCue 约 4 次），收益是真正的结构化去重/更新/演化追踪。

## Consequences

- 新表 `memory_assertion`、`assertion_group`、`assertion_lineage` 与 MemoryCue 并存，确认稳定后移除 MemoryCue
- Manager 串行化避免竞态（两条新断言同时合并同一条旧断言导致冗余）
- 无递归合并 — 接受首次 Manager 可能产出一对冗余断言，下次收束
- Topic 仅作统计锚点和显式标签，不做 embedding 输入（只有 state 文本参与向量化）
- ErrorStrategy 从 MemoryCue 的 SWALLOW 改为 THROW — 断言管线更复杂，部分失败比完全跳过更危险
