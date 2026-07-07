## PURPOSE

Chat Agent is an AI-powered English coaching application that combines conversational practice with a spaced-repetition flashcard system. The primary purpose is to help Chinese English learners improve through:
- **Conversation Practice**: Real-time AI chat with grammar correction, vocabulary suggestions, and fluency assessment via LangChain4j + DeepSeek API
- **Flashcard Review**: FSRS-6 spaced repetition system for vocabulary retention, with REST API + React frontend
- **Persistent Memory**: Dual-track memory system (RAG semantic retrieval for topic continuity + Learning Profile for skill assessment)

## STACK

### Backend
- **Java 17 / Spring Boot 3.4.7 / Maven** — core framework
- **LangChain4j + langgraph4j 1.8.16** — LLM orchestration with streaming, checkpoint persistence (H2-backed `Saver`)
- **DeepSeek API** — primary LLM (default: `deepseek-v4-flash`, OpenAI-compatible adapter)
- **H2 file database + Spring Data JPA** — zero-infrastructure persistence
- **ONNX all-MiniLM-L6-v2** — local embeddings for RAG (384-dim, 80MB model, ~200MB heap)
- **WebSocket JSON protocol** — real-time bidirectional chat
- **FSRS-6 Scheduler** — pure Java instance class with Adam optimizer (numerical gradients, h=1e-4)

### Frontend
- **React 18 + TypeScript** — progressive migration from vanilla JS, Vite Library Mode (IIFE bundles)
- **CSS Modules** — scoped styling with Zelda: Breath of the Wild theme via CSS custom properties (47 tokens)
- **useReducer + context** — centralized WebSocket state management (7 message types)

### Infrastructure
- **Spring Security** — form login + BCrypt + remember-me
- **Caffeine Cache** — 24h FSRS config cache
- **Async thread pools** — memoryExecutor (4/8), embeddingExecutor (2/2), llmLogExecutor (2/4), optimizerExecutor

## ARCHITECTURE

### Package Layout
```
com.hugosol.chatagent/
├── graph/          — langgraph4j: ChatState (6 channels) + 1-node correction graph
├── agent/          — ConversationAgent, CorrectionAgent, ReportAgent, LearningAgent, MemoryCueAgent
├── flashcard/      — FSRS-6 scheduler (instance class), CardState, Rating, AleaPrng
├── websocket/      — ChatWebSocketHandler (entry), ChatMessageHandler (protocol)
├── controller/     — FlashcardController (11 REST endpoints), ReviewController, TuneController
├── protocol/       — ClientMessage/ServerMessage sealed types, ProtocolDispatcher
├── service/        — SessionService, TurnProcessor, FlashcardService, EmbeddingService, AssertionService
├── model/          — JPA entities (Session, UserMemory, MemoryCue, MemoryAssertion, Card, etc.)
├── repository/     — Spring Data JPA repositories
├── dto/            — data transfer records
├── config/         — LangChain4jConfig, SecurityConfig, WebSocketConfig, AsyncConfig
└── speech/         — (vacant, V2 STT/TTS)
```

### Key Architectural Decisions
1. **LangGraph as state container, not orchestrator**: 1-node graph (START → correction → END). Conversation extracted to Service layer for streaming control. Parallel execution via TurnProcessor.processTurn()
2. **ChatState encapsulation**: Internal to SessionService. ChatMessageHandler and ReportAgent never import ChatState directly
3. **Session ID as unified key**: Same UUID across H2, WebSocket (sessionToWs map), and langgraph4j checkpoint (RunnableConfig.threadId)
4. **FSRS two-layer config**: FsrsParameters (system-managed W[21]) + UserPreferences (user-configurable learning steps, retention, fuzz). Merged at runtime via FsrsSchedulerConfig.merge()
5. **Memory system**: MemoryCue (RAG semantic retrieval with LRU queue) + LearningProfile (first-turn System Prompt injection) — superseded earlier Topic Memory + Tag Consolidation approaches
6. **Progressive React migration**: IIIFE bundles via Vite Library Mode, CSS Modules, useReducer+context for WebSocket state. Vanilla JS gradually replaced per-module (Phase 1: CorrectionSidebar, Phase 2: ChatProvider+Header, Phase 3: MessageList+ChatInput+Footer)

