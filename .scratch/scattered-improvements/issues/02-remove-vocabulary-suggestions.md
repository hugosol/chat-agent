# Remove vocabularySuggestions from entire pipeline

**Status:** `ready-for-agent`

## Parent

`.scratch/scattered-improvements/PRD.md` — 零星优化

## What to build

Completely remove the `vocabularySuggestions` field from the entire codebase — no remaining references in any layer.

This field is redundant with `CorrectionAgent`'s `WORD_CHOICE` corrections, which already capture vocabulary improvements per-turn and are summarized in `errorSummary`.

## Files

### Prompts
| File | Change |
|------|--------|
| `prompts/report.txt` | Delete the `"vocabularySuggestions": "3-5 better words..."` line |
| `prompts/memory-profile.txt` | Delete the `- Vocabulary Suggestions: {vocabularySuggestions}` line |

### Java — main
| File | Change |
|------|--------|
| `agent/ReportAgent.java` | Remove `vocabularySuggestions` from `ReportResult` record; remove `getString(sections, "vocabularySuggestions")` from `parseReport()`; update empty fallback `new ReportResult(...)` to match new record signature |
| `agent/MemoryAgent.java` | `mergeProfile()` signature: remove third parameter `String vocabularySuggestions`; remove `.replace("{vocabularySuggestions}", vocabularySuggestions)` |
| `protocol/ServerMessage.java` | Remove `vocabularySuggestions` from `ReportData` record |
| `websocket/CoachMessageHandler.java` | Remove vocabularySuggestions argument from `new ServerMessage.ReportData(...)` construction |
| `service/MemoryService.java` | Remove `report.vocabularySuggestions()` argument from `memoryAgent.mergeProfile(...)` call |
| `service/EntityMapper.java` | Remove `sr.setVocabularySuggestions(report.vocabularySuggestions())` from `buildReport()` |
| `model/SessionReport.java` | Remove field `vocabularySuggestions`, `@Column` annotation, getter, setter |

### Java — test
| File | Change |
|------|--------|
| `agent/ReportAgentTest.java` | Remove `vocabularySuggestions` from mock JSON; remove `result.vocabularySuggestions()` assertions |
| `agent/MemoryAgentTest.java` | `mergeProfile` calls: remove third String argument |
| `service/MemoryServiceTest.java` | `mergeProfile` mock verifications: remove third `eq(anyString())` argument from all calls |

### Frontend
| File | Change |
|------|--------|
| `static/app.js` | In `showReport()`: delete the `Vocabulary Suggestions` DOM line |

### WireMock stubs
| File | Change |
|------|--------|
| `test/resources/wiremock/report.json` | Remove `vocabularySuggestions` from response JSON |
| `test/resources/wiremock/daily-report.json` | Remove `vocabularySuggestions` from response JSON |

## Acceptance criteria

- `mvn compile` succeeds (no broken references)
- `mvn test` passes (all unit tests updated)
- `mvn verify` passes (E2E wiremock stubs still match, no DOM assertions broken)
- `grep -r "vocabularySuggestions" src/` returns zero results
- Report modal in frontend shows 5 sections (Assessment, Topic, Score, Errors, Takeaway) instead of 6
- Learning Profile generation continues to work with only `errorSummary` (no vocabularySuggestions)
