# Restore MemoryCueService to session-end pipeline + E2E coverage

> Status: `ready-for-agent`

## Parent

[PRD: Restore MemoryCue Generation](../PRD.md)

## What to build

Wire `MemoryCueService` back into `SessionComplete.complete()` so that MemoryCue generation runs in parallel with `AssertionService` at session end. Remove the dead `MemoryCueStatus.FIRST_CALL_FAILED` enum value and migrate existing rows. Adapt all unit and E2E tests to verify the restored pipeline delivers both MemoryCue and Assertion records.

### Key design decisions

- `SessionComplete` gains a `MemoryCueService` dependency. `complete()` calls `memoryCueService.generateCuesAsync(sessionId, userId, mode, segments)` — fire-and-forget, parallel with `assertionService.generateAssertionsAsync(...)`.
- `MemoryCueService.generateCuesAsync` signature changes to `(sessionId, userId, mode, segments)` — receives pre-split `List<List<MessageData>>` segments from the orchestrator. Internal `detectSwitches` and `splitBySwitches` calls are removed.
- Japanese mode (`JAPANESE_BUSINESS`) and null `userId` skip all three memory pipelines (`MemoryCueService`, `AssertionService`, `LearningProfileService`) — this guard already exists in `SessionComplete`, MemoryCueService just joins it.
- `MemoryCueStatus.FIRST_CALL_FAILED` is removed from the enum. A `DataInitializer` migration updates any existing `FIRST_CALL_FAILED` rows to `SEGMENT_FAILED`: `UPDATE memory_cues SET status = 'SEGMENT_FAILED' WHERE status = 'FIRST_CALL_FAILED'`.
- `detectSwitches` failure: `SessionComplete` already treats empty switch points as single-segment fallback. No `FIRST_CALL_FAILED` audit row is written — silent degradation, consistent with SWALLOW error strategy.

### Non-goals

- Do NOT change `MemoryCueAgent` public API.
- Do NOT benchmark RAG retrieval quality.
- Do NOT make Assertion results feed RAG retrieval.

## Acceptance criteria

- [ ] `SessionComplete` constructor accepts `MemoryCueService`; `complete()` calls `generateCuesAsync(sessionId, userId, mode, segments)` in parallel with `AssertionService`.
- [ ] `MemoryCueService.generateCuesAsync` accepts `List<List<MessageData>> segments`; no longer calls `detectSwitches` or `splitBySwitches` internally.
- [ ] `MemoryCueStatus` enum contains only `COMPLETED` and `SEGMENT_FAILED`.
- [ ] `DataInitializer` migrates existing `FIRST_CALL_FAILED` rows to `SEGMENT_FAILED` on startup.
- [ ] Japanese mode and null `userId` skip `MemoryCueService` (and `AssertionService` + `LearningProfileService`).
- [ ] `MemoryCueServiceTest`: `firstCallFailed_skipsIndexing` test removed. Remaining tests (`noSwitch_writesOneCompletedRecordAndIndexes`, `withSwitch_writesMultipleCompletedRecordsAndIndexesBoth`, `segmentGenerateFails_indexesOnlyCompleted`) adapted to pass segments directly.
- [ ] `SessionCompleteTest` extended: verifies `MemoryCueService.generateCuesAsync` called with correct segments; verifies NOT called for Japanese mode and null userId.
- [ ] `ChatAgentMemoryCueIT`: test body uncommented, adapted, and passing. Verifies MemoryCue + Assertion records both exist after session end.
- [ ] `ChatAgentAssertionIT.singleSessionGeneratesAssertions`: extended to also verify MemoryCue records exist.
- [ ] `ChatAgentAssertionIT.japaneseModeSkipsAssertions`: extended to also verify no MemoryCue records are created.
- [ ] `mvn test` — all unit tests green.
- [ ] `mvn verify -Dtest="ChatAgentMemoryCueIT,ChatAgentAssertionIT"` — both E2E tests green.

## Blocked by

- [01-lift-shared-orchestration](01-lift-shared-orchestration.md)
