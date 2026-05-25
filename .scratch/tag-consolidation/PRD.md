Status: ready-for-agent

# PRD: MemoryCue Tag Consolidation & Limit

## Problem Statement

A Learner accumulates many MemoryCue records across Practice sessions. Each MemoryCue carries a list of tags generated independently by the LLM per conversation segment. Over time, semantically identical concepts are expressed with different tag strings (e.g. `"dog"` vs `"spaniel"`, `"job"` vs `"work"`). This fragmentation makes future tag-based retrieval and analysis unreliable. Additionally, the LLM currently has no limit on the number of tags per MemoryCue entry, producing inflated and noisy tag lists.

## Solution

Introduce a two-part solution at session end:

1. **Tag limit**: Constrain the MemoryCue generation prompt to produce at most 5 tags per entry, selecting the most relevant ones.

2. **Tag consolidation**: After all MemoryCue entries for a Practice session are generated successfully, run an LLM-driven pass over **all historical MemoryCue tags for the same Learner × AgentMode** to merge semantically equivalent tags into canonical forms. Update affected database rows in place. Deduplicate each row's tags after merging.

Additionally, add performance instrumentation: log the elapsed time from END_SESSION message receipt to SESSION_REPORT sent (conversation close latency), and from MemoryCue generation dispatch to tag consolidation complete (background task duration).

## User Stories

1. As a Learner using WORKPLACE_STANDUP mode, I want my repeated conversations about "work" and "job" to register under a single tag, so that my topic memory is coherent across sessions.

2. As a Learner using DAILY_TALK mode, I want subcategories like "spaniel" and "poodle" to be consolidated under "dog", so that retrieval doesn't fragment my pet-related conversations.

3. As a product owner, I want tags stored in a consistent vocabulary, so that future tag-search and tag-filter features produce meaningful results.

4. As a system administrator, I want each MemoryCue entry to have at most 5 tags, so that tag quality remains high and LLM output is predictable.

5. As a developer, I want tag consolidation to be isolated by AgentMode, so that WORKPLACE_STANDUP tags never collide with DAILY_TALK tags.

6. As a developer, I want tag consolidation to be protected by a lock, so that two concurrent session-end tasks for the same Learner+Mode do not conflict.

7. As an operator, I want the system to log LLM cost metrics on every consolidation pass so that I can monitor tag maintenance overhead.

8. As a developer, I want to measure session-end latency (END_SESSION → Report delivered) and total background task time (trigger → tag consolidation complete), so that I can monitor system performance.

9. As a developer maintaining the codebase, I want the domain glossary and architecture docs to reflect the new tag consolidation behavior, so that future contributors understand the full MemoryCue data flow.

## Implementation Decisions

### Tag limit enforcement
Implemented entirely in the MemoryCue entry prompt. The backend does not truncate; if the LLM still exceeds 5 the data is accepted as-is.

### Tag consolidation data flow
1. After all segment generation tasks finish, query MemoryCue rows for the current session.
2. If any row has status SEGMENT_FAILED, skip consolidation (log WARN).
3. If detectSwitches itself failed (FIRST_CALL_FAILED), skip consolidation.
4. Otherwise: read all COMPLETED MemoryCue rows for the same userId + mode across all sessions.
5. Flatten tags across all rows, count frequencies into a map.
6. Send the frequency map to the LLM with a consolidation prompt; receive a mapping of originalTag → canonicalTag.
7. For each MemoryCue row, replace tags according to the mapping, deduplicate the resulting list, then save only rows whose tags changed.

### Completion signal
Segment generation tasks are collected into a list of CompletableFuture. A consolidation task is chained via allOf().thenRunAsync() — the outer detectSwitches task returns immediately without blocking a thread.

### Locking
A static Object lock protects the consolidation step. Only one consolidation execution per process at a time. This prevents race conditions when two sessions for the same user end concurrently.

### In-place update without versioning
Consolidated tags are written directly back to existing MemoryCue rows. No original-tags column, no revision counter, no audit trail. This is a deliberate trade-off for simplicity — tags are auxiliary metadata, not critical audit data.

### Deduplication after merge
After applying the canonical mapping, each MemoryCue's tag list is deduplicated. Two tags that both map to the same canonical are collapsed to one entry in that row's list.

