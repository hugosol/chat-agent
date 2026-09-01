Status: ready-for-agent

## Parent

[PRD: Memory Assertion Extraction](../PRD.md)

## What to build

Wire the full Extractor → Manager pipeline into the session-end flow, replacing the MemoryCue write path while keeping both systems coexisting.

**Change to `SessionComplete.complete()`:**

Replace the `memoryCueService.generateCuesAsync(...)` call with `assertionService.generateAssertionsAsync(...)`. The new call signature matches the existing pattern:

```java
assertionService.generateAssertionsAsync(sessionId, userId, mode, messages)
```

This single method orchestrates both phases internally: Extractor (segment → topics → states → INSERT → index) then Manager (search → judge → merge → lineage).

**Mode guard** — the existing `mode != AgentMode.JAPANESE_BUSINESS` guard already protects the MemoryCue call. The assertion call goes inside the same guard block. Japanese Business mode continues to skip all post-session memory generation.

**Coexistence** — the `MemoryCueService` bean, `MemoryCueAgent`, `memory_cues` table, MemoryCue embedding store, and all related code are preserved untouched. Only the call site in `SessionComplete` changes. This allows both pipelines to be validated side-by-side in production before cutting over.

**`AssertionService.generateAssertionsAsync()`** — public entry point:
1. Resolve the V1 `AssertionGroup` (`error-pattern`) from the repository.
2. Call `extract(sessionId, userId, mode, messages, errorPatternGroup)` — Phase 1.
3. Collect newly inserted assertion IDs.
4. Call `manage(sessionId, userId, mode, newAssertionIds)` — Phase 2.
5. Log total pipeline elapsed time.
6. Return `CompletableFuture<Void>` (consistent with existing `generateCuesAsync` contract).

**Update `SessionCompleteTest`:**
- Replace `@Mock MemoryCueService` with `@Mock AssertionService`
- Update all existing test methods: verify `assertionService.generateAssertionsAsync()` is called (or not called) in the same scenarios
- Existing test scenarios preserved:
  - Normal completion → assertion pipeline fires
  - Null userId → skipped
  - Japanese mode → skipped
  - Report failure → still fires assertion pipeline (resilience)
  - Persist failure → still fires assertion pipeline

**No `MemoryCueService` tests modified** — they remain as regression tests for the coexisting MemoryCue pipeline.

## Acceptance criteria

- [ ] `SessionComplete.complete()` calls `assertionService.generateAssertionsAsync()` instead of `memoryCueService.generateCuesAsync()`
- [ ] `JAPANESE_BUSINESS` mode skips the assertion pipeline (unchanged guard behavior)
- [ ] Null `userId` skips the assertion pipeline
- [ ] `generateAssertionsAsync()` returns `CompletableFuture<Void>` matching existing contract
- [ ] `SessionCompleteTest` updated: mocks `AssertionService` instead of `MemoryCueService`, all 7 test scenarios pass
- [ ] `MemoryCueService` bean, table, and tests remain intact and compile/pass
- [ ] All existing tests continue to pass (no test regressions from the dependency swap)
- [ ] Build: `mvn compile` + `mvn test` pass

## Blocked by

- [03-manager-pipeline](03-manager-pipeline.md) — needs full `AssertionService` with both extract and manage methods
