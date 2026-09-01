# PRD: Restore MemoryCue Generation — 恢复双管线并行记忆生成

> Status: `ready-for-agent`
> Created: 2026-06-26

---

## Problem Statement

当前会话结束流水线（`SessionComplete`）中，MemoryCue 生成已被移除，仅保留 AssertionService 和 LearningProfileService。

这导致两个后果：

1. **RAG 检索退化。** MemoryCue 的 embedding store 是会话中检索历史记忆的唯一数据源（AssertionService 是 write-only，不喂检索端）。MemoryCue 生成停止后，不再有新的 COMPLETED 记录进入 embedding store。随着时间推移，RAG 检索命中率持续下降，最终完全失效。

2. **架构偏离设计意图。** ADR `dual-memory-system.md` 定义了 MemoryCue + UserLearningProfile 双轨并行，ADR `0012-assertion-extraction.md` 明确说 "V1 仅写入端，MemoryCue 检索端保持不变"。但代码实现中 MemoryCue 写入端被意外移除，与 ADR 矛盾。

恢复 MemoryCue 生成，使其与 AssertionService 在会话结束时并行运行，各自独立写入。

---

## Solution

在 `SessionComplete.complete()` 中重新引入 MemoryCue 生成管线，与现存的 AssertionService 和 LearningProfileService 并行执行。

同时重构编排逻辑，消除重复 LLM 调用——`detectSwitches` 和 `splitBySwitches` 从两个 Service 内部提升到 `SessionComplete`，共享一次调用结果。

---

## User Stories

1. As a **Learner**, I want the Agent to remember topics from my current conversation and reference them in future sessions, so that I don't have to re-explain context every time I practice.

2. As a **Learner**, I want the Agent to recall past MemoryCue topics during my conversation via RAG retrieval, so that I get natural follow-ups like "How did that login module work out?" without being prompted.

3. As a **Developer**, I want MemoryCue and Assertion pipelines to run independently in parallel at session end, so that a failure in one does not block the other.

4. As a **Developer**, I want `detectSwitches` called exactly once per session end (not twice), so that LLM costs are not doubled by the two parallel pipelines sharing the same upstream step.

5. As a **Developer**, I want AssertionService to pass only the current segment's messages to the LLM during topic extraction, so that each segment's output is scoped to its own content rather than the full conversation.

6. As a **Developer**, I want the `buildLabeledMessages` helper consolidated to a single location, so that future changes to message labeling format only happen in one place.

7. As a **Developer**, I want `MemoryCueStatus.FIRST_CALL_FAILED` removed from the codebase, so that dead enum values don't confuse future readers.

8. As a **QA Engineer**, I want the E2E MemoryCue test (`ChatAgentMemoryCueIT`) uncommented and passing, so that the restored pipeline has end-to-end coverage.

---

## Implementation Decisions

### Architecture: Dual parallel pipelines

Both MemoryCueService and AssertionService run in parallel at session end, orchestrated by SessionComplete. LearningProfileService continues to run alongside them.

Japanese mode (`JAPANESE_BUSINESS`) skips all three memory pipelines.

### Shared detectSwitches and splitBySwitches

`SessionComplete` calls `MemoryCueAgent.detectSwitches()` once, then performs `splitBySwitches` internally. The resulting `List<List<MessageData>> segments` is passed to both `MemoryCueService.generateCuesAsync()` and `AssertionService.generateAssertionsAsync()`.

This eliminates the duplicate LLM call (previously each Service called detectSwitches independently).

### SessionComplete as orchestrator

`SessionComplete` gains two new dependencies: `MemoryCueAgent` (for detectSwitches) and `MemoryCueService` (for cue generation). It becomes the single orchestration point:

```
SessionComplete.complete():
  detectSwitches (via MemoryCueAgent)
  → splitBySwitches (internal, shared helper)
  → memoryCueService.generateCuesAsync(..., segments)    ─┐
  → assertionService.generateAssertionsAsync(..., segments) ─┤ parallel fire-and-forget
  → learningProfileService.generateLearningProfileAsync(...)─┘
```

### Service method signature changes

- `MemoryCueService.generateCuesAsync(sessionId, userId, mode, segments)` — accepts pre-split segments; no longer calls detectSwitches internally.
- `AssertionService.generateAssertionsAsync(sessionId, userId, mode, segments)` — accepts segments.
- `AssertionService.extract(sessionId, userId, mode, segments, group, ctx)` — package-private, accepts segments.

Both Services remove their internal `detectSwitches`, `splitBySwitches`, and `buildLabeledMessages` methods.

### Per-segment LLM in AssertionService (bug fix)

`AssertionService.extract()` currently passes the **full conversation's** `buildLabeledMessages(messages)` to the LLM for topic extraction, despite iterating per-segment. This is incorrect — each segment's topic extraction should only see that segment's messages.

After fix: `buildLabeledMessages(segment)` is called per segment, scoping LLM context correctly.

