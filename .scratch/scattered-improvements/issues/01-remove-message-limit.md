# Remove 20-message history truncation

**Status:** `ready-for-agent`

## Parent

`.scratch/scattered-improvements/PRD.md` — 零星优化

## What to build

Remove the hardcoded 20-message truncation in `ConversationAgent.buildMessages()`. Currently only the last 20 messages from history are sent to the LLM each turn. Change this to send all history messages (no limit).

The existing `TokenTracker` (80% of 128k tokens = 102,400 tokens warning threshold) remains as the sole context overflow guard. With realistic usage patterns (~90 tokens per turn pair) the 128k limit would only be approached after ~980 turns — well beyond any reasonable session length.

## Files

| File | Change |
|------|--------|
| `agent/ConversationAgent.java` | In `buildMessages()`: replace `int start = Math.max(0, history.size() - 20)` with simply iterating from `0` |
| `agent/ConversationAgentTest.java` | Delete `historyTruncatedToLast20()` test method (tests behavior that no longer exists) |

## Acceptance criteria

- A session with 25+ turns sends all user+agent messages to the LLM (not just last 20)
- Existing tests in `ConversationAgentTest` still pass after removing the truncation test
- TokenTracker continues to work — no regression in the 80% warning logic
- `mvn test` passes
