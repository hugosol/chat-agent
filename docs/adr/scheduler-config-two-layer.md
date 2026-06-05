# ADR: Two-Layer FSRS Configuration (FsrsParameters + UserPreferences)

**Date:** 2026-06-05
**Status:** Accepted

## Context

FSRS-6 has 7 categories of scheduling parameters: W[21] weights, desired retention, learning steps, relearning steps, maximum interval, fuzz toggle, and short-term stability behavior. The naive approach would store all of them in a single entity.

However, not all parameters have the same owner: W[21] is computed by the Optimizer from review history; learning steps are chosen by the Learner via UI. Storing them in one place conflates two distinct data lifecycles.

## Decision

**Split FSRS configuration into two layers:**

1. **FsrsParameters** (JPA entity, `fsrs_parameters` table) — system-managed: 21 DOUBLE columns (w0–w20) plus `enable_short_term`. Soft-linked to Learner via `userId` string. Created by `DataInitializer` with FSRS-6 defaults; overwritten by the Optimizer. The Learner never directly edits these.

2. **UserPreferences** (existing entity, extended) — user-configurable: `learningSteps`, `relearningSteps`, `desiredRetention`, `maximumInterval`, `enableFuzz`, `shuffleDueCards`. Stored as nullable columns; null values fall through to `FsrsSchedulerConfig.defaults()` at merge time.

At runtime, `ReviewService` reads both, merges them via `FsrsSchedulerConfig.merge()`, and passes a single config object to the `FsrsScheduler`. This keeps the Scheduler unaware of persistence concerns — it only sees the merged runtime config.

## Alternatives Considered

### Single entity (all in UserPreferences)
- **Pro**: simpler schema, fewer joins/queries
- **Con**: W[21] has 21 columns — mixing them with preference columns makes the entity unwieldy
- **Con**: conflates system-computed data (optimizer output) with user-chosen settings — no clear boundary for migrations or rollbacks
- **Con**: if the Optimizer wants to store historical parameter snapshots for A/B comparison, a single entity forces versioning into UserPreferences

### Single entity (all in a new FsrsConfig entity)
- **Pro**: clean separation from UserPreferences
- **Con**: UserPreferences already exists and carries per-user settings — splitting all config into a separate entity would require migrating existing `newCardDailyLimit`/`timezone` fields or maintaining two per-user config entities
- **Rejected because**: UserPreferences is the established home for per-user settings; adding FSRS user-configurable fields there is the least-surprising location for future developers

## Consequences

- `ReviewService.rateCard()` now reads two entities per request (FsrsParameters + UserPreferences). The overhead is negligible (two indexed queries per user).
- The merge logic in `FsrsSchedulerConfig.merge()` must handle null UserPreferences fields gracefully (null → use default from `FsrsSchedulerConfig.defaults()`).
- Adding a new user-configurable FSRS parameter means touching three places: UserPreferences entity, FsrsSchedulerConfig record, and the merge logic.
