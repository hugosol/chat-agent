# ADR: FsrsScheduler from Static Utility to Instance Class

**Date:** 2026-06-05
**Status:** Accepted

## Context

`FsrsScheduler` was originally a `public final class` with all `static` methods and hardcoded constants (W[21] array, learning step durations, desired retention, etc.). This worked for the MVP where every Learner used identical FSRS parameters.

With the introduction of per-Learner configurable parameters (learning steps, desired retention, fuzz toggle) and the Optimizer (per-user W[21] values), the static approach is no longer viable. Each `repeat()` call needs different parameter values depending on which Learner is reviewing.

## Decision

**Refactor `FsrsScheduler` from a static utility class to an instance class.** The constructor accepts a single `FsrsSchedulerConfig` record containing all 7 parameter categories. All current `static` methods (`repeat`, `initNewCard`, `createInitState`) become instance methods. No backward-compatible static overloads are retained.

## Alternatives Considered

### Keep static, add parameter arguments to each method
- `repeat(CardState, Rating, Instant, DoubleSupplier, FsrsSchedulerConfig)` — every caller must pass config
- **Pro**: minimal refactoring of the Scheduler class itself
- **Con**: callers that don't need custom config (`FlashcardService.createCardState()`, `CardBatchService`) must now acquire and pass a config they don't use
- **Con**: `createInitState()` doesn't depend on any config parameter — forcing a config argument is misleading
- **Rejected because**: the "pass config everywhere" pattern is noisy and breaks encapsulation

### Retain static overloads as compatibility layer
- Static methods delegate to a default-config instance internally
- **Pro**: existing callers don't change; only ReviewService uses the new instance API
- **Con**: two ways to call the same function — maintenance burden and confusion about which to use
- **Con**: the compatibility layer would need to stay indefinitely, defeating the purpose of the refactor
- **Rejected because**: churn is acceptable at this stage (3 callers + 12 test methods); better to have one clean API

### `createInitState()` stays static
- `createInitState()` produces the same result regardless of parameters (stability=2.5, difficulty=0.0, state=0)
- **Pro**: no unnecessary churn for callers that only use `createInitState` (FlashcardService, CardBatchService)
- **Con**: inconsistent — some methods static, some instance on the same class
- **Rejected because**: consistency matters more than avoiding 2-3 trivial call-site changes

## Consequences

- Every caller of `FsrsScheduler.repeat()`, `initNewCard()`, or `createInitState()` must change from `FsrsScheduler.method(...)` to `new FsrsScheduler(config).method(...)` or hold a Scheduler reference.
- `ReviewService` becomes responsible for constructing the Scheduler instance (reading FsrsParameters + UserPreferences, merging to FsrsSchedulerConfig).
- `FlashcardService.createCard()` and `CardBatchService.importCards()` — which only use `createInitState()` — now need a Scheduler instance. They can use `FsrsScheduler.withDefaults()` or receive it via constructor injection.
- All 12 `FsrsSchedulerTest` test methods must be updated to use instance calls.
