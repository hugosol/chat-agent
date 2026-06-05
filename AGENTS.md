# AGENTS.md �?Chat Agent

## Quick Reference

```bash
# Verify the build
mvn compile

# Run unit tests (Java + Vitest frontend tests)
mvn test

# Run E2E regression tests (Playwright + WireMock, first run downloads Chromium ~150MB)
mvn verify

# Run locally with local profile (api-key in application-local.yml)
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Or set env var directly
set DEEPSEEK_API_KEY=sk-your-key
mvn spring-boot:run

# H2 console (for debugging data, local profile opens without auth)
# http://localhost:8080/h2-console �?jdbc:h2:file:./data/englishcoach �?sa / (empty)

# File logs (only with local profile, written to ./logs/)
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Key Facts

- **Java 17** / **Spring Boot 3.4.7** / **Maven** �?`mvn compile` for build verification, `mvn test` for unit tests (Java + Vitest), `mvn verify` for E2E tests.
- **Spring Security**: form login + remember-me + BCrypt. SecurityConfig is always loaded �?no conditional annotations. Auth behavior is driven entirely by `app.security.permit-all-paths` in YAML config. Default user: `admin/admin123` (from `application.yml` �?`DataInitializer`).
- **E2E tests**: Playwright (Java) + WireMock 3.x �?`src/test/java/com/hugosol/chatagent/e2e/`. Seven IT test classes: `ChatAgentSessionIT`, `ChatAgentResumeIT`, `ChatAgentMemoryIT`, `DailyTalkIT`, `ChatAgentMemoryCueIT`, `ManagePageIT`, `FlashcardIT`. WireMock runs on fixed port `19090`, mocks DeepSeek at HTTP layer. DOM-based waits (no WebSocket frame interception). Screenshots auto-saved to `target/e2e-screenshots/` via `@AfterEach`. Uses `@ActiveProfiles("e2e")` + `application-e2e.yml` with `permit-all-paths: [/**]` to bypass authentication.
- **Package**: `com.hugosol.chatagent`
- **DeepSeek via LangChain4j**: uses OpenAI-compatible adapter (`dev.langchain4j:langchain4j-open-ai`). Default model is `deepseek-v4-flash` (see `application.yml`, not README which says `deepseek-chat`).
- **Two LLM beans**: `ChatLanguageModel` (sync, used by `TaskRunner` for all sync agents) + `StreamingChatLanguageModel` (`OpenAiStreamingChatModel`, for ConversationAgent). Both in `LangChain4jConfig`. `ChatLanguageModel` is returned directly without `LoggableChatModel` wrapper �?logging is handled by `TaskRunner.requestModel()`.
- **langgraph4j**: `org.bsc.langgraph4j:langgraph4j-core:1.8.16` �?independent library, NOT a LangChain4j subproject. State channels use `Channels.base(() -> default)` and `Channels.appender(ArrayList::new)`, **not** `Channels.of()`.
- **1-node graph**: `START �?correction �?END`. Only CorrectionNode remains in the graph. Conversation was extracted to Service layer for streaming.
- **Parallel execution**: `TurnProcessor.processTurn()` launches conversation synchronously on the caller thread (generates prompt, registers streaming handler, returns immediately �?tokens stream via OkHttp dispatch threads) and correction (graph) via `CompletableFuture.runAsync(task, llmRequestExecutor)`. Conversation tokens stream to frontend immediately; correction results arrive asynchronously.
- **MemorySaver checkpoints**: survive page refresh, **lost on server restart**. No persistence until session ends.
- **Session resume**: WS disconnect no longer destroys `activeStates`. Frontend stores `sessionId` in `localStorage` and sends `RESUME_SESSION` on reconnect.
- **Multi-tab**: `sessionToWs` map is one-to-one (sessionId �?wsId, flipped from old `wsToSession`). Page Visibility API triggers auto-resume on tab activation. Stale delta protection skips streaming tokens for already-rendered messageIds.
- **Frontend**: fully migrated to React + TypeScript in `src/main/frontend/` (Vite build to `static/`). **Phase 4 complete**: all chat page and manage page features are 100% React (Header, ChatInput, MessageList, Footer, CorrectionSidebar, StatusBar, ReportModal, DebugPanel, FlashcardPanel, ManageApp, CardsTab, TagsTab, etc.). `app.js`, `flashcard.js`, `style.css`, and manage page vanilla JS files deleted. `ChatProvider` + `chatReducer` (React Context + useReducer) manages all WebSocket messages and state — no vanilla bridge. Tests under `src/__tests__/` include `chat/`, `manage/`, `shared/`, `state/`, `header/`, `correction-sidebar/`. E2E tests use `data-testid` selectors (CSS Modules hash class names). Implementation patterns and browser compat notes in `docs/frontend-notes.md`. Migration details in `docs/adr/frontend-react-migration.md#implementation-notes`.
- **Correction display**: numbered summary bubble (`1. original �?corrected` / `2. ...`) inserted after user message in chat flow; detailed items in sidebar (type + explanation). Sidebar is an absolute overlay (no longer squeezes chat) and starts collapsed. Floating ⚠️ N �?badge toggles visibility.
- **WebSocket endpoint**: `/ws/chat` �?JSON protocol. Handshake authenticated via Spring Security (JSESSIONID cookie). If Principal is null (E2E profile), falls back to `"anonymous"`.
- **Architecture document**: `docs/architecture.md` is the design blueprint + decision log. Read before structural changes; **do not edit casually**.

## User Module & Data Isolation

- **User entity**: `User` (id, username, password/BCrypt), table `users`. `UserRepository.findByUsername()`.
- **Initial user seeding**: `DataInitializer` (CommandLineRunner) reads `app.initial-users` from YAML, BCrypt-hashes passwords, creates users only if not existing.
- **PasswordEncoder**: standalone `PasswordEncoderConfig` (always loaded), separate from `SecurityConfig` for reuse outside web context.
- **Data isolation**: `Session.userId` (NOT NULL) added. All per-session queries (findBySessionId) naturally isolated by UUID. Only cross-session queries (`getHistory`, user progress) filter by `userId`. `UserProgress` is now per-user (was singleton).
- **Runtime user context**: `ChatState` has `USER_ID` channel. `SessionService.getUserId()` reads from ChatState �?works in async threads (no ThreadLocal dependency).
- **RESUME_SESSION validation**: checks session ownership via `ChatState.userId` before allowing resume. Returns "Session not found" if userId mismatch.
- **Session cleanup on logout**: `SessionCleanupLogoutHandler` clears all `activeStates` for the logging-out user. Tab close without logout only unbinds WS �?sessions survive for resume.

## Environment

| Variable | Default | Notes |
|----------|---------|-------|
| `DEEPSEEK_API_KEY` | *(required by default)* | Bypass with `local` profile: place key in `application-local.yml` |
| `DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | |
| `DEEPSEEK_MODEL` | `deepseek-v4-flash` | Config in `application.yml` |
| `LOG_DIR` | `./logs/` | File log output directory (local profile only) |

No `.env` file support �?use `local` profile (`application-local.yml`, gitignored) or set vars directly in shell.

## Data & Persistence

- **H2 file database** at `./data/englishcoach` (the `data/` directory is gitignored).
- `spring.jpa.hibernate.ddl-auto: update` �?tables auto-created on first run.
- Data is written to H2 **only at session end** (`SessionDbStore.completeSession()`). Runtime state stays in `SessionService.activeStates` (ConcurrentHashMap) + MemorySaver checkpoints.
- H2 console enabled at `/h2-console`. Use `DB_CLOSE_DELAY=-1` to keep data alive between requests. With `local` profile, H2 console does not require login.

## Project Structure

```
com.hugosol.chatagent/
├── graph/           # LangGraph: ChatState (6 channels incl. USER_ID + MODE) + 1 node + builder
�?  └── nodes/       # CorrectionNode (only remaining node)
├── agent/           # ConversationAgent (streaming), CorrectionAgent, ReportAgent, LearningAgent, MemoryCueAgent
�?  └── common/       # TaskRunner (sync engine), TaskDefinition, TaskName, TaskContext, ErrorStrategy
├── flashcard/       # FSRS-6 scheduler (repeat + init) + CardState + Rating enum + AleaPrng (deterministic fuzz)
├── websocket/       # ChatWebSocketHandler (WS entry), ChatMessageHandler (protocol logic)
├── controller/      # FlashcardController �?REST API (Cards CRUD + Tags CRUD + Import/Export, 10 endpoints)
├── protocol/        # ClientMessage/ServerMessage sealed types, ProtocolDispatcher, MessageHandler
├── service/         # SessionService (state + tokens + sessionToWs), TurnProcessor (parallel turns),
�?                   # SessionComplete (session-ending pipeline), SessionDbStore (entity persistence),
�?                   # FlashcardService (createCard with FSRS init + Tag upsert),
�?                   # LearningProfileService, MemoryCueService,
�?                   # EmbeddingService (RAG vectorization), SessionCleanupLogoutHandler, TokenTracker, EntityMapper
�?  └── card/          # CardCsvParser (CSV解析器), CardBatchService (批量导入/导出编排)
├── model/           # JPA entities + enums: User, Session, Message, Card, Tag, ErrorRecord, SessionReport,
�?                   # UserProgress, UserLearningProfile, MemoryCue, AgentMode, MemoryCueStatus, TimeLabel,
�?                   # BatchOperationLog, BatchOperationType, BatchOperationStatus, ...
├── repository/      # Spring Data JPA repos (11: User, Session, Message, Card, Tag, ErrorRecord, SessionReport,
�?                   # UserProgress, UserLearningProfile, MemoryCue, LlmCallLog, BatchOperationLog)
├── dto/             # Data transfer records: MessageData, CorrectionData, MemoryContent, CueMatch, AddCardRequest/Response, TagResponse, ImportResult, ImportError
├── config/          # LangChain4jConfig, SecurityConfig, WebSocketConfig, AsyncConfig,
�?                   # AppProperties, PasswordEncoderConfig, DataInitializer, PromptLoader
└── speech/          # (vacant �?V2 will add STT/TTS adapters when needed)

src/test/java/com/hugosol/chatagent/e2e/
├── ChatAgentSessionIT.java    # Full session: 3 turns + sidebar + H2 assertions
├── ChatAgentResumeIT.java     # Page reload �?session resume verification
├── ChatAgentMemoryIT.java     # Two sessions back-to-back �?memory merge verification
├── DailyTalkIT.java           # DAILY_TALK mode �?teaching-style corrections
├── ChatAgentMemoryCueIT.java  # Session end �?MemoryCue structured generation verification
├── ManagePageIT.java          # Manage page: tag/card CRUD, search, sort, deck chip filtering, pagination, detail modal, TTS
├── FlashcardIT.java           # 闪卡录入：两阶段面板 �?标签创建 �?保存 �?H2 数据验证（不依赖 WireMock，闪卡不�?LLM�?br>├── FlashcardBatchIT.java      # 闪卡批量导入/导出：完整往返流程 �?导出 CSV �?删卡 �?导入 CSV �?FSRS 状态还原验证
└── helper/
    ├── E2ETestBase.java          # @SpringBootTest base: WireMock (19090), Playwright, DOM waits, @ActiveProfiles("e2e")
    └── WireMockStubs.java        # Scenario state machine stubs (memory cue stubs included, JSON Path body matching)

src/test/resources/
├── application-e2e.yml           # E2E profile: mem H2, base-url �?localhost:19090, permit-all-paths: [/**]
├── prompts/                      # Test prompt overrides (correction, report, memory-cue-split, memory-cue-entry)
└── wiremock/                     # Mock response files (conv SSE + corr JSON + report + memory + memory-cue JSON)
```

## WebSocket Protocol

```
Client �?Server:
  START_SESSION { mode }
  USER_INPUT { text: "...", messageId: 1 }
  END_SESSION
  RESUME_SESSION { sessionId: "..." }

Server �?Client:
  SESSION_STARTED { sessionId, mode }
  AGENT_STREAM_DELTA { delta: "Sounds", messageId }
  AGENT_STREAM_END { text: "full text", messageId, tokenUsage }
  CORRECTION_RESULT { corrections: [...], messageId }
  SESSION_RESUMED { sessionId, mode, messages, corrections, tokenUsage }
  STATE_UPDATE { state, tokenUsage }
  TOKEN_WARNING { usage }
  SESSION_REPORT { report: {...} }
  SESSION_HISTORY { sessions: [...] }
  ERROR { message }
```

## Conventions & Gotchas

- **E2E tests**: `*IT.java` suffix. `mvn test` runs unit tests (surefire + Vitest frontend tests via `frontend-test` execution, excludes IT). `mvn verify` runs E2E (failsafe, includes IT). Uses `@ActiveProfiles("e2e")` �?`application-e2e.yml` (memory H2, WireMock base-url, all paths permitted).
- **WireMock**: Fixed port `19090`. `matchingJsonPath("$.messages[0].content", containing(keyword))` for body matching (avoids JSON encoding issues). Scenario state machine (`STARTED �?round-2 �?round-3`) controls stub rotation across turns. `Runtime.addShutdownHook` stops WireMock so all IT classes share one server instance.
- **DOM waits**: No WebSocket frame interception (Playwright Java's `onFrameReceived` unreliable). All waits use `page.waitForFunction()` on DOM state: input bar visibility (session started), input disabled �?enabled (streaming done), correction bubble count increase (correction arrived), report modal visibility (session ended).
- **Playwright**: Headless Chromium with mobile Safari viewport (390×844, Safari UA, `setIsMobile(true)`). Browser launched once per test class in `@BeforeAll`. Screenshots auto-saved in `@AfterEach` to `target/e2e-screenshots/`.
- **Mock data**: 3 conversation SSE streams (3-5 chunks each), 3 correction JSON arrays, 1 report JSON object. Keywords aligned with test prompt files in `src/test/resources/prompts/` (correction.txt starts with "Correction prompt:", report.txt with "Report prompt.").
- **Token limit**: 128000 hardcoded in `ChatWebSocketHandler`. Warning at 80%. Uses actual token count from `ChatResponse.tokenUsage().totalTokenCount()` (not estimated).
- **Error types**: 5 categories �?GRAMMAR, WORD_CHOICE, CHINGLISH, PRONUNCIATION, FLUENCY.
- **iOS quirks**: TTS requires user-gesture-triggered 🔊 button click (no autoplay). Safe-area CSS (`env(safe-area-inset-top)`) for notch/status-bar spacing on Safari.
- **Null guard**: `TurnProcessor.onCompleteResponse` checks `response != null` before accessing `tokenUsage()` �?LangChain4j may callback with null on network errors.
- **Session ID flow**: `SessionService` creates a Session (JPA generates a UUID `id`), `ChatMessageHandler` tracks `sessionToWs` mapping, `TurnProcessor` uses it as `RunnableConfig.threadId`. All three layers (H2, WebSocket, checkpoint) share the same ID.
- **Type safety**: `MessageData.role` and `CorrectionData.type` use Java enums (`MessageRole`, `ErrorType`) directly �?no more raw `String` conversion with silent fallbacks. `ErrorType` uses `@JsonCreator` for case-insensitive LLM JSON deserialization.
- **State encapsulation**: `ChatState` is an internal detail of `SessionService`. `ChatMessageHandler` and `ReportAgent` never import `ChatState` �?all reads/writes go through `SessionService` methods.
- **AgentMode enum**: `AgentMode` carries `displayName` + `templatePath` fields. Each Mode maps to a subdirectory under `prompts/` containing `description.txt` and `rules.txt`. Currently two modes: `WORKPLACE_STANDUP` and `DAILY_TALK` (Chris persona �?casual friend+tutor chat). `ConversationAgent` pre-loads all Mode templates at construction and resolves `{Description}` / `{Rules}` placeholders. Mode is validated at WebSocket entry via `AgentMode.valueOf()`.
- **Streaming WebSocket sends**: always use `synchronized(wsSession)` when sending from async threads (callback context). `sendSynced()` helper wraps IOException.
- **Session resume**: disconnect only removes `sessionToWs` mapping, never calls `removeSession()`. State stays in `activeStates` until explicit `END_SESSION`. On reconnect, `RESUME_SESSION` validates `userId` ownership.
- **UserId fallback**: `requireUserId()` returns `"anonymous"` if `ws.getPrincipal()` is null. Production always has a Principal (Spring Security interceptor); E2E profile has no auth so this gracefully bypasses.
- **Config-driven security**: No `@ConditionalOnProperty` or `@Profile` on `SecurityConfig`. All path-level auth control is via `app.security.permit-all-paths` in YAML.
- **Memory injection**: Every round performs RAG semantic search against `EmbeddingService`. On round 1, if RAG returns no matches, a fallback loads the most recent session's last COMPLETED MemoryCue from H2 as a conversation continuity anchor with a time label. LearningProfile is injected on round 1 only. There is no dual-track switching between Topic Memory and RAG �?all memory retrieval is unified through the embedding pipeline. `ConversationAgent` accepts `MemoryContent` DTO to encapsulate all memory data.
- **MemoryCue module**: `memory_cues` table + `MemoryCueAgent` (two-step LLM: topic switch detection �?per-segment `{topic, summary}` JSON). `MemoryCueService` dispatches post-session generation asynchronously on `llmRequestExecutor`, parallel with Report and Profile Merge. Completed cues are vectorized asynchronously by `EmbeddingService.indexAsync()`. `MemoryCueStatus` tracks completion state per segment (COMPLETED / SEGMENT_FAILED / FIRST_CALL_FAILED). AgentMode isolation via `mode` column.
- **RAG retrieval**: `EmbeddingService` with `InMemoryEmbeddingStore` + ONNX embeddings. Store persists to `./data/embedding-store.json` on disk, with corrupted-file fallback to H2 rebuild. Data isolated by `userId × AgentMode` at both H2 and vector store layers. Dedicated `embeddingExecutor` thread pool (core=2, max=2). Configurable via `app.memory.retrieval.*`.
- **Thread pool**: `llmRequestExecutor` (core=4, max=8) handles correction LLM calls (during turns) and memory processing (MemoryCue split + parallel segment generation + Report + Profile Merge, at session end, orchestrated via `SessionComplete`). Turn-time correction and end-session memory tasks do not overlap chronologically.

## Flashcard Module

### REST API
- `FlashcardController`: `POST /api/cards/add`, `GET /api/tags`, `POST /api/cards/import`, `GET /api/cards/export`, `POST /api/cards/{id}/forget`, `POST /api/cards/forget?deckId=`. Authenticated via JSESSIONID cookie. `/api/**` requires login, CSRF disabled.
- `ReviewController`: `GET /api/review/start`, `POST /api/review/next`, `GET /api/review/stats`, `GET /api/review/decks`. Response includes `{card, stats, preview}` — `preview` shows all 4 rating outcomes' due dates. User preferences via `GET/PUT /api/user/preferences`.

### Entities
- **Card**: (id, userId, front, back, stability, difficulty, cardState, due, reps, lapses, step, lastReview, firstReviewDate). `@ManyToMany` Tag via `card_tags`. FSRS state initialized via `FsrsScheduler.createInitState()`.
- **Tag**: (id, name, type, userId). `type="deck"` makes a Tag a reviewable Deck.
- **ReviewLog**: (id, userId, cardId, rating, stateBefore/After, stabilityBefore/After, difficultyBefore/After, stepBefore, scheduledDays, elapsedDays, reviewedAt, firstReview, deckId). Created on every `rateCard()`.
- **FsrsParameters**: (id, userId, w0-w20, enableShortTerm). System-managed 21 FSRS weights per Learner. Created by DataInitializer; overwritten by FSRS Optimizer. Soft-linked via userId (no FK).
- **UserPreferences**: (id, userId, newCardDailyLimit, dayStartHour, timezone, lastDeckId, lastMode, learningSteps, relearningSteps, desiredRetention, maximumInterval, enableFuzz, shuffleDueCards). FSRS fields are nullable — null falls back to `FsrsSchedulerConfig.defaults()`.

### FSRS Scheduler (refactored)
- `FsrsScheduler` is an **instance class** accepting `FsrsSchedulerConfig` (immutable record bundling all 7 parameter categories: W[21], desiredRetention, learningSteps, relearningSteps, maximumInterval, enableFuzz, enableShortTerm).
- Instance methods: `enchantCard(now)` (prepare for first review; was `initNewCard`), `repeat(card, rating, now, fuzzSource)`, `preview(card, now)` → `Map<Rating, CardState>`, `reschedule(reviewLogs, now)` → `CardState`, `retrievability(card, now)`.
- Static methods: `createInitState(now)` (brand-new card, state=0, does not depend on config), `forgettingCurve(elapsed, stability, decay)` (pure math).
- `FsrsSchedulerConfig` has static `defaults()` (FSRS-6 standard) and `merge(FsrsParameters, UserPreferences)` for per-Learner runtime config.
- 12 unit tests + 5 reschedule tests + 4 preview tests.

### Review Flow
- `ReviewService.rateCard()`: builds Scheduler from Caffeine-cached `FsrsSchedulerConfig` → calls `scheduler.repeat()` → updates Card → creates ReviewLog.
- `ReviewService.getNextCard()`: 4 modes (STANDARD/REVIEW_ONLY/NEW_ONLY/CRAM). `shuffleDueCards` toggles random vs chronological due-card order (native `ORDER BY RAND()`).
- `ReviewService.previewCard()`: returns all 4 rating outcomes (no fuzz) — used by RatingButtons to show "Good · 约15天后" labels.
- `ReviewService.rescheduleAllCards(userId)`: async replay of all ReviewLogs with current config. Triggered automatically after FSRS Optimizer completes.
- `ReviewService.forgetCard(cardId)` + `forgetDeck(deckId)`: resets FSRS state to `createInitState` + physically deletes all ReviewLogs. `@Transactional` atomic.

### Caffeine Cache
- `CaffeineCacheManager` with `expireAfterAccess(24h)` on cache `"fsrsConfig"`.
- `@Cacheable` on config lookup; `@CacheEvict` on settings save; programmatic `evict()` after optimizer/reschedule.
- Avoids repeated DB reads of `FsrsParameters` + `UserPreferences` on every `rateCard()`.

### FSRS Optimizer (planned)
- `FsrsOptimizer`: pure Java, manual Adam + finite-difference numerical gradients (h=1e-4), same algorithm as py-fsrs. Minimizes BCELoss over ReviewLog history.
- `FsrsOptimizeService`: orchestration layer — reads ReviewLog, calls Optimizer, writes FsrsParameters, evicts cache, triggers reschedule.
- Triggers: `POST /api/fsrs/optimize` (async + progress polling) + `@Scheduled` weekly.
- Cross-validated against py-fsrs using 12,580 real Anki review logs.

### Frontend Pages
- Manage page (`/manage`): CardsTab (CRUD + search + sort + deck filter + detail modal + **forget** button), TagsTab (CRUD), CardToolbar (sort dropdown + batch import/export).
- Review page (`/review`): DeckPicker → ReviewPage (flip + rate + stats bar). RatingButtons show interval preview. CompletePage shows session summary.
- Settings page (`/settings`): 9 configurable fields (daily limit, day start, timezone, learning/relearning steps with presets + explanations, desired retention, max interval, fuzz toggle, shuffle toggle). Save validates all fields.

### Other
- **AleaPrng**: Deterministic PRNG (Johannes Baagøe) for cross-implementation fuzz. Used via `DoubleSupplier` in `repeat()`.
- **E2E**: `ReviewIT` (9 scenarios — deck/mode selection, flip & rate, stats bar, complete, REVIEW_ONLY, NEW_ONLY, CRAM, limit dialog, preferences). `FlashcardIT`, `FlashcardBatchIT`, `ManagePageIT` (card/tag CRUD + forget scenarios). Rating button clicks use `data-testid` selectors (not text), survives UI text changes.
- **Two-stage flashcard input**: Stage 1 (~60px front input + "继续") → Stage 2 (~70vh back textarea + chip tag autocomplete + "保存"). FlashcardPanel and Debug panel mutually exclusive.
- **Batch import/export**: CSV with full FSRS state round-trip. `Apache Commons CSV 1.11.0`. `spring.servlet.multipart.max-file-size=5MB`. Frontend: `BatchOperationModal` three-stage state machine.

## Logging

- **File logs** (`logback-spring.xml`): Only active with `local` profile. Console keeps INFO level; file writes DEBUG level to `./logs/chat-agent.YYYY-MM-DD.log` with daily rolling and 3-day retention. `ReportAgent` and `LearningAgent` prompt/response printing has been downgraded from `log.info` to `log.debug` to keep the console clean.
- **LLM Call Log** (`llm_call_logs` table): Every LLM API call is persisted asynchronously �?`request_prompt` (full prompt blob), `system_prompt` and `chat_history` (split for structured querying), `response_text`, token usage (input/output), duration (ms), and status (SUCCESS/ERROR). Sync agents (Correction, Report, Learning, MemoryCue) log via `TaskRunner.requestModel()` with full runtime context (sessionId, userId, agentType, mode) �?prompt stored in `system_prompt`, `chat_history` is null. Streaming agent (ConversationAgent) is logged manually in `TurnProcessor.onCompleteResponse()` with full metadata (sessionId, userId, agentType, mode, input/output tokens) �?prompt JSON parsed into `system_prompt` and `chat_history`. Records older than 3 days are cleaned up on startup via `LlmCallLogService.cleanupOnStartup()`. Query via H2 console: `SELECT * FROM llm_call_logs ORDER BY create_time DESC`.
- **`llmLogExecutor` thread pool**: core=2, max=4, dedicated to async LLM call log writes (defined in `AsyncConfig`).

## Agent skills

### Issue tracker

Issues 以本�?markdown 文件形式存放�?`.scratch/<feature>/` 目录下。详�?`docs/agents/issue-tracker.md`�?

### Triage labels

使用五个标准 triage 角色标签：`needs-triage`、`needs-info`、`ready-for-agent`、`ready-for-human`、`wontfix`。详�?`docs/agents/triage-labels.md`�?

### Domain docs

单上下文布局 �?`CONTEXT.md` + `docs/adr/` 在仓库根目录。详�?`docs/agents/domain.md`�?
