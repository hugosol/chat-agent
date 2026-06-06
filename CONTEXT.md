# CONTEXT — Chat Agent

Chat Agent 是一个基于 AI 的英语口语练习 Web 应用。使用者（Learner）选择一个对话模式（AgentMode），通过 WebSocket 与 AI Agent 进行实时对话练习。系统提供流式回复、语法/措辞纠正（Correction）、会话报告（Report）以及跨会话的持久化记忆（UserLearningProfile）。

## People and Identity

| Term | Definition | Aliases to avoid |
|------|------------|-----------------|
| **Learner** | A person who uses Chat Agent to practice spoken English | User, student, customer |
| **Principal** | Spring Security representation of the authenticated Learner within a request context | Current user, auth context |
| **Login session** | The Spring Security HTTP session (JSESSIONID cookie) that proves the Learner is authenticated | Auth session, HTTP session |
| **Remember-Me** | A persistent cookie that survives browser restart and automatically re-establishes a Login session within 14 days | Auto-login, persistent login |
| **Administrator** | The Learner whose username is `admin`. Possesses the ability to manage other Learner accounts (create, disable, reset passwords) through the Profile page | Admin, system admin |
| **Enabled** | An attribute of a Learner account that determines whether they can log in. A disabled Learner is rejected at the next login attempt with a generic credential error. Existing sessions are unaffected | Active, account status, locked |

## Conversation Lifecycle

| Term | Definition | Aliases to avoid |
|------|------------|-----------------|
| **Practice session** | A single English practice conversation from START to END, identified by a UUID | Session, conversation, chat session |
| **Turn** | One round-trip: a Learner message followed by the Agent reply and any corrections | Round, exchange, message-pair |
| **AgentMode** | A pre-defined conversation mode that bundles both the social context (what Scenario used to be) and the AI role (what Persona used to be) into a single selection. The Learner picks one from a dropdown to start a Practice session. Examples: `WORKPLACE_STANDUP` (a daily standup with a friendly teammate), `DAILY_TALK` (casual chat with Chris, a friend / English tutor hybrid). Each AgentMode carries a `templatePath` pointing to per-Mode prompt files. | Scenario, scene, Persona, role, character, conversation type |
| **Mode template** | Per-AgentMode prompt files under `prompts/{templatePath}/` — a `description.txt` (identity + scenario) and a `rules.txt` (behavioral constraints). These are pre-loaded by ConversationAgent at startup into an EnumMap for zero-I/O prompt assembly at request time. | Mode prompt, per-mode template |
| **System Prompt skeleton** | The `conversation-system.txt` file that serves as a structural wrapper, containing only `{Description}` and `{Rules}` placeholders (plus dynamic sections like `{topicSummary}`). Its sole purpose is to let placeholder ordering control LLM attention without modifying per-Mode files. | Skeleton template, prompt wrapper |
| **Streaming** | Agent reply delivered token-by-token to the frontend in real-time, before the full response is complete | Token streaming, incremental output |
| **Stale delta** | A streaming token that arrives at a browser tab that no longer owns the Practice session due to multi-tab competition | Belated token, orphaned delta |

## Correction and Assessment

| Term | Definition | Aliases to avoid |
|------|------------|-----------------|
| **Correction** | A linguistic error found by the CorrectionAgent, with original text, corrected text, type, and explanation | Fix, error, correction item |
| **Correction type** | One of five categories: GRAMMAR, WORD_CHOICE, CHINGLISH, PRONUNCIATION, FLUENCY | Error category, error kind |
| **Correction sidebar** | The slide-out panel displaying detailed Correction items with type, original->corrected, and explanation | Sidebar, correction panel |
| **Correction bubble** | A numbered summary inserted after the Learner message in the chat flow | Correction summary, inline correction |
| **Report** | Post-session analysis: summary, fluency score, error summary, key takeaway | Session report, assessment, feedback |
| **Progress** | Per-Learner aggregated statistics: total sessions completed and total minutes practiced | Statistics, learning progress, track record |

