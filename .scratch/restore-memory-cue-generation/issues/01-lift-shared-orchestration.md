# Lift shared orchestration to SessionComplete

> Status: `ready-for-agent`

## Parent

[PRD: Restore MemoryCue Generation](../PRD.md)

## What to build

Hoist `detectSwitches`, `splitBySwitches`, and `buildLabeledMessages` from `AssertionService` / `MemoryCueAgent` / `MemoryCueService` into `SessionComplete`, so that the switch-detection LLM call runs exactly once per session end — not twice. Change `AssertionService` to accept pre-split segments instead of raw messages, removing its internal copies of all three helpers. Fix the per-segment LLM scoping bug where `AssertionService.extract()` incorrectly passes the full conversation's labeled messages to the LLM for every segment instead of only that segment's messages.

### Key design decisions

- `SessionComplete` gains a `MemoryCueAgent` dependency. It calls `detectSwitches(messages, mode, ctx)` once, then splits via `splitBySwitches` and builds labeled text via `buildLabeledMessages` — both as private methods.
- `AssertionService.generateAssertionsAsync` signature changes to `(sessionId, userId, mode, segments)` — receives pre-split `List<List<MessageData>>` segments.
- `AssertionService` removes its internal `memoryCueAgent.detectSwitches()` call, `splitBySwitches()`, and `buildLabeledMessages()`. The `extract()` method iterates segments directly.
- The per-segment LLM bug is fixed: inside the per-segment loop, `buildLabeledMessages(segment)` replaces `buildLabeledMessages(messages)` for both topic extraction and state extraction calls.
- When `detectSwitches` returns empty (SWALLOW), `SessionComplete` treats the full conversation as a single segment — same degradation behavior both downstream services already handle.

### Non-goals

- Do NOT wire `MemoryCueService` into `SessionComplete` yet. That's Slice 2.
- Do NOT touch `MemoryCueStatus.FIRST_CALL_FAILED` yet. That's Slice 2.
- Do NOT change `MemoryCueAgent` public API. It keeps `detectSwitches()` and `generateCue()` unchanged.

## Acceptance criteria

- [ ] `SessionComplete` constructor accepts `MemoryCueAgent`; `complete()` calls `detectSwitches` once, splits, and passes segments to `AssertionService`.
- [ ] `AssertionService.generateAssertionsAsync` accepts `List<List<MessageData>> segments` instead of `List<MessageData> messages`.
- [ ] `AssertionService` no longer imports or calls `MemoryCueAgent`; no `detectSwitches`, `splitBySwitches`, or `buildLabeledMessages` methods remain in `AssertionService`.
- [ ] Per-segment LLM calls in `AssertionService.extract()` use `buildLabeledMessages(segment)` — each segment's topic/state extraction only sees that segment's messages.
- [ ] `SessionCompleteTest` passes with new `@Mock MemoryCueAgent`; verifies `detectSwitches` called once and segments forwarded to `AssertionService`.
- [ ] `AssertionServiceTest` passes with tests adapted to pass segments directly instead of raw messages + mocked `detectSwitches`.
- [ ] `mvn test -Dtest="SessionCompleteTest,AssertionServiceTest"` — all green.

## Blocked by

None — can start immediately.
