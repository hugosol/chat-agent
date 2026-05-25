# Tag Consolidation — Agent & Prompt

**Status:** `ready-for-agent`

## Parent

`.scratch/tag-consolidation/PRD.md` — MemoryCue Tag Consolidation & Limit

## What to build

Add a tag consolidation capability to `MemoryCueAgent`: an LLM-driven pass that merges semantically equivalent tags into canonical forms. This slice covers only the Agent layer (prompt file + method + unit tests) — the Service layer orchestration is in #3.

### New prompt contract

Input: one line per tag in the format `tagname(count)`, aggregating across all sessions for the same Learner × AgentMode.

Rules encoded in prompt:
- Canonical tag MUST come from the existing tag list (no inventing new terms)
- Subcategory merges to parent (e.g., `"spaniel"` → `"dog"`)
- Higher-frequency tag preferred as canonical
- Unrelated tags kept separate
- Only tags needing change appear in output; unchanged tags omitted

Output: JSON object mapping `originalTag → canonicalTag`.

### New method

`MemoryCueAgent.consolidateTags(Map<String, Integer> frequencyMap) → Map<String, String>`: sends the frequency map to the LLM with the consolidation prompt, receives a mapping, parses the JSON response.

### Error handling

Exception during LLM call or JSON parse → logged + returns empty map (caller handles graceful degradation). LLM cost metrics are logged automatically via the existing `LoggableChatModel` wrapper on the sync `ChatLanguageModel` bean.

## Acceptance criteria

- [ ] New `src/main/resources/prompts/tag-consolidation.txt`: consolidation prompt with `{tagList}` placeholder, rules as specified above, output format as JSON mapping
- [ ] New test prompt `src/test/resources/prompts/tag-consolidation.txt`: simplified version with `E2E_MARKER_TAG_CONSOLIDATION` marker for WireMock matching (used in #4)
- [ ] `MemoryCueAgent.consolidateTags(Map<String, Integer>) → Map<String, String>`: formats frequency map as `tagname(count)` lines, replaces `{tagList}`, calls LLM, parses JSON
- [ ] `MemoryCueAgent` constructor loads `tag-consolidation.txt` via `PromptLoader`
- [ ] `MemoryCueAgentTest.consolidateTags_normalMapping`: LLM returns `{"spaniel": "dog", "poodle": "dog"}` → parsed correctly
- [ ] `MemoryCueAgentTest.consolidateTags_noChanges`: LLM returns `{}` → empty map
- [ ] `MemoryCueAgentTest.consolidateTags_randomTagUnchanged`: LLM returns only changed tags; unrelated tags absent → correct partial map
- [ ] `MemoryCueAgentTest.consolidateTags_llmError`: LLM throws or returns invalid JSON → empty map, log warning
- [ ] `MemoryCueAgentTest.consolidateTags_frequencyFormat`: verifies prompt contains `tagname(count)` formatted lines

## Blocked by

None — can start immediately