## Multi-Tab Coordination

| Term | Definition | Aliases to avoid |
|------|------------|-----------------|
| **Active Tab** | The browser tab whose WebSocket is currently bound as the owner of a Practice session | Current tab, bound tab, owner tab |
| **Stale Tab** | A browser tab that previously owned a Practice session but has been displaced by a newer Active Tab | Old tab, displaced tab, orphaned tab |
| **Tab takeover** | When resuming a Practice session from Tab B displaces Tab A ownership binding -- intended, not an error | Session steal (avoid), ping-pong, binding switch |
| **Visibility resume** | The pattern where a Stale Tab auto-resumes its Practice session via the Page Visibility API when the Learner switches back to it | Auto-resume, reactivation refresh |
| **State rebuild** | The frontend act of clearing all DOM and reconstructing chat messages, correction bubbles, and sidebar items from a fresh SESSION_RESUMED payload | Full render, DOM reset, re-render |

## Runtime State

| Term | Definition | Aliases to avoid |
|------|------------|-----------------|
| **ChatState** | The in-memory langgraph container holding a Practice session messages, corrections, mode, and userId | Session state, graph state |
| **Active states map** | The ConcurrentHashMap holding all active ChatState instances, keyed by Practice session UUID | activeStates (code name), session store, state cache |
| **Binding map** | The one-to-one ConcurrentHashMap mapping a Practice session UUID to its Active Tab WebSocket ID | sessionToWs (code name), WS binding, connection map |
| **Checkpoint** | A langgraph snapshot of ChatState preserved in MemorySaver, enabling state restoration after a WebSocket disconnect | Snapshot, savepoint |

## Memory and Long-Term Context

