# Tag Limit in MemoryCue Entry Prompt

**Status:** `ready-for-agent`

## Parent

`.scratch/tag-consolidation/PRD.md` — MemoryCue Tag Consolidation & Limit

## What to build

Constrain the MemoryCue entry generation prompt to produce at most 5 tags per segment, selecting the most relevant ones. This is a prompt-only change — the backend does not truncate; if the LLM still exceeds 5, the data is accepted as-is.

The existing `memory-cue-entry.txt` prompt instructs the LLM to "Include as many as are relevant" for the `tags` field. Change this to enforce a 5-tag cap.

## Acceptance criteria

- [ ] `src/main/resources/prompts/memory-cue-entry.txt`: `tags` field description changed from "Include as many as are relevant" to "Include at most 5 tags — choose the ones most relevant to the text."
- [ ] Existing unit tests (`MemoryCueAgentTest`, `MemoryCueServiceTest`) pass unchanged (they do not assert on tag count)
- [ ] Existing E2E test `EnglishCoachMemoryCueIT` passes unchanged

## Blocked by

None — can start immediately
