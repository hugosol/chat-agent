# AGENTS.md — Chat Agent

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
# http://localhost:8080/h2-console — jdbc:h2:file:./data/englishcoach — sa / (empty)

# File logs (with local or prd profile, written to ./logs/)
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Tech Stack Summary

> Full tech stack with versions and rationale: [docs/architecture.md](docs/architecture.md)

- **Java 17** / **Spring Boot 3.4.7** / **Maven**
- **LangChain4j** (OpenAI-compatible adapter) + **langgraph4j** 1.8.16
- **DeepSeek** API (default model: `deepseek-v4-flash`)
- **H2** file database + **Spring Data JPA**
- **WebSocket** JSON protocol
- **React 18 + TypeScript** (Vite Library Mode, CSS Modules)
- **ONNX** all-MiniLM-L6-v2 for RAG embeddings

## Project Structure

```
com.hugosol.chatagent/
├── graph/           # LangGraph: ChatState (6 channels incl. USER_ID + MODE) + 1 node + builder
│   └── nodes/       # CorrectionNode (only remaining node)
├── agent/           # ConversationAgent (streaming), CorrectionAgent, ReportAgent, LearningAgent, MemoryCueAgent (detectSwitches reused by AssertionService)
│   └── common/       # TaskRunner (sync engine), TaskDefinition, TaskName (9 tasks), TaskContext, ErrorStrategy
├── flashcard/       # FSRS-6 scheduler (repeat + init) + CardState + Rating enum + AleaPrng (deterministic fuzz)
├── websocket/       # ChatWebSocketHandler (WS entry), ChatMessageHandler (protocol logic)
├── controller/      # FlashcardController — REST API (Cards CRUD + Tags CRUD + Import/Export + Back patch, 11 endpoints)
│                     # ReviewController — Review API (start, next, stats, decks)
│                     # TuneController — Tune API (review-count, optimize-logs, reschedule-logs)
├── protocol/        # ClientMessage/ServerMessage sealed types, ProtocolDispatcher, MessageHandler
├── service/         # SessionService (state + tokens + sessionToWs), TurnProcessor (parallel turns),
│                   # SessionComplete (session-ending pipeline), SessionDbStore (entity persistence),
│                   # FlashcardService (createCard with FSRS init + Tag upsert),
│                   # LearningProfileService, MemoryCueService, AssertionService,
│                   # EmbeddingService (RAG vectorization), SessionCleanupLogoutHandler, TokenTracker, EntityMapper
│   └── card/          # CardCsvParser, CardBatchService
├── model/           # JPA entities + enums
├── repository/      # Spring Data JPA repos
├── dto/             # Data transfer records
├── config/          # LangChain4jConfig, SecurityConfig, WebSocketConfig, AsyncConfig, etc.
└── speech/          # (vacant — V2 will add STT/TTS adapters when needed)
```

> Full project tree with frontend structure: [docs/architecture.md](docs/architecture.md)

## Conventions & Gotchas

> 做前端改动前必读 [docs/frontend-notes.md](docs/frontend-notes.md)（iOS 兼容、CSS 规范、测试 mock 模式）
> 做闪卡改动前必读 [docs/fsrs.md](docs/fsrs.md)（FSRS 算法、调度器设计、优化器）
> 测试清单与规范见 [docs/tests.md](docs/tests.md)

- **ADR 优先级**: `docs/adr/` 为历史决策记录，以代码和 README.md、docs/architecture.md 等持续更新文档为准。仅当 ADR 明确过期时追加过期标识。
- **Maven**: `mvn compile` (build), `mvn test` (unit tests), `mvn verify` (E2E — failsafe, includes IT)
- **Spring Security**: form login + remember-me + BCrypt. Config-driven: `app.security.permit-all-paths` controls auth bypass. No `@ConditionalOnProperty` on SecurityConfig.
- **Token limit**: 128000 hardcoded in `ChatWebSocketHandler`. Warning at 80%. Uses actual token count from `ChatResponse.tokenUsage().totalTokenCount()` (not estimated).
- **Error types**: 5 categories — GRAMMAR, WORD_CHOICE, CHINGLISH, PRONUNCIATION, FLUENCY.
- **Session ID flow**: `SessionService` creates a Session (JPA generates UUID), `ChatMessageHandler` tracks `sessionToWs`, `TurnProcessor` uses it as `RunnableConfig.threadId`. All three layers (H2, WebSocket, checkpoint) share the same ID.
- **Null guard**: `TurnProcessor.onCompleteResponse` checks `response != null` before accessing `tokenUsage()` — LangChain4j may callback with null on network errors.
- **Type safety**: `MessageData.role` and `CorrectionData.type` use Java enums (`MessageRole`, `ErrorType`) directly. `ErrorType` uses `@JsonCreator` for case-insensitive deserialization.
- **State encapsulation**: `ChatState` is internal to `SessionService`. `ChatMessageHandler` and `ReportAgent` never import `ChatState` directly.
- **Streaming WebSocket sends**: always use `synchronized(wsSession)` when sending from async threads. `sendSynced()` helper wraps IOException.
- **Session resume**: disconnect only removes `sessionToWs` mapping, never calls `removeSession()`. State stays in `activeStates` until explicit `END_SESSION`. `RESUME_SESSION` validates `userId` ownership.
- **UserId fallback**: `requireUserId()` returns `"anonymous"` if `ws.getPrincipal()` is null (E2E profile only — production always has a Principal).
- **Multi-tab**: `sessionToWs` map is one-to-one (sessionId → wsId). Page Visibility API triggers auto-resume on tab activation. Stale delta protection skips streaming tokens for already-rendered messageIds.
- **langgraph4j**: `Channels.base(() -> default)` and `Channels.appender(ArrayList::new)`, **not** `Channels.of()`.
- **1-node graph**: `START → correction → END`. Conversation extracted to Service layer for streaming. Parallel execution via `TurnProcessor.processTurn()`.
- **Correction display**: numbered summary bubble (`1. original → corrected`) in chat flow; detailed items in sidebar (absolute overlay, starts collapsed). Floating ⚠️ N ⚠️ badge toggles sidebar visibility.