| Term | Definition | Aliases to avoid |
|------|------------|-----------------|
| **UserLearningProfile** | A persistent summary record in H2 that captures a Learner's conversation topics or learning progress, surviving across Practice sessions | Memory, memory record, profile |
| **Learning Profile** | A UserLearningProfile of type LEARNING_PROFILE — a 400-character summary of the Learner's English strengths, weaknesses, and improvement trends | Learning summary, skill profile |
| **Memory Merge** | An LLM-driven process that combines an old Learning Profile (version N) with a newly completed session's Report to produce an updated version (N+1). Topic summary is handled by MemoryCue — no LLM merge, each new session generates fresh MemoryCue entries directly. | Incremental update, memory consolidation |
| **Memory Injection** | The unified RAG pipeline that injects historical conversation context into each Turn's System Prompt. On each user message, EmbeddingService performs semantic search against past MemoryCue records. When RAG returns matches, they populate the MemoryCueQueue for cross-turn recall. When RAG returns no matches (cold start or topic gap), a fallback anchor — the most recent session's last completed MemoryCue — is loaded from H2 to provide conversation continuity. This fallback anchor survives approximately one round before eviction. LearningProfile is injected on the first Turn only. | Context injection, memory recall |
| **Active Engagement** | A System Prompt instruction directing the Agent to proactively bring up unfinished topics and ask questions based on the injected memory | Initiative prompt, engagement instruction |
| **Memory Version** | A sequential integer on each UserLearningProfile record, starting at 1 and incremented by each Memory Merge operation | Version number, revision counter |
| **MemoryCue** | A structured memory record in the `memory_cues` table — one row per conversation topic segment, containing topic and summary. Generated post-session by MemoryCueAgent via two-step LLM analysis (topic switch detection + per-segment summarization). Vectorized by EmbeddingService into an InMemoryEmbeddingStore for semantic RAG retrieval starting from Round 4. | Structured memory, topic cue |
| **Memory Cue Split** | The first LLM step in MemoryCue generation: analyzes the full conversation transcript and detects topic switch points, returning a list of message index boundaries | Topic switch detection, split detection |
| **Memory Cue Entry** | The second LLM step in MemoryCue generation: for each identified segment, produces a `(topic, summary)` pair in structured JSON | Segment summarization, cue generation |
| **MemoryCueQueue** | An LRU ordered set (capacity = topK + 1) living in ChatState across Turns. The +1 extra slot is deliberate — it serves as an LRU buffer so newly-loaded matches don't immediately evict older entries. On first RAG load (queue empty), `topK + 1` results are pushed; on subsequent loads, `topK` results are pushed with dedup (same cueId refreshes to head). When full, the least-recently-accessed entry is evicted from the tail. Fallback anchor entries (inserted when RAG returns no matches) survive approximately one round before being evicted via FIFO — they help the Agent maintain continuity without polluting the long-term queue. The queue is sorted FIFO + dedup, not by relevance score. Injected into the System Prompt as a numbered list sorted tail→head (old→new) with time labels per entry. | LRU queue, memory queue, context queue |
| **MemoryCue Retrieval** | Vector semantic retrieval of historical MemoryCue records using ONNX embeddings (all-MiniLM-L6-v2, 384 dimensions). Starting from Round 2 (after UserLearningProfile's 1-turn window), each user message triggers a cosine-similarity search against past cues filtered by userId × AgentMode. Results are managed through MemoryCueQueue — first load retrieves `topK + 1` entries, subsequent loads retrieve `topK` entries, with LRU dedup and eviction for cross-turn memory migration. | RAG retrieval, semantic search, vector recall |
| **EmbeddingService** | The RAG vectorization module: manages InMemoryEmbeddingStore lifecycle (init from disk or H2, async indexing, search with metadata filtering, JSON disk serialization). Uses a dedicated `embeddingExecutor` thread pool for ONNX CPU-bound operations. | Vector service, embedding module |
| **CueMatch** | A DTO record returned by `EmbeddingService.search()` containing `(cueId, topic, summary, score, createdAt)`, decoupled from JPA entities. `createdAt` carries the original MemoryCue creation time for time-aware labeling in the System Prompt. | Search result, match record |
| **MemoryContent** | A DTO record encapsulating all memory data injected into the System Prompt: `(lastConversationTimeLabel, learningProfile, cueMatches)`. `cueMatches` carries a structured list of CueMatch records instead of pre-joined text, eliminating string-splitting fragility. Time labels are applied by ConversationAgent during prompt formatting. | Prompt payload, memory injection package |

## Observability

| Term | Definition | Aliases to avoid |
|------|------------|----------------|
| **LLM Call Log** | Every LLM API call is persisted as a row in the `llm_call_logs` table: `request_prompt` (full prompt blob), `system_prompt` and `chat_history` (split for structured querying), `response_text`, token usage (input/output), duration (ms), and status (SUCCESS/ERROR). Sync agents (Correction, Report, Memory, MemoryCue) are intercepted transparently via a `LoggableChatModel` wrapper; the ConversationAgent (streaming) is logged manually via `TurnProcessor`. Writes are async (non-blocking) and records older than 3 days are cleaned up on startup. Used for debugging and cost tracking. | LLM log, call log, API log |

## Data Isolation

| Term | Definition | Aliases to avoid |
|------|------------|-----------------|
| **Ownership** | A data row belongs to exactly one Learner, enforced by the userId column on the Practice session row | Data isolation, tenant ownership |
| **Cross-session query** | A query that spans multiple Practice sessions (e.g., history listing, progress aggregation) -- must filter by Learner | Multi-session query, user-scoped query |
| **Per-session query** | A query scoped to a single Practice session UUID -- naturally isolated without a Learner filter | Single-session query, session-scoped query |

## Flashcard 模块

| Term | Definition | Aliases to avoid |
|------|------------|-----------------|
| **Flashcard** | A vocabulary review card created by the Learner, containing front (word/expression) and back (definition), with user-assigned Tags for organization. Independent of Practice sessions. | Card, 闪卡 |
| **Front / Back** | The two faces of a Flashcard: front = the word or phrase to remember, back = the translation or correction. | 正面/背面, question/answer |
| **Tag** | A label for categorizing Flashcards. Linked to Cards via Many-to-Many. The `type` field is nullable (null in MVP), reserved for future Deck concept — when set to a specific value, the Tag effectively becomes a deck. | Label, 标签, 牌组 |
| **FSRS-6** | Free Spaced Repetition Scheduler version 6 — a 21-parameter algorithm for computing review intervals. Fully integrated: initializes Card scheduling state on creation, and applies `repeat()` on every Review via `ReviewService.rateCard()`. The Scheduler accepts runtime configuration (`FsrsSchedulerConfig`) that merges system-managed parameters (`FsrsParameters`) with user-configurable preferences (`UserPreferences`). | Scheduler, 算法 |
| **FsrsSchedulerConfig** | An immutable runtime record bundling all 7 scheduling parameter categories (W[21], desired_retention, learning_steps, relearning_steps, maximum_interval, enable_fuzz, enable_short_term) into a single object passed to the FsrsScheduler. Contains static `defaults()` providing the FSRS-6 standard values. Also provides `merge(FsrsParameters, UserPreferences)` to produce a Learner-specific config by overlaying user preferences onto system defaults. | SchedulerConfig, runtime params |
| **FsrsParameters** | A JPA entity (`fsrs_parameters` table) storing system-managed FSRS parameters per Learner: 21 weight columns (w0–w20) plus `enable_short_term`. Associated via `userId` string (soft link, no foreign key). Created by `DataInitializer` with default FSRS-6 weights for new Learners. Updated by the Optimizer when recomputing optimal parameters. The Learner never directly edits these values. | System params, FSRS weights |
| **Learning step** | The time intervals for new Cards in the Learning state (cardState=1). Default: two steps of 1 minute then 10 minutes (`"1m,10m"`). Stored in `UserPreferences.learningSteps` as a shorthand string; parsed into `Duration[]` at runtime. Learner-configurable via the settings UI. | 初学间隔, learning_steps |
| **Relearning step** | The time intervals for lapsed Cards in the Relearning state (cardState=3). Default: one step of 10 minutes (`"10m"`). Stored in `UserPreferences.relearningSteps`. A Card enters Relearning when rated Again in Review state; after completing relearning steps, it graduates back to Review. | 重学间隔, relearning_steps |
| **Desired retention** | A float (0.0–1.0) defining the target probability of correctly recalling a Card at its scheduled review time. Default 0.9 (90%). Higher values produce shorter intervals and more frequent reviews; lower values reduce review load at the cost of more forgetting. Stored in `UserPreferences.desiredRetention`. | 目标正确率, request_retention |
| **Maximum interval** | An integer capping how many days into the future a Card can be scheduled. Default 36500 (≈100 years, effectively no cap). Stored in `UserPreferences.maximumInterval`. Setting it to 365 means no Card is ever scheduled more than one year away. | 最大间隔, max_interval |
| **Fuzz** | A small random perturbation applied to Review-state intervals ≥ 2.5 days to prevent Cards from clustering on the same due date. Enabled by default; controlled by `UserPreferences.enableFuzz`. The PRNG is `AleaPrng` for cross-implementation reproducibility. | interval fuzzing, enable_fuzz |
| **Short-term stability** | An algorithm behavior that applies a same-day stability multiplier when a Card is reviewed multiple times within 24 hours. Always enabled at the system level (`enable_short_term` in `FsrsParameters`), not exposed to the Learner. Controls how same-day cramming affects long-term memory estimates. | same-day multiplier, enable_short_term |
| **Shuffle due cards** | A boolean (`UserPreferences.shuffleDueCards`) controlling whether multiple Cards due at the same time are presented in random order rather than chronological order. Applies to all four Review Modes. When disabled, Cards are ordered by ascending `due`. | 洗牌, randomize due order |
| **FSRS Optimizer** | A module that computes optimal W[21] parameters from a Learner's ReviewLog history using numerical optimization (Apache Commons Math3, Nelder-Mead). Triggerable via REST (`POST /api/fsrs/optimize`) or scheduled background task. After optimization, automatically reschedules all Cards to reflect the new parameters. Also provides optimal desired retention computation and learning step recommendations. | 优化器, parameter optimization |
| **Reschedule** | The process of re-running a Card's entire ReviewLog history through the Scheduler with new parameters to compute its current scheduling state. Triggered automatically after the Optimizer produces new W[21] values. Updates each Card's `due`, `stability`, `difficulty`, and other FSRS state fields without modifying the ReviewLog records themselves. | 重调度, card reschedule |
| **Alea PRNG** | A deterministic pseudo-random number generator (Johannes Baagøe's algorithm) used for fuzz in the FSRS scheduler. Replaces `java.util.Random` to enable cross-implementation reproducibility. | Fuzz, deterministic random |
| **Two-stage input** | The flashcard creation UI flow: Stage 1 = minimal panel (~60px) with only front input, Stage 2 = expanded panel (~70vh) with back input + chip tag input + save button. Designed to let the Learner type while still seeing the chat. | 两阶段录入 |
| **Chip tag input** | An autocomplete tag input where selected tags render as inline "chips" with × to remove. Data sourced from `GET /api/tags`, filtered client-side. Backspace on empty input removes the last chip. | Chip input, 标签输入 |
| **activePanel** | A React state variable on the chat page that enforces mutual exclusivity between the Debug panel and the Flashcard panel — opening one collapses the other. Managed by the `Header` component via `activePanel`/`onTogglePanel` props. Originally a `window`-global variable before the React migration. | Panel state, panel toggle |
| **FlashcardController** | The first `@RestController` in the codebase, serving `POST /api/cards/add` (create card with Tag auto-upsert) and `GET /api/tags` (autocomplete data source). Authenticated via JSESSIONID cookie; CSRF exempt via `SecurityConfig`. | REST controller, 闪卡 API |
| **BatchOperationLog** | A JPA entity recording each import/export operation for audit purposes. Contains operation type (IMPORT/EXPORT), target deck tag, filename, success/skip counts, and failure details as JSON. Queried via H2 console; no frontend display page. | Batch log, 批量操作日志 |
| **CSV Import/Export** | Bulk import/export of flashcards per deck tag via CSV files. CSV format includes front, back, and full FSRS state fields (stability/difficulty/cardState/due/reps/lapses/lastReview), with cardState using human-readable text mapping (New/Learning/Review/Relearning) for cross-system readability. Import is limited to a single deck tag, validates all rows before transactional insertion, and returns detailed error list on failure. | CSV 批量导入导出 |
| **BatchOperationModal** | A shared modal component on the manage page for both import and export operations, using a three-stage state machine: select-tag (choose deck tag) → ready (select file or confirm export) → loading (upload spinner) → result (show success/skip results). | Batch modal, 批量操作模态框 |
| **Deck** | A Tag whose `type` is set to `"deck"`, grouping Flashcards into a reviewable collection. A Deck is selected in the DeckPicker to scope a Review Session. | 牌组 |
| **Review** | The act of rating a Flashcard (Again / Hard / Good / Easy), applying the FSRS-6 `repeat()` function to compute the card's next `due` date and update its scheduling state fields (stability, difficulty, cardState, step, reps, lapses, lastReview). The Scheduler is configured per-request from the Learner's merged `FsrsSchedulerConfig` (system defaults + user preferences + optimizer overrides), enabling personalized intervals. If it is the card's first Review, `firstReviewDate` is set. Each Review creates a ReviewLog. | 复习, rate, 评分 |
| **ReviewLog** | A JPA entity stored in the `review_logs` table recording each individual Review event with before/after FSRS state snapshots (stability, difficulty, state, step), the rating, elapsed days, and whether it was the Card's first review. Used for audit and analysis; queried per-card or per-Learner. | 复习日志 |
| **ReviewStats** | A transient record holding daily aggregate counts, computed from database COUNT / MIN queries at request time. Fields: `reviewedToday` (cards whose `lastReview ≥ todayStart`), `remaining` (due cards: `cardState ≠ 0 AND due ≤ now`), `learnedToday` (cards whose `firstReviewDate ≥ todayStart`), `dailyLimit` (from UserPreferences), `nextDueAt` (earliest future `due` in the deck). Not persisted — derived fresh each request. | 复习统计 |
| **Review Session** | An ephemeral review exercise from the Learner clicking "开始" until all available cards per the selected mode are exhausted. Implemented as a React state machine (deck-picker → reviewing → complete). NOT a persisted entity — counts are not tracked per-session; stats always reflect the full day's work. | 一轮复习, review round |
| **Review Mode** | One of four strategies for selecting the next Card during a Review Session: `STANDARD` (due cards then new cards up to the daily limit), `REVIEW_ONLY` (only due cards), `NEW_ONLY` (only new cards up to the daily limit), `CRAM` (random card from the entire Deck, no state filter, no limit). When `shuffle_due_cards` is enabled, due-card ordering is randomized instead of chronological. | 复习模式 |
| **Daily New Card Limit** | A per-Learner preference stored in `UserPreferences.newCardDailyLimit` that caps how many new cards (cardState = 0) the Learner can encounter per day in STANDARD or NEW_ONLY modes. Read by the backend on every request; saved by DeckPicker on "开始". | 每日新卡上限 |
| **todayStart** | A timestamp representing "start of today" for the Learner, computed from their `timezone` and `dayStartHour` preferences. Used as the threshold for `reviewedToday` and `learnedToday` COUNT queries so that stats reflect the Learner's own day boundary. | 今日起始时间 |

## Relationships

- A **Learner** has exactly one **Login session** at a time (per browser).
- A **Login session** may include a **Remember-Me** cookie for persistence across browser restarts.
- A **Learner** can have zero or more **Practice sessions**, each with a unique UUID.
- A **Learner** has exactly one **Progress** record.
- A **Practice session** is owned by exactly one **Learner** (via userId).
- A **Practice session** contains zero or more **Turns**.
- A **Turn** consists of one Learner message, one Agent reply, and zero or more **Corrections**.
- A **Practice session** at its end produces exactly one **Report**.
- A **Practice session** has at most one **Active Tab** at any moment (via the Binding map).
- A **Tab takeover** occurs when a Learner resumes their own Practice session from a different tab, displacing the previous Active Tab.
- A **Stale Tab** self-heals via a **Visibility resume** that triggers a **State rebuild**.
- A **Learner** has zero or more **UserLearningProfile** records, each identified by type.
- A **Practice session** end triggers one **Memory Merge** for **Learning Profile** (LLM-based), executed asynchronously alongside **MemoryCue** generation.
- The first **Turn** of a new Practice session includes a **Memory Injection** of the latest Learning Profile and RAG-retrieved MemoryCue into the Agent's System Prompt.
- Each completed **MemoryCue** segment uses a version counter to track generation order. Old versions remain as immutable history.
- Each **UserLearningProfile** record now stores the `session_id` of the **Practice session** that triggered its generation, enabling traceability.
- A **Practice session** end triggers **Memory Cue Split** to detect topic switch points, then fires one **Memory Cue Entry** per segment in parallel — each producing a **MemoryCue** row with COMPLETED or SEGMENT_FAILED status. After all segments complete, **Tag Consolidation** merges equivalent tags across all historical MemoryCue rows for the same Learner+Mode into canonical forms in-place.
- A **Learner** has exactly one **UserPreferences** record, storing new card daily limit, timezone, day start hour, last-used deck/mode, and user-configurable FSRS parameters (learning steps, relearning steps, desired retention, maximum interval, fuzz toggle, shuffle toggle).
- A **Learner** has exactly one **FsrsParameters** record (soft-linked via userId) storing system-managed scheduler weights. Created automatically on first access; updated by the **FSRS Optimizer**.
- A **Card** has zero or more **ReviewLogs** — one per **Review** event.
- A **ReviewLog** belongs to exactly one **Card** and exactly one **Learner**.
- A **ReviewStats** is computed entirely from **Card** table COUNT/MIN queries at request time — it has no entity and is not persisted.

## Flagged Ambiguities

- **Session** was used to mean both the Spring Security HTTP session (Login session) and the English practice conversation (Practice session). These are wholly distinct concepts. A Login session authorizes access; a Practice session contains conversation data. **Resolution:** Always prefix -- "Login session" for auth, "Practice session" for English conversation. Never say "session" alone.

- **State** was used to mean both the ChatState object (a single Practice session data container) and the Active states map (the collection of all active ChatState instances). **Resolution:** Use "ChatState" for the individual container; use "Active states map" for the collection.

- **Token** was used to mean both an LLM usage unit (counted by TokenTracker) and a CSRF token. **Resolution:** Use "LLM token" or "token usage" for the LLM context; use "CSRF token" for security context. Never say "token" alone.

- **Memory** was used to mean both the in-memory runtime state (ChatState, Active states map) and the persistent cross-session UserLearningProfile entity. **Resolution:** Use "In-memory state" or "Active states map" for runtime; use "UserLearningProfile" for the persisted H2 record. Never say "memory" alone.

- **Resume** was used for both the WebSocket protocol message (RESUME_SESSION) and the Page Visibility API pattern (Visibility resume). **Resolution:** "Protocol resume" for the WebSocket message; "Visibility resume" for the tab-activation auto-refresh. Both restore UI state but through different triggers.

## Example Dialogue

> **Dev:** "When a Learner clicks Start, we create a Practice session. But what if they already have an active one from another tab?"
>
> **Domain expert:** "The Binding map is one-to-one -- the new Tab takeover overwrites the old binding. The Stale Tab will not know it is displaced until its next interaction."
>
> **Dev:** "So the old tab shows stale conversation data until the Learner types?"
>
> **Domain expert:** "No -- we added Visibility resume. When the Learner switches back, the tab detects it is visible again and does a protocol resume, which triggers a full State rebuild from the latest ChatState."
>
> **Dev:** "What about in-flight streaming? If a Turn was mid-reply when the takeover happened, the Stale delta goes to the old tab?"
>
> **Domain expert:** "Fixed -- the Turn processor now resolves the Active Tab at send time. The Agent reply always lands on whichever tab owns the Binding at that moment."
>
> **Dev:** "And the Stale Tab Visibility resume picks up the completed reply?"
>
> **Domain expert:** "Exactly. And the frontend delta handler skips any msgId that already has a complete Agent message from the State rebuild. No duplicate bubbles."
>
> **Dev:** "Does the Agent remember what we talked about last week?"
>
> **Domain expert:** "Yes — at session end, MemoryCue entries are generated from the conversation transcript and vectorized by EmbeddingService. The Learning Profile runs a Memory Merge that combines the old profile with the new error data. On the next Practice session's first Turn, a Memory Injection puts the latest Learning Profile and RAG-retrieved MemoryCue into the System Prompt."
>
> **Dev:** "So the Agent just knows 'Hugo went to Japan' without me re-telling it?"
>
> **Domain expert:** "Exactly. And the Active Engagement instruction tells the Agent to naturally bring it up: 'So how was that trip to Japan you mentioned last time?'"
>
> **Dev:** "What happens to old Memory Versions?"
>
> **Domain expert:** "They're kept. Each Memory Merge inserts a new row with version N+1. Old versions are immutable — we can trace how the Learner's profile evolved."
>
> **Dev:** "And does logout clean up the Active states map?"
>
> **Domain expert:** "Yes — the cleanup handler walks the map and removes every Practice session owned by that Learner. UserLearningProfile persists in H2 regardless."
