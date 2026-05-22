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

# H2 console (for debugging data)
# http://localhost:8080/h2-console → jdbc:h2:file:./data/englishcoach → sa / (empty)
```

## Key Facts

- **Java 17** / **Spring Boot 3.4.7** / **Maven** — `mvn compile` for build verification, `mvn test` for unit tests, `mvn verify` for E2E tests.
- **E2E tests**: Playwright (Java) + WireMock 3.x — `src/test/java/com/hugosol/webagent/e2e/`. Two IT test classes: `EnglishCoachSessionIT` (full session + sidebar + H2 assertions), `EnglishCoachResumeIT` (page reload → session resume). WireMock runs on fixed port `19090`, mocks DeepSeek at HTTP layer. DOM-based waits (no WebSocket frame interception). Screenshots auto-saved to `target/e2e-screenshots/` via `@AfterEach`.
- **Package**: `com.hugosol.webagent` (note: Maven `groupId` is `com.example` — ignore that, it's vestigial).
- **DeepSeek via LangChain4j**: uses OpenAI-compatible adapter (`dev.langchain4j:langchain4j-open-ai`). Default model is `deepseek-v4-flash` (see `application.yml`, not README which says `deepseek-chat`).
- **Two LLM beans**: `ChatLanguageModel` (sync, for CorrectionAgent/ReportAgent) + `StreamingChatLanguageModel` (`OpenAiStreamingChatModel`, for ConversationAgent). Both in `LangChain4jConfig`.
- **langgraph4j**: `org.bsc.langgraph4j:langgraph4j-core:1.8.16` — independent library, NOT a LangChain4j subproject. State channels use `Channels.base(() -> default)` and `Channels.appender(ArrayList::new)`, **not** `Channels.of()`.
- **1-node graph**: `START → correction → END`. Only CorrectionNode remains in the graph. Conversation was extracted to Service layer for streaming.
- **Parallel execution**: `TurnProcessor.processTurn()` launches conversation (streaming) and correction (graph) in parallel via `CompletableFuture`. Conversation tokens stream to frontend immediately; correction results arrive asynchronously.
- **MemorySaver checkpoints**: survive page refresh, **lost on server restart**. No persistence until session ends.
- **Session resume**: WS disconnect no longer destroys `activeStates`. Frontend stores `sessionId` in `localStorage` and sends `RESUME_SESSION` on reconnect.
- **Frontend**: vanilla HTML/JS/CSS in `src/main/resources/static/`. No npm, no webpack, no build tools. Correction sidebar toggled via `×` button.
- **Correction display**: numbered summary bubble (`1. original → corrected` / `2. ...`) inserted after user message in chat flow; detailed items in sidebar (type + explanation). Sidebar is an absolute overlay (no longer squeezes chat) and starts collapsed. Header "Corrections N" button toggles visibility.
- **WebSocket endpoint**: `/ws/coach` — JSON protocol.
- **Architecture document**: `architecture.md` is the design blueprint + decision log. Read before structural changes; **do not edit casually**.

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
- Data is written to H2 **only at session end** (`SessionService.completeSession()`). Runtime state stays in `SessionStateStore` (ConcurrentHashMap) + MemorySaver checkpoints.
- H2 console enabled at `/h2-console`. Use `DB_CLOSE_DELAY=-1` to keep data alive between requests.

## Project Structure

```
com.hugosol.webagent/
├── graph/           # LangGraph: CoachState (7 channels) + 1 node + builder
│   └── nodes/       # CorrectionNode (only remaining node)
├── agent/           # ConversationAgent (streaming), CorrectionAgent, ReportAgent
├── websocket/       # CoachWebSocketHandler (WS entry), CoachMessageHandler (protocol logic)
├── protocol/        # ClientMessage/ServerMessage sealed types, ProtocolDispatcher, MessageHandler
├── service/         # SessionStateStore (state + tokens), TurnProcessor (parallel turns),
│                    # SessionArchiver (entity conversion), ReportGenerator, SessionService (JPA), TokenTracker
├── model/           # JPA entities + enums (ScenarioType, PersonaType, ErrorType, etc.)
├── repository/      # Spring Data JPA repos
├── config/          # LangChain4jConfig (2 beans), WebSocketConfig, PromptLoader
└── speech/          # (vacant — V2 will add STT/TTS adapters when needed)

src/test/java/com/hugosol/webagent/e2e/
├── EnglishCoachSessionIT.java   # Full session: 3 turns + sidebar + H2 assertions
├── EnglishCoachResumeIT.java    # Page reload → session resume verification
└── helper/
    ├── E2ETestBase.java         # @SpringBootTest base: WireMock (19090), Playwright, DOM waits
    └── WireMockStubs.java       # Scenario state machine stubs (7 stubs, JSON Path body matching)

