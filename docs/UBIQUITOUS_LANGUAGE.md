# Ubiquitous Language

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
| **Scenario** | The social context for role-play (e.g., workplace standup, one-on-one meeting) | Setting, scene, topic |
| **Persona** | The role the AI plays during a Practice session (e.g., team colleague, manager) | Character, role, personality |
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
| **CoachState** | The in-memory langgraph container holding a Practice session messages, corrections, scenario, persona, and userId | Session state, graph state |
| **Active states map** | The ConcurrentHashMap holding all active CoachState instances, keyed by Practice session UUID | activeStates (code name), session store, state cache |
| **Binding map** | The one-to-one ConcurrentHashMap mapping a Practice session UUID to its Active Tab WebSocket ID | sessionToWs (code name), WS binding, connection map |
| **Checkpoint** | A langgraph snapshot of CoachState preserved in MemorySaver, enabling state restoration after a WebSocket disconnect | Snapshot, savepoint |

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

## Flagged Ambiguities

- **Session** was used to mean both the Spring Security HTTP session (Login session) and the English practice conversation (Practice session). These are wholly distinct concepts. A Login session authorizes access; a Practice session contains conversation data. **Resolution:** Always prefix -- "Login session" for auth, "Practice session" for English conversation. Never say "session" alone.

- **State** was used to mean both the CoachState object (a single Practice session data container) and the Active states map (the collection of all active CoachState instances). **Resolution:** Use "CoachState" for the individual container; use "Active states map" for the collection.

- **Token** was used to mean both an LLM usage unit (counted by TokenTracker) and a CSRF token. **Resolution:** Use "LLM token" or "token usage" for the LLM context; use "CSRF token" for security context. Never say "token" alone.

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
> **Dev:** "Clear. One more thing -- does logout clean up the Active states map?"
>
> **Domain expert:** "Yes -- the cleanup handler walks the map and removes every Practice session owned by that Learner. A simple tab close without logout only unbinds the binding -- the Practice session stays alive for resume."
