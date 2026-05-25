# CONTEXT — English Coach

English Coach 是一个基于 AI 的英语口语练习 Web 应用。使用者（Learner）选择一个对话模式（AgentMode），通过 WebSocket 与 AI Agent 进行实时对话练习。系统提供流式回复、语法/措辞纠正（Correction）、会话报告（Report）以及跨会话的持久化记忆（User Memory）。

## People and Identity

| Term | Definition | Aliases to avoid |
|------|------------|-----------------|
| **Learner** | A person who uses the English Coach to practice spoken English | User, student, customer |
| **Principal** | Spring Security representation of the authenticated Learner within a request context | Current user, auth context |
| **Login session** | The Spring Security HTTP session (JSESSIONID cookie) that proves the Learner is authenticated | Auth session, HTTP session |
| **Remember-Me** | A persistent cookie that survives browser restart and automatically re-establishes a Login session within 14 days | Auto-login, persistent login |

## Conversation Lifecycle

| Term | Definition | Aliases to avoid |
|------|------------|-----------------|
| **Practice session** | A single English practice conversation from START to END, identified by a UUID | Session, conversation, chat session |
| **Turn** | One round-trip: a Learner message followed by the Agent reply and any corrections | Round, exchange, message-pair |
| **AgentMode** | A pre-defined conversation mode that bundles both the social context (what Scenario used to be) and the AI role (what Persona used to be) into a single selection. The Learner picks one from a dropdown to start a Practice session. Examples: `WORKPLACE_STANDUP` (a daily standup with a friendly teammate), `DAILY_TALK` (casual chat with Chris, a friend / English tutor hybrid). Each AgentMode carries a `templatePath` pointing to per-Mode prompt files. | Scenario, scene, Persona, role, character, conversation type |
| **Mode template** | Per-AgentMode prompt files under `prompts/{templatePath}/` — a `description.txt` (identity + scenario) and a `rules.txt` (behavioral constraints). These are pre-loaded by ConversationAgent at startup into an EnumMap for zero-I/O prompt assembly at request time. | Mode prompt, per-mode template |
| **System Prompt skeleton** | The `conversation-system.txt` file that serves as a structural wrapper, containing only `{Description}` and `{Rules}` placeholders (plus dynamic sections like `{topicSummary}`). Its sole purpose is to let placeholder ordering control LLM attention without modifying per-Mode files. | Skeleton template, prompt wrapper |
| **Streaming** | Agent reply delivered token-by-token to the frontend in real-time, before the full response is complete | Token streaming, incremental output |
| **Stale delta** | A streaming token that arrives at a browser tab that no longer owns the Practice session due to multi-tab competition | Belated token, orphaned delta |

## Correction and Assessment

| Term | Definition | Aliases to avoid |
|------|------------|-----------------|
| **Correction** | A linguistic error found by the CorrectionAgent, with original text, corrected text, type, and explanation | Fix, error, correction item |
| **Correction type** | One of five categories: GRAMMAR, WORD_CHOICE, CHINGLISH, PRONUNCIATION, FLUENCY | Error category, error kind |
| **Correction sidebar** | The slide-out panel displaying detailed Correction items with type, original->corrected, and explanation | Sidebar, correction panel |
| **Correction bubble** | A numbered summary inserted after the Learner message in the chat flow | Correction summary, inline correction |
| **Report** | Post-session analysis: summary, fluency score, error summary, vocabulary suggestions, key takeaway | Session report, assessment, feedback |
| **Progress** | Per-Learner aggregated statistics: total sessions completed and total minutes practiced | Statistics, learning progress, track record |

## Multi-Tab Coordination

| Term | Definition | Aliases to avoid |
|------|------------|-----------------|
| **Active Tab** | The browser tab whose WebSocket is currently bound as the owner of a Practice session | Current tab, bound tab, owner tab |
| **Stale Tab** | A browser tab that previously owned a Practice session but has been displaced by a newer Active Tab | Old tab, displaced tab, orphaned tab |
| **Tab takeover** | When resuming a Practice session from Tab B displaces Tab A ownership binding -- intended, not an error | Session steal (avoid), ping-pong, binding switch |
| **Visibility resume** | The pattern where a Stale Tab auto-resumes its Practice session via the Page Visibility API when the Learner switches back to it | Auto-resume, reactivation refresh |
| **State rebuild** | The frontend act of clearing all DOM and reconstructing chat messages, correction bubbles, and sidebar items from a fresh SESSION_RESUMED payload | Full render, DOM reset, re-render |

## Runtime State

