Status: ready-for-agent

## Parent

[PRD: Memory Assertion Extraction](../PRD.md)

## What to build

Implement Phase 2 of the assertion pipeline — the Manager. After the Extractor produces new assertions, the Manager searches for semantically similar old assertions and merges them, recording lineage edges.

**`AssertionService.manage(sessionId, userId, mode, newAssertionIds)` method:**

For each new assertion (sequential — not parallel — to avoid merge race conditions):

1. **Search(top-3)** the assertion embedding store for semantically similar old assertions (same `userId` + `mode`, `enabled=true`, excluding the current assertion's own ID).
2. **Judge** each candidate (parallel): LLM call with `JUDGE_SAME` task — returns `YES` or `NO`.
3. Collect all `YES` candidates.
4. For each `YES` candidate (sequential):
   - **Merge** LLM call with `MERGE_ASSERTION` task — produces merged state text.
   - **Soft-delete** the old assertion: `enabled=false`.
   - **Remove** old assertion from assertion embedding store (`remove(oldId)`).
   - **Insert** new merged assertion: `enabled=true`, `group_id` from the new assertion, `topic` from the **newer** assertion (the one being processed), `state` from Merge LLM output.
   - **Index** the merged assertion into the assertion embedding store.
   - **Record lineage**: INSERT into `assertion_lineage` — `parent_id` = old assertion ID, `child_id` = new merged assertion ID, `operation='MERGE'`.

**Why sequential?** If two new assertions both match the same old assertion and both try to merge with it simultaneously, you get redundant merged assertions with overlapping information. Sequential processing ensures each old assertion is merged at most once per Manager run.

**Error handling** — all LLM steps use `ErrorStrategy.THROW`. If a Judge or Merge call fails, the entire Manager aborts. This prevents partial merges (e.g., old assertion disabled but no new merged assertion created).

**Unit tests (extend `AssertionServiceTest`):**
- Manager with no semantic matches: Search returns empty → no Judge calls, no Merge calls, no lineage edges
- Manager with one YES match: Search→Judge→Merge→old disabled→new inserted→lineage edge created
- Manager with multiple YES matches: each old assertion produces a separate merged assertion with its own lineage edge
- Manager with mixed YES/NO: only YES candidates trigger Merge
- Assertion removed from embedding store after soft-delete (verify search no longer returns it)
- Judge LLM failure: THROW propagates, no state mutation occurs
- Merge LLM failure: THROW propagates, old assertion still enabled (no partial state)

## Acceptance criteria

- [ ] `AssertionService.manage()` processes new assertions sequentially (not parallel)
- [ ] Search uses the independent assertion embedding store, filtered by `userId` + `mode`
- [ ] Judge returns YES → Merge executes; Judge returns NO → skip
- [ ] Merged old assertions have `enabled=false`
- [ ] Soft-deleted assertions are removed from the assertion embedding store
- [ ] Merged assertion `topic` comes from the newer assertion (not from LLM output)
- [ ] `assertion_lineage` edge recorded with `operation='MERGE'` for each parent→child pair
- [ ] LLM failure at any Manager step → exception propagates, no state mutation
- [ ] Unit tests cover: no matches, one match, multiple matches, mixed YES/NO, embed store removal, Judge failure, Merge failure
- [ ] All existing tests continue to pass

## Blocked by

- [02-extractor-pipeline](02-extractor-pipeline.md) — needs `AssertionService` and `JUDGE_SAME`/`MERGE_ASSERTION` task registrations