src/test/resources/
├── application-test.yml         # Test profile: mem H2, base-url → localhost:19090
├── prompts/                     # Test prompt overrides (correction.txt, report.txt)
└── wiremock/                    # 7 mock response files (3 conv SSE + 3 corr JSON + 1 report JSON)
```

## WebSocket Protocol

```
Client → Server:
  START_SESSION { scenario, persona }
  USER_INPUT { text: "...", messageId: 1 }
  END_SESSION
  RESUME_SESSION { sessionId: "..." }

Server → Client:
  SESSION_STARTED { sessionId, scenario, persona }
  AGENT_STREAM_DELTA { delta: "Sounds", messageId }
  AGENT_STREAM_END { text: "full text", messageId, tokenUsage }
  CORRECTION_RESULT { corrections: [...], messageId }
  SESSION_RESUMED { sessionId, messages, corrections, tokenUsage }
  STATE_UPDATE { state, tokenUsage }
  TOKEN_WARNING { usage }
  SESSION_REPORT { report: {...} }
  ERROR { message }
```

## Conventions & Gotchas

- **E2E tests**: `*IT.java` suffix. `mvn test` runs unit tests (surefire, excludes IT). `mvn verify` runs E2E (failsafe, includes IT). Test profile `@ActiveProfiles("test")` enables `application-test.yml` (memory H2, WireMock base-url).
- **WireMock**: Fixed port `19090`. `matchingJsonPath("$.messages[0].content", containing(keyword))` for body matching (avoids JSON encoding issues). Scenario state machine (`STARTED → round-2 → round-3`) controls stub rotation across turns. `Runtime.addShutdownHook` stops WireMock so all IT classes share one server instance.
- **DOM waits**: No WebSocket frame interception (Playwright Java's `onFrameReceived` unreliable). All waits use `page.waitForFunction()` on DOM state: input bar visibility (session started), input disabled → enabled (streaming done), correction bubble count increase (correction arrived), report modal visibility (session ended).
- **Playwright**: Headless Chromium with mobile Safari viewport (390×844, Safari UA, `setIsMobile(true)`). Browser launched once per test class in `@BeforeAll`. Screenshots auto-saved in `@AfterEach` to `target/e2e-screenshots/`.
- **Mock data**: 3 conversation SSE streams (3-5 chunks each), 3 correction JSON arrays, 1 report JSON object. Keywords aligned with test prompt files in `src/test/resources/prompts/` (correction.txt starts with "Correction prompt:", report.txt with "Report prompt.").
- **Token limit**: 128000 hardcoded in `CoachWebSocketHandler`. Warning at 80%. Uses actual token count from `ChatResponse.tokenUsage().totalTokenCount()` (not estimated).
- **Error types**: 5 categories — GRAMMAR, WORD_CHOICE, CHINGLISH, PRONUNCIATION, FLUENCY.
- **iOS quirks**: TTS requires user-gesture-triggered 🔊 button click (no autoplay). Safe-area CSS (`env(safe-area-inset-top)`) for notch/status-bar spacing on Safari.
- **Null guard**: `TurnProcessor.onCompleteResponse` checks `response != null` before accessing `tokenUsage()` — LangChain4j may callback with null on network errors.
- **Session ID flow**: `SessionService` creates a Session (JPA generates a UUID `id`), `CoachMessageHandler` tracks `wsToSession` mapping, `TurnProcessor` uses it as `RunnableConfig.threadId`. All three layers (H2, WebSocket, checkpoint) share the same ID.
- **Type safety**: `MessageData.role` and `CorrectionData.type` use Java enums (`MessageRole`, `ErrorType`) directly — no more raw `String` conversion with silent fallbacks. `ErrorType` uses `@JsonCreator` for case-insensitive LLM JSON deserialization.
- **State encapsulation**: `CoachState` is an internal detail of `SessionStateStore`. `CoachMessageHandler` and `ReportGenerator` never import `CoachState` — all reads/writes go through `SessionStateStore` methods.
- **Scenario & Persona enums**: `ScenarioType` and `PersonaType` carry `displayName` + `description`/`roleDescription`/`fullDescription` fields. `ConversationAgent` resolves prompt placeholders via enum accessors (no hardcoded switch). Persona is validated at WebSocket entry via `PersonaType.valueOf()`.
- **Streaming WebSocket sends**: always use `synchronized(wsSession)` when sending from async threads (callback context). `sendSynced()` helper wraps IOException.
- **Session resume**: disconnect only removes `wsToSession` mapping, never calls `removeSession()`. State stays in `activeStates` until explicit `END_SESSION`.
