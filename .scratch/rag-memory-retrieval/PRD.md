# PRD: RAG-based MemoryCue Retrieval (Replacing Tag-based Search)

**Status:** `ready-for-agent`

## Problem Statement

The current MemoryCue system stores structured topic segments with `{topic, summary, tags}` at session end, but tags are too scattered and tag consolidation is ineffective. Tags are intended for future keyword-based retrieval (`JSON_CONTAINS`), but their quality is unreliable due to:

1. **Per-entry tag generation is inconsistent**: The LLM generates tags independently per segment with no awareness of existing canonical tags, resulting in highly variable labeling across segments and sessions.

2. **Tag consolidation is structurally flawed**: The consolidation LLM only rewrites the current session's tags. Previously written cues with outdated tags are never backfilled, so the database accumulates mixed canonical/non-canonical tags over time.

3. **Excessive complexity for weak gains**: Tag consolidation requires a static global lock, an additional LLM call, and a dedicated prompt file — all for a retrieval mechanism that was never built and whose accuracy would be unreliable.

4. **Prompt contradiction**: The `memory-cue-entry.txt` instruction says "at most 5 tags" but the few-shot example shows 7 tags, causing the LLM to routinely exceed the limit.

The existing User Memory system (merged text summary + learning profile) handles the first 3 turns of conversation via System Prompt injection. MemoryCue was designed to complement it with structured, tag-based search — but the tag approach is not viable without significant further investment in controlled vocabularies, backfill pipelines, and retrieval infrastructure.

## Solution

Replace the tag-based retrieval layer with a **Retrieval-Augmented Generation (RAG)** approach using vector semantic similarity. This eliminates tags entirely and uses dense vector embeddings for semantically-aware retrieval, which maps naturally to per-turn dynamic search.

### Core Changes

1. **RAG pipeline**: Each MemoryCue's `topic + summary` is vectorized using a local ONNX embedding model (`all-MiniLM-L6-v2`, 384 dimensions) and stored in an in-memory vector store with JSON disk persistence.

