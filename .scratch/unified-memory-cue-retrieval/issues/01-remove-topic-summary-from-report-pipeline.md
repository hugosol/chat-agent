# 01 — Remove Topic Summary from Report Pipeline

**Status:** `ready-for-agent`

## Parent

[PRD: Unified MemoryCue Retrieval — Remove Topic Memory](../PRD.md)

## What to build

Remove `topicSummary` from the entire report output pipeline — from the backend `ReportResult` model, through the WebSocket `ReportData` message, to the frontend report modal. Simultaneously, delete the `TOPIC_SUMMARY` value from the `MemoryType` enum and remove the TOPIC_SUMMARY direct-write branch from `MemoryService` (since it no longer has a data source). Also delete the `memory-topic.txt` prompt file (both main and test copies) and the `loadTopicCreatedAt()` method.

This is a pure removal: Topic Summary is no longer generated, transmitted, displayed, or persisted. The scope cuts through all layers end-to-end:

- **Model**: `ReportResult` record loses `topicSummary` field (5 → 4 fields). `MemoryType` enum loses `TOPIC_SUMMARY` value (2 → 1: only `LEARNING_PROFILE` remains).
- **Service**: `MemoryService.generateMemoryAsync()` drops the TOPIC_SUMMARY direct-write branch. `MemoryService.loadTopicCreatedAt()` deleted.
- **Agent**: `report.txt` JSON schema drops `topicSummary` field.
- **Protocol**: `ServerMessage.ReportData` loses `topicSummary` field (5 → 4 fields). All callers that construct `ReportData` drop one argument.
- **Persistence**: `SessionComplete.FALLBACK_REPORT` loses `topicSummary`. `EntityMapper.buildReport()` loses one parameter. `SessionDbStore.completeSession()` loses one argument.
- **Frontend**: `showReport()` in `app.js` removes the "Topic Summary" section from the report modal HTML.
- **Resources**: `src/main/resources/prompts/memory-topic.txt` deleted. `src/test/resources/prompts/memory-topic.txt` deleted.

## Acceptance criteria

- [ ] `ReportResult` record compiles with 4 fields (overallAssessment, errorSummary, fluencyScore, keyTakeaway) — no `topicSummary`
- [ ] `ServerMessage.ReportData` record compiles with 4 fields matching ReportResult
- [ ] `MemoryType` enum contains only `LEARNING_PROFILE` (TOPIC_SUMMARY removed)
- [ ] `MemoryService.generateMemoryAsync()` no longer writes TOPIC_SUMMARY records — only fires the LEARNING_PROFILE merge task
- [ ] `MemoryService.loadTopicCreatedAt()` method deleted
- [ ] `report.txt` prompt template no longer mentions `topicSummary` in its JSON schema
- [ ] Frontend report modal does not display a "Topic Summary" section
- [ ] `memory-topic.txt` files deleted from both `src/main/resources/prompts/` and `src/test/resources/prompts/`
- [ ] All existing unit tests compile and pass: `ReportAgentTest`, `CoachMessageHandlerTest`, `SessionCompleteTest`, `SessionDbStoreTest`, `EntityMapperTest`, `ProtocolDispatcherTest`, `MemoryServiceTest`
- [ ] `mvn test` passes (unit tests only — E2E handled in issue #04)

## Blocked by

None — can start immediately.

## Comments

