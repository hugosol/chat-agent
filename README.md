# Chat Agent

AI-powered English speaking practice tool for Chinese developers.  
Uses **LangChain4j** + **langgraph4j** + **DeepSeek** to run 5 AI agents that role-play conversations, correct English errors in real-time, generate session reports, and maintain cross-session memory with structured topic cues.

## Quick Start

```bash
# 1. Clone
git clone <repo-url>
cd chat-agent

# 2. Set DeepSeek API key (pick one):
#    Option A: Create application-local.yml and set the key there, then run with local profile
#    Option B: Set environment variable

#    Option A — local profile:
#    Create src/main/resources/application-local.yml with:
#      langchain4j.openai.chat-model.api-key: sk-your-deepseek-api-key
mvn spring-boot:run -Dspring-boot.run.profiles=local

#    Option B — environment variable (Windows):
set DEEPSEEK_API_KEY=sk-your-deepseek-api-key
mvn spring-boot:run

#    Option B — environment variable (macOS / Linux):
export DEEPSEEK_API_KEY=sk-your-deepseek-api-key
mvn spring-boot:run

# 4. Open browser → http://localhost:8080
#    Default login: admin / admin123
#    (credentials configurable via app.initial-users in application.yml)
```

> **Note**: Frontend is **incrementally migrating** to React + TypeScript (built with Vite). Phase 1: shared Header component. Phase 2: shared utility layer (`src/shared/` — utils, TTS, Modal, Toast, ChipInput) + test framework (`src/__tests__/` by functional domain). Chat (`app.js`), flashcard (`flashcard.js`), and manage panels (`manage/*.js`) remain vanilla JS for now. `src/main/frontend/` has its own `package.json` and uses npm for frontend build. Build output (JS/CSS) is placed in `src/main/resources/static/shared/`. Node.js is required for local development.

## How to Use

| Step | Action |
|------|--------|
| 1 | Log in with username and password at the login page |
| 2 | Select **mode** (e.g. Standup Meeting, Daily Talk) from the dropdown |
| 3 | Click **Start Session** |
| 4 | Type your English message → press **Enter** or click **Send** |
| 5 | Agent replies with natural English + embedded corrections |
| 6 | Correction summary appears below your message in chat; tap the **⚠️ N ◂** floating badge at screen center-right to expand the correction sidebar with detailed items |
| 7 | Click **🔊** on any Agent message to hear TTS playback |
| 8 | Click **End & Report** to get a fluency score + error summary |
| 9 | Click **Logout** in header to sign out |

> **iOS tip**: The keyboard microphone (🎤) can be used for system-level dictation — the recognized text appears in the input field, then press Send.

## Profiles

| Profile | Config File | Login Required? | H2 Console |
|---------|------------|:---:|:---:|
| `default` | `application.yml` | ✅ Yes | Authenticated only |
| `local` | `application-local.yml` | ✅ Yes | Open (no auth) |
| `e2e` | `application-e2e.yml` (test only) | ❌ No | Disabled |

Profiles control authentication via `app.security.permit-all-paths` — a list of URL patterns that bypass login. The `e2e` profile sets `[/**]` to allow unrestricted access for automated tests.

## Testing

```bash
# Unit tests only
mvn test

# E2E regression tests (first run downloads Chromium ~150MB)
mvn verify
```

E2E tests use **Playwright** (Java) with headless Chromium in mobile Safari viewport (390×844), and **WireMock** (fixed port `19090`) to mock DeepSeek API responses at the HTTP layer. DOM-based assertions verify the full browser-to-server-to-browser flow:

| Test Class | What It Verifies |
|-----------|-----------------|
| `ChatAgentSessionIT` | Complete session: Start → 3-turn conversation → corrections in sidebar → End & Report → H2 data persistence |
| `ChatAgentResumeIT` | Page reload → `localStorage` sessionId survives → all messages + corrections restored in DOM |
| `ChatAgentMemoryIT` | Two sessions back-to-back → Topic Memory v1→v2 direct write → Learning Profile v1→v2 merge → topic memory mode-scoped isolation → learning profile cross-mode sharing |
| `DailyTalkIT` | DAILY_TALK mode → 3-turn casual conversation → teaching-style corrections → mode-scoped memory |
| `ChatAgentMemoryCueIT` | Session end → MemoryCue two-step LLM (topic split + per-segment summarization) → `memory_cues` table COMPLETED records |
| `ManagePageIT` | Manage page full flow: nav sidebar → tag CRUD → card CRUD → search → sort → deck chip filtering → pagination → detail modal → orphan alert → delete cascade |
| `FlashcardIT` | 闪卡录入：两阶段面板 → chip 标签创建 → 保存 → H2 数据验证（不依赖 WireMock，闪卡不调 LLM） |

