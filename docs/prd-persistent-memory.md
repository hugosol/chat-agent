# PRD: Persistent Memory — Cross-Session Context Recall

## Problem Statement

The English Coach treats every Practice session as a blank slate. The Agent has no memory of what the Learner discussed previously — hobbies, travel plans, ongoing work projects, or language weaknesses identified in prior sessions. Each session starts with generic greetings, wasting the Learner's first few Turns on re-establishing context that the system already possesses in its database. The Learner must manually re-introduce topics they've already covered, which breaks the illusion of a real conversation partner and reduces engagement.

Additionally, the Agent rarely drives the conversation — it responds passively to Learner input without asking follow-up questions or suggesting new topics. There is no mechanism to track what the Learner is interested in or what language areas need repeated practice.

The H2 database already stores completed session histories, Reports, and error patterns. This data is unused for any cross-session purpose.

## Solution

Add persistent User Memory that survives across Practice sessions. After each session ends, the system asynchronously generates updated summaries of the Learner's conversation topics and language skills by merging new session data with existing memory. When the Learner starts a new Practice session, these summaries are injected into the Agent's first System Prompt so the Agent can greet the Learner with context and proactively continue conversations.

Two independent User Memory types:

1. **Topic Memory** — a 500-character summary of conversation topics discussed across all sessions. Enables the Agent to recall "last time we talked about your trip to Japan" without the Learner re-introducing it.
2. **Learning Profile** — a 400-character summary of the Learner's English strengths, weaknesses, and improvement trends. Enables the Agent to know "this Learner struggles with past tense but has improved article usage."

The Agent also receives an Active Engagement instruction when memory is present, directing it to naturally bring up unfinished topics and ask questions about the Learner's interests.

## User Stories

### Memory Generation

1. As a learner, when I end a Practice session, I want the system to automatically generate an updated Topic Memory summarizing what I discussed, so that my conversation context persists across sessions.
2. As a learner, when I end a Practice session, I want the system to automatically generate an updated Learning Profile assessing my language strengths and weaknesses, so that the Agent can track my progress.
3. As a learner, I want memory generation to happen in the background without slowing down my session-end flow, so that I receive my Report immediately.
4. As a learner, I want old versions of my memory to be preserved, so that I (or a human coach) can review how my topics and skills evolved over time.
5. As a learner, if memory generation fails silently, I want the system to gracefully continue with the previous version, so that no data is permanently lost.

### Memory Injection

6. As a learner, when I start a new Practice session, I want the Agent to know what topics I discussed last time, so that it can naturally continue the conversation.
7. As a learner, when I start a new Practice session, I want the Agent to know my current language level and common mistakes, so that it can adapt its responses appropriately.
8. As a learner, I want the Agent to proactively ask me about unfinished topics from previous sessions, so that conversations feel more natural and personal.
9. As a learner, I want the Agent to suggest practice topics based on my Learning Profile weaknesses, so that I focus on areas that need improvement.
10. As a new learner with no history, I want to start a Practice session normally without any memory context, so that the first-time experience is not degraded.
11. As a learner resuming a Practice session mid-conversation, I do not want memory injected again, so that the Agent stays focused on the current conversation flow rather than re-introducing old topics.

### System Prompt Optimization

12. As a developer, I want the system to use structured messages (SystemMessage / UserMessage / AssistantMessage) for conversation prompts, so that the Agent correctly distinguishes instructions from conversation history.
13. As a developer, I want the memory injection to be part of the System Prompt with independent placeholder positions, so that I can control attention priority by ordering the placeholders.
14. As a developer, I want the Active Engagement instruction to appear only when memory is present, so that the Agent does not receive irrelevant instructions during sessions without memory context.

### Data Integrity

15. As a learner, I want my memory to be stored securely in the database with proper user ownership, so that my personal conversation topics are not visible to other learners.
16. As a developer, I want each memory record to carry a version number that increments with each update, so that I can trace how memory evolved and resolve any concurrent-generation edge cases.

## Implementation Decisions

### 1. User Memory Entity