| Term | Definition | Aliases to avoid |
|------|------------|-----------------|
| **CoachState** | The in-memory langgraph container holding a Practice session messages, corrections, mode, and userId | Session state, graph state |
| **Active states map** | The ConcurrentHashMap holding all active CoachState instances, keyed by Practice session UUID | activeStates (code name), session store, state cache |
| **Binding map** | The one-to-one ConcurrentHashMap mapping a Practice session UUID to its Active Tab WebSocket ID | sessionToWs (code name), WS binding, connection map |
| **Checkpoint** | A langgraph snapshot of CoachState preserved in MemorySaver, enabling state restoration after a WebSocket disconnect | Snapshot, savepoint |

## Memory and Long-Term Context

| Term | Definition | Aliases to avoid |
|------|------------|-----------------|
| **User Memory** | A persistent summary record in H2 that captures a Learner's conversation topics or learning progress, surviving across Practice sessions | Memory, memory record, profile |
| **Topic Memory** | A User Memory of type TOPIC_SUMMARY — a 500-character summary of conversation topics discussed across past Practice sessions of the same AgentMode (mode-scoped isolation). | Topic summary, conversation memory |
| **Learning Profile** | A User Memory of type LEARNING_PROFILE — a 400-character summary of the Learner's English strengths, weaknesses, and improvement trends | Learning summary, skill profile |
| **Memory Merge** | An LLM-driven process that combines an old User Memory (version N) with a newly completed session's Report to produce an updated version (N+1) | Incremental update, memory consolidation |
| **Memory Injection** | The act of inserting Topic Memory and Learning Profile into the first three Turns' System Prompt (messageId ≤ 3) so the Agent has multiple chances to naturally bring up past context | Context injection, memory recall |
| **Active Engagement** | A System Prompt instruction directing the Agent to proactively bring up unfinished topics and ask questions based on the injected memory | Initiative prompt, engagement instruction |
| **Memory Version** | A sequential integer on each User Memory record, starting at 1 and incremented by each Memory Merge operation | Version number, revision counter |
| **MemoryCue** | A structured memory record in the `memory_cues` table — one row per conversation topic segment, containing topic, summary, and tags. Generated post-session by MemoryCueAgent via two-step LLM analysis (topic switch detection + per-segment summarization). Coexists alongside legacy User Memory; retrieval is planned for v2. | Structured memory, topic cue, memory tag |
| **Memory Cue Split** | The first LLM step in MemoryCue generation: analyzes the full conversation transcript and detects topic switch points, returning a list of message index boundaries | Topic switch detection, split detection |
| **Memory Cue Entry** | The second LLM step in MemoryCue generation: for each identified segment, produces a `(topic, summary, tags)` triple in structured JSON | Segment summarization, cue generation |
| **Tag Consolidation** | The post-session LLM-driven process that merges semantically equivalent tags across all MemoryCue records for a given Learner+AgentMode into canonical forms, updating rows in-place. Runs after all segments complete, protected by a static lock, idempotent. Entry prompt enforces ≤5 tags. | Canonicalization, tag merge |

## Observability

| Term | Definition | Aliases to avoid |
|------|------------|----------------|
| **LLM Call Log** | Every LLM API call is persisted as a row in the `llm_call_logs` table: prompt, response, token usage (input/output), duration (ms), and status (SUCCESS/ERROR). Sync agents (Correction, Report, Memory, MemoryCue) are intercepted transparently via a `LoggableChatModel` wrapper; the ConversationAgent (streaming) is logged manually via `TurnProcessor`. Writes are async (non-blocking) and records older than 3 days are cleaned up on startup. Used for debugging and cost tracking. | LLM log, call log, API log |

## Data Isolation

| Term | Definition | Aliases to avoid |
|------|------------|-----------------|
| **Ownership** | A data row belongs to exactly one Learner, enforced by the userId column on the Practice session row | Data isolation, tenant ownership |
| **Cross-session query** | A query that spans multiple Practice sessions (e.g., history listing, progress aggregation) -- must filter by Learner | Multi-session query, user-scoped query |
| **Per-session query** | A query scoped to a single Practice session UUID -- naturally isolated without a Learner filter | Single-session query, session-scoped query |

## Relationships

