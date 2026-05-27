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
- **Injection window**: 1 turn (messageId ≤ 1), giving the Agent a chance to reference past context naturally.
- **Traceability**: Added `session_id` column so each User Memory record knows which Practice session triggered its generation.

### 2. MemoryCue (RAG-enabled)

- **Purpose**: Structured, searchable topic memory with vector semantic retrieval.
- **Structure**: One row per conversation topic segment, with `(topic, summary)` pair + status tracking.
- **Generation**: Post-session, a `MemoryCueAgent` performs two LLM steps:
  1. **Split** (`detectSwitches`): Analyze full transcript → list of topic switch point indices.
  2. **Entry** (`generateCue`): For each segment, produce `{topic, summary}` JSON.
- **Vectorization**: After save, `EmbeddingService.indexAsync()` embeds `topic + summary` using ONNX all-MiniLM-L6-v2 (384 dimensions) and stores in `InMemoryEmbeddingStore` with JSON disk persistence.
- **Retrieval**: Starting Round 4, `TurnProcessor` triggers `EmbeddingService.search()` on each user message. Top-2 results (similarity ≥ 0.6) filtered by `userId × AgentMode` are injected into System Prompt as `{memoryCues}`.
- **Execution**: MemoryCue generation and vectorization run on `memoryExecutor` asynchronously. Retrieval is synchronous per-turn.

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
  │    ├─ Step 2: generateCue per segment (parallel)
  │    │    └─ MemoryCue rows written to memory_cues table
  │    └─ After each COMPLETED save → EmbeddingService.indexAsync()
  │         └─ Vectorized into InMemoryEmbeddingStore + disk serialization

Each Turn (Round 4+):
  TurnProcessor.processTurn()
  ├─ EmbeddingService.search(userInput, mode, userId, topK=2, threshold=0.6)
  ├─ Build MemoryContent(memoryCuesText=results)
  └─ ConversationAgent.generateStream(..., MemoryContent, ...)
       └─ Injects {memoryCues} into System Prompt
```

### Why Not Replace User Memory?

- User Memory is optimized for prompt injection (compact text blob).
- MemoryCue is optimized for search (tagged, segmented, JSON-column queryable).
- They serve different purposes and are likely to diverge further.
- Replacing User Memory now would increase scope without clear benefit.

## Consequences

### Positive

- **Better conversation continuity**: 1-turn injection window means the Agent picks up past context when the Learner starts with a brief greeting.
- **Search-ready memory**: MemoryCue's tag column (JSON) is ready for `JSON_CONTAINS` queries in v2.
- **Independent failure**: MemoryCue failures do not affect Report or User Memory generation.
- **Mode isolation**: WORKPLACE_STANDUP and DAILY_TALK MemoryCue data are separate.
- **Traceable User Memory**: session_id column enables debugging of which session produced which memory version.

### Negative

- **Dual storage**: Two tables to maintain, two generation pipelines.
- **ONNX memory overhead**: ~200MB heap for the all-MiniLM-L6-v2 embedding model.
- **Thread pool expansion**: memoryExecutor grew from core=2/max=4 to core=4/max=8; added dedicated embeddingExecutor (core=2/max=2).

### Future Path

- If MemoryCue vector retrieval proves successful, consider replacing User Memory's topic summary injection with MemoryCue-based retrieval.
- Multi-user deployment would require disk sharding of `embedding-store.json` by userId × mode.
