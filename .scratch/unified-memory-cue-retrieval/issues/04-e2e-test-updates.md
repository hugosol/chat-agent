# 04 — E2E Test Updates

**Status:** `ready-for-agent`

## Parent

[PRD: Unified MemoryCue Retrieval — Remove Topic Memory](../PRD.md)

## What to build

Update the E2E test suite and WireMock stubs to match the new behavior after issues #01–#03. Topic Summary is gone from the entire pipeline, so E2E tests must stop asserting its existence and instead verify the correct new state.

### E2E test file changes

**EnglishCoachMemoryIT.java**:
- Remove H2 assertions that verify `TOPIC_SUMMARY` exists in `user_memory` / `user_learning_profiles` table
- Update `TOPIC_SUMMARY` references to `LEARNING_PROFILE` (or remove them entirely if they were only checking the topic path)
- After #03, round 1 uses RAG search which may hit or miss — WireMock stubs must either provide RAG hits or the test must verify the fallback anchor behavior
- Session 1 assertions: remove checks for TOPIC_SUMMARY rows; keep LEARNING_PROFILE version assertions
- Session 2 assertions: remove "Topic v2 content contains the same topic summary" check; keep LearningProfile v1/v2 differ check (LLM merge still works)

**DailyTalkIT.java**:
- Remove H2 assertions that verify `TOPIC_SUMMARY` exists with `mode=DAILY_TALK`
- Remove frontend DOM assertions that check for "Topic Summary" text in the report modal
- After #03, the same RAG vs fallback consideration applies
- Keep all other assertions: conversation flow, correction bubbles, sidebar items, session status, message counts, error records, LEARNING_PROFILE verification

### WireMock stub changes

**report.json** (workplace mode report mock):
- Delete the `"topicSummary"` key-value pair from the JSON response body
- Keep all other fields: `overallAssessment`, `errorSummary`, `fluencyScore`, `keyTakeaway`

**daily-report.json** (daily talk mode report mock):
- Delete the `"topicSummary"` key-value pair from the JSON response body
- Keep all other fields

### Resource file cleanup

**src/test/resources/prompts/memory-topic.txt**: Delete this file. The main copy was already deleted in #01. This test copy is no longer referenced by any WireMock stub or test.

## Acceptance criteria

- [ ] `EnglishCoachMemoryIT` passes — no TOPIC_SUMMARY assertions remain; LEARNING_PROFILE assertions still pass
- [ ] `DailyTalkIT` passes — no TOPIC_SUMMARY H2 assertions; no "Topic Summary" text search in the DOM; report modal still appears and other sections are intact
- [ ] `report.json` has no `topicSummary` field; parsed successfully by `ReportAgent` during tests
- [ ] `daily-report.json` has no `topicSummary` field; parsed successfully
- [ ] `src/test/resources/prompts/memory-topic.txt` does not exist
- [ ] `mvn verify` passes (all E2E tests green)

## Blocked by

- [#01 — Remove Topic Summary from Report Pipeline](./01-remove-topic-summary-from-report-pipeline.md)
- [#02 — Rename Memory Domain Types](./02-rename-memory-domain-types.md)
- [#03 — Unified MemoryCue Retrieval: RAG-First + Fallback Anchor](./03-unified-memorycue-rag-retrieval.md)

## Comments