**Decision**: New `UserMemory` JPA entity extending `BaseEntity`. Fields: `userId` (String), `content` (String, max 2000 chars), `type` (enum `MemoryType` with values `TOPIC_SUMMARY` and `LEARNING_PROFILE`), `version` (Integer, starts at 1). No unique constraint — the same user+type can have multiple rows (historical versions).

**Rationale**: Each memory generation inserts a new row rather than updating in-place. Old versions are immutable history. Query for latest uses `findTopByUserIdAndTypeOrderByVersionDesc()`. The `version` field enables tracing which base version a merge was applied to, and would enable rollback or diff-based debugging if needed.

### 2. MemoryType Enum

**Decision**: `MemoryType` enum with `TOPIC_SUMMARY` and `LEARNING_PROFILE`. Stored in `UserMemory.type` via `@Enumerated(EnumType.STRING)`. Defined as a standalone enum in the model package.

**Rationale**: Enums prevent magic strings scattered across the codebase. Adding a new memory type in the future requires only adding an enum constant and a new prompt template — no model changes.

### 3. Async Memory Generation — Trigger Point

**Decision**: Memory generation triggers in `CoachMessageHandler.onEndSession()`, immediately after `SessionStore.completeSession()` commits, before session cleanup. A single `MemoryService.generateMemoryAsync(userId, report)` call passes the `ReportAgent.ReportResult` object.

**Rationale**: `completeSession()` has just committed the Report and all session data to H2. The `ReportResult` object is already in memory at the call site. No additional I/O is needed to gather input data. The call is fire-and-forget — execution moves to a background thread immediately.

### 4. Async Memory Generation — Execution Model

**Decision**: `CompletableFuture.runAsync(task, executor)` using a dedicated `ExecutorService` bean. `MemoryService` submits two independent tasks (one per memory type) to the same thread pool for parallelism. No `join()` or `get()` is called — the HTTP request thread returns immediately and sends the `SESSION_REPORT` to the frontend without any blocking.

**Rationale**: Topic Memory and Learning Profile generation are fully independent — they read different old summaries, use different prompt templates, and write to different rows. Running them in parallel halves total wall-clock time for background processing. The calling thread is never blocked.

### 5. Thread Pool Configuration

**Decision**: Custom `ThreadPoolExecutor` configured via `AsyncConfig` as a Spring Bean:

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| `corePoolSize` | 2 | Matches peak single-user parallelism (2 memory types) |
| `maxPoolSize` | 4 | Handles 2 concurrent users ending sessions simultaneously |
| `keepAliveTime` | 60s | Idle thread cleanup, not persistent |
| `workQueue` | `LinkedBlockingQueue` | Unbounded; task volume is trivially low |
| `rejectedPolicy` | `CallerRunsPolicy` | Never discard; run in caller's thread if queue full |
| Thread naming | `memory-%d` prefix | Debuggability via `Executors.defaultThreadFactory()` wrapper |

The executor bean is qualified as `@Qualifier("memoryExecutor")`.

**Rationale**: Threads spend most of their time waiting on HTTP I/O (DeepSeek API call). CPU-bound work is negligible. A small pool with a large queue keeps resource usage minimal.

### 6. E2E Test Synchronous Execution

**Decision**: In the E2E profile (`application-e2e.yml`), set `app.memory.async: false`. The `AsyncConfig` reads this property and returns a `DirectExecutor` (runs tasks on the calling thread) instead of the thread pool. This makes memory generation synchronous in E2E tests, enabling deterministic assertions on H2 state immediately after session end.

**Rationale**: Asynchronous background tasks in E2E tests create race conditions between test assertions and task completion. Making them synchronous eliminates flakiness. The `DirectExecutor` pattern uses `AbstractExecutorService` with `execute(Runnable)` simply calling `command.run()`.

### 7. Memory Merge — Data Sources

**Decision**: The `MemoryMerge` process uses `SessionReport` fields as input:

| Memory Type | Input Fields |
|-------------|-------------|
| Topic Memory | `SessionReport.summary` (overallAssessment) |
| Learning Profile | `SessionReport.errorSummary` |