Test resources: `src/test/resources/wiremock/` (mock response files for conversation, correction, report, memory merge, and memory cue), `src/test/resources/application-e2e.yml` (in-memory H2, permit all paths).

## H2 Database Console

The app uses an embedded H2 file database. Access the console for debugging:

```
URL:      http://localhost:8080/h2-console
JDBC URL: jdbc:h2:file:./data/englishcoach
Username: sa
Password: (leave empty)
```

> H2 console is open by default with the `local` profile. With the `default` profile, you must log in first.

## Logging

**File logs** (`logback-spring.xml`, local profile only):

```bash
# Activate local profile to enable file logging
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Logs written to: ./logs/chat-agent.YYYY-MM-DD.log
# Console: INFO level — File: DEBUG level, 3-day rolling retention
```

**LLM Call Log** (`llm_call_logs` table): Every LLM API call (prompt, response, token usage, duration, status) is persisted asynchronously in H2. Query via H2 console:

```sql
SELECT * FROM llm_call_logs ORDER BY create_time DESC;
```

Records older than 3 days are automatically cleaned up on startup.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.4 |
| Security | Spring Security (form login, remember-me, BCrypt) |
| LLM | DeepSeek V4 FAST (via LangChain4j OpenAI-compatible adapter) |
| Agent orchestration | langgraph4j 1.8.16 (`org.bsc.langgraph4j`) |
| Database | H2 (file mode) + Spring Data JPA |
| Communication | WebSocket (JSON protocol) |
| Frontend | React 18 + TypeScript (Vite build) + Vanilla HTML/JS (incremental migration) |
| TTS | Browser SpeechSynthesis (user-gesture triggered for iOS support) |
| Embedding & RAG | ONNX all-MiniLM-L6-v2 (384-dim, ~200MB heap) + InMemoryEmbeddingStore (JSON disk persistence) |

## Architecture

```
Browser (login page → chat page with 🔊 TTS)
    │  HTTP + WebSocket JSON
    ▼
Spring Security  ──►  /login  ──►  /index.html  ──►  /ws/chat
    │
    ▼
ChatWebSocketHandler  ──►  ChatMessageHandler  ──►  TurnProcessor  ──►  LangGraph (1 node: correction)
    │                              │                        │
     │                              │                        ├── EmbeddingService.search() → RAG MemoryCue (every round)
     │                              │                        ├── LearningProfile + last MemoryCue fallback (round 1 only)
    │                              │                        ├── Future A: ConversationAgent → DeepSeek (streaming)
    │                              │                        └── Future B: CorrectionNode → CorrectionAgent → DeepSeek
    │                              │
    │                              ├── SessionService (runtime state + token tracking)
    │                              ├── ReportAgent → DeepSeek (session-end)
    │                              ├── LearningProfileService (Topic direct write + Profile merge) ──► MemoryAgent → DeepSeek
    │                              ├── MemoryCueService (async topic split + segment cues) ──► MemoryCueAgent → DeepSeek
    │                              │       └── EmbeddingService.indexAsync() → ONNX vectorization → embedding-store.json
    │                              ├── EmbeddingService (RAG search + index + disk persistence)
    │                              └── SessionDbStore → H2 (JPA)
    │
    ▼
AGENT_STREAM_DELTA / AGENT_STREAM_END / CORRECTION_RESULT / SESSION_REPORT
```

### Authentication & User Module

- **Spring Security** form login with HTTP session cookie + remember-me (14 days).
- **User data isolation**: `Session` entity has `userId` field. All per-session queries (find by sessionId UUID) are naturally isolated. Only cross-session queries (history, progress) filter by user.
- **Runtime user context**: `ChatState` stores `userId` as a langgraph channel, accessible to all async processing threads.
- **Logout**: Explicit logout clears all active sessions via `SessionCleanupLogoutHandler`. Tab close without logout preserves sessions for resume.
- **Multi-tab**: `sessionToWs` map is one-to-one (sessionId → wsId). Page Visibility API triggers auto-resume on tab activation, keeping UI fresh across tabs.
- **Config-driven auth**: `app.security.permit-all-paths` controls which URL patterns skip authentication. No conditional annotations on SecurityConfig.

### 5 AI Agents