### Data Flow
```
User Input → WebSocket → ChatMessageHandler → TurnProcessor
  ├─ EmbeddingService.search() → MemoryCueQueue (LRU)
  ├─ CorrectionAgent.correct() → langgraph4j checkpoint
  └─ ConversationAgent.generateStream() → streaming WS deltas

Session End → SessionComplete pipeline:
  ├─ ReportAgent → LearningProfile update
  ├─ MemoryCueAgent → detectSwitches → generateCue per segment → EmbeddingService.indexAsync()
  └─ AssertionService → assertion extraction pipeline
```

## PATTERNS

- **Deep modules**: Prefer narrow interfaces with deep implementations. ChatState is internal; services expose minimal public methods
- **Fail-fast**: ErrorStrategy THROW for critical pipelines (assertion extraction), SWALLOW for non-critical (legacy MemoryCue)
- **Null guard**: TurnProcessor.onCompleteResponse checks response != null before tokenUsage() — LangChain4j may callback null on network errors
- **Thread safety**: synchronized(wsSession) for WebSocket sends from async threads; static Object lock for tag consolidation; Manager serialization for assertion dedup
- **Type safety**: Java enums for MessageRole and ErrorType; ErrorType uses @JsonCreator for case-insensitive deserialization
- **Boring over clever**: Pure Java Adam optimizer (no DL frameworks), InMemoryEmbeddingStore (no pgvector), H2 (no PostgreSQL)
- **Determinism**: Fixed RNG seed (42) for FSRS optimizer; deterministic fuzz via AleaPrng
- **Progressive UI migration**: Each React component independently bundled; vanilla JS bridge via window.ChatAgent.registerHandler()

## TRADEOFFS

| Tradeoff | Decision | Rationale |
|----------|----------|-----------|
| Numerical vs analytical gradients | Numerical (h=1e-4) | Single-JAR deployment; 42 loss evals/step acceptable for async background task |
| Rust/WASM vs pure Java FSRS | Pure Java | Avoid platform binary dependency; cross-validate with py-fsrs on loss, not per-element W[21] |
| InMemoryEmbeddingStore vs pgvector | InMemory + JSON file | Zero infrastructure for single-user; future: shard by userId×mode for multi-user |
| ONNX local vs API embedding | ONNX all-MiniLM-L6-v2 | ~200MB heap acceptable; no API cost; no network latency |
| Tag consolidation vs RAG | RAG semantic retrieval | Tags inconsistent across sessions; vector similarity bridges vocabulary gaps |
| Static vs instance FSRS Scheduler | Instance class | Per-user W[21] parameters require per-user Scheduler instances |
| Two-layer vs single config entity | Two-layer (FsrsParameters + UserPreferences) | System-computed W[21] vs user-chosen preferences have different data lifecycles |
| SPA vs progressive React migration | Progressive (Vite Library Mode) | Lower risk; existing Spring Boot multi-HTML architecture preserved |
| CSS-in-JS vs CSS Modules + tokens | CSS Modules + CSS custom properties | Zero build deps; runtime theme switching potential; Zelda theme via 47 design tokens |
| Versioned vs in-place tag update | In-place (superseded by RAG) | Tags are low-fidelity metadata; simplicity over audit trail |

## PHILOSOPHY

1. **Correctness first, then maintainability**: Code that compiles to minimal allocations; no needless copies or computation
2. **Boring technology**: H2 over PostgreSQL, pure Java over DL frameworks, CSS custom properties over preprocessors, React 18 over cutting-edge frameworks
3. **Single-JAR deployment**: No native binaries, no external services required at runtime beyond DeepSeek API
4. **Progressive over big-bang**: React migration per-module, memory system evolution (Topic Memory → MemoryCue → Assertion Extraction), FSRS config gradual refinement
5. **Deep modules**: ChatState encapsulation, FsrsSchedulerConfig merge at boundary, WebSocket protocol as sealed types — narrow interfaces, deep implementations
6. **Evidence-driven**: cross-validate FSRS optimizer against py-fsrs with identical test dataset; Vitest for React components; Playwright+WireMock for E2E
7. **Fail-fast on critical paths**: THROW on assertion pipeline failure; SWALLOW only on non-critical auxiliary features
8. **Data isolation by userId**: per-session queries by UUID; cross-session queries filtered by userId; MemoryCue isolated by userId×AgentMode