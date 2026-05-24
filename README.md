# Web Agent

AI-powered English speaking practice tool for Chinese Java developers.  
Uses **LangChain4j** + **langgraph4j** + **DeepSeek** to run 3 AI agents that role-play conversations, correct English errors in real-time, and generate session reports.

## Quick Start

```bash
# 1. Clone
git clone <repo-url>
cd web-agent

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

> **Note**: `node_modules` / `npm` / `webpack` are not used. The frontend is vanilla HTML + JS served directly by Spring Boot from `src/main/resources/static/`.

## How to Use

| Step | Action |
|------|--------|
| 1 | Log in with username and password at the login page |
| 2 | Select **mode** (e.g. Standup Meeting, Daily Talk) from the dropdown |
| 3 | Click **Start Session** |
| 4 | Type your English message → press **Enter** or click **Send** |
| 5 | Agent replies with natural English + embedded corrections |
| 6 | Correction summary appears below your message in chat; tap **"Corrections N"** in header to see details |
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
| `EnglishCoachSessionIT` | Complete session: Start → 3-turn conversation → corrections in sidebar → End & Report → H2 data persistence |
| `EnglishCoachResumeIT` | Page reload → `localStorage` sessionId survives → all messages + corrections restored in DOM |

Test resources: `src/test/resources/wiremock/` (7 mock response files), `src/test/resources/application-e2e.yml` (in-memory H2, permit all paths).

## H2 Database Console

The app uses an embedded H2 file database. Access the console for debugging:

```
URL:      http://localhost:8080/h2-console
JDBC URL: jdbc:h2:file:./data/englishcoach
Username: sa
Password: (leave empty)
```

> H2 console is open by default with the `local` profile. With the `default` profile, you must log in first.

Tables: `users`, `sessions`, `messages`, `error_records`, `session_reports`, `user_progress`

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
| Frontend | Native HTML + Vanilla JS (no build tools) |
| TTS | Browser SpeechSynthesis (user-gesture triggered for iOS support) |

## Architecture

```
Browser (login page → chat page with 🔊 TTS)
    │  HTTP + WebSocket JSON
    ▼
Spring Security  ──►  /login  ──►  /index.html  ──►  /ws/coach
    │
    ▼
CoachWebSocketHandler  ──►  CoachMessageHandler  ──►  TurnProcessor  ──►  LangGraph (1 node: correction)
    │                              │                        │
    │                              │                        ├── Future A: ConversationAgent → DeepSeek (streaming)
    │                              │                        └── Future B: CorrectionNode → CorrectionAgent → DeepSeek
    │                              │
    │                              ├── SessionService (runtime state + token tracking)
    │                              ├── ReportAgent → DeepSeek (session-end)
    │                              └── SessionStore → H2 (JPA)
    │
    ▼