| Agent | Responsibility |
|-------|---------------|
| **ConversationAgent** | Role-plays according to the selected AgentMode (scenario + persona combined), generates natural English dialogue |
| **CorrectionAgent** | Analyzes user input for 5 error types: grammar, word choice, Chinglish, pronunciation hints, fluency |
| **ReportAgent** | Generates end-of-session summary: fluency score, error breakdown, key takeaway |
| **MemoryAgent** | Saves session topic summary directly as a new User Memory version. Merges new session error data with existing Learning Profile via LLM into an updated summary. |
| **MemoryCueAgent** | Two-step post-session LLM: detects topic switch points in conversation, then generates structured `(topic, summary)` pairs per segment. Each completed entry is asynchronously vectorized by `EmbeddingService` for RAG semantic retrieval. |

### LangGraph State Machine (Per-Turn)

```
START → CorrectionNode → END
```

The Service layer manages the session loop. ConversationAgent is invoked in parallel via `TurnProcessor` with streaming WebSocket push. `SessionService` manages runtime state and token tracking. `MemorySaver` checkpoints state per `threadId` — survives page refresh, lost on server restart.

Every round performs RAG semantic search via `EmbeddingService.search()` against historical MemoryCue entries (top-2, cosine ≥ 0.6). On round 1, if RAG returns no matches, a fallback loads the most recent session's last COMPLETED MemoryCue from H2 as a conversation continuity anchor with a time label. LearningProfile is injected on round 1 only. There is no dual-track switching between Topic Memory and RAG — all memory retrieval is unified through the embedding pipeline. At session end, `LearningProfileService` directly saves the new Topic Memory as a new version and fires an async LLM merge for Learning Profile, while `MemoryCueService` concurrently dispatches topic-split and per-segment cue generation, followed by `EmbeddingService.indexAsync()` vectorization — all on the `memoryExecutor` thread pool (core=4, max=8) and `embeddingExecutor` (core=2, max=2).

## Project Structure

```
chat-agent/
├── pom.xml
├── src/main/java/com/hugosol/chatagent/
│   ├── ChatAgentApplication.java
│   ├── graph/
│   │   ├── ChatState.java
│   │   ├── ChatGraphBuilder.java
│   │   └── nodes/
│   │       └── CorrectionNode.java
│   ├── dto/
│   │   ├── MessageData.java
│   │   ├── CorrectionData.java
│   │   ├── MemoryContent.java
│   │   └── CueMatch.java
│   ├── agent/
│   │   ├── ConversationAgent.java
│   │   ├── CorrectionAgent.java
│   │   ├── ReportAgent.java
│   │   ├── MemoryAgent.java
│   │   └── MemoryCueAgent.java
│   ├── websocket/
│   │   ├── ChatWebSocketHandler.java
│   │   └── ChatMessageHandler.java
│   ├── protocol/
│   │   ├── ClientMessage.java
│   │   ├── ServerMessage.java
│   │   ├── MessageHandler.java
│   │   └── ProtocolDispatcher.java
│   ├── speech/         (预留，V2 按实际需求定义 STT/TTS 接口)
│   ├── model/          (JPA entities + enums: User, Session, Message, ErrorRecord, SessionReport, UserProgress, UserLearningProfile, MemoryCue, LlmCallLog, MemoryCueStatus, AgentMode, TimeLabel, ...)
│   ├── repository/     (Spring Data JPA)
│   ├── service/        (SessionService, TurnProcessor, SessionDbStore, LearningProfileService, MemoryCueService, EmbeddingService, LlmCallLogService, TokenTracker, EntityMapper, SessionCleanupLogoutHandler)
│   └── config/         (LangChain4jConfig, LoggableChatModel, SecurityConfig, WebSocketConfig, AsyncConfig, AppProperties, PasswordEncoderConfig, DataInitializer, PromptLoader)
├── src/main/resources/
│   ├── application.yml
│   ├── application-local.yml
│   ├── logback-spring.xml
│   └── prompts/
│       ├── conversation-system.txt       ← 骨架模板（{Description} / {Rules} 占位符）
│       ├── workplace_standup/            ← per-AgentMode 子目录
│       │   ├── description.txt           ← 身份声明 + 场景描述
│       │   └── rules.txt                 ← 行为规则
│       ├── daily_talk/                   ← per-AgentMode 子目录 (Chris)
│       │   ├── description.txt           ← 身份声明 + 场景描述
│       │   └── rules.txt                 ← 行为规则
│       ├── correction.txt
│       ├── report.txt
│       ├── memory-profile.txt
│       ├── memory-cue-split.txt
│       └── memory-cue-entry.txt
├── src/main/resources/static/
│   ├── login/
│   │   ├── main.html
│   │   ├── main.js
│   │   └── main.css
│   ├── manage/
│   │   ├── index.html
│   │   ├── card.js
│   │   ├── tag.js
│   │   ├── modal.js
│   │   └── manage.css
│   ├── shared/
│   │   ├── header-bundle.js          // React Header IIFE bundle (replaces nav.js)
│   │   ├── header-bundle.css         // React Header styles (CSS Modules)
│   │   ├── react.production.min.js   // React 18 runtime (UMD)
│   │   ├── react-dom.production.min.js // ReactDOM 18 runtime (UMD)
│   │   └── base.css
│   ├── index.html
│   ├── app.js
│   ├── flashcard.js
│   └── style.css
└── src/test/
    ├── java/com/hugosol/chatagent/e2e/    # E2E regression tests (Playwright + WireMock)
    │   ├── ChatAgentSessionIT.java
    │   ├── ChatAgentResumeIT.java
    │   ├── ChatAgentMemoryIT.java
    │   ├── DailyTalkIT.java
    │   ├── ChatAgentMemoryCueIT.java
    │   └── helper/
    │       ├── E2ETestBase.java
    │       └── WireMockStubs.java
    └── resources/
        ├── application-e2e.yml           # E2E profile (memory H2, WireMock base-url, permit-all-paths: [/**])
        ├── prompts/                       # Test prompt overrides (correction, report, memory-cue-split, memory-cue-entry)
        └── wiremock/                      # Mock response files (SSE streams + JSON for all agents)
```

