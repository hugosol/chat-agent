# Documentation Update — Tag Consolidation

**Status:** `ready-for-agent`

## Parent

`.scratch/tag-consolidation/PRD.md` — MemoryCue Tag Consolidation & Limit

## What to build

Update five documentation files to reflect the new tag consolidation behavior. No code changes — this slice ensures future contributors understand the full MemoryCue data flow including the new consolidation step.

### Files to update

1. **CONTEXT.md** — Memory and Long-Term Context section: add **Tag Consolidation** term definition after the existing MemoryCue Entry row. Definition: "The post-session LLM-driven process that merges semantically equivalent tags across all MemoryCue records for a given Learner+AgentMode into canonical forms, updating rows in-place." Also add a relationship line in the Relationships section: "A Practice session end triggers Memory Cue Split, then Memory Cue Entry generation per segment, then Tag Consolidation across all historical MemoryCue rows for the same Learner+Mode."

2. **docs/architecture.md**:
   - Decision Log table: add row #37 — Tag Consolidation: in-place UPDATE on `memory_cues.tags`, static lock serialization, partial-failure skip on SEGMENT_FAILED/FIRST_CALL_FAILED, idempotent re-run produces no new saves
   - Session Lifecycle diagram (the `[用户 End Session]` block): add a step showing consolidation as a post-segment step in the MemoryCue pipeline, after segment generation and before the diagram ends

3. **README.md**:
   - 5 AI Agents table: update MemoryCueAgent description to mention tag limit (≤5) and tag consolidation pass
   - Project Structure: add `tag-consolidation.txt` to the prompts listing under `src/main/resources/prompts/`

4. **AGENTS.md** — Dual memory system bullet: append "Tag consolidation runs post-segment with a static lock and a 5-tag limit enforced in the entry prompt. In-place UPDATE, no versioning."

5. **docs/adr/tag-consolidation-in-place.md** — New ADR documenting the decision to update MemoryCue tags in-place without versioning or an `original_tags` column:
   - Context: MemoryCue tags are LLM-generated and drift semantically over sessions; User Memory has versioning but MemoryCue tags don't
   - Decision: in-place UPDATE with deduplication, static lock serialization, idempotent re-run
   - Consequences: simple implementation, no schema migration needed, old tag data is lost (acceptable trade-off — tags are auxiliary metadata, not critical audit data)

## Acceptance criteria

- [ ] `CONTEXT.md` Memory section: "Tag Consolidation" term added after MemoryCue Entry row, with definition
- [ ] `CONTEXT.md` Relationships section: consolidation added to the session-end relationship line
- [ ] `docs/architecture.md` Decision Log: row #37 added with the decisions listed above
- [ ] `docs/architecture.md` Session Lifecycle diagram: consolidation step added to the end-session block
- [ ] `README.md` Agents table: MemoryCueAgent row updated to include tag limit and consolidation
- [ ] `README.md` Project Structure: `tag-consolidation.txt` added to prompts listing
- [ ] `AGENTS.md` Dual memory system bullet: tag consolidation + 5-tag limit note appended
- [ ] `docs/adr/tag-consolidation-in-place.md`: new ADR with Context, Decision, Consequences sections, covers the three ADR criteria (hard to reverse, surprising without context, real trade-off)

## Blocked by

- #3 — MemoryCueService Consolidation Orchestration + Performance Instrumentation (documents the implemented behavior)
