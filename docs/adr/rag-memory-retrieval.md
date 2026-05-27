# ADR: RAG-based MemoryCue Retrieval (Replacing Tag-based Search)

**Date:** 2026-05-27
**Status:** Accepted

## Context

The MemoryCue system originally stored structured topic segments with `{topic, summary, tags}` at session end. Tags were intended for future keyword-based retrieval (`JSON_CONTAINS`), but this approach proved unreliable:

1. Per-entry tag generation was inconsistent — the LLM generates tags independently per segment with no awareness of existing canonical tags.
2. Tag consolidation was structurally flawed — it only rewrites current-session tags, never backfilling older cues.
3. Excessive complexity for weak gains — static global lock, additional LLM call, dedicated prompt file.

The existing User Memory system (merged text summary + learning profile) handles the first turn via System Prompt injection. MemoryCue was designed to complement it, but the tag approach wasn't viable.

## Decision

Replace the tag-based retrieval layer with a **Retrieval-Augmented Generation (RAG)** approach using vector semantic similarity:

1. **Tags removed entirely**: The `tags` column, `StringListConverter`, `consolidateTags` pipeline, and `tag-consolidation.txt` prompt were deleted. `CueResult` simplified from `(topic, summary, tags)` to `(topic, summary)`.

2. **RAG pipeline**: Each MemoryCue's `topic + summary` is vectorized using a local ONNX embedding model (`all-MiniLM-L6-v2`, 384 dimensions) and stored in an `InMemoryEmbeddingStore` with JSON disk persistence.

3. **Dynamic per-turn retrieval**: Starting from Round 4, every user message triggers a semantic search against historical MemoryCue vectors. The top-2 results (similarity ≥ 0.6) are injected into the System Prompt via a new `{memoryCues}` placeholder.

4. **Data isolation**: All MemoryCue and RAG data is isolated by `userId × AgentMode` at both H2 and vector store layers.

## Alternatives Considered

### Embedding Model
- **ONNX all-MiniLM-L6-v2** (chosen): 384 dimensions, 80MB model, local inference, no API cost. Maven dependency via `langchain4j-embeddings-all-minilm-l6-v2`. ~200MB heap at runtime.
- **OpenAI text-embedding-ada-002**: 1536 dimensions, API cost per query, network latency. Rejected for cost and latency at sustained usage.
- **SentenceTransformers via DJL**: Larger dependency footprint, no langchain4j integration. Rejected for complexity.

### Vector Store
- **InMemoryEmbeddingStore + JSON disk serialization** (chosen): Zero infrastructure, built-in `serializeToFile`/`fromFile` methods. Single-file persistence at `./data/embedding-store.json`. Limited to single-user deployment (see future concerns).
- **PostgreSQL pgvector / Pinecone / Weaviate**: External infrastructure, multi-user scaling. Rejected as over-engineered for current single-user deployment.

## Consequences

### Positive
- Semantic retrieval works with different vocabulary (e.g., "auth service" vs "login module")
- No more tag quality issues or consolidation complexity
- Configurable sensitivity via `app.memory.retrieval.*` properties
- Disk persistence survives server restarts with incremental diff rebuild

### Negative
- ONNX model adds ~200MB heap memory overhead
- Single-file disk store won't scale to multi-user without sharding
- Model load failure at startup prevents application from starting (fail-fast)
- Semantic search quality depends on embedding model accuracy (not benchmarked in this iteration)

## Architecture Flow

```
Session End:
  detectSwitches → splitBySwitches → per segment generateCue({topic, summary})
    → save to H2 memory_cues table
    → EmbeddingService.indexAsync(topic + summary + metadata)

Each Turn (Round 4+):
  User says something
  → EmbeddingService.search(userMessage, mode, userId, topK=2, threshold=0.6)
  → {memoryCues} = summaries concatenated with ", as well as, "
  → ConversationAgent injects {memoryCues} into System Prompt
```

## Configuration

```yaml
app:
  memory:
    user-memory-rounds: 1
    retrieval:
      top-k: 2
      similarity-threshold: 0.6
```

## Future Concerns

- Multi-user disk sharding: single `embedding-store.json` file will need directory-partitioned files per userId-mode
- Embedding model accuracy benchmarking for production evaluation
- Automatic retry on `indexAsync()` failure (currently relies on startup H2 diff rebuild)
