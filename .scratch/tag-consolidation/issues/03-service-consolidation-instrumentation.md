# MemoryCueService Consolidation Orchestration + Performance Instrumentation

**Status:** `ready-for-agent`

## Parent

`.scratch/tag-consolidation/PRD.md` — MemoryCue Tag Consolidation & Limit

## What to build

Wire the tag consolidation Agent (#2) into the async MemoryCue pipeline at session end. Refactor `MemoryCueService.generateCuesAsync()` to track segment completion and chain a post-segment consolidation step. Add performance instrumentation logging for both conversation close latency and background task duration.

### Service orchestration changes

The current implementation uses `executor.execute()` for segment tasks — fire-and-forget with no completion signal. Change to:

1. Collect segment tasks as `List<CompletableFuture<Void>>` via `CompletableFuture.runAsync()`
2. Chain consolidation via `CompletableFuture.allOf(futures).thenRunAsync(consolidationTask, executor)`
3. The outer `detectSwitches` task returns immediately — no blocking on main thread

### Consolidation task logic (inside the thenRunAsync callback)

1. Query this session's MemoryCue rows by `sessionId`
2. If any row has status `SEGMENT_FAILED` → log WARN, skip consolidation, return
3. If the outer task saved a `FIRST_CALL_FAILED` row → skip consolidation, return
4. Otherwise: query all COMPLETED MemoryCue rows for this `userId + mode` across all sessions
5. Flatten tags across all rows, count frequencies into `Map<String, Integer>`
6. Call `agent.consolidateTags(frequencyMap)` → `Map<String, String>` canonical mapping
7. For each MemoryCue row, replace tags according to the mapping, deduplicate the resulting list
8. Save only rows whose tags actually changed (skip save-if-unchanged)
9. Log LLM cost metrics (token usage) at INFO level via the consolidation call's response

### Locking

A `static final Object consolidationLock = new Object()` protects steps 1-9. Only one consolidation execution per process at a time. The lock is acquired inside `thenRunAsync` — it serializes consolidation without blocking the executor's other work (Memory Merge, Report, parallel segment generation).

### Performance instrumentation

Two log additions:
- **Conversation close latency** (`CoachMessageHandler.onEndSession`): record `System.currentTimeMillis()` at method entry, log `"Conversation close latency: {}ms"` after `SESSION_REPORT` is sent
- **Background task duration** (`MemoryCueService.generateCuesAsync`): record trigger timestamp at method entry, pass to the consolidation `thenRunAsync`, log `"Background task duration (MemoryCue + consolidation): {}ms"` after consolidation completes

### Error tolerance

Any exception during consolidation (LLM failure, DB error) is caught, logged at WARN level, and swallowed. Original MemoryCue data remains unchanged. Consolidation failure does not block session completion.

## Acceptance criteria

- [ ] `MemoryCueService.generateCuesAsync()`: segment tasks collected as `List<CompletableFuture<Void>>` instead of fire-and-forget `executor.execute()`
- [ ] `CompletableFuture.allOf(futures).thenRunAsync(consolidationTask, executor)` chains after all segments complete
- [ ] Outer `detectSwitches` task returns immediately — `generateCuesAsync()` does not block the caller
- [ ] Consolidation task: queries current session's cues → skips if any `SEGMENT_FAILED` found (log WARN)
- [ ] Consolidation task: skips if `FIRST_CALL_FAILED` row exists
- [ ] Consolidation task: queries all COMPLETED cues for `userId + mode`, builds frequency map, calls `agent.consolidateTags()`
- [ ] Tag replacement: applies canonical mapping to each row's tag list, deduplicates, saves only changed rows
- [ ] `static final Object` lock serializes consolidation steps 1-9
- [ ] Consolidation exception caught, logged at WARN, swallowed — original data unchanged
- [ ] `CoachMessageHandler.onEndSession()`: logs conversation close latency (method entry → after SESSION_REPORT sent)
- [ ] `MemoryCueService.generateCuesAsync()`: logs background task duration (trigger → consolidation complete)
- [ ] `MemoryCueServiceTest.consolidation_withMapping_replacesAndDeduplicates`: mock agent returns mapping, verify saved rows have replaced+deduped tags
- [ ] `MemoryCueServiceTest.consolidation_noChanges_skipsSave`: mock agent returns empty mapping, verify no save calls for unchanged rows
- [ ] `MemoryCueServiceTest.consolidation_segmentFailed_skips`: one segment has SEGMENT_FAILED → consolidation skipped, log WARN
- [ ] `MemoryCueServiceTest.consolidation_firstCallFailed_skips`: detectSwitches failed → consolidation skipped
- [ ] `MemoryCueServiceTest.consolidation_idempotent`: re-running consolidation on already-mapped data produces no new saves
- [ ] `MemoryCueServiceTest.consolidation_agentError_swallowed`: agent throws → caught, logged WARN, original data intact
- [ ] Existing `MemoryCueServiceTest` tests still pass (no-switch, with-switch, segment-fail, first-call-fail)
- [ ] `execute()` → `runAsync()` change: existing tests updated to use `CompletableFuture.allOf().join()` for test synchronization instead of `Thread.sleep()`

## Blocked by

- #2 — Tag Consolidation Agent & Prompt (needs `MemoryCueAgent.consolidateTags()`)