2. **Dynamic per-turn retrieval**: Starting from Round 4 (after User Memory's three-turn window), every user message triggers a semantic search against historical MemoryCue vectors. The top-2 results (similarity ≥ 0.6) are injected into the System Prompt via a new `{memoryCues}` placeholder.

3. **Tags removed entirely**: The `tags` column, `StringListConverter`, `consolidateTags` pipeline, and `tag-consolidation.txt` prompt are deleted. The `memory-cue-entry.txt` prompt is simplified from `{topic, summary, tags}` JSON to `{topic, summary}`.

4. **Topic split retained**: `detectSwitches` (topic segmentation) and the two-step LLM generation remain unchanged — topic splitting and per-segment summarization are still valuable for creating fine-grained vector index entries.

5. **Data isolation**: All MemoryCue and RAG data is isolated by `userId × AgentMode`. The InMemoryEmbeddingStore attaches mode and userId as metadata on each entry, and search queries filter by both dimensions.

### Architecture Flow

```
Session End:
  detectSwitches → splitBySwitches → per segment generateCue({topic, summary})
    → save to H2 memory_cues table
    → EmbeddingService.indexAsync(topic + summary + metadata)

Each Turn (Round 4+):
  User says something
  → EmbeddingService.search(userMessage, mode, userId, topK=2, threshold=0.6)
  → {memoryCues} = summaries concatenated with ", as well as, "
  → ConversationAgent injects {memoryCues} into System Prompt below {topicSummary}
```

### Configuration

```yaml
app:
  memory:
    user-memory-rounds: 3       # topicSummary + learningProfile persist for N turns
    retrieval:
      top-k: 2                  # max search results per turn
      similarity-threshold: 0.6 # minimum cosine similarity to consider a match
```

`start-round` for RAG is auto-derived as `user-memory-rounds + 1 = 4`.

## User Stories

1. As an English learner, when I start a new conversation and mention a past topic in Round 4 or later (after the initial greeting window), the coach should naturally recall relevant historical context from that exact topic.

2. As an English learner, when I talk about a topic using different vocabulary than my past session (e.g., "auth service" vs "login module"), the coach should still retrieve the right historical context through semantic understanding.

3. As an English learner, the coach should only inject historical context when it is genuinely relevant (similarity ≥ 0.6), avoiding the jarring experience of jumping to an unrelated past topic.

4. As an English learner, multiple relevant past topics should be injectable in the same turn (Top-2), naturally bridged together without overwhelming the current conversation.

5. As an English learner, in WORKPLACE_STANDUP mode I should only see historical work-related memory cues, never cues from my DAILY_TALK sessions.

6. As a developer, I should be able to configure the RAG sensitivity (top-K, similarity threshold) through `application.yml` without code changes.

7. As a developer, the RAG embedding store should persist across server restarts via disk serialization, only incrementally rebuilding when new MemoryCues are added.

8. As a developer, if the disk serialization file is corrupted, the system should automatically rebuild the vector index from the H2-backed MemoryCue table on next startup.

9. As a developer, if the ONNX model fails to load at startup, the application should fail fast with a clear error message rather than silently degrading.

10. As a developer, the embedding indexing pipeline should be asynchronous and non-blocking — MemoryCueService should not wait for vectorization to complete before continuing session-end processing.

11. As a developer, the tag consolidation pipeline and its associated LLM calls, static lock, and prompt files should be removed to reduce system complexity.

12. As a developer, the prompt contradiction in `memory-cue-entry.txt` (instruction says "at most 5 tags" but example shows 7) should be resolved by removing tags entirely.

13. As a developer, the active engagement behavior (`{activeEngagement}` text) should trigger when either User Memory (Round 1-3) or RAG results (Round 4+) are present — always encouraging the coach to naturally reference injected memory.

14. As a developer, I need clear logged information about which MemoryCues were retrieved, their similarity scores, and which mode/user they were filtered by, to debug retrieval quality.

## Implementation Decisions

### 1. Module Extraction: `EmbeddingService`

A new deep module encapsulating all ONNX vector operations and InMemoryEmbeddingStore management.

**Public interface:**
- `init()` — `@EventListener(ApplicationReadyEvent.class)`: Load store from disk JSON file, or rebuild from H2 `memory_cues` table if no file exists. Performs incremental diff: queries H2 for COMPLETED records newer than the file timestamp and adds only those. Also cleans orphaned entries (present in store but deleted from H2).
- `indexAsync(String cueId, String topic, String summary, AgentMode mode, String userId)` — Embed `topic + " " + summary` with ONNX, add to InMemoryEmbeddingStore with metadata `{cueId, topic, mode, userId}`, then asynchronously save the full store to disk via `serializeToFile()`.
- `search(String userInput, AgentMode mode, String userId, int topK, double threshold)` → `List<CueMatch>` — Embed the user input, search the store with `mode == currentMode AND userId == currentUserId` filter, return matches above threshold.
- `saveToDisk()` — `@PreDestroy`: Serialize the full `InMemoryEmbeddingStore` to `./data/embedding-store.json` via Gson. If this fails, log the full stack trace and let the exception propagate.

**Embedding model:** `all-MiniLM-L6-v2` (384 dimensions, ~80MB model file, via `langchain4j-embeddings-all-minilm-l6-v2` Maven dependency). About 200MB heap during runtime. Documented as a known memory footprint concern.

**Error handling:**
- ONNX model load failure at startup → fail fast (prevent app from starting)
- Disk JSON file corrupted on init → log warning, fall back to full H2 rebuild
- `indexAsync()` ONNX failure → log warning, skip (will be rebuilt on next startup via H2 diff)

### 2. New Data Type: `MemoryContent`

Encapsulates all memory data injected into the System Prompt, replacing three separate parameters.

```java
// com.hugosol.webagent.dto
public record MemoryContent(
    String topicSummary,
    String learningProfile,
    String memoryCuesText  // RAG results for Round 4+
) {
    public boolean isEmpty();
}
```

### 3. New Data Type: `CueMatch`

Return type for `EmbeddingService.search()`, decoupling from JPA entities.

```java
// com.hugosol.webagent.dto
public record CueMatch(
    String cueId,
    String topic,
    String summary,
    double score
) {}
```

### 4. ConversationAgent Changes

- `generateStream()` signature changes: `(history, mode, MemoryContent, messageId, handler)` — reduces parameter count from 7 to 5.
- `buildPromptJson()` parallel change for LLM call logging.
- `buildSystemContent()` implements 3-branch injection logic:
  - **Round 1-3 (User Memory active):** Inject `{topicSummary}` + `{learningProfile}` + `{activeEngagement}`, keep `{memoryCues}` empty.
  - **Round 4+ (RAG has results):** Inject `{memoryCues}` + `{activeEngagement}`, keep `{topicSummary}` and `{learningProfile}` empty.
  - **Round 4+ (no results):** All placeholders empty — no active engagement either.

### 5. TurnProcessor Changes

`processTurn()` becomes the orchestration layer that builds `MemoryContent`:

- Round ≤ `user-memory-rounds`: `new MemoryContent(topicSummary, learningProfile, null)`
- Round > `user-memory-rounds`: Call `embeddingService.search()`, concatenate results with `", as well as, "`, build `new MemoryContent(null, null, memoryCuesText)`

This keeps `ConversationAgent` pure — it does not depend on `EmbeddingService`.

### 6. System Prompt Template

`conversation-system.txt` adds `{memoryCues}` between `{topicSummary}` and `{learningProfile}`:

```
{Description}

Rules:
{Rules}

{topicSummary}

{memoryCues}

{learningProfile}

{activeEngagement}
```

`{activeEngagement}` controls independently — triggers when either topicSummary or memoryCuesText is non-empty.

### 7. Vector Store Serialization

LangChain4j's `InMemoryEmbeddingStore` does not implement `java.io.Serializable`, but provides built-in Gson-based JSON serialization:
- `serializeToFile(Path)` / `fromFile(Path)` — read/write the full store as JSON
- Stored at `./data/embedding-store.json` (alongside H2 file database)

On each `indexAsync()`, after embedding, the store is asynchronously serialized to disk. On `@PreDestroy`, a final synchronous `saveToDisk()` provides crash protection.

### 8. Thread Pool

Dedicated `embeddingExecutor` (core=2, max=2, defined in `AsyncConfig`) for ONNX CPU-bound embedding operations, separate from the I/O-focused `memoryExecutor`. Overhead is ~2MB of thread stack space.

### 9. Data Isolation

All memory data is isolated by `userId × AgentMode`:

| Layer | Isolation Keys |
|-------|---------------|
| H2 `memory_cues` table | `userId + mode` columns |
| `InMemoryEmbeddingStore` metadata | `userId + mode` per entry, filtered at query |
| Disk JSON file | Single file for current single-user deployment; future multi-user can shard to `embedding-store-{userId}-{mode}.json` |
| User Memory (TOPIC_SUMMARY) | `userId + type + mode` |
| User Memory (LEARNING_PROFILE) | `userId + type` (global, no mode) |
| `UserProgress` | `userId` only |

### 10. Prompt File Updates

**`memory-cue-entry.txt`:** Remove `tags` field. Change "three fields" to "two fields". Remove contradictory 7-tag example. Updated format:
```json
{"topic": "Sprint Planning for Auth Module", "summary": "Discussed the login module sprint plan..."}
```

**`memory-cue-split.txt`:** No changes needed (no tags involvement).

**`conversation-system.txt`:** Add `{memoryCues}` placeholder.

**`tag-consolidation.txt`:** Delete (both main and test versions).

### 11. Entity Changes

**`MemoryCue` entity:**
- Remove `tags` field (JPA column + `StringListConverter`)
- Remove `@Convert(converter = StringListConverter.class)` annotation
- Update constructor from 8 parameters to 7 (no tags)
- Remove `getTags()` / `setTags()` accessors

**`MemoryCueRepository`:**
- Remove `findByUserIdAndMode(userId, mode)` — only used by consolidation
- Add `findAllByStatus(status)` — used by `EmbeddingService.init()`

### 12. Deleted Code

| Component | Files |
|-----------|-------|
| `consolidateTags()` method | `MemoryCueService.java` (method body + `consolidationLock` static field) |
| `MemoryCueAgent.consolidateTags()` | `MemoryCueAgent.java` |
| `StringListConverter` | `model/StringListConverter.java` |
| `tag-consolidation.txt` prompt | Both `src/main/resources/prompts/` and `src/test/resources/prompts/` |
| Tag consolidation E2E test | `EnglishCoachMemoryCueIT.shouldConsolidateTagsAfterSessionEnd()` |
| Tag consolidation WireMock stubs | `WireMockStubs.java` related methods + response JSON files |
| `CueResult` tags field | `MemoryCueAgent.CueResult` simplified to `(String topic, String summary)` |

### 13. Configuration

All under `app.memory.*` namespace:

```yaml
app:
  memory:
    user-memory-rounds: 3
    retrieval:
      top-k: 2
      similarity-threshold: 0.6
```

### 14. Documentation Updates

| Document | Operation | Details |
|----------|-----------|---------|
| `docs/architecture.md` | Modify | Add RAG architecture section (EmbeddingService + InMemoryEmbeddingStore flow diagram). Add complete data isolation table (`userId × mode` across H2, runtime, and RAG layers). **Also fix existing report prompt description discrepancy**: Section 五 describes a 4-item list but actual prompt produces 5-field JSON with `topicSummary` missing from doc and `fluencyScore` described with non-existent justification text. |
| `CONTEXT.md` | Modify | Add glossary terms: `MemoryCue Retrieval` (vector semantic retrieval), `EmbeddingService` (RAG vectorization), `CueMatch` (search result record), `MemoryContent` (system prompt injection payload). |
| `README.md` | Modify | Update project structure to reflect new modules (EmbeddingService, CueMatch, MemoryContent). Document `app.memory.*` configuration keys. Add ONNX heap memory (~200MB) as known concern. |
| `docs/adr/dual-memory-system.md` | Modify | Update MemoryCue description from tag-based (`(topic, summary, tags)`) to RAG-based (`(topic, summary)`). Replace "tags for JSON_CONTAINS" future path with "RAG vector retrieval" future path. |
| `docs/adr/rag-memory-retrieval.md` | Create | New ADR documenting the RAG-over-tags decision: rationale for switching from keyword tags to vector embeddings, embedding model choice (ONNX all-MiniLM-L6-v2 vs alternatives considered), vector store choice (InMemoryEmbeddingStore + JSON vs external vector DB), and disk serialization strategy. |
| `AGENTS.md` | Modify | Update project structure diagram to include `service/EmbeddingService.java`, `dto/CueMatch.java`, `dto/MemoryContent.java`. Remove references to tags and consolidation. Update Quick Reference if new CLI commands become relevant. |

### 15. Execution Order

Suggested dependency-aware execution sequence:

```
1  →  2  →  3  →  4  →  10  →  5  →  6  →  7  →  8  →  14  →  15  →  11  →  9
(dep  →  EmbeddingService → DTOs → Tags removal → CueResult → ConversationAgent → TurnProcessor → MemoryCueService → Config → Documentation → Data migration → Tests → Verification)
```

Order rationale:
- Blocks 1-3 (dependencies, EmbeddingService, DTOs) have no internal dependencies and can be done first.
- Block 4 (tags removal) touches the same entity that CueResult change (10) modifies — do them together.
- Blocks 5-6-7 (ConversationAgent, TurnProcessor, MemoryCueService) are the core integration and share MemoryContent; do them sequentially.
- Block 8 (config) is standalone once the config keys are decided.
- Blocks 14-15 (documentation + data migration) are write-only, no impact on runtime.
- Block 11 (tests) should come after all code changes to catch regressions.
- Block 9 (ADR) can be authored anytime after the design is settled; it is listed last as a formality.

## Testing Decisions

### Testing Principles

- Only test external behavior, not internal implementation details.
- Unit tests cover business logic (split, entry generation, search orchestration, injection logic).
- E2E tests cover the happy path (session end → MemoryCue generation → RAG injection on future turn).
- WireMock stubs for E2E use prompt markers for LLM response matching.

### New Tests

1. **`EmbeddingServiceTest`** (unit tests):
   - `init()` from empty disk → builds from H2
   - `init()` from existing disk → loads without re-embedding
   - `init()` from corrupted disk → rebuilds from H2
   - `indexAsync()` → store contains entry with correct metadata + text
   - `search()` with matching query → returns top results above threshold
   - `search()` with non-matching query → returns empty list
   - `search()` with wrong mode → returns empty (mode isolation)
   - `search()` with wrong userId → returns empty (user isolation)

2. **`MemoryCueServiceTest` updates**:
   - Replace consolidation assertions with `EmbeddingService.indexAsync()` invocation assertions
   - Remove all consolidation-related tests

3. **`ConversationAgentTest` updates**:
   - Adapt to `MemoryContent` parameter instead of three separate strings
   - Test all 3 injection branches: User Memory branch, RAG memory branch, no-memory branch
   - Verify activeEngagement appears in branches 1 and 2 but not branch 3

4. **`TurnProcessorTest` updates**:
   - Test `processTurn()` with messageId=1 → Search NOT called, topicSummary injected
   - Test `processTurn()` with messageId=5 → Search IS called, memoryCuesText injected
   - Test `processTurn()` with messageId=5 and no results → Search returns empty, nothing injected

### Existing Tests to Modify

5. **`MemoryCueAgentTest`**: Remove consolidateTags tests. Update `CueResult` construction to 2 fields.

6. **`MemoryCueRepositoryTest`**: Remove `findByUserIdAndMode_filtersByMode` test. Add `findAllByStatus` test.

7. **`EnglishCoachMemoryCueIT` E2E test**:
   - Remove `shouldConsolidateTagsAfterSessionEnd()` entirely
   - Update `memoryCueGeneratedAtSessionEndWithTopicSwitch()`: remove `getTags()` assertions
   - Keep the E2E marker matching (WireMock) and H2 assertions for topic/summary

### WireMock Changes

- Update `memory-cue-entry-success.json` and `memory-cue-entry-seg2.json`: remove `tags` field from response JSON
- Delete `tag-consolidation-response.json`
- Remove consolidation-related scenario stubs from `WireMockStubs.java`
- Update `memory-cue-entry.txt` test prompt to match 2-field structure

### Prior Art

- `MemoryServiceTest` and `MemoryCueServiceTest` patterns (service layer, Mockito, CompletableFuture verification)
- `ConversationAgentTest` patterns (system content assertion, injection condition testing)
- `EnglishCoachMemoryCueIT` patterns (Playwright + WireMock scenario state machine)
- `EnglishCoachMemoryIT` patterns (H2 assertions after async completion)

## Out of Scope

- **Replacing User Memory**: The existing User Memory system (topic summary + learning profile, merged text blob) remains unchanged. RAG MemoryCue is additive, not a replacement.
- **Automatic retry on `indexAsync()` failure**: Failed embeddings are non-blocking and will be retried on next startup via H2 diff.
- **Multi-user disk sharding**: The `embedding-store.json` file is a single file. If the system later serves multiple users, it should be sharded or directory-partitioned. This is noted in the documentation as a future concern.
- **Frontend UI changes**: No frontend changes. RAG injection is purely in the System Prompt, invisible to the UI.
- **Database migration tools**: Hibernate `ddl-auto=update` continues to be used. The tags column is removed via `ALTER TABLE` in a `@PostConstruct` or `DataInitializer` SQL statement.
- **Embedding model accuracy benchmarking**: Not part of this implementation iteration. Deployment evaluation is recommended.

## Further Notes

- **ONNX memory footprint**: The `all-MiniLM-L6-v2` model consumes approximately 200MB of heap at runtime. This is acceptable for current single-user deployment but should be monitored.
- **InMemoryEmbeddingStore filter verification**: LangChain4j 1.0.0-beta1's `InMemoryEmbeddingStore` supports `EmbeddingSearchRequest.filter()` with `Filter.and(Filter.eq("key", value))` for metadata filtering. This should be verified during implementation — if the built-in filter is insufficient, post-search filtering by userId and mode is a safe fallback.
