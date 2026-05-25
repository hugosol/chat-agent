# ADR: Dual Memory System — User Memory + MemoryCue Coexistence

**Date:** 2026-05-25
**Status:** Accepted

## Context

The English Coach had a single memory track: User Memory (topic summary + learning profile) generated via LLM merging of session reports. This served well for System Prompt injection but was limited:

1. **Injection window too narrow**: Memory was injected only in the first turn (messageId=1). In casual modes like DAILY_TALK, the Learner's first message is often a simple greeting, giving the Agent no chance to naturally reference past topics.
2. **No structured search**: The merged summary is a flat text blob. You can't ask "what did we discuss about Japan?" without loading and reading the entire summary.
3. **No topic segmentation**: A session covering both work standup and travel plans gets merged into one undifferentiated block.

## Decision

We introduce a **dual memory system** where two memory types coexist:

### 1. User Memory (existing, enhanced)

- **Purpose**: System Prompt injection — gives the Agent conversation continuity.
- **Structure**: Merged text summary (TOPIC_SUMMARY + LEARNING_PROFILE), versioned, mode-scoped.
- **Injection window**: Expanded from 1 turn to 3 turns (messageId ≤ 3), giving the Agent multiple chances to reference past context naturally.
- **Traceability**: Added `session_id` column so each User Memory record knows which Practice session triggered its generation.

### 2. MemoryCue (new)

- **Purpose**: Structured, searchable topic memory for future retrieval.
- **Structure**: One row per conversation topic segment, with `(topic, summary, tags)` triple + status tracking.
- **Generation**: Post-session, a `MemoryCueAgent` performs two LLM steps:
  1. **Split** (`detectSwitches`): Analyze full transcript → list of topic switch point indices.
  2. **Entry** (`generateCue`): For each segment, produce `{topic, summary, tags}` JSON.
- **Execution**: Runs on `memoryExecutor` asynchronously, in parallel with Report and User Memory merge. Failures do not block session completion.
- **Status tracking**: Each segment is independently tracked as COMPLETED, SEGMENT_FAILED, or FIRST_CALL_FAILED.

### Architecture Integration

```
onEndSession():
  ├─ memoryExecutor:
  │    reportAgent.generate() → MemoryService.generateMemoryAsync()
  │    └─ User Memory (TOPIC_SUMMARY + LEARNING_PROFILE) updated
  │
  ├─ memoryExecutor (parallel with above):
  │    MemoryCueService.generateCuesAsync()
  │    ├─ Step 1: detectSwitches → switch points
  │    └─ Step 2: generateCue per segment (parallel)
  │         └─ MemoryCue rows written to memory_cues table
  │
  └─ Main thread (waits for report only):
       sessionStore.completeSession() → SESSION_REPORT → sessionService.remove()
```

### Why Not Replace User Memory?

- User Memory is optimized for prompt injection (compact text blob).
- MemoryCue is optimized for search (tagged, segmented, JSON-column queryable).
- They serve different purposes and are likely to diverge further.
- Replacing User Memory now would increase scope without clear benefit.

## Consequences

### Positive

- **Better conversation continuity**: 3-turn injection window means the Agent picks up past context even when the Learner starts with a brief greeting.
- **Search-ready memory**: MemoryCue's tag column (JSON) is ready for `JSON_CONTAINS` queries in v2.
- **Independent failure**: MemoryCue failures do not affect Report or User Memory generation.
- **Mode isolation**: WORKPLACE_STANDUP and DAILY_TALK MemoryCue data are separate.
- **Traceable User Memory**: session_id column enables debugging of which session produced which memory version.

### Negative

- **Dual storage**: Two tables to maintain, two generation pipelines.
- **No retrieval yet**: MemoryCue is write-only in v1 — retrieval will be addressed in v2.
- **Thread pool expansion**: memoryExecutor grew from core=2/max=4 to core=4/max=8 to handle the additional parallel workload.

### Future Path

- If MemoryCue retrieval (v2) proves successful, consider replacing User Memory's topic summary injection with MemoryCue-based retrieval.
- The `memory_cues` table is designed with a JSON `tags` column specifically for `JSON_CONTAINS` keyword queries — no schema migration needed.
