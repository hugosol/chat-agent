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
- **langgraph4j**: `org.bsc.langgraph4j:langgraph4j-core:1.8.16` — independent library, NOT a LangChain4j subproject. State channels use `Channels.base(() -> default)` and `Channels.appender(ArrayList::new)`, **not** `Channels.of()`.
- **3-node linear graph** per turn: `conversation → correction → merge → END`. The session loop is managed by `GraphExecutionService`, NOT inside the graph. Original design had 8 nodes; the reduction was intentional to avoid langgraph4j's HITL interrupts.
- **MemorySaver checkpoints**: survive page refresh, **lost on server restart**. No persistence until session ends.
- **Frontend**: vanilla HTML/JS/CSS in `src/main/resources/static/`. No npm, no webpack, no build tools.
- **WebSocket endpoint**: `/ws/coach` — JSON protocol (see `architecture.md` for full schema).
- **Architecture document**: `architecture.md` is the design blueprint + decision log. It records 25 design decisions and implementation deviations. Read it before making structural changes; **do not edit it** casually.

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

## Project Structure (non-obvious)

```
com.hugosol.webagent/
├── graph/           # LangGraph: CoachState (10 channels) + 3 nodes + builder
│   └── nodes/       # ConversationNode, CorrectionNode, MergeResponseNode
├── agent/           # ConversationAgent, CorrectionAgent, ReportAgent
├── websocket/       # CoachWebSocketHandler (WS endpoint + JSON router)
├── service/         # GraphExecutionService (graph orchestration), SessionService (JPA)
├── model/           # JPA entities (Session, Message, ErrorRecord, SessionReport, UserProgress) + enums
├── repository/      # Spring Data JPA repos
├── config/          # LangChain4jConfig, WebSocketConfig, PromptLoader
└── speech/          # STT/TTS interfaces (V1 stubs, V2 will implement Whisper)
```

Prompts live in `src/main/resources/prompts/*.txt` and are loaded by `PromptLoader`.

## Conventions & Gotchas

- **No tests.** `mvn test` runs nothing. Use `mvn compile` for verification.
- **Token limit**: 128000 hardcoded in `CoachWebSocketHandler`. Warning at 80%.
- **Error types**: 5 categories — GRAMMAR, WORD_CHOICE, CHINGLISH, PRONUNCIATION, FLUENCY. The `CorrectionAgent` returns JSON parsed into `CorrectionData`.
- **iOS quirks**: TTS requires user-gesture-triggered 🔊 button click (no autoplay). `SpeechSynthesis.onend` is unreliable on iOS — UI updates happen on `AGENT_RESPONSE` receipt instead.
- **Session ID flow**: `SessionService` creates a UUID as `threadId`, `CoachWebSocketHandler` tracks `wsToSession` mapping, `GraphExecutionService` uses it as `RunnableConfig.threadId`.
- **`CoachState extends AgentState`**: accessed via `this.<Type>value(KEY).orElse(default)` — type-safe accessors are in `CoachState.java`, always use them instead of raw `data()` map access.