### LLM prompt contract: tag consolidation
- Input: one line per tag in the format `tagname(count)`, aggregating across all sessions for the same Learner × Mode.
- Rules encoded in prompt: canonical tag MUST come from the existing tag list; subcategory merges to parent (e.g., `"spaniel"` → `"dog"`); higher-frequency tag preferred as canonical; unrelated tags kept separate.
- Output: JSON object mapping originalTag → canonicalTag. Only tags needing change appear; unchanged tags are omitted.

### LLM prompt contract: memory cue entry
The existing MemoryCue entry prompt's `tags` field description changes from "Include as many as are relevant" to "Include at most 5 tags — choose the ones most relevant to the text."

### Error tolerance
Any exception during consolidation is caught, logged at WARN level, and swallowed. Original MemoryCue data remains unchanged. LLM call failures do not block session completion.

### Performance instrumentation
- **Conversation close latency**: In the WebSocket handler's onEndSession, record start timestamp at method entry, log elapsed time after SESSION_REPORT is sent.
- **Background task duration**: In MemoryCueService's generateCuesAsync, record trigger timestamp at method entry, pass it to the consolidation task, log elapsed time after consolidation completes.

### Documentation updates
The following documentation files are updated to reflect the new behavior:

- **CONTEXT.md** (Memory and Long-Term Context section): Add **Tag Consolidation** term — the post-session LLM-driven process that merges semantically equivalent tags across all MemoryCue records for a given Learner+Mode into canonical forms, in-place.
- **docs/architecture.md** (Decision Log): Add decision entry documenting the tag consolidation approach (in-place UPDATE, static lock, partial-failure skip). Update the Session Lifecycle diagram to show consolidation as a post-segment step in the MemoryCue pipeline.
- **README.md** (5 AI Agents table): Update MemoryCueAgent description to mention the tag limit and consolidation. Update Project Structure to include the new `tag-consolidation.txt` prompt file.
- **AGENTS.md** (Dual memory system bullet): Add note about tag consolidation running post-segment with a lock, and the 5-tag limit enforced in the prompt.
- **docs/adr/**: Create a new ADR (`tag-consolidation-in-place.md`) documenting the decision to update MemoryCue tags in-place without versioning or an original-tags column. This satisfies all three ADR criteria: hard to reverse (old tag data is lost), surprising without context (User Memory has versioning, MemoryCue tags don't), and a real trade-off (simplicity vs audit trail).

### Schema changes
None. The existing `memory_cues` table's `tags` column (VARCHAR, JSON serialization via StringListConverter) is sufficient.

## Testing Decisions

### What makes a good test
Tests verify external behavior: tag limit enforcement, consolidation producing expected canonical results, partial-failure skip logic, deduplication after merge, and idempotency (re-running consolidation on already-consolidated data produces no changes). Avoid testing LLM internals — stub the LLM calls.

### Modules tested
- **Unit tests**: A new consolidation method in MemoryCueAgent for correct prompt formatting and JSON response parsing. MemoryCueService tests the async orchestration: completion signal, failure-skip logic, deduplication, and save-only-if-changed guard.
- **E2E tests**: `EnglishCoachMemoryCueIT` extended with WireMock-stubbed consolidation responses to verify persisted tags reflect the canonical mapping after session end.

### Prior art
- Unit: `MemoryCueAgentTest` (LLM response parsing), `MemoryCueServiceTest` (async dispatch with DirectExecutorService).
- E2E: `EnglishCoachMemoryCueIT` (full session → MemoryCue generation with WireMock).

## Out of Scope

- Tag-based search or retrieval (planned for v2).
- Cross-Mode tag consolidation (each mode is independent by design).
- Tag consolidation triggered outside of session-end (e.g., manual admin trigger, scheduled job).
- Any user-facing UI changes related to tag display.
- Migration or backfill of existing data.
- A `revision` counter or `original_tags` column preserving pre-consolidation tag data.
- Adding MemoryCue consolidation into the E2E regression tests in this PRD.

## Further Notes

- Consolidation is purely async and non-blocking for the Learner — it runs after the Report is already delivered.
- The static lock is a simple global lock; if per-user throughput becomes a bottleneck in production, it can be replaced with a per-user+mode ConcurrentHashMap without changing the consolidation logic.
- All consolidated tag changes are idempotent — re-running consolidation on already-mapped data should produce no new saves.
- The LLM call cost for consolidation is negligible: input is a short list of tags with counts, output is a small mapping JSON.