The old memory content (previous version) is read from H2 and passed alongside the new data into the LLM prompt.

**Rationale**: `SessionReport` is already an LLM-refined summary of the session. Feeding raw messages would consume more tokens without proportional benefit. This design reuses intermediate LLM output, keeping generation cost low. Additional input fields can be added later if summary quality needs improvement.

### 8. MemoryAgent — LLM Interface

**Decision**: New `MemoryAgent` component analogous to `CorrectionAgent` and `ReportAgent`. Uses the synchronous `ChatLanguageModel.chat(String prompt)` API — no streaming needed. Returns plain text, not JSON. Two methods: `mergeTopic(oldSummary, newSessionSummary)` and `mergeProfile(oldProfile, errorSummary)`. Each loads its own prompt template via `PromptLoader`.

**Rationale**: Memory generation runs in background threads — there is no frontend to stream to. The output is pure natural language (no JSON structure to parse). Using the synchronous API simplifies error handling and eliminates `CompletableFuture` nesting (the outer executor handles parallelism).

### 9. Prompt Templates — Memory

**Decision**: Two new prompt files under `src/main/resources/prompts/`:

- `memory-topic.txt`: Instructs the LLM to merge old topic memory with new session overview. Output constrained to under 500 characters, English only, plain text.
- `memory-profile.txt`: Instructs the LLM to merge old learning profile with new error summary. Output constrained to under 400 characters, English only, plain text.

Both templates handle the case where old memory is empty (first session) by instructing the LLM to create a fresh summary from new data alone. The template uses natural language labels ("Previous topic memory:", "Latest session overview:") to distinguish old from new for the LLM — no structured metadata needed.

### 10. Memory Merge — Failure Handling

**Decision**: If `MemoryAgent` throws an exception (network error, LLM timeout, unparseable response), the task catches and logs the error at `WARN` level. No retries. The previous memory version remains untouched. The session report is already persisted — only the memory update is lost for that session.

**Rationale**: Memory is a quality-of-life feature, not a critical data path. Retry logic adds complexity for minimal gain — the next session end will naturally include both the current session's data and the missed session's data (via the Report, which is already persisted). Losing one generation's incremental merge is harmless.

### 11. SessionService — Runtime Memory Access

**Decision**: `SessionService.init()` reads the latest Topic Memory and Learning Profile from H2 via `MemoryService.loadLatestContent(userId, type)` and passes them into `CoachState.initialState()`. Two new getters (`getTopicMemory(sessionId)`, `getLearningProfile(sessionId)`) expose these values to callers.

**Rationale**: Memory is loaded once per Practice session at initialization. Always using the latest version ensures the Agent sees the most current memory. Loading into CoachState avoids repeated H2 queries during a session. Memory values are simple strings — no caching layer needed beyond the CoachState container.

### 12. CoachState — New Channels

**Decision**: Two new channels added to CoachState: `TOPIC_MEMORY` and `LEARNING_PROFILE`, both typed as `String` with `Channels.base(() -> "")`. The `initialState()` static factory method signature is extended to accept `topicMemory` and `learningProfile` parameters in addition to the existing four.

These channels are defined in the SCHEMA map (for consistency with the langgraph4j pattern) but are never read or written by any graph node — they exist solely for the ConversationAgent to access on the first Turn.

### 13. Conversation System Prompt — Structured Messages Migration

**Decision**: `ConversationAgent` switches from `StreamingChatLanguageModel.chat(String prompt, handler)` to `chat(List<ChatMessage> messages, handler)`. The single flat prompt string is replaced with a structured messages list containing one `SystemMessage` followed by alternating `UserMessage` and `AssistantMessage` entries from conversation history.

**Rationale**: The OpenAiStreamingChatModel correctly serializes `SystemMessage` to `role: "system"`, `UserMessage` to `role: "user"`, and `AssistantMessage` to `role: "assistant"` in the HTTP request to DeepSeek. This lets the LLM distinguish instructions (system) from conversation (user/assistant), preserving instruction attention regardless of history length. The old approach of embedding everything in a single user message caused attention dilution as conversation history grew.

