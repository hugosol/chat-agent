# 03 — Unified MemoryCue Retrieval: RAG-First + Fallback Anchor

**Status:** `ready-for-agent`

## Parent

[PRD: Unified MemoryCue Retrieval — Remove Topic Memory](../PRD.md)

## What to build

Replace the old dual-track memory injection (Topic Memory for rounds 1–N, RAG MemoryCue for rounds N+ onwards) with a single unified pipeline: every round performs RAG semantic search against `EmbeddingService`. On round 1, if RAG returns no matches, fall back to loading the most recent session's last COMPLETED MemoryCue from H2 as a conversation continuity anchor.

This is the core behavior change of the PRD. It involves six coordinated changes:

### 1. CoachState cleanup

Remove the `TOPIC_MEMORY` channel from `CoachState`: delete the constant, the Channels schema entry, the `initialState()` parameter, and the `topicMemory()` accessor. Channel count: 7 → 6. Update `SessionService.init()` to not load topic memory from H2. Remove `SessionService.getTopicMemory()`.

### 2. MemoryContent DTO refactor

Change `MemoryContent` fields from `(topicSummary, learningProfile, cueMatches, topicCreatedAt)` to `(lastConversationTimeLabel, learningProfile, cueMatches)`. Delete the secondary convenience constructor. `lastConversationTimeLabel` is `null` unless the fallback anchor is triggered (then it contains the time label of the fallback cue, e.g. "earlier today"). `learningProfile` is passed only on round 1, `null` thereafter.

### 3. conversation-system.txt template

Rename the `{topicSummary}` placeholder to `{lastConversation}`. New template structure:

```
{Description}
Rules:
{Rules}

{lastConversation}

{memoryCues}

{learningProfile}

{activeEngagement}
```

All five placeholders are now independently resolved — no more if/else mutual exclusion between User Memory path and RAG path.

### 4. TurnProcessor.resolveMemoryContext() rewrite

The old logic (lines 98–126) is replaced:

```
Every round:
  1. RAG search: embeddingService.search(userInput, mode, userId, topK, threshold)
  2. Push results into MemoryCueQueue
  3. If round 1 AND queue is empty after push:
     a. Load fallback: memoryCueRepository.findTopByUserIdAndModeAndStatusOrderByCreateTimeDesc(userId, mode, COMPLETED)
     b. If found, push a synthetic CueMatch from this fallback MemoryCue into the queue
     c. Set lastConversationTimeLabel from the fallback cue's createTime
  4. Build MemoryContent(lastConversationTimeLabel, learningProfile, queue.getEntries())
     - learningProfile: round 1 → sessionService.getLearningProfile(); round 2+ → null
```

No more `messageId <= userMemoryRounds` comparison. No more `topicCreatedAt` parameter.

### 5. ConversationAgent.buildSystemContent() new branching

Three placeholders resolved independently, no longer if/else between User Memory and RAG:

| Placeholder | Condition | Content |
|-------------|-----------|---------|
| `{lastConversation}` | `lastConversationTimeLabel != null` | `"The last conversation was {timeLabel}. Pick up conversation naturally from where it left off."` |
| `{learningProfile}` | `learningProfile != null` | `"[Your Learning Profile]\n{text}"` |
| `{memoryCues}` | `cueMatches != null && !cueMatches.isEmpty()` | `"[Memory Cues]\n1. [from {label}] {topic}: {summary}\n..."` |
| `{activeEngagement}` | Any of the above three is non-empty | Active engagement instruction text |

Existing `formatMemoryCuesForPrompt()` and `ACTIVE_ENGAGEMENT_TEXT` constants remain unchanged.

### 6. Supporting changes

- **MemoryCueRepository**: Add `findTopByUserIdAndModeAndStatusOrderByCreateTimeDesc(String userId, AgentMode mode, MemoryCueStatus status)` for the fallback query.
- **AppProperties**: Delete `userMemoryRounds` field and `getUserMemoryRounds()` getter. Remove `app.memory.user-memory-rounds` from `application.yml`. Remove `userMemoryRounds` from `TurnProcessor` constructor/injection.
- **workplace_standup/description.txt**: Change `"during your daily standup."` → `"during standup or quick catch-up."` Remove the time-of-day assumption; let `TimeLabel` from MemoryCue timestamps drive temporal context.
- **MemoryCueQueue**: No changes — existing capacity (`topK + 1`), FIFO dedup, and LRU eviction work correctly for the new usage pattern (the fallback anchor naturally evicts after ~1 round of LRU).
- **SessionComplete**, **EmbeddingService**, **TokenTracker**, **LlmCallLog**, **langgraph4j** graph: Unchanged.

## Acceptance criteria

- [ ] `CoachState` has 6 channels (TOPIC_MEMORY removed); `CoachState.initialState()` takes 6 parameters
- [ ] `SessionService.getTopicMemory()` no longer exists; `SessionService.init()` does not call `MemoryService.loadLatestContent()` for topic memory
- [ ] `MemoryContent` record has exactly 3 fields: `lastConversationTimeLabel`, `learningProfile`, `cueMatches`
- [ ] `conversation-system.txt` uses `{lastConversation}` placeholder (not `{topicSummary}`)
- [ ] `TurnProcessor.resolveMemoryContext()` runs RAG search every round; no `userMemoryRounds` comparison
- [ ] Round 1 with empty RAG results triggers the fallback: loads last COMPLETED MemoryCue from H2, pushes to queue, sets `lastConversationTimeLabel`
- [ ] Round 1 with RAG hits does NOT trigger the fallback — RAG results are used directly
- [ ] `ConversationAgent.buildSystemContent()` resolves `{lastConversation}`, `{learningProfile}`, and `{memoryCues}` independently (all three can be non-empty simultaneously in the system prompt)
- [ ] `MemoryCueRepository` has the new `findTopByUserIdAndModeAndStatusOrderByCreateTimeDesc` query method
- [ ] `AppProperties` no longer has `userMemoryRounds`; `application.yml` no longer has `app.memory.user-memory-rounds`
- [ ] `workplace_standup/description.txt` says "standup or quick catch-up" (not "daily standup")
- [ ] `mvn test` passes (unit tests: `TurnProcessorTest`, `ConversationAgentTest`, `CoachStateTest`, `SessionCompleteTest`, `CoachMessageHandlerTest`)

## Blocked by

- [#01 — Remove Topic Summary from Report Pipeline](./01-remove-topic-summary-from-report-pipeline.md)
- [#02 — Rename Memory Domain Types](./02-rename-memory-domain-types.md)

## Comments

