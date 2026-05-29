# 02 — Rename Memory Domain Types

**Status:** `ready-for-agent`

## Parent

[PRD: Unified MemoryCue Retrieval — Remove Topic Memory](../PRD.md)

## What to build

Rename four core types and one method to clarify that the "User Memory" system now exclusively handles Learning Profiles. This is a pure rename — no behavior changes. After #01 removed TOPIC_SUMMARY, the remaining enum value `LEARNING_PROFILE` makes the old `MemoryType` name misleading. The old `UserMemory` entity name is also misleading now that topic memory has moved to the MemoryCue system.

Renames:

| Old | New |
|-----|-----|
| `MemoryType` enum | `LearningType` enum |
| `UserMemory` entity (table `user_memory`) | `UserLearningProfile` entity (table `user_learning_profiles`) |
| `UserMemoryRepository` interface | `UserLearningProfileRepository` interface |
| `MemoryService` class | `LearningProfileService` class |
| `generateMemoryAsync()` method | `generateLearningProfileAsync()` method |

Scope across layers:

- **Model**: Rename enum + entity. Update `@Table` annotation on the entity. The `type` column stays as-is (values were already reduced to only `LEARNING_PROFILE` in #01).
- **Repository**: Rename interface. Update method signatures from `MemoryType` to `LearningType`.
- **Service**: Rename `MemoryService` → `LearningProfileService`. Rename method. Update all internal method signatures.
- **Agent**: `LearningAgent` — update any `MemoryType` / `MemoryService` references in imports (unlikely, but verify).
- **WebSocket**: `CoachMessageHandler` — update injection from `MemoryService` → `LearningProfileService`.
- **Session**: `SessionComplete` — update injection and method call from `generateMemoryAsync()` to `generateLearningProfileAsync()`.
- **Graph**: `SessionService` — update any references (should be minimal after #01 removed topic memory loading; if `loadLatestContent` calls still exist, update them).
- **Config**: `AppProperties` — any references to `MemoryService` in config classes (unlikely, but verify).
- **Tests**: Rename `MemoryServiceTest.java` → `LearningProfileServiceTest.java`. Rename `UserMemoryRepositoryTest.java` → `UserLearningProfileRepositoryTest.java`. Update all enum references from `MemoryType.LEARNING_PROFILE` to `LearningType.LEARNING_PROFILE`.

## Acceptance criteria

- [ ] `LearningType` enum exists with single value `LEARNING_PROFILE`; `MemoryType` enum no longer exists
- [ ] `UserLearningProfile` entity exists with `@Table(name = "user_learning_profiles")`; `UserMemory` entity no longer exists
- [ ] `UserLearningProfileRepository` interface exists; `UserMemoryRepository` no longer exists
- [ ] `LearningProfileService` class exists with method `generateLearningProfileAsync()`; `MemoryService` no longer exists
- [ ] All injection points (`CoachMessageHandler`, `SessionComplete`, `SessionService`) reference `LearningProfileService`
- [ ] No file imports or references `MemoryType`, `UserMemory`, `UserMemoryRepository`, or `MemoryService`
- [ ] Test files renamed with updated enum references
- [ ] `mvn test` passes (unit tests only)

## Blocked by

- [#01 — Remove Topic Summary from Report Pipeline](./01-remove-topic-summary-from-report-pipeline.md)

## Comments