Only `ConversationAgent` switches to structured messages. `CorrectionAgent` and `ReportAgent` remain on the String API, as they have no multi-turn conversation history and gain no benefit from role separation.

### 14. System Prompt Template Restructuring

**Decision**: The old `conversation.txt` (flat template with `{history}` and `{userInput}` placeholders) is replaced with `conversation-system.txt`. This new template contains only the System Prompt portion: persona description, scenario, rules, and three independent memory placeholders (`{topicSummary}`, `{learningProfile}`, `{activeEngagement}`). The `{history}` and `{userInput}` placeholders are removed — conversation history is now passed as structured `ChatMessage` objects.

**Rationale**: Separating the System Prompt from conversation data aligns with the structured messages migration. The three independent memory placeholders (rather than one `{memory_block}`) allow the developer to control attention priority by ordering them in the template — for example, placing `{activeEngagement}` last so the LLM sees the engagement instruction closest to the conversation, maximizing compliance.

### 15. Memory Injection Format

**Decision**: When memory is present for the first Turn, the three placeholders are replaced as follows:

| Placeholder | Has Memory | No Memory |
|-------------|-----------|-----------|
| `{topicSummary}` | `[Conversation Memory]\n` + actual content | Empty string |
| `{learningProfile}` | `[Your Learning Profile]\n` + actual content | Empty string |
| `{activeEngagement}` | `[Active Engagement]\nBased on the memory above, if there is an unfinished topic, naturally bring it up early in the conversation. Ask the user questions about their interests to keep the conversation engaging.` | Empty string |

The section headers and the Active Engagement instruction text are concatenated by the code (either in `ConversationAgent` or via helper methods), not by the LLM. When no memory is present, all three placeholders resolve to empty strings and the System Prompt contains only persona + rules + scenario — identical to current behavior.

### 16. First Turn Detection

**Decision**: First Turn detection in `TurnProcessor.processTurn()` uses the `CoachState.messages` size after the current user message has been added (via `addMessage`). If `historySnapshot.size() <= 1`, it is the first Turn of a new Practice session. This check is combined with a memory-presence check: if either `topicSummary` or `learningProfile` is non-blank, call the memory-aware method; otherwise fall through to the standard method.

**Rationale**: After `addMessage` but before the Agent reply, a new session has exactly one message (the first user input), while a resumed session has 2+ messages (history from before the disconnect). The `<= 1` check naturally handles both cases without needing an additional `isFirstTurn` flag or channel. The memory-presence check ensures the standard code path is used for new users with no history, keeping the System Prompt clean.

### 17. ConversationAgent — Method Split

**Decision**: `ConversationAgent` exposes two public methods: `generateStream(historySnapshot, scenario, persona, handler)` for subsequent turns and `generateStreamFirstTurn(historySnapshot, scenario, persona, topicSummary, learningProfile, handler)` for the first turn with memory. Both construct a `List<ChatMessage>` and call the same underlying streaming API. The only difference is whether memory placeholders are filled.

**Rationale**: Two explicit methods make the caller's intent clear and avoid boolean flag parameters. A future refactoring can extract shared logic into a private helper, but the public API surface remains descriptive.

### 18. Correction and Report Agents — Unchanged

**Decision**: `CorrectionAgent` and `ReportAgent` continue using the synchronous `ChatLanguageModel.chat(String prompt)` API without modification. No structured messages migration for these agents.

**Rationale**: Both agents operate on single prompts with no multi-turn conversation history. The benefit of role separation (system/user/assistant) is zero when there is only one message to send. Keeping them unchanged minimizes risk.

### 19. MemoryService API

**Decision**: `MemoryService` exposes three public methods:

| Method | Purpose | Called By |
|--------|---------|-----------|
| `generateMemoryAsync(userId, report)` | Submits Topic Memory and Learning Profile generation tasks to executor | `CoachMessageHandler` (session end) |
| `loadLatestContent(userId, type)` | Reads the most recent memory content for a given user+type from H2 | `SessionService` (session init) |

