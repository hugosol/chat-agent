# E2E Test Extension for Tag Consolidation

**Status:** `ready-for-agent`

## Parent

`.scratch/tag-consolidation/PRD.md` — MemoryCue Tag Consolidation & Limit

## What to build

Extend `EnglishCoachMemoryCueIT` to verify the tag consolidation pipeline end-to-end: after a session with topic switches completes, persisted MemoryCue tags reflect the canonical mapping produced by the consolidation LLM call.

Add WireMock stubs for the consolidation API call and a mock response file containing a canonical mapping. The test verifies that tags in the `memory_cues` table are replaced according to the mapping after session end.

Note: this extends the dedicated `EnglishCoachMemoryCueIT` only — the core regression test `EnglishCoachSessionIT` is not modified (consolidation is an async background task not observable via DOM waits).

### WireMock setup

The consolidation call goes through DeepSeek's API (mocked by WireMock). The stub matches on the POST body containing the `E2E_MARKER_TAG_CONSOLIDATION` marker from the test prompt override.

### Test flow

1. Start WORKPLACE_STANDUP session
2. Send messages designed to trigger topic switches (e.g., work → travel → pets)
3. End session, wait for report
4. Wait for async consolidation to complete (a few seconds in non-E2E mode, or inline if `app.memory.async: false`)
5. Query `memoryCueRepository.findBySessionId(sessionId)`
6. Assert: tags in persisted rows use canonical forms per the mock mapping (e.g., `"spaniel"` replaced by `"dog"`)

## Acceptance criteria

- [ ] New WireMock stub in `WireMockStubs.java`: matches POST body containing `E2E_MARKER_TAG_CONSOLIDATION`, returns `tag-consolidation-response.json`
- [ ] New `src/test/resources/wiremock/tag-consolidation-response.json`: mock consolidation response, e.g., `{"spaniel": "dog", "poodle": "dog", "job": "work"}`
- [ ] Test prompt `src/test/resources/prompts/tag-consolidation.txt` (created in #2) used by E2E profile to trigger the marker-based WireMock match
- [ ] `EnglishCoachMemoryCueIT.shouldConsolidateTagsAfterSessionEnd()`: end-to-end test verifying persisted tags reflect canonical mapping
- [ ] Test messages include subcategory tags that the mock mapping consolidates (e.g., conversation about spaniels → entry mock returns tags `["spaniel", "poodle", "pet"]`)
- [ ] WireMock scenario state machine extended to support the additional consolidation stub call (new state phase or parallel stub)
- [ ] Screenshot on failure captured via existing `@AfterEach` hook
- [ ] `mvn verify` passes the new test

## Blocked by

- #3 — MemoryCueService Consolidation Orchestration + Performance Instrumentation (needs the consolidation pipeline wired end-to-end)