AGENT_STREAM_DELTA / AGENT_STREAM_END / CORRECTION_RESULT / SESSION_REPORT
```

### Authentication & User Module

- **Spring Security** form login with HTTP session cookie + remember-me (14 days).
- **User data isolation**: `Session` entity has `userId` field. All per-session queries (find by sessionId UUID) are naturally isolated. Only cross-session queries (history, progress) filter by user.
- **Runtime user context**: `CoachState` stores `userId` as a langgraph channel, accessible to all async processing threads.
- **Logout**: Explicit logout clears all active sessions via `SessionCleanupLogoutHandler`. Tab close without logout preserves sessions for resume.
- **Multi-tab**: `sessionToWs` map is one-to-one (sessionId → wsId). Page Visibility API triggers auto-resume on tab activation, keeping UI fresh across tabs.
- **Config-driven auth**: `app.security.permit-all-paths` controls which URL patterns skip authentication. No conditional annotations on SecurityConfig.

### 3 AI Agents

| Agent | Responsibility |
|-------|---------------|
| **ConversationAgent** | Role-plays according to the selected AgentMode (scenario + persona combined), generates natural English dialogue |
| **CorrectionAgent** | Analyzes user input for 5 error types: grammar, word choice, Chinglish, pronunciation hints, fluency |
| **ReportAgent** | Generates end-of-session summary: fluency score, error breakdown, vocabulary suggestions, key takeaway |

### LangGraph State Machine (Per-Turn)

```
START → CorrectionNode → END
```

The Service layer manages the session loop. ConversationAgent is invoked in parallel via `TurnProcessor` with streaming WebSocket push. `SessionService` manages runtime state and token tracking. `MemorySaver` checkpoints state per `threadId` — survives page refresh, lost on server restart.

## Project Structure

```
web-agent/
├── pom.xml
├── src/main/java/com/hugosol/webagent/
│   ├── WebAgentApplication.java
│   ├── graph/
│   │   ├── CoachState.java
│   │   ├── CoachGraphBuilder.java
│   │   ├── MessageData.java
│   │   ├── CorrectionData.java
│   │   └── nodes/
│   │       └── CorrectionNode.java
│   ├── agent/
│   │   ├── ConversationAgent.java
│   │   ├── CorrectionAgent.java
│   │   └── ReportAgent.java
│   ├── websocket/
│   │   ├── CoachWebSocketHandler.java
│   │   └── CoachMessageHandler.java
│   ├── protocol/
│   │   ├── ClientMessage.java
│   │   ├── ServerMessage.java
│   │   ├── MessageHandler.java
│   │   └── ProtocolDispatcher.java
│   ├── speech/         (预留，V2 按实际需求定义 STT/TTS 接口)
│   ├── model/          (JPA entities + enums: User, Session, Message, ErrorRecord, SessionReport, UserProgress, AgentMode, ...)
│   ├── repository/     (Spring Data JPA)
│   ├── service/        (SessionService, TurnProcessor, SessionStore, TokenTracker, EntityMapper, SessionCleanupLogoutHandler)
│   └── config/         (LangChain4jConfig, SecurityConfig, WebSocketConfig, AppProperties, PasswordEncoderConfig, DataInitializer, PromptLoader)
├── src/main/resources/
│   ├── application.yml
│   ├── application-local.yml
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
│       ├── memory-topic.txt
│       └── memory-profile.txt
├── src/main/resources/static/
│   ├── login/
│   │   ├── main.html
│   │   ├── main.js
│   │   └── main.css
│   ├── index.html
│   ├── app.js
│   └── style.css
└── src/test/
    ├── java/com/hugosol/webagent/e2e/    # E2E regression tests (Playwright + WireMock)
    │   ├── EnglishCoachSessionIT.java
    │   ├── EnglishCoachResumeIT.java
    │   └── helper/
    │       ├── E2ETestBase.java
    │       └── WireMockStubs.java
    └── resources/
        ├── application-e2e.yml           # E2E profile (memory H2, WireMock base-url, permit-all-paths: [/**])
        ├── prompts/                       # Test prompt overrides
        └── wiremock/                      # 7 mock response files (SSE streams + JSON)
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

## Known Limitations

| Limitation | Detail |
|-----------|--------|
| **iOS TTS** | Requires clicking 🔊 button on each message (browser blocks auto-play without user gesture) |
| **Mobile input** | Text-only (SpeechRecognition API not supported by iOS Safari/Chrome). iOS keyboard mic provides system dictation. |
| **Correction sidebar** | Overlay panel (doesn't squeeze chat). Starts collapsed, tap "Corrections N" in header to toggle. |
| **Token window** | UI shows warning at 80% usage. User must manually end session before overflow. |

## V2 Roadmap

- [ ] OpenAI Whisper for server-side voice input
- [x] Additional AgentMode values (DAILY_TALK with Chris persona — casual friend+tutor chat)
- [ ] More AgentMode scenarios (e.g. 1-on-1 Meeting, Technical Presentation)
- [ ] Technical presentation practice scenario
- [ ] Progress trend charts (error reduction over time)
- [ ] Redis/Postgres checkpoint saver for session persistence across restarts
- [ ] Human-in-the-loop correction review