- A **Learner** has exactly one **Login session** at a time (per browser).
- A **Login session** may include a **Remember-Me** cookie for persistence across browser restarts.
- A **Learner** can have zero or more **Practice sessions**, each with a unique UUID.
- A **Learner** has exactly one **Progress** record.
- A **Practice session** is owned by exactly one **Learner** (via userId).
- A **Practice session** contains zero or more **Turns**.
- A **Turn** consists of one Learner message, one Agent reply, and zero or more **Corrections**.
- A **Practice session** at its end produces exactly one **Report**.
- A **Practice session** has at most one **Active Tab** at any moment (via the Binding map).
- A **Tab takeover** occurs when a Learner resumes their own Practice session from a different tab, displacing the previous Active Tab.
- A **Stale Tab** self-heals via a **Visibility resume** that triggers a **State rebuild**.
- A **Learner** has zero or more **User Memory** records, each identified by type.
- A **Practice session** end triggers one **Memory Merge** per **User Memory** type (Topic Memory and Learning Profile), executed asynchronously alongside **MemoryCue** generation.
- The first three **Turns** of a new Practice session include a **Memory Injection** of the latest Topic Memory and Learning Profile into the Agent's System Prompt (messageId ≤ 3).
- A **Memory Merge** reads the previous **Memory Version**, generates a new one, and inserts it — old versions remain as immutable history.
- Each **User Memory** record now stores the `session_id` of the **Practice session** that triggered its generation, enabling traceability.
- A **Practice session** end triggers **Memory Cue Split** to detect topic switch points, then fires one **Memory Cue Entry** per segment in parallel — each producing a **MemoryCue** row with COMPLETED or SEGMENT_FAILED status. After all segments complete, **Tag Consolidation** merges equivalent tags across all historical MemoryCue rows for the same Learner+Mode into canonical forms in-place.

## Flagged Ambiguities

- **Session** was used to mean both the Spring Security HTTP session (Login session) and the English practice conversation (Practice session). These are wholly distinct concepts. A Login session authorizes access; a Practice session contains conversation data. **Resolution:** Always prefix -- "Login session" for auth, "Practice session" for English conversation. Never say "session" alone.

- **State** was used to mean both the CoachState object (a single Practice session data container) and the Active states map (the collection of all active CoachState instances). **Resolution:** Use "CoachState" for the individual container; use "Active states map" for the collection.

- **Token** was used to mean both an LLM usage unit (counted by TokenTracker) and a CSRF token. **Resolution:** Use "LLM token" or "token usage" for the LLM context; use "CSRF token" for security context. Never say "token" alone.

- **Memory** was used to mean both the in-memory runtime state (CoachState, Active states map) and the persistent cross-session User Memory entity. **Resolution:** Use "In-memory state" or "Active states map" for runtime; use "User Memory" for the persisted H2 record. Never say "memory" alone.

- **Resume** was used for both the WebSocket protocol message (RESUME_SESSION) and the Page Visibility API pattern (Visibility resume). **Resolution:** "Protocol resume" for the WebSocket message; "Visibility resume" for the tab-activation auto-refresh. Both restore UI state but through different triggers.

## Example Dialogue

> **Dev:** "When a Learner clicks Start, we create a Practice session. But what if they already have an active one from another tab?"
>
> **Domain expert:** "The Binding map is one-to-one -- the new Tab takeover overwrites the old binding. The Stale Tab will not know it is displaced until its next interaction."
>
> **Dev:** "So the old tab shows stale conversation data until the Learner types?"
>
> **Domain expert:** "No -- we added Visibility resume. When the Learner switches back, the tab detects it is visible again and does a protocol resume, which triggers a full State rebuild from the latest CoachState."
>
> **Dev:** "What about in-flight streaming? If a Turn was mid-reply when the takeover happened, the Stale delta goes to the old tab?"
>
> **Domain expert:** "Fixed -- the Turn processor now resolves the Active Tab at send time. The Agent reply always lands on whichever tab owns the Binding at that moment."
>
> **Dev:** "And the Stale Tab Visibility resume picks up the completed reply?"
>
> **Domain expert:** "Exactly. And the frontend delta handler skips any msgId that already has a complete Agent message from the State rebuild. No duplicate bubbles."
>
> **Dev:** "Does the Agent remember what we talked about last week?"
>
> **Domain expert:** "Yes — at session end, a Memory Merge runs asynchronously. It combines the old Topic Memory with the new Report to produce an updated version. On the next Practice session's first Turn, a Memory Injection puts the Topic Memory and Learning Profile into the System Prompt."
>
> **Dev:** "So the Agent just knows 'Hugo went to Japan' without me re-telling it?"
>
> **Domain expert:** "Exactly. And the Active Engagement instruction tells the Agent to naturally bring it up: 'So how was that trip to Japan you mentioned last time?'"
>
> **Dev:** "What happens to old Memory Versions?"
>
> **Domain expert:** "They're kept. Each Memory Merge inserts a new row with version N+1. Old versions are immutable — we can trace how the Learner's profile evolved."
>
> **Dev:** "And does logout clean up the Active states map?"
>
> **Domain expert:** "Yes — the cleanup handler walks the map and removes every Practice session owned by that Learner. User Memory persists in H2 regardless."
