# ADR: Tag Consolidation — In-Place Update Without Versioning

**Status:** Accepted

**Date:** 2026-05-26

## Context

MemoryCue records carry LLM-generated tags per conversation segment. Over multiple Practice sessions, semantically identical concepts accumulate with different tag strings (e.g., `"dog"` vs `"spaniel"`, `"job"` vs `"work"`). This fragmentation makes future tag-based retrieval unreliable.

A tag consolidation step was introduced: after all MemoryCue entries for a session are generated, an LLM pass merges equivalent tags into canonical forms across all historical rows for the same Learner+AgentMode.

The question: should consolidated tags be written in-place (overwriting the existing `tags` column), or versioned (inserting new rows with a revision counter, preserving the original tags)?

User Memory (the other memory system in this project) uses versioning — each Memory Merge inserts a new row with `version N+1`, old rows remain immutable.

## Decision

**Tags are updated in-place with no versioning.** The consolidated tag list replaces the original list directly in the existing `memory_cues` row. No `original_tags` column, no revision counter, no audit trail.

Specific design choices:
- `MemoryCue.tags` (`List<String>`, JSON serialized via `StringListConverter`) is overwritten with the consolidated tag list
- A `static final Object` lock serializes consolidation, preventing race conditions when two sessions for the same user end concurrently
- Consolidation skips when any segment has `SEGMENT_FAILED` or `FIRST_CALL_FAILED` status
- The operation is idempotent: re-running consolidation on already-mapped data produces no new saves (tags unchanged → skip save)
- The entry prompt enforces ≤5 tags per segment to limit noise

## Consequences

**Positive:**
- No schema migration — `memory_cues` table unchanged
- No `original_tags` column, no `revision` counter, no additional rows
- Simpler mental model: tags are "current canonical state," not "versioned history"
- Lower storage overhead per session

**Negative:**
- Old tag data is permanently lost after consolidation — cannot reconstruct pre-consolidation tag lists
- No ability to audit how tags evolved across sessions
- If the consolidation LLM makes a bad merge decision, there is no rollback mechanism

**Risks mitigated:**
- Tags are auxiliary metadata, not critical audit data — the topic and summary fields remain intact
- The static lock prevents concurrent consolidation races that could produce inconsistent tag states
- Idempotency ensures safe retry without data corruption
- Consolidation can be re-run on all historical data by simply triggering a new session end (the frequency map reads all rows)

## Alternatives considered

**Versioned inserts (like User Memory):** Rejected because tags are low-fidelity metadata, not high-value summaries. Versioning would multiply row count (N sessions × M segments × V versions) for marginal audit value. The simplicity trade-off favors in-place update.

**Separate `original_tags` column:** Rejected as half-measure — preserves data without providing useful retrieval patterns. Adds schema complexity without a clear use case.
