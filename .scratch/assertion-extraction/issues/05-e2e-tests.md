Status: ready-for-agent

## Parent

[PRD: Memory Assertion Extraction](../PRD.md)

## What to build

End-to-end integration test verifying the full assertion pipeline from conversation to database. This replaces the existing `ChatAgentMemoryCueIT` pattern with assertion-specific verification while keeping the MemoryCue test intact (coexistence).

**New test: `ChatAgentAssertionIT`** — extends `E2ETestBase`:

**Test scenario 1 — Single session generates assertions:**
1. Start a `WORKPLACE_STANDUP` session.
2. Send multiple messages across different topics (e.g., grammar mistake, vocabulary issue, a resolved problem).
3. Wait for agent responses after each turn.
4. End the session (`endSession()`).
5. Wait for async pipeline to complete (~500ms).
6. Verify `memory_assertions` table has at least one row with `enabled=true`, correct `user_id`, `mode=WORKPLACE_STANDUP`, `group_id` pointing to `error-pattern`.
7. Verify each assertion has non-null `topic` and non-empty `state`.
8. Verify the assertion embedding store has entries (search returns results).

**Test scenario 2 — Two sessions produce merge lineage:**
1. Start session 1, discuss a recurring error pattern (e.g., "I struggle with past tense").
2. End session 1, wait for pipeline.
3. Start session 2, discuss the same error pattern again.
4. End session 2, wait for pipeline.
5. Verify `assertion_lineage` table has at least one row with `operation='MERGE'` — session 2's assertion merged with session 1's.
6. Verify the merged old assertion has `enabled=false`.
7. Verify the merged new assertion has `enabled=true`.
8. Verify recursive CTE query from the merged child returns the full ancestry chain.

**Test scenario 3 — Japanese mode is skipped:**
1. Start a `JAPANESE_BUSINESS` session.
2. Send several messages, end session.
3. Wait for pipeline.
4. Verify `memory_assertions` table has zero rows for this session (mode guard working).

**Test scenario 4 — Coexistence: MemoryCue pipeline still works:**
1. Run `ChatAgentMemoryCueIT` (existing test) — must pass unchanged.
2. Run `ChatAgentMemoryIT` (existing LearningProfile test) — must pass unchanged.

**Cleanup** — each test cleans the `memory_assertions` and `assertion_lineage` tables before running (matching the `cleanMemoryCueTable()` pattern in `ChatAgentMemoryCueIT`).

**Do NOT delete `ChatAgentMemoryCueIT`** — it stays as a regression test for the coexisting MemoryCue pipeline.

## Acceptance criteria

- [ ] `ChatAgentAssertionIT` compiles and all 4 test scenarios pass in `mvn verify`
- [ ] Single-session test: assertions generated with correct fields
- [ ] Two-session test: lineage edges created, old assertions soft-deleted, recursive CTE works
- [ ] Japanese mode test: zero assertions generated
- [ ] `ChatAgentMemoryCueIT` continues to pass (coexistence verified)
- [ ] `ChatAgentMemoryIT` continues to pass (LearningProfile unaffected)
- [ ] All existing E2E tests continue to pass

## Blocked by

- [04-session-complete-integration](04-session-complete-integration.md) — needs full pipeline wired into `SessionComplete`