## Configuration

Environment variables (set before running):

| Variable | Default | Description |
|----------|---------|-------------|
| `DEEPSEEK_API_KEY` | *(required)* | Your DeepSeek API key |
| `DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | DeepSeek API endpoint |
| `DEEPSEEK_MODEL` | `deepseek-v4-flash` | Model name |

App-level configuration in `application.yml`:

| Property | Default | Description |
|----------|---------|-------------|
| `app.initial-users` | `[{username: admin, password: admin123}]` | Initial user accounts (BCrypt-hashed on startup) |
| `app.security.permit-all-paths` | `[/login/**]` | URL patterns that skip authentication |
| `app.token-limit` | `128000` | Max LLM tokens per session |
| `app.token-limit-ratio` | `0.8` | Warning threshold ratio |
| `app.memory.profile-max-length` | `400` | Learning Profile merged text max characters |
| `app.memory.cue-topic-max-words` | `7` | MemoryCue topic name max word count |
| `app.memory.cue-summary-max-sentences` | `4` | MemoryCue summary max sentence count |
| `app.memory.retrieval.top-k` | `2` | Max RAG search results per turn |
| `app.memory.retrieval.similarity-threshold` | `0.6` | Minimum cosine similarity for RAG match |
| `app.llm.max-output-tokens.default` | `2048` | Default max output tokens for all agents |
| `app.llm.max-output-tokens.report` | `4096` | Max output tokens for ReportAgent (overrides default) |

## Known Limitations

| Limitation | Detail |
|-----------|--------|
| **iOS TTS** | Requires clicking 🔊 button on each message (browser blocks auto-play without user gesture) |
| **Mobile input** | Text-only (SpeechRecognition API not supported by iOS Safari/Chrome). iOS keyboard mic provides system dictation. |
| **Correction sidebar** | Starts hidden. When corrections arrive, a floating ⚠️ N ◂ badge appears at center-right; click to expand the 260px sidebar. Click ▸ in header to collapse. Opening ☰ nav menu auto-collapses sidebar. |
| **Token window** | UI shows warning at 80% usage. User must manually end session before overflow. |
| **ONNX memory** | The all-MiniLM-L6-v2 embedding model consumes ~200MB heap at runtime. |

## V2 Roadmap

- [ ] OpenAI Whisper for server-side voice input
- [x] Additional AgentMode values (DAILY_TALK with Chris persona — casual friend+tutor chat)
- [x] Cross-session memory (Topic Memory + Learning Profile dual memory system)
- [x] Structured MemoryCue (topic segmentation + tagged memory entries, write-only in v1)
- [x] RAG-based MemoryCue retrieval (ONNX vector embeddings, semantic similarity search)
- [x] 闪卡录入模块（FSRS-6 初始化 + 两阶段面板 + chip 标签 + REST API）
- [ ] 闪卡复习功能（FSRS-6 repeat + 评分按钮 Again/Hard/Good/Easy + 每日复习队列）
- [ ] More AgentMode scenarios (e.g. 1-on-1 Meeting, Technical Presentation)
- [ ] Technical presentation practice scenario
- [ ] Progress trend charts (error reduction over time)
- [ ] Redis/Postgres checkpoint saver for session persistence across restarts
- [ ] Human-in-the-loop correction review
