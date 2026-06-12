# 02: Skip correction + memory for Japanese mode

**Status:** `ready-for-agent`

## Parent

PRD: `.scratch/japanese-business-mode/PRD.md`

## What to build

Add mode guards in `TurnProcessor` and `SessionComplete` so that Japanese mode sessions skip:
1. Structured correction analysis (`CorrectionAgent` + `CorrectionNode`)
2. MemoryCue generation at session end
3. LearningProfile generation at session end

Conversational correction still happens through prompt rules (inline rephrasing) — only the structured per-message correction pipeline is suppressed. Memory injection (`resolveMemoryContext()`) naturally returns empty `MemoryContent` for modes with no history — no code changes needed there.

### TurnProcessor — skip correction

In `TurnProcessor.processTurn()`, wrap the `startCorrection()` call in a mode guard. After resolving the mode from session state, skip the correction async future when mode is `JAPANESE_BUSINESS`. No `correctionFuture` is created, no `addPendingCorrection()` call, no `CORRECTION_RESULT` WebSocket message sent.

The guard pattern: `if (mode != AgentMode.JAPANESE_BUSINESS) { ... }` — additive, zero impact on existing modes.

### SessionComplete — skip MemoryCue + LearningProfile

In `SessionComplete.complete()`, wrap the two async calls in mode guards:
- `learningProfileService.generateLearningProfileAsync()` — skip for Japanese mode
- `memoryCueService.generateCuesAsync()` — skip for Japanese mode

Both guards use the same `if (mode != AgentMode.JAPANESE_BUSINESS)` pattern. The `generatedReport()` and `sessionStore.completeSession()` calls are NOT guarded — report generation and session persistence still work for Japanese mode.

### Files to modify

- `src/main/java/com/hugosol/chatagent/service/TurnProcessor.java`
- `src/main/java/com/hugosol/chatagent/service/SessionComplete.java`

### Files unchanged

- `CorrectionAgent.java`, `CorrectionNode.java`, `ErrorType.java` — no changes; just skipped at call site
- `MemoryCueService.java`, `LearningProfileService.java` — no changes; just skipped at call site
- `resolveMemoryContext()` — already handles empty history gracefully
- All E2E tests — continue to target WORKPLACE_STANDUP / DAILY_TALK, guards only affect JAPANESE_BUSINESS

## Acceptance criteria

- [ ] `TurnProcessor.processTurn()` does NOT call `startCorrection()` when mode is `JAPANESE_BUSINESS`
- [ ] `TurnProcessor.processTurn()` still calls `startCorrection()` for WORKPLACE_STANDUP and DAILY_TALK modes
- [ ] `SessionComplete.complete()` does NOT call `learningProfileService.generateLearningProfileAsync()` for JAPANESE_BUSINESS mode
- [ ] `SessionComplete.complete()` does NOT call `memoryCueService.generateCuesAsync()` for JAPANESE_BUSINESS mode
- [ ] `SessionComplete.complete()` still calls both memory services for WORKPLACE_STANDUP mode
- [ ] Report generation and session persistence still work for Japanese mode (not guarded)
- [ ] `resolveMemoryContext()` returns empty `MemoryContent` for Japanese mode without crashing
- [ ] `mvn test` passes — `TurnProcessorTest` has 1 new test (Japanese mode does not invoke correction, verified via `verify(correctionNode, never())`); `SessionCompleteTest` has 2 new tests (Japanese mode skips `generateLearningProfileAsync()` and `generateCuesAsync()`, verified via `verify(..., never())`); all existing tests unchanged
- [ ] `mvn verify` E2E tests pass — no regressions

## Blocked by

- `01-mode-foundation` — requires `AgentMode.JAPANESE_BUSINESS` enum value to exist