## Data Isolation

- **Data isolation**: `Session.userId` (NOT NULL). Per-session queries naturally isolated by UUID; cross-session queries filter by `userId`.
- **Runtime user context**: `ChatState` has `USER_ID` channel. `SessionService.getUserId()` reads from ChatState — works in async threads (no ThreadLocal).
- **RESUME_SESSION validation**: checks session ownership via `ChatState.userId`. Returns "Session not found" if userId mismatch.
- **Session cleanup on logout**: `SessionCleanupLogoutHandler` clears all `activeStates` for the logging-out user.

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

> Full protocol with JSON examples: [docs/architecture.md](docs/architecture.md#websocket-协议)

## Flashcard Module

> 完整 FSRS 参考见 [docs/fsrs.md](docs/fsrs.md)
> 术语定义见 [CONTEXT.md](CONTEXT.md) 中 Flashcard 相关条目

- **REST API**: `FlashcardController` (cards CRUD, import/export, back patch) + `ReviewController` (start, next, stats, decks). `PATCH /api/cards/{id}/back` allows editing card back text during review. Authenticated via JSESSIONID cookie.
- **FSRS Scheduler**: instance class, `FsrsSchedulerConfig` runtime record. 4 review modes (STANDARD/REVIEW_ONLY/NEW_ONLY/CRAM).
- **FSRS Optimizer**: pure Java, Adam + finite-difference gradients (h=1e-4). Triggered via `POST /api/fsrs/optimize` or `@Scheduled` weekly. Every invocation is logged to `fsrs_optimize_logs` and `fsrs_reschedule_logs` tables for audit.
- **Caffeine Cache**: `expireAfterAccess(24h)` on `fsrsConfig` cache.
- **Frontend pages**: `/manage` (CardsTab/TagsTab), `/review` (DeckPicker → ReviewPage → CompletePage, with inline back edit), `/tune` (FSRS optimizer/rescheduler task logs + review count), `/settings` (preferences).

## Environment

| Variable | Default | Notes |
|----------|---------|-------|
| `DEEPSEEK_API_KEY` | *(required by default)* | Bypass with `local` profile: place key in `application-local.yml` |
| `DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | |
| `DEEPSEEK_MODEL` | `deepseek-v4-flash` | Config in `application.yml` |
| `LOG_DIR` | `./logs/` | File log output directory (local and prd profiles) |

No `.env` file support — use `local` profile (`application-local.yml`, gitignored) or set vars directly in shell.

## Logging

- **File logs**: Active with `local` and `prd` profiles. Daily rolling, 3-day retention. `./logs/chat-agent.YYYY-MM-DD.log`.
- **FSRS Task Log**: Optimizer and rescheduler invocations are persisted in `fsrs_optimize_logs` and `fsrs_reschedule_logs` tables. Query: `SELECT * FROM fsrs_optimize_logs ORDER BY start_time DESC`.
- **LLM Call Log**: Persisted asynchronously in `llm_call_logs` table via `llmLogExecutor` thread pool (core=2, max=4). Records older than 3 days cleaned up on startup. Query: `SELECT * FROM llm_call_logs ORDER BY create_time DESC`.

## Agent Skills

### Issue Tracker

Issues 以本地 markdown 文件形式存放在 `.scratch/<feature>/` 目录下。详见 `docs/agents/issue-tracker.md`。

### Triage Labels

使用五个标准 triage 角色标签：`needs-triage`、`needs-info`、`ready-for-agent`、`ready-for-human`、`wontfix`。详见 `docs/agents/triage-labels.md`。

### Domain Docs

单上下文布局 — `CONTEXT.md` + `docs/adr/` 在仓库根目录。详见 `docs/agents/domain.md`。