Private helper methods handle reading the previous version, calling `MemoryAgent`, and inserting a new version row. Database operations use `TransactionTemplate` for explicit transaction boundaries within async threads.

**Rationale**: `TransactionTemplate` is preferred over `@Transactional` because the async thread is outside Spring's AOP proxy scope. Explicit transaction management eliminates the risk of `@Transactional` silently not applying.

### 20. Existing Components — Required Changes

**Components modified (no new files)**:

- `CoachState`: New `TOPIC_MEMORY` and `LEARNING_PROFILE` channel constants, schema entries, getters, `initialState()` signature
- `SessionService`: `init()` loads memory and passes to `initialState()`, new getter methods
- `ConversationAgent`: Structured messages, two methods, new prompt template
- `TurnProcessor`: First Turn detection and method routing
- `CoachMessageHandler`: `onEndSession()` triggers `generateMemoryAsync()`
- `application.yml`: New `app.memory.async` property (default `true`)
- `application-e2e.yml`: Sets `app.memory.async: false`

**Components unchanged**: `CorrectionAgent`, `ReportAgent`, `CoachGraphBuilder`, `CoachWebSocketHandler`, `SessionStore`, `TokenTracker`, `EntityMapper`, `SessionCleanupLogoutHandler`, all repositories except the new one, frontend HTML/JS/CSS.

### 21. E2E Test Strategy

**Decision**: New E2E test class `EnglishCoachMemoryIT` (`*IT.java` suffix, picked up by failsafe). The test profile disables async execution so memory generation is synchronous. WireMock provides 4 new stubs using scenario state machines for memory LLM responses:

| Stub Group | Scenario | States | Matched By |
|------------|----------|--------|-----------|
| Topic Memory | `memory-topic-rounds` | STARTED → round-2 | Request body contains keyword from `memory-topic.txt` template |
| Learning Profile | `memory-profile-rounds` | STARTED → round-2 | Request body contains keyword from `memory-profile.txt` template |

Test flow: Session1 → END → verify 2 UserMemory rows in H2 (version=1) → Session2 → START → END → verify 4 UserMemory rows (version=1 and version=2), version-2 content differs from version-1.

**Rationale**: DB-level assertions are more reliable than DOM-based assertions for verifying memory behavior. The synchronous execution in the E2E profile eliminates timing flakiness. Following existing WireMock patterns (JSON Path matching, scenario state machines, 19090 port) maintains consistency.

### 22. SESSION_RESUMED — No Memory Data

**Decision**: The `SESSION_RESUMED` WebSocket response does not include memory data. Memory is only used when constructing the System Prompt for the first Turn of a new session. Resume restores an existing conversation mid-flow — injecting memory would interrupt the Learner with old topics when they are in the middle of a current topic.

**Rationale**: Protocol simplicity. The frontend does not render memory summaries — they exist only as LLM context. Adding them to the protocol payload would add bytes without any UI purpose.

### 23. System Prompt Active Engagement — Always Present

**Decision**: A generic active-engagement instruction ("When appropriate, ask the user questions to keep the conversation engaging") is added to the `conversation-system.txt` base template, present in every Turn regardless of memory. The `{activeEngagement}` placeholder provides an additional, memory-specific engagement instruction that only appears on the first Turn when memory exists.

**Rationale**: The Learner explicitly requested that the Agent learn to proactively ask questions. Having a base-level engagement rule ensures the Agent asks follow-ups even in sessions without memory context. The memory-specific instruction amplifies this behavior when there are known topics to pursue.

## Testing Decisions

### What Makes a Good Test

- Tests should verify external behavior: memory is generated, stored, and injected correctly.
- Use `@DataJpaTest` for repository queries — verify `findTopByUserIdAndTypeOrderByVersionDesc()` returns correct ordering.
- Use Mockito unit tests for `MemoryService` — mock `MemoryAgent` and repository, verify correct flow and error handling.
- Use Playwright E2E (`EnglishCoachMemoryIT`) for end-to-end validation — follows the existing `E2ETestBase` pattern with WireMock on port 19090, DOM-based waits, and H2 assertions.
- Do not test LLM prompt quality in automated tests — that is a human evaluation concern.

