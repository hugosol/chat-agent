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

# 4. Open browser
#    Desktop: http://localhost:8080
#    Phone:   http://<your-computer-ip>:8080
```

> **Note**: `node_modules` / `npm` / `webpack` are not used. The frontend is vanilla HTML + JS served directly by Spring Boot from `src/main/resources/static/`.

## How to Use

| Step | Action |
|------|--------|
| 1 | Select **scenario** (Standup / 1-on-1) and **persona** (Colleague / Manager) |
| 2 | Click **Start Session** |
| 3 | Type your English message → press **Enter** or click **Send** |
| 4 | Agent replies with natural English + embedded corrections |
| 5 | Click **🔊** on any Agent message to hear TTS playback |
| 6 | Click **End & Report** to get a fluency score + error summary |

> **iOS tip**: The keyboard microphone (🎤) can be used for system-level dictation — the recognized text appears in the input field, then press Send.

## H2 Database Console

The app uses an embedded H2 file database. Access the console for debugging:

```
URL:      http://localhost:8080/h2-console
JDBC URL: jdbc:h2:file:./data/englishcoach
Username: sa
Password: (leave empty)
```

Tables: `sessions`, `messages`, `error_records`, `session_reports`, `user_progress`

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.4 |
| LLM | DeepSeek V4 FAST (via LangChain4j OpenAI-compatible adapter) |
| Agent orchestration | langgraph4j 1.8.16 (`org.bsc.langgraph4j`) |
| Database | H2 (file mode) + Spring Data JPA |
| Communication | WebSocket (JSON protocol) |
| Frontend | Native HTML + Vanilla JS (no build tools) |
| TTS | Browser SpeechSynthesis (user-gesture triggered for iOS support) |

## Architecture

```
Browser (text input + 🔊 TTS)
    │  WebSocket JSON
    ▼
CoachWebSocketHandler  ──►  GraphExecutionService  ──►  LangGraph (3 nodes)
    │                              │                        │
    │                              │                        ├── ConversationNode → ConversationAgent → DeepSeek
    │                              │                        ├── CorrectionNode   → CorrectionAgent   → DeepSeek
    │                              │                        └── MergeResponseNode (token counting)
    │                              │
    │                              ├── SessionService ──►  H2 (JPA)
    │                              └── ReportAgent    ──►  DeepSeek (session-end)
    │
    ▼
STATE_UPDATE / AGENT_RESPONSE / SESSION_REPORT
```

### 3 AI Agents

| Agent | Responsibility |
|-------|---------------|
| **ConversationAgent** | Role-plays as a workplace colleague/manager, generates natural English dialogue |
| **CorrectionAgent** | Analyzes user input for 5 error types: grammar, word choice, Chinglish, pronunciation hints, fluency |
| **ReportAgent** | Generates end-of-session summary: fluency score, error breakdown, vocabulary suggestions, key takeaway |

### LangGraph State Machine (Per-Turn)

```
START → ConversationNode → CorrectionNode → MergeResponseNode → END
```

The Service layer manages the session loop. `MemorySaver` checkpoints state per `threadId` — survives page refresh, lost on server restart.

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
│   │       ├── ConversationNode.java
│   │       ├── CorrectionNode.java
│   │       └── MergeResponseNode.java
│   ├── agent/
│   │   ├── ConversationAgent.java
│   │   ├── CorrectionAgent.java
│   │   └── ReportAgent.java
│   ├── websocket/
│   │   └── CoachWebSocketHandler.java
│   ├── speech/
│   │   ├── SpeechToTextService.java
│   │   └── TextToSpeechService.java
│   ├── model/          (JPA entities + enums)
│   ├── repository/     (Spring Data JPA)
│   ├── service/        (GraphExecutionService, SessionService)
│   └── config/         (LangChain4j, WebSocket, PromptLoader)
├── src/main/resources/
│   ├── application.yml
│   └── prompts/
│       ├── conversation.txt
│       ├── correction.txt
│       └── report.txt
└── src/main/resources/static/
    ├── index.html
    ├── app.js
    └── style.css
```

## Configuration

Environment variables (set before running):

| Variable | Default | Description |
|----------|---------|-------------|
| `DEEPSEEK_API_KEY` | *(required)* | Your DeepSeek API key |
| `DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | DeepSeek API endpoint |
| `DEEPSEEK_MODEL` | `deepseek-chat` | Model name |

All other settings are in `src/main/resources/application.yml`.

## Known Limitations

| Limitation | Detail |
|-----------|--------|
| **iOS TTS** | Requires clicking 🔊 button on each message (browser blocks auto-play without user gesture) |
| **Mobile input** | Text-only (SpeechRecognition API not supported by iOS Safari/Chrome). iOS keyboard mic provides system dictation. |
| **Checkpoint persistence** | MemorySaver — lost on server restart. Upgrade to Postgres/Redis Saver for production. |
| **Token window** | UI shows warning at 80% usage. User must manually end session before overflow. |

## V2 Roadmap

- [ ] OpenAI Whisper for server-side voice input
- [ ] Technical presentation practice scenario
- [ ] Progress trend charts (error reduction over time)
- [ ] Redis/Postgres checkpoint saver for session persistence across restarts
- [ ] Human-in-the-loop correction review
