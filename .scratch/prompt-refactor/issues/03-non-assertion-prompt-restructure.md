# Non-assertion agent prompt restructure

- **Status**: `ready-for-agent`
- **Created**: 2026-06-29

## Parent

PRD: `.scratch/prompt-refactor/PRD.md`

## What to build

Restructure prompt files for the four remaining non-assertion agents into per-task subdirectories with separated `system.txt`. Remove the `---USER---` delimiter convention from all of them, inlining `userTemplate` strings as Java literals in each agent's constructor.

### Prompt file restructure

Each task gets its own subdirectory with a `system.txt`:

```
prompts/
  correction/system.txt
  memory-profile/system.txt
  memory-cue/split/system.txt
  memory-cue/entry/system.txt
  report/system.txt
```

The content after `---USER---` in each file is removed from the file and becomes a Java string literal in the corresponding agent constructor.

### Agent class changes

Remove `USER_DELIMITER` constant and `split(USER_DELIMITER, 2)` from:

| Agent | Task | userTemplate (inlined) |
|-------|------|------------------------|
| `CorrectionAgent` | CORRECTION | `"User's utterance: {userInput}"` |
| `LearningAgent` | MERGE_LEARNING | `"{oldLearningProfile}\n{errorSummary}"` |
| `MemoryCueAgent` | CHAT_SWITCHES | `"{messages}"` |
| `MemoryCueAgent` | GENERATE_MEMORY_CUE | `"{segment}"` |
| `ReportAgent` | REPORT | `"{fullConversation}\n{allCorrections}"` |

`ReportAgent` also restructures its per-mode overrides — the `report.txt` files under `japanese_business/` and (if they exist) `workplace_standup/` and `daily_talk/` get the same `---USER---` removal treatment. If a per-mode `report.txt` doesn't exist, `PromptLoader.loadIfExists()` returns null and the default template is used — no change needed.

`PromptLoader` usage changes from flat filenames to subdirectory paths (e.g., `load("correction.txt")` → `load("correction/system.txt")`).

## Acceptance criteria

- [ ] All five prompt files load from new subdirectory paths without errors
- [ ] `USER_DELIMITER` constant and `split(USER_DELIMITER, 2)` are removed from `CorrectionAgent`, `LearningAgent`, `MemoryCueAgent`, and `ReportAgent`
- [ ] No `USER_DELIMITER` or `---USER---` string remains anywhere in the codebase (grep confirms zero matches)
- [ ] Each agent's `userTemplate` is a Java string literal matching the original content after `---USER---`
- [ ] `ReportAgent` per-mode overrides (e.g., `japanese_business/report.txt`) still load correctly
- [ ] All existing agent unit tests pass (`CorrectionAgentTest`, `LearningAgentTest`, `MemoryCueAgentTest`, `ReportAgentTest`)
- [ ] Test prompt templates under `src/test/resources/prompts/` match the restructured layout (5 files moved, content trimmed of `---USER---` sections)
- [ ] `PromptLoaderTest` path strings are updated to match new layout
- [ ] Full build passes: `mvn test` and `mvn verify` (E2E)

## Blocked by

- `01-example-msg-formatter` (MemoryCueAgent already uses `ExampleMsgFormatter.toXml()` from Slice 1; this slice only changes prompt paths and removes `USER_DELIMITER`)
