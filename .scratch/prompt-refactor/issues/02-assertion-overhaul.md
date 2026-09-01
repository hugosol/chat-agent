# Assertion overhaul — prompts, mode binding, dev-progress

- **Status**: `ready-for-agent`
- **Created**: 2026-06-29

## Parent

PRD: `.scratch/prompt-refactor/PRD.md`

## What to build

Overhaul the assertion subsystem end-to-end: restructure prompt files into per-task subdirectories with externalized few-shot examples, add mode-awareness to the assertion pipeline via a new `AssertionGroup.mode` column, and seed the `dev-progress` group for WORKPLACE_STANDUP conversations.

### Prompt restructure

Restructure `assertion/` prompts into subdirectories with separated `system.txt` and `examples.txt`:

```
assertion/
  extract-topics/{system.txt, examples.txt}
  extract-state/system.txt
  judge-same/{system.txt, examples.txt}
  merge-assertion/system.txt
```

Remove `---USER---` delimiter from all assertion prompt files. The `userTemplate` strings (previously the content after `---USER---`) become Java string literals in `AssertionService.registerTasks()`.

Few-shot examples move from inline `List<ChatMessage>` Java literals to human-readable `examples.txt` format parsed by `ExampleMsgFormatter.parseFewShot()`.

### Mode binding

Add `mode VARCHAR` column to `AssertionGroup` entity (nullable — JPA `ddl-auto=update` handles schema migration). Add `findByMode(String mode)` to `AssertionGroupRepository`.

`DataInitializer.initAssertionGroups()` seeds two groups:

| name | description | mode |
|------|-------------|------|
| `error-pattern` | Grammar and word choice error patterns recurring in the user's conversations | `WORKPLACE_STANDUP` |
| `dev-progress` | The user's daily development progress, tasks completed, blockers encountered, and technologies or tools mentioned | `WORKPLACE_STANDUP` |

Includes a backfill migration: any existing `error-pattern` row with null mode gets updated to `WORKPLACE_STANDUP`.

### Pipeline change

`AssertionService.generateAssertionsAsync()` replaces hardcoded `findByName("error-pattern")` with `findByMode(mode.name())`. Groups are processed serially: for each group, extract topics → extract states → then run manage (search→judge→merge). When `findByMode` returns empty list (e.g., DAILY_TALK mode), the pipeline completes immediately.

## Acceptance criteria

- [ ] All assertion prompt files load from new subdirectory paths without errors
- [ ] `examples.txt` files are correctly parsed by `parseFewShot()` into few-shot messages matching the original inline Java literals
- [ ] `AssertionGroup` entity has a `mode` column; JPA auto-creates it on startup
- [ ] `AssertionGroupRepository.findByMode("WORKPLACE_STANDUP")` returns both `error-pattern` and `dev-progress`
- [ ] `DataInitializer` seeds both groups with correct mode; backfills existing null-mode rows
- [ ] Starting a WORKPLACE_STANDUP session triggers assertion extraction for both groups
- [ ] Starting a DAILY_TALK session completes without errors (assertion pipeline silently skipped — no groups for that mode)
- [ ] Starting a JAPANESE_BUSINESS session skips assertion (existing behavior preserved — non-Japanese-only guard in `SessionComplete.complete()`)
- [ ] All existing assertion-related tests pass; new tests cover mode-aware group querying and dev-progress seeding
- [ ] Test prompt templates under `src/test/resources/prompts/assertion/` match the restructured layout

## Blocked by

- `01-example-msg-formatter` (depends on `ExampleMsgFormatter.toXml()` and `parseFewShot()`)