### Modules to Test

| Module | Test Type | What It Verifies |
|--------|-----------|-----------------|
| `UserMemoryRepository` | `@DataJpaTest` | `findTopByUserIdAndTypeOrderByVersionDesc` ordering, version filtering |
| `MemoryService` | Unit test (Mockito) | Correct merge flow, error not propagating, async submission |
| `MemoryAgent` | Unit test (Mockito) | Prompt template construction, non-empty response handling |
| `ConversationAgent` | Unit test | Structured messages construction, placeholder replacement, first-turn vs subsequent-turn differentiation |
| `TurnProcessor` | Unit test | First-turn detection logic, method routing correctness |
| `CoachState` | Unit test | New channel initialization, getter correctness |
| `EnglishCoachMemoryIT` | Playwright E2E | Full session flow with memory generation, H2 assertions on versioning |

### Prior Art

- Existing unit tests: `SessionStoreTest`, `SessionServiceTest`, `CoachMessageHandlerTest` use Mockito mocks and the `@ExtendWith(MockitoExtension.class)` pattern.
- Existing E2E tests: `EnglishCoachSessionIT` extends `E2ETestBase`, uses Playwright + WireMock, verifies DOM state and H2 data.
- Existing repository tests: `UserRepositoryTest` uses `@DataJpaTest` with H2 in-memory database.
- WireMock stubs: Follow the `WireMockStubs.registerAllStubs()` pattern — scenario state machines with `matchingJsonPath` body matchers.

## Out of Scope

- **Frontend UI for memory** — no memory display, no memory management panel, no "What the Agent remembers about you" view.
- **Manual memory editing** — no API or UI to let the Learner correct or delete memory entries.
- **Memory export/import** — no JSON export of memory history.
- **Embedding-based semantic memory** — no vector search, no RAG pipeline. Memory is purely summary-based via LLM generation.
- **Time decay algorithms** — the LLM decides what to retain or forget organically based on prompt instructions. No explicit "forget topics older than N days" logic.
- **Memory TTL or retention policies** — old memory versions are preserved indefinitely.
- **Cross-Learner memory sharing** — memories are strictly per-user with no sharing or federation.
- **STT/TTS integration** — the `speech/` package remains vacant for V2.
- **Memory in correction or report prompts** — memory context is only injected into conversation prompts, not into correction analysis or report generation.
- **Real-time memory updates during a session** — memory is only generated at session end, not incrementally during Turns.
- **Agent-initiated memory recall mid-session** — the Agent cannot query memory after the first Turn. Subsequent Turns rely on conversation history to carry context forward.

## Further Notes

- The User Memory entity uses `@Enumerated(EnumType.STRING)` for the `type` field with a `MemoryType` enum (`TOPIC_SUMMARY`, `LEARNING_PROFILE`). Adding a new memory type requires only: (1) new enum constant, (2) new prompt template file, (3) new generation task in `MemoryService`.
- Each memory generation produces one DeepSeek API call per type per session. With the default `deepseek-v4-flash` model, cost is negligible given the tiny output sizes (400-500 characters).
- The structured messages migration for `ConversationAgent` is a prerequisite for clean memory injection. Without it, memory text would be concatenated into a flat user message alongside persona instructions and conversation history, causing attention dilution.
- The three-placeholder template design allows reordering without code changes — to prioritize Learning Profile over Topic Memory, swap their positions in `conversation-system.txt`.
- Memory generation uses the same `ChatLanguageModel` bean as `CorrectionAgent` and `ReportAgent` — no separate LLM configuration needed. The same `max-tokens: 2048` output limit applies, which is far above the 400-500 character summaries.
- This PRD implements decisions that evolved through 74 grilling questions covering memory types, data model, async execution, prompt engineering, structured messages migration, first-turn detection, E2E testing, and thread pool configuration.
