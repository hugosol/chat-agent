# 05 — Documentation Sync

**Status:** `ready-for-human`

## Parent

[PRD: Unified MemoryCue Retrieval — Remove Topic Memory](../PRD.md)

## What to build

Synchronize four documentation files with the changes made in issues #01–#03. This is a human-in-the-loop task because it involves domain terminology definitions, architectural decision records, and diagram updates that require judgment.

### README.md (~12 changes)

- Architecture diagram: update if it labels Topic Memory or UserMemory
- Agent table: remove any row referencing a Topic Memory agent; update MemoryService → LearningProfileService
- Memory injection description: rewrite to describe the unified RAG pipeline (every-round search + fallback anchor), removing the dual-track description
- Project structure: remove `memory-topic.txt` and `MemoryAgent.java` entries (if present)
- Placeholder reference: `{topicSummary}` → `{lastConversation}`
- Configuration section: remove `app.memory.user-memory-rounds`
- V2 Roadmap: update any references to Topic Memory removal or memory system changes

### AGENTS.md (~5 changes)

- CoachState channel count: 7 → 6
- `MemoryService` → `LearningProfileService` in all references
- `UserMemory` → `UserLearningProfile` in all references
- Repository list: `UserMemoryRepository` → `UserLearningProfileRepository`
- Memory injection paragraph in "Memory injection" section: completely rewrite to describe unified RAG with fallback anchor, MemoryCueQueue LRU cycle, and three independent `ConversationAgent` conditions
- Remove the line "Topic Memory is a direct write (no LLM merge)"

### CONTEXT.md (~15 changes)

- "Memory and Long-Term Context" glossary section: delete the Topic Memory entry entirely
- Rewrite the MemoryCueQueue entry: add capacity design rationale (`topK + 1` as LRU buffer), fallback anchor survival cycle (evicted after ~1 round via FIFO), sorting rules (FIFO + dedup, not score-sorted)
- Update MemoryContent entry: new field list `(lastConversationTimeLabel, learningProfile, cueMatches)`
- Update Memory Injection definition: describe the unified pipeline
- Update relationship descriptions: remove Topic Memory ↔ Report relationship; add fallback anchor ↔ MemoryCue relationship
- Update example conversations if they reference topic memory

### docs/architecture.md (~22 changes)

- Decision log: add new entry for MemoryCueQueue LRU eviction design (capacity `topK + 1`, fallback anchor lifecycle)
- CoachState schema diagram: remove TOPIC_MEMORY from the channel list; update channel count
- Per-turn conversation flow diagram/description: rewrite to show every-round RAG with round-1 fallback
- `MemoryService` → `LearningProfileService` in all architecture descriptions
- Data model diagram: `UserMemory` → `UserLearningProfile`
- Enum definitions section: `MemoryType` → `LearningType`
- Any other stale references to the old memory architecture

## Acceptance criteria

- [ ] README.md accurately describes the current memory system (unified RAG + fallback)
- [ ] AGENTS.md channel count is 6; all type names are `LearningProfileService` / `UserLearningProfile` / `UserLearningProfileRepository`
- [ ] CONTEXT.md glossary has no Topic Memory entry; MemoryCueQueue entry includes capacity design and LRU cycle
- [ ] docs/architecture.md decision log has the new MemoryCueQueue LRU entry; schema/diagram text is updated
- [ ] All four files reviewed and approved by a human

## Blocked by

- [#01 — Remove Topic Summary from Report Pipeline](./01-remove-topic-summary-from-report-pipeline.md)
- [#02 — Rename Memory Domain Types](./02-rename-memory-domain-types.md)
- [#03 — Unified MemoryCue Retrieval: RAG-First + Fallback Anchor](./03-unified-memorycue-rag-retrieval.md)

## Comments

