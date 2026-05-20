# AGENTS.md — Web Agent (English Coach)

## Quick Reference

```bash
# Verify the build (no tests exist)
mvn compile

# Run locally with local profile (api-key in application-local.yml)
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Or set env var directly
set DEEPSEEK_API_KEY=sk-your-key
mvn spring-boot:run

# H2 console (for debugging data)
# http://localhost:8080/h2-console → jdbc:h2:file:./data/englishcoach → sa / (empty)
```

## Key Facts

- **Java 17** / **Spring Boot 3.4.7** / **Maven** — only `mvn compile` matters; there are **no tests** (`src/test/` is empty).
- **Package**: `com.hugosol.webagent` (note: Maven `groupId` is `com.example` — ignore that, it's vestigial).
- **DeepSeek via LangChain4j**: uses OpenAI-compatible adapter (`dev.langchain4j:langchain4j-open-ai`). Default model is `deepseek-v4-flash` (see `application.yml`, not README which says `deepseek-chat`).
- **Two LLM beans**: `ChatLanguageModel` (sync, for CorrectionAgent/ReportAgent) + `StreamingChatLanguageModel` (`OpenAiStreamingChatModel`, for ConversationAgent). Both in `LangChain4jConfig`.
- **langgraph4j**: `org.bsc.langgraph4j:langgraph4j-core:1.8.16` — independent library, NOT a LangChain4j subproject. State channels use `Channels.base(() -> default)` and `Channels.appender(ArrayList::new)`, **not** `Channels.of()`.
- **1-node graph**: `START → correction → END`. Only CorrectionNode remains in the graph. Conversation was extracted to Service layer for streaming.
- **Parallel execution**: `GraphExecutionService.processTurn()` launches conversation (streaming) and correction (graph) in parallel via `CompletableFuture`. Conversation tokens stream to frontend immediately; correction results arrive asynchronously.
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
- Data is written to H2 **only at session end** (`SessionService.completeSession()`). Runtime state stays in `GraphExecutionService.activeStates` (ConcurrentHashMap) + MemorySaver checkpoints.
- H2 console enabled at `/h2-console`. Use `DB_CLOSE_DELAY=-1` to keep data alive between requests.

## Project Structure

```
com.hugosol.webagent/
├── graph/           # LangGraph: CoachState (7 channels) + 1 node + builder
│   └── nodes/       # CorrectionNode (only remaining node)
├── agent/           # ConversationAgent (streaming), CorrectionAgent, ReportAgent
├── websocket/       # CoachWebSocketHandler (WS + JSON router + resume logic)
├── service/         # GraphExecutionService (parallel orchestration), SessionService (JPA)
├── model/           # JPA entities + enums (ScenarioType, PersonaType, ErrorType, etc.)
├── repository/      # Spring Data JPA repos
├── config/          # LangChain4jConfig (2 beans), WebSocketConfig, PromptLoader
└── speech/          # STT/TTS interfaces (V1 stubs, V2 will implement Whisper)
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

- **No tests.** `mvn test` runs nothing. Use `mvn compile` for verification.
- **Token limit**: 128000 hardcoded in `CoachWebSocketHandler`. Warning at 80%. Uses actual token count from `ChatResponse.tokenUsage().totalTokenCount()` (not estimated).
- **Error types**: 5 categories — GRAMMAR, WORD_CHOICE, CHINGLISH, PRONUNCIATION, FLUENCY.
- **iOS quirks**: TTS requires user-gesture-triggered 🔊 button click (no autoplay). Safe-area CSS (`env(safe-area-inset-top)`) for notch/status-bar spacing on Safari.
- **Null guard**: `GraphExecutionService.onCompleteResponse` checks `response != null` before accessing `tokenUsage()` — LangChain4j may callback with null on network errors.
- **Session ID flow**: `SessionService` creates a Session (JPA generates a UUID `id`), `CoachWebSocketHandler` tracks `wsToSession` mapping, `GraphExecutionService` uses it as `RunnableConfig.threadId`. All three layers (H2, WebSocket, checkpoint) share the same ID.
- **Scenario & Persona enums**: `ScenarioType` and `PersonaType` carry `displayName` + `description`/`roleDescription`/`fullDescription` fields. `ConversationAgent` resolves prompt placeholders via enum accessors (no hardcoded switch). Persona is validated at WebSocket entry via `PersonaType.valueOf()`.
- **Streaming WebSocket sends**: always use `synchronized(wsSession)` when sending from async threads (callback context). `sendSynced()` helper wraps IOException.
- **Session resume**: disconnect only removes `wsToSession` mapping, never calls `removeSession()`. State stays in `activeStates` until explicit `END_SESSION`.
