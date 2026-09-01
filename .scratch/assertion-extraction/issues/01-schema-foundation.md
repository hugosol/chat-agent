Status: ready-for-agent

## Parent

[PRD: Memory Assertion Extraction](../PRD.md)

## What to build

Create the database schema and embedding infrastructure for MemoryAssertion — 3 new tables plus an independent vector store.

**Schema — Three JPA entities extending `BaseEntity`:**

- **`AssertionGroup`** — classification dimension. Table `assertion_groups`. Columns: `id` (PK, auto-generated), `name` (unique), `description` (CLOB — used as `{groupDescription}` LLM prompt parameter). Seed row: `error-pattern` / `"user在对话中出现的语法/用词错误类型"`.

- **`MemoryAssertion`** — independent fact record. Table `memory_assertions`. Columns: `id` (PK, UUID), `group_id` (FK → `assertion_groups`), `session_id`, `user_id`, `mode` (enum string), `topic` (VARCHAR — short concept label from Step1 LLM), `state` (CLOB — one-sentence natural language assertion from Step2 LLM, also the input for embedding vectorization), `enabled` (BOOLEAN, DEFAULT true — soft-delete flag, set false when merged). Inherits `create_time`/`update_time` from `BaseEntity`.

- **`AssertionLineage`** — evolution DAG edge. Table `assertion_lineage`. Columns: `parent_id` (FK → `memory_assertions`), `child_id` (FK → `memory_assertions`), `operation` (VARCHAR, DEFAULT 'MERGE'). Composite PK on `(parent_id, child_id)`.

**Repository interfaces** — standard Spring Data JPA for all three entities. `MemoryAssertionRepository` needs: `findByEnabled(boolean)`, `findByUserIdAndMode(String userId, AgentMode mode)` (for userId+mode isolation). `AssertionLineageRepository` needs: recursive CTE query to trace full ancestry from a `child_id`.

**Independent Embedding Store** — a separate `InMemoryEmbeddingStore<TextSegment>` Spring bean (qualified name `assertionEmbeddingStore`), physically isolated from the MemoryCue embedding store. Persisted to disk as a separate JSON file (e.g., `./data/assertion-embedding-store.json`), with versioning and H2 rebuild fallback matching the existing `EmbeddingService` pattern.

**DataInitializer** — seed the `assertion_groups` table with one row on startup if empty (idempotent: check existence before insert).

## Acceptance criteria

- [ ] `AssertionGroup`, `MemoryAssertion`, `AssertionLineage` JPA entities compile and map to correct table/column names
- [ ] `MemoryAssertionRepository.findByEnabled(true)` excludes records with `enabled=false`
- [ ] `MemoryAssertionRepository` filters by `userId` + `mode` correctly
- [ ] `AssertionLineageRepository` recursive CTE returns full ancestor chain for a given `child_id`
- [ ] `DataInitializer` inserts `error-pattern` group exactly once (idempotent on restart)
- [ ] Assertion embedding store is a distinct bean from the MemoryCue embedding store
- [ ] All existing tests continue to pass (no tables conflict, no bean collision)

## Blocked by

None — can start immediately.