### buildLabeledMessages consolidation

The `buildLabeledMessages` helper (duplicated in MemoryCueAgent and AssertionService) is temporarily placed in `SessionComplete` as a private method. Both Services receive already-split segments and no longer need it. A future refactor may extract it to a shared utility.

### Error handling: detectSwitches failure

When `detectSwitches` returns empty (LLM call SWALLOWed), `SessionComplete` logs a warning and treats the full conversation as a single segment. Both downstream Services handle single-segment input gracefully.

`MemoryCueStatus.FIRST_CALL_FAILED` is removed — no audit row is persisted for the shared call failure. The SWALLOW error strategy already implies "silent degradation, no audit needed."

### Enum cleanup

`MemoryCueStatus.FIRST_CALL_FAILED` is removed from the enum. A migration in `DataInitializer` updates any existing rows with this status to `SEGMENT_FAILED`:

```sql
UPDATE memory_cues SET status = 'SEGMENT_FAILED' WHERE status = 'FIRST_CALL_FAILED'
```

### MemoryCueAgent unchanged

`MemoryCueAgent` retains its two public methods — `detectSwitches(List<MessageData>, AgentMode, TaskContext)` and `generateCue(List<MessageData>, AgentMode, int, TaskContext)` — without modification. No new `detectAndSplit` method is added; the split logic lives in SessionComplete.

---

## Testing Decisions

### Test seam

The highest seam is `SessionComplete.complete()` — all downstream changes flow through this method. Unit tests mock MemoryCueAgent, MemoryCueService, AssertionService, LearningProfileService, and verify correct orchestration.

### What makes a good test

- Verify **external behavior**: that the correct services are called with correct arguments in correct modes.
- Verify **boundary conditions**: Japanese mode skips all memory pipelines; null userId skips all memory pipelines; detectSwitches failure degrades gracefully.
- Do NOT test internal implementation (e.g., exact segment contents, internal thread scheduling).

### Unit tests to create/modify

| Test class | Changes |
|------------|---------|
| `SessionCompleteTest` | Add `@Mock MemoryCueAgent` and `@Mock MemoryCueService`. Verify `memoryCueService.generateCuesAsync()` called with correct segments. Verify NOT called for Japanese mode and null userId. |
| `MemoryCueServiceTest` | `firstCallFailed_skipsIndexing` removed. Remaining 3 tests adapted to pass segments instead of raw messages + mock detectSwitches. |
| `AssertionServiceTest` | ~10 extract tests adapted to pass segments instead of raw messages + mock detectSwitches. Per-segment LLM scoping verified. |

### E2E tests to modify

| Test class | Changes |
|------------|---------|
| `ChatAgentMemoryCueIT` | Uncomment test body. Adapt to verify both MemoryCue and Assertion records exist after session end. |
| `ChatAgentAssertionIT` | `singleSessionGeneratesAssertions`: add verification that MemoryCue records are also created. `japaneseModeSkipsAssertions`: add verification that no MemoryCue records are created. |

### Prior art

- `SessionCompleteTest` already tests orchestration with mocked AssertionService and LearningProfileService — same pattern extended to MemoryCueService.
- `ChatAgentAssertionIT` already uses `Thread.sleep()` + repository queries to verify async pipeline output — same pattern for MemoryCue verification.
- `MemoryCueServiceTest` already tests per-segment generation with mocked Agent — same structure, adapted signatures.

---

## Out of Scope

- **RAG retrieval quality benchmarking.** Restoring MemoryCue generation ensures RAG has fresh data; measuring retrieval precision/recall is deferred.
- **MemoryCue removal.** ADR 0012 mentions "确认稳定后移除 MemoryCue" — this PRD explicitly restores it; removal is a separate future decision.
- **AssertionService feeding RAG retrieval.** Currently only MemoryCue feeds the RAG embedding store. Making Assertion results searchable is out of scope.
- **buildLabeledMessages permanent home.** Temporarily in SessionComplete; a follow-up may extract to a shared utility class.
- **Per-segment error recovery in AssertionService.** Fixed the LLM scoping bug, but broader error handling improvements (e.g., partial segment failure vs. full pipeline abort) are out of scope.

---

## Further Notes

### Related ADRs

- `dual-memory-system.md` (superseded) — original dual pipeline design. This PRD restores that design.
- `0012-assertion-extraction.md` — assertion extraction V1. States "V1 仅写入端，MemoryCue 检索端保持不变." This PRD aligns code with that statement.
- `rag-memory-retrieval.md` — RAG retrieval depends on MemoryCue embedding store being populated.

### CONTEXT.md updates

The relationships section currently states "Currently replaces MemoryCue generation in the write pipeline for non-Japanese modes." After this PRD, it should state that both MemoryCue and Assertion pipelines run in parallel, with MemoryCue feeding RAG retrieval and Assertion providing structured memory.
