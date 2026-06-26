Status: ready-for-agent

## Parent

[PRD: Memory Assertion Extraction](../PRD.md)

## What to build

Implement Phase 1 of the assertion pipeline — the Extractor. Given a completed conversation transcript, generate structured MemoryAssertion rows with independent vector embeddings.

**New `TaskName` enum values (4):**
- `EXTRACT_TOPICS` — input: `{groupName}`, `{groupDescription}`, `{messages}`; output: `List<String>` (JSON array of topic strings)
- `EXTRACT_STATE` — input: `{groupName}`, `{topic}`, `{messages}`; output: plain text (one-sentence state)
- `JUDGE_SAME` — input: `{newState}`, `{oldState}`; output: `YES` / `NO`
- `MERGE_ASSERTION` — input: `{stateA}`, `{stateB}`; output: merged state text

All four use `ErrorStrategy.THROW`.

**Prompt templates** — four files under `resources/prompts/assertion/`:
- `extract-topics.txt` — parameterized with `{groupName}`, `{groupDescription}`, `{messages}`. Instructs LLM to find "recurring concepts that appear across multiple turns" (avoiding "high frequency" which LLMs can't count). Output: JSON string array.
- `extract-state.txt` — parameterized with `{groupName}`, `{topic}`, `{messages}`. Constrained fill-in-the-blank: LLM answers "how does this topic manifest in the conversation?" — it does NOT decide what to extract.
- `judge-same.txt` — parameterized with `{newState}`, `{oldState}`. Binary YES/NO for semantic equivalence.
- `merge-assertion.txt` — parameterized with `{stateA}`, `{stateB}`. Merges two states into one coherent sentence. Does NOT output topic (topic comes from the newer assertion in code).

**`AssertionService`** — new `@Service`:
- Constructor receives: `TaskRunner`, `MemoryCueAgent` (reuse `detectSwitches()`), `MemoryAssertionRepository`, `AssertionGroupRepository`, assertion `EmbeddingService` (or raw `InMemoryEmbeddingStore`), `@Qualifier("llmRequestExecutor") ExecutorService`.
- Constructor registers all four new tasks with `TaskRunner`.
- `extract(sessionId, userId, mode, messages, group)` method:
  1. Call `memoryCueAgent.detectSwitches(messages, mode, ctx)` to get segment boundaries.
  2. Split messages into segments via the same `splitBySwitches` logic (extracted or duplicated from `MemoryCueService`).
  3. Per segment: Step1 LLM → `List<String> topics`. Per topic: Step2 LLM → state string.
  4. For each (topic, state) pair: `INSERT` into `memory_assertions` (enabled=true, group_id from param) → `indexAsync` state text to assertion embedding store.
  5. Per-step `log.info` with elapsed milliseconds.
- On any LLM failure: `THROW` immediately — no partial results, no fallback records. This is intentional: partial assertion state is more dangerous than missing assertions.

**Reuse, not duplicate** — the `detectSwitches()` call and `splitBySwitches()` helper are reused from `MemoryCueAgent`/`MemoryCueService`. If needed, extract `splitBySwitches` to a shared location rather than duplicating.

**Unit tests (`AssertionServiceTest`):**
- Normal flow: segment → topics → states → INSERT → index (mock TaskRunner responses, verify repository saves and embedding store adds)
- Empty conversation: detectSwitches returns empty → no LLM calls, no INSERT
- Single topic: Step1 returns 1 topic, Step2 returns 1 state, exactly 1 assertion saved
- LLM failure at any step: THROW propagates, subsequent steps never execute (verify no calls after failure point)
- Multiple segments: each segment's topics are processed independently

## Acceptance criteria

- [ ] Four `TaskName` values added to enum
- [ ] Four prompt templates exist and are loaded at construction time
- [ ] `AssertionService.extract()` produces `MemoryAssertion` rows with `enabled=true` and correct `group_id`, `session_id`, `user_id`, `mode`
- [ ] Each extracted assertion is indexed in the independent assertion embedding store (verify via search after extract)
- [ ] Log statements include per-step timing (detectSwitches, Step1, Step2, index) in milliseconds
- [ ] LLM failure at any step → exception propagates, no partial assertions persisted
- [ ] Empty conversation → no assertions, no errors
- [ ] Unit tests cover: normal flow, empty input, single topic, LLM failure at each step
- [ ] All existing tests continue to pass

## Blocked by

- [01-schema-foundation](01-schema-foundation.md) — needs JPA entities, repositories, and assertion embedding store bean
