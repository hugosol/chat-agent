# AGENTS.md — Web Agent (English Coach)

## Quick Reference

```bash
# Verify the build
mvn compile

# Run unit tests (existing unit tests only)
mvn test

# Run E2E regression tests (Playwright + WireMock, first run downloads Chromium ~150MB)
mvn verify

# Run locally with local profile (api-key in application-local.yml)
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Or set env var directly
set DEEPSEEK_API_KEY=sk-your-key
mvn spring-boot:run

# H2 console (for debugging data, local profile opens without auth)
# http://localhost:8080/h2-console → jdbc:h2:file:./data/englishcoach → sa / (empty)
```

## Key Facts

- **Java 17** / **Spring Boot 3.4.7** / **Maven** — `mvn compile` for build verification, `mvn test` for unit tests, `mvn verify` for E2E tests.
- **Spring Security**: form login + remember-me + BCrypt. SecurityConfig is always loaded — no conditional annotations. Auth behavior is driven entirely by `app.security.permit-all-paths` in YAML config. Default user: `admin/admin123` (from `application.yml` → `DataInitializer`).
- **E2E tests**: Playwright (Java) + WireMock 3.x — `src/test/java/com/hugosol/webagent/e2e/`. Two IT test classes: `EnglishCoachSessionIT` (full session + sidebar + H2 assertions), `EnglishCoachResumeIT` (page reload → session resume). WireMock runs on fixed port `19090`, mocks DeepSeek at HTTP layer. DOM-based waits (no WebSocket frame interception). Screenshots auto-saved to `target/e2e-screenshots/` via `@AfterEach`. Uses `@ActiveProfiles("e2e")` + `application-e2e.yml` with `permit-all-paths: [/**]` to bypass authentication.
- **Package**: `com.hugosol.webagent` (note: Maven `groupId` is `com.example` — ignore that, it's vestigial).
- **DeepSeek via LangChain4j**: uses OpenAI-compatible adapter (`dev.langchain4j:langchain4j-open-ai`). Default model is `deepseek-v4-flash` (see `application.yml`, not README which says `deepseek-chat`).
- **Two LLM beans**: `ChatLanguageModel` (sync, for CorrectionAgent/ReportAgent) + `StreamingChatLanguageModel` (`OpenAiStreamingChatModel`, for ConversationAgent). Both in `LangChain4jConfig`.
- **langgraph4j**: `org.bsc.langgraph4j:langgraph4j-core:1.8.16` — independent library, NOT a LangChain4j subproject. State channels use `Channels.base(() -> default)` and `Channels.appender(ArrayList::new)`, **not** `Channels.of()`.
- **1-node graph**: `START → correction → END`. Only CorrectionNode remains in the graph. Conversation was extracted to Service layer for streaming.
- **Parallel execution**: `TurnProcessor.processTurn()` launches conversation (streaming) and correction (graph) in parallel via `CompletableFuture`. Conversation tokens stream to frontend immediately; correction results arrive asynchronously.
- **MemorySaver checkpoints**: survive page refresh, **lost on server restart**. No persistence until session ends.
- **Session resume**: WS disconnect no longer destroys `activeStates`. Frontend stores `sessionId` in `localStorage` and sends `RESUME_SESSION` on reconnect.
- **Multi-tab**: `sessionToWs` map is one-to-one (sessionId → wsId, flipped from old `wsToSession`). Page Visibility API triggers auto-resume on tab activation. Stale delta protection skips streaming tokens for already-rendered messageIds.
- **Frontend**: vanilla HTML/JS/CSS in `src/main/resources/static/`. No npm, no webpack, no build tools. Correction sidebar toggled via `×` button. Login page at `/login/main.html` with dark theme, served from `static/login/`.
- **Correction display**: numbered summary bubble (`1. original → corrected` / `2. ...`) inserted after user message in chat flow; detailed items in sidebar (type + explanation). Sidebar is an absolute overlay (no longer squeezes chat) and starts collapsed. Header "Corrections N" button toggles visibility.
- **WebSocket endpoint**: `/ws/coach` — JSON protocol. Handshake authenticated via Spring Security (JSESSIONID cookie). If Principal is null (E2E profile), falls back to `"anonymous"`.
- **Architecture document**: `docs/architecture.md` is the design blueprint + decision log. Read before structural changes; **do not edit casually**.

## User Module & Data Isolation

- **User entity**: `User` (id, username, password/BCrypt), table `users`. `UserRepository.findByUsername()`.
- **Initial user seeding**: `DataInitializer` (CommandLineRunner) reads `app.initial-users` from YAML, BCrypt-hashes passwords, creates users only if not existing.
- **PasswordEncoder**: standalone `PasswordEncoderConfig` (always loaded), separate from `SecurityConfig` for reuse outside web context.
- **Data isolation**: `Session.userId` (NOT NULL) added. All per-session queries (findBySessionId) naturally isolated by UUID. Only cross-session queries (`getHistory`, user progress) filter by `userId`. `UserProgress` is now per-user (was singleton).
- **Runtime user context**: `CoachState` has `USER_ID` channel. `SessionService.getUserId()` reads from CoachState — works in async threads (no ThreadLocal dependency).
- **RESUME_SESSION validation**: checks session ownership via `CoachState.userId` before allowing resume. Returns "Session not found" if userId mismatch.
- **Session cleanup on logout**: `SessionCleanupLogoutHandler` clears all `activeStates` for the logging-out user. Tab close without logout only unbinds WS — sessions survive for resume.

## Environment

| Variable | Default | Notes |
|----------|---------|-------|
| `DEEPSEEK_API_KEY` | *(required by default)* | Bypass with `local` profile: place key in `application-local.yml` |
| `DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | |
| `DEEPSEEK_MODEL` | `deepseek-v4-flash` | Config in `application.yml` |

No `.env` file support — use `local` profile (`application-local.yml`, gitignored) or set vars directly in shell.

## Data & Persistence

- **H2 file database** at `./data/englishcoach` (the `data/` directory is gitignored).
- `spring.jpa.hibernate.ddl-auto: update` — tables auto-created on first run.
- Data is written to H2 **only at session end** (`SessionStore.completeSession()`). Runtime state stays in `SessionService.activeStates` (ConcurrentHashMap) + MemorySaver checkpoints.
- H2 console enabled at `/h2-console`. Use `DB_CLOSE_DELAY=-1` to keep data alive between requests. With `local` profile, H2 console does not require login.

## Project Structure

```
com.hugosol.webagent/
├── graph/           # LangGraph: CoachState (7 channels incl. USER_ID + MODE) + 1 node + builder
│   └── nodes/       # CorrectionNode (only remaining node)
├── agent/           # ConversationAgent (streaming), CorrectionAgent, ReportAgent
├── websocket/       # CoachWebSocketHandler (WS entry), CoachMessageHandler (protocol logic)
├── protocol/        # ClientMessage/ServerMessage sealed types, ProtocolDispatcher, MessageHandler
├── service/         # SessionService (state + tokens + sessionToWs), TurnProcessor (parallel turns),
│                    # SessionStore (entity persistence), SessionCleanupLogoutHandler, TokenTracker, EntityMapper
├── model/           # JPA entities + enums: User, Session, Message, ErrorRecord, SessionReport, UserProgress, AgentMode, ...
├── repository/      # Spring Data JPA repos (6: User, Session, Message, ErrorRecord, SessionReport, UserProgress)
├── config/          # LangChain4jConfig, SecurityConfig, WebSocketConfig, AppProperties, PasswordEncoderConfig, DataInitializer, PromptLoader
└── speech/          # (vacant — V2 will add STT/TTS adapters when needed)

src/test/java/com/hugosol/webagent/e2e/
├── EnglishCoachSessionIT.java   # Full session: 3 turns + sidebar + H2 assertions
├── EnglishCoachResumeIT.java    # Page reload → session resume verification
└── helper/
    ├── E2ETestBase.java         # @SpringBootTest base: WireMock (19090), Playwright, DOM waits, @ActiveProfiles("e2e")
    └── WireMockStubs.java       # Scenario state machine stubs (7 stubs, JSON Path body matching)

src/test/resources/
├── application-e2e.yml          # E2E profile: mem H2, base-url → localhost:19090, permit-all-paths: [/**]
├── prompts/                     # Test prompt overrides (correction.txt, report.txt)
└── wiremock/                    # 7 mock response files (3 conv SSE + 3 corr JSON + 1 report JSON)
```

## WebSocket Protocol

```
Client → Server:
  START_SESSION { mode }
  USER_INPUT { text: "...", messageId: 1 }
  END_SESSION
  RESUME_SESSION { sessionId: "..." }

Server → Client:
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

- **E2E tests**: `*IT.java` suffix. `mvn test` runs unit tests (surefire, excludes IT). `mvn verify` runs E2E (failsafe, includes IT). Uses `@ActiveProfiles("e2e")` → `application-e2e.yml` (memory H2, WireMock base-url, all paths permitted).
- **WireMock**: Fixed port `19090`. `matchingJsonPath("$.messages[0].content", containing(keyword))` for body matching (avoids JSON encoding issues). Scenario state machine (`STARTED → round-2 → round-3`) controls stub rotation across turns. `Runtime.addShutdownHook` stops WireMock so all IT classes share one server instance.
- **DOM waits**: No WebSocket frame interception (Playwright Java's `onFrameReceived` unreliable). All waits use `page.waitForFunction()` on DOM state: input bar visibility (session started), input disabled → enabled (streaming done), correction bubble count increase (correction arrived), report modal visibility (session ended).
- **Playwright**: Headless Chromium with mobile Safari viewport (390×844, Safari UA, `setIsMobile(true)`). Browser launched once per test class in `@BeforeAll`. Screenshots auto-saved in `@AfterEach` to `target/e2e-screenshots/`.
- **Mock data**: 3 conversation SSE streams (3-5 chunks each), 3 correction JSON arrays, 1 report JSON object. Keywords aligned with test prompt files in `src/test/resources/prompts/` (correction.txt starts with "Correction prompt:", report.txt with "Report prompt.").
- **Token limit**: 128000 hardcoded in `CoachWebSocketHandler`. Warning at 80%. Uses actual token count from `ChatResponse.tokenUsage().totalTokenCount()` (not estimated).
- **Error types**: 5 categories — GRAMMAR, WORD_CHOICE, CHINGLISH, PRONUNCIATION, FLUENCY.
- **iOS quirks**: TTS requires user-gesture-triggered 🔊 button click (no autoplay). Safe-area CSS (`env(safe-area-inset-top)`) for notch/status-bar spacing on Safari.
- **Null guard**: `TurnProcessor.onCompleteResponse` checks `response != null` before accessing `tokenUsage()` — LangChain4j may callback with null on network errors.
- **Session ID flow**: `SessionService` creates a Session (JPA generates a UUID `id`), `CoachMessageHandler` tracks `sessionToWs` mapping, `TurnProcessor` uses it as `RunnableConfig.threadId`. All three layers (H2, WebSocket, checkpoint) share the same ID.
- **Type safety**: `MessageData.role` and `CorrectionData.type` use Java enums (`MessageRole`, `ErrorType`) directly — no more raw `String` conversion with silent fallbacks. `ErrorType` uses `@JsonCreator` for case-insensitive LLM JSON deserialization.
- **State encapsulation**: `CoachState` is an internal detail of `SessionService`. `CoachMessageHandler` and `ReportAgent` never import `CoachState` — all reads/writes go through `SessionService` methods.
- **AgentMode enum**: `AgentMode` carries `displayName` + `templatePath` fields. Each Mode maps to a subdirectory under `prompts/` containing `description.txt` and `rules.txt`. Currently two modes: `WORKPLACE_STANDUP` and `DAILY_TALK` (Chris persona — casual friend+tutor chat). `ConversationAgent` pre-loads all Mode templates at construction and resolves `{Description}` / `{Rules}` placeholders. Mode is validated at WebSocket entry via `AgentMode.valueOf()`.
- **Streaming WebSocket sends**: always use `synchronized(wsSession)` when sending from async threads (callback context). `sendSynced()` helper wraps IOException.
- **Session resume**: disconnect only removes `sessionToWs` mapping, never calls `removeSession()`. State stays in `activeStates` until explicit `END_SESSION`. On reconnect, `RESUME_SESSION` validates `userId` ownership.
- **UserId fallback**: `requireUserId()` returns `"anonymous"` if `ws.getPrincipal()` is null. Production always has a Principal (Spring Security interceptor); E2E profile has no auth so this gracefully bypasses.
- **Config-driven security**: No `@ConditionalOnProperty` or `@Profile` on `SecurityConfig`. All path-level auth control is via `app.security.permit-all-paths` in YAML.

## Agent skills

### Issue tracker

Issues 以本地 markdown 文件形式存放在 `.scratch/<feature>/` 目录下。详见 `docs/agents/issue-tracker.md`。

### Triage labels

使用五个标准 triage 角色标签：`needs-triage`、`needs-info`、`ready-for-agent`、`ready-for-human`、`wontfix`。详见 `docs/agents/triage-labels.md`。

### Domain docs

单上下文布局 — `CONTEXT.md` + `docs/adr/` 在仓库根目录。详见 `docs/agents/domain.md`。
