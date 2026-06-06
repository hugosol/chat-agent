# ADR: Mode-Scoped Topic Memory Isolation

> ⚠️ **已过时（2026-06）**：Topic Memory 已被 MemoryCue 体系取代。MemoryCue 在生成阶段就携带 `mode` 字段实现模式隔离，RAG 检索时通过 `userId × AgentMode` 双重过滤。本文档保留作为历史决策记录。

**Status:** Accepted (superseded)

**Date:** 2026-05-24

## Context

With the addition of `AgentMode.DAILY_TALK`, the English Coach now supports two conversation modes (Workplace Standup + Daily Talk). Topic Memory — the 500-character summary of conversation topics discussed across sessions — was previously shared across all Practice sessions regardless of mode.

This created a problem: work-related topics from standup sessions would "pollute" the casual chat context of Daily Talk sessions, and vice versa. For example, a Learner practicing workplace English about sprint planning would see those topics injected into their Daily Talk session with Chris, breaking the casual atmosphere.

Learning Profile, on the other hand, represents the Learner's English skill level (grammar weaknesses, strengths, improvement trends) — this should remain cross-mode since the same Learner has the same skill level regardless of mode.

## Decision

Add a nullable `mode` field (`AgentMode` enum) to the `UserMemory` entity:

- `TOPIC_SUMMARY` records: `mode` = current AgentMode (mode-scoped isolation)
- `LEARNING_PROFILE` records: `mode` = null (cross-mode shared)

The `UserMemoryRepository` query changes from `findTopByUserIdAndTypeOrderByVersionDesc` to `findTopByUserIdAndTypeAndModeOrderByVersionDesc`. When `mode` is null, Spring Data JPA generates `WHERE mode IS NULL`; when non-null, `WHERE mode = ?`.

`MemoryService` public methods gain a `mode` parameter. The callers (`SessionService.init`, `CoachMessageHandler.onEndSession`) pass the current session's AgentMode for TOPIC_SUMMARY and null for LEARNING_PROFILE.

## Consequences

**Positive:**
- Different modes have independent topic memory — work and casual topics don't cross-contaminate
- Learning Profile remains cross-mode, accurately reflecting the Learner's overall English skill
- No new `MemoryType` enum values needed (no `TOPIC_SUMMARY_DAILY_TALK`, etc.)
- Semantic clarity: `mode = null` means "shared", `mode = X` means "scoped to X"

**Negative:**
- More `UserMemory` rows overall (N modes × 2 TOPIC_SUMMARY versions each, vs 1 shared × 2 versions)
- Repository query gains a third parameter, requiring test mocks to be updated
- Memory merge logic is mode-agnostic (unchanged), but callers must correctly pass mode

**Risks mitigated:**
- Confusing work topics appearing in casual conversations
- Casual topics distracting from workplace English practice
