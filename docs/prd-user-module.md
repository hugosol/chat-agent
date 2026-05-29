# PRD: User Module — Authentication & Multi-Tenant Data Isolation

## Problem Statement

The English Coach currently is a single-user desktop application with zero authentication, zero user identity, and zero data isolation. All data (Session, Message, ErrorRecord, SessionReport, UserProgress) is global and shared. The H2 database console is publicly accessible. There is no concept of "who" is using the tool, and no way to distinguish one learner's progress from another's. To support multiple learners on a shared instance and to protect the tool from unauthorized access, the application needs a user identity layer and per-user data isolation.

## Solution

Add a minimal user module consisting of:

1. **Spring Security** for authentication (form login, session cookie, logout).
2. A **User entity** with BCrypt-hashed passwords, seeded via a `CommandLineRunner` from `application.yml` configuration.
3. **Per-user data isolation** by adding `userId` to the `Session` and `UserProgress` entities, propagating user context through the runtime state (`CoachState`) and service layer. Repository queries are filtered by user only where necessary (cross-session queries). Per-session queries remain unchanged, relying on UUID uniqueness.
4. A **multi-page frontend** with a standalone login page (`login/main.html`) and the existing chat page (`index.html`) protected behind authentication.
5. **Session cookie + Remember-Me** for login persistence across browser restarts.
6. **Multi-tab support** via WebSocket session competition, resolved by a Page Visibility API auto-resume pattern (no new protocol message types).

## User Stories

### Authentication

1. As a learner, I want to see a login page when I first access the application, so that I can authenticate before using the English Coach.
2. As a learner, I want to log in with my username and password, so that the system knows who I am.
3. As a learner, I want to see an error message on the login page when my credentials are wrong, so that I can retry with correct credentials.
4. As a learner, I want the system to remember me for 14 days after I check "Remember Me", so that I don't need to log in every time I close and reopen my browser.
5. As a learner, I want to see a logout button in the chat page, so that I can explicitly end my authenticated session.
6. As a learner, I want to be redirected back to the login page after logging out, so that I can switch users.
7. As an unauthenticated user, I want to be redirected to the login page when I try to access the chat page directly, so that the application is properly protected.
8. As a learner with multiple browser tabs, I want my login session to work across all tabs, so that I don't need to log in separately for each tab.

### Data Isolation

9. As a learner, I want my conversation history only visible to me, so that my learning progress is private.
10. As a learner, I want my error corrections and session reports only visible to me, so that my weaknesses are not exposed to others.
11. As a learner, I want my learning progress statistics (total sessions, total minutes) tracked per-user, so that my progress reflects only my own sessions.
12. As a learner, I want my past session history list to show only my own sessions, so that I'm not distracted by other learners' data.

### Multi-Tab Session Handling

13. As a learner, I want to be able to open my current session in a new browser tab and continue where I left off, so that I can switch devices or browser windows seamlessly.
14. As a learner, when I switch back to a previously open tab with an active session, I want the chat UI to automatically refresh to show the latest state, so that I always see the correct conversation.
15. As an initial user, I want the system to create my account automatically on first startup with credentials from a configuration file, so that I can start using the tool immediately without manual database setup.

## Implementation Decisions

### 1. Authentication Mechanism

**Decision**: Spring Security form login with HTTP Session cookie + Remember-Me.

**Rationale**: WebSocket handshake is an HTTP upgrade request that automatically carries the `JSESSIONID` cookie. Spring Security authenticates at the handshake level — no custom token passing, no query parameters, no `AUTH` protocol message needed. The `Principal` is available in every `WebSocketSession` via `getPrincipal()`.

### 2. Multi-Page Architecture

**Decision**: Separate `/login/main.html` (public) and `/index.html` (authenticated). Login form uses traditional HTML `<form action="/login" method="post">` — Spring Security handles the 302 redirect chain. Login page has its own independent `main.js` and `main.css` files in the `/login/` directory.

**Rationale**: The application will grow to include more pages (dashboard, history). A multi-page architecture with standard Spring Security redirect is more extensible than an SPA state toggle.

### 3. User Entity Design

**Decision**: Minimal `User` entity with fields: `id` (UUID), `username`, `password` (BCrypt hash), plus inherited `createTime`/`updateTime` from `BaseEntity`. No roles, no `enabled` flag, no `displayName`. Username is unique.

**Rationale**: This is a learning tool, not a multi-role management system. Unnecessary fields can be added later if needed.

### 4. Password Encryption

**Decision**: BCrypt via Spring Security's `BCryptPasswordEncoder`. Password complexity and BCrypt strength at the default (`$2a$10$`).

### 5. Initial User Seeding

**Decision**: `CommandLineRunner` in a `DataInitializer` class reads `app.initial-users` from `application.yml` (a list of `{username, password}` records). On startup, if a user with that username does not exist, the `CommandLineRunner` BCrypt-hashes the plaintext password and inserts the user via `UserRepository`.

The initial password appears in plaintext in `application.yml`. This is acceptable for a private local deployment. For production, the password would be set via an environment variable.

### 6. Data Isolation Strategy

**Decision**: Add `userId` (String, `@Column(nullable = false)`) to the `Session` entity. All child entities (`Message`, `ErrorRecord`, `SessionReport`) remain unchanged — they are already isolated by UUID `sessionId`. The `UserProgress` entity becomes per-user by adding `userId` with a `unique = true` constraint.

**Rationale**: All data access paths are session-scoped (find by `sessionId`). UUID collision is practically impossible. Per-session queries need no `userId` filter. Only cross-session queries (`getHistory`, user progress) filter by `userId`. This minimizes repository changes and avoids unnecessary JOINs.

### 7. Runtime User Context — CoachState

**Decision**: Add `userId` as a channel in `CoachState` (following the existing `sessionId`/`scenario`/`persona` pattern). `CoachState.initialState()` now accepts `userId` as a parameter. The `SessionService.init()` method passes the `userId` from the authenticated `Principal` into the state at session creation.

**Rationale**: `CoachState` is already the session-scoped data container. Putting `userId` there makes it accessible to all runtime components (`TurnProcessor`, `SessionService`) without parameter threading or `SecurityContextHolder` (which is ThreadLocal and fails in `CompletableFuture` async threads).

### 8. Service Layer User Context

**Decision**: Get `userId` from `CoachState` via `sessionService.getUserId(sessionId)` in runtime paths. At session creation (`START_SESSION`), `userId` comes from the `Principal` and is explicitly passed to `SessionService.init()`. At session resume (`RESUME_SESSION`), `userId` is validated against the persisted `Session` entity.

**Rationale**: Avoids relying on `SecurityContextHolder` (unavailable in async threads used by `TurnProcessor`). The `CoachState` is thread-safe (`ConcurrentHashMap`). Provides a single source of truth for the current user within a session.

### 9. Repository Query Isolation

**Decision**: Only cross-session queries get `userId` filtering:

| Repository | Query | Change |
|---|---|---|
| `SessionRepository` | `findAllByOrderByStartTimeDesc()` | → `findByUserIdOrderByStartTimeDesc(userId)` |
| `UserProgressRepository` | `findAll()` (first element) | → `findByUserId(userId)` |
| `MessageRepository` | `findBySessionId()` | Unchanged |
| `ErrorRecordRepository` | `findBySessionId()` | Unchanged |
| `SessionReportRepository` | `findBySessionId()` | Unchanged |

### 10. WebSocket Handshake Authentication

**Decision**: Remove `setAllowedOrigins("*")` — rely on default same-origin policy. Spring Security's filter chain intercepts the `/ws/coach` HTTP upgrade request. Unauthenticated users receive a 401/403 and the WebSocket upgrade is refused. Authenticated users have their `JSESSIONID` cookie validated and the `Principal` is available in the handler.

### 11. CSRF Protection

**Decision**: Spring Security default CSRF enabled. Three paths are exempted: `/ws/coach/**` (WebSocket cannot be cross-site submitted), `/h2-console/**` (admin tool), and `/logout` (no data modification — only session destruction).

### 12. Multi-Tab Session Handling

**Decision**: Flip the session-to-WebSocket mapping from `wsToSession` (wsId → sessionId) to `sessionToWs` (sessionId → wsId). This is a one-to-one mapping — `put()` naturally overwrites the previous binding. Combined with Page Visibility API auto-resume on the frontend, this creates a self-correcting system:

- When Tab B resumes a session already owned by Tab A, `sessionToWs.put()` overwrites the mapping. Tab A's WebSocket stays open but loses its binding.
- When Tab A becomes visible again (`visibilitychange`), its frontend auto-sends `RESUME_SESSION`, which puts its wsId back, retrieves the full state via `SESSION_RESUMED`, and rebuilds the UI from scratch.
- `handleSessionResumed()` on the frontend already performs a full UI rebuild (clears `innerHTML`, iterates `messages` and `corrections` arrays).

**No new protocol message types are added.** The existing `ERROR "No active session"` handles cases where a stale tab tries to send a message before the visibility change triggers a resume.

### 13. Stale Streaming Delta Protection

**Decision**: `TurnProcessor` callbacks no longer send to the captured old `WebSocketSession`. Instead, they resolve the current binding at send time via `sessionService.getWsForSession(sessionId)`. This ensures that if a conversation turn completes while a different tab owns the session, the `AGENT_STREAM_END` goes to the correct tab.

On the frontend, `handleStreamDelta` checks whether the `msgId` already has a complete agent message in the DOM (from a prior resume). If so, the stale delta is skipped.

### 14. Session Cleanup on Logout

**Decision**: Explicit logout triggers cleanup of the user's runtime state. A `SessionCleanupLogoutHandler` (Spring Security `LogoutHandler`) removes all `activeStates` entries for the logging-out user. WebSocket disconnection without logout (closing a tab) only calls `unbind()` — state is preserved for resume.

| Trigger | Runtime State | Can Resume? |
|---------|:---:|:---:|
| WebSocket disconnect (close tab) | Preserved | Yes |
| Explicit logout | Cleared | No |
| Server restart | Lost (in-memory) | No (re-login required) |

### 15. Spring Security URL Protection Matrix

| Path Pattern | Access |
|---|---|
| `/login/**` (`/login/main.html`, `main.js`, `main.css`) | Unauthenticated |
| `/h2-console/**` | Authenticated only |
| `/logout` | Authenticated only + CSRF disabled |
| `/` (root) | Redirects to `/login/main.html` |
| `/ws/coach` | Authenticated only |
| `/index.html`, `/app.js`, `/style.css` | Authenticated only |
| All other paths | Authenticated |

### 16. Login Failure Experience

**Decision**: Failed login returns a 302 redirect to `/login/main.html?error`. `main.js` checks `window.location.search.includes('error')` and displays a red error message "Invalid username or password" above the form.

There is no "forgot password" or "register" flow in this version. Users are created via the initial seeding configuration.

### 17. Remember-Me Key

**Decision**: Remember-Me HMAC key is generated via `UUID.randomUUID().toString()` on each server startup. This means server restart invalidates all Remember-Me cookies and users must re-login. Acceptable because: (a) server restart already requires re-login (active states are in-memory and lost), (b) no need for persistent key storage.

### 18. Data Migration

**Decision**: Delete the `data/` directory (H2 file database). Tables will be recreated with the new `userId` columns via `ddl-auto: update`. No migration script is written — historical data from the single-user era is not preserved.

### 19. E2E Test Strategy

**Decision**: Test profile (`application-test.yml`) disables Spring Security via `spring.autoconfigure.exclude`. E2E tests (`EnglishCoachSessionIT`, `EnglishCoachResumeIT`) continue to test business logic without authentication overhead. Security behavior is tested independently via `@WebMvcTest` unit tests with `@WithMockUser`.

### 20. CSRF for Pure HTML Forms

**Decision**: The login form in `main.html` is a plain `<form action="/login" method="post">`. Spring Security's `CsrfFilter` auto-injects the CSRF token as a request attribute. Since the login page is served by Spring Boot's static resource handler (not Thymeleaf), the CSRF token is read from a `<meta name="_csrf">` tag rendered by the server's `CsrfTokenRequestAttributeHandler`, and injected into forms by `main.js` at page load. The logout button in `index.html` uses the same mechanism, but its endpoint is also CSRF-exempted as a belt-and-suspenders measure.

## Testing Decisions

### What Makes a Good Test

- Tests should verify external behavior, not implementation details.
- Use `@WebMvcTest` + `MockMvc` for Security tests — test redirects, status codes, and session state.
- Use existing `E2ETestBase` pattern for integration tests — Playwright + WireMock, DOM-based waits.
- Repositories are tested via `@DataJpaTest` to verify query filters work correctly.

### Modules to Test

| Module | Test Type | What It Verifies |
|--------|-----------|-----------------|
| SecurityConfig | `@WebMvcTest` + `@WithMockUser` | Login 302, unauthenticated redirect, logout redirect, `/login/**` permit, Remember-Me |
| SessionService | Unit test (Mockito) | `sessionToWs` flip correctness, `getUserId`, `removeAllForUser` |
| SessionDbStore | Unit test (Mockito) | `createSession` with userId, `getHistory` filtered by userId, `updateUserProgress` per-user |
| UserRepository | `@DataJpaTest` | `findByUsername` |
| SessionRepository | `@DataJpaTest` | `findByUserIdOrderByStartTimeDesc` filter |
| DataInitializer | Unit test | Creates user only when not exists, BCrypt hash verification |
| E2E (existing) | Playwright IT | Full session flow unchanged (Security disabled in test profile) |

### Prior Art

- Existing unit tests: `SessionDbStoreTest`, `SessionServiceTest`, `CoachMessageHandlerTest` — use Mockito mocks, verify method calls and state transitions.
- Existing E2E tests: `EnglishCoachSessionIT`, `EnglishCoachResumeIT` — use `E2ETestBase`, Playwright, WireMock on port 19090.
- Security tests: New file `SecurityConfigTest` following Spring Security's `@WebMvcTest` pattern with `SecurityMockMvcRequestPostProcessors`.

## Out of Scope

- **User registration flow** — no sign-up page. Users are created via configuration.
- **Password change / forgot password** — no self-service password management.
- **Role-based access control (RBAC)** — the User entity has no roles field. All authenticated users have equal access.
- **Admin dashboard** — no user management interface.
- **Persistent Remember-Me key** — the key is regenerated on each server restart.
- **REST API endpoints** — no new HTTP API surface is added beyond Spring Security's built-in `/login` and `/logout`.
- **Email verification or phone confirmation** — no external verification of user identity.
- **Audit logging of login events** — no login success/failure logging beyond default Spring Security events.
- **Rate limiting on login attempts** — no brute-force protection.
- **Migration of existing H2 data** — the `data/` directory is deleted; historical data is not migrated.
- **New ServerMessage protocol types** — no new WebSocket message types are added. All multi-tab coordination uses existing messages + Page Visibility API.
- **Session TTL / background cleanup** — runtime state (`activeStates`) has no expiration timer. It is only cleaned on explicit logout or server restart.

## Further Notes

- The `password` field in `application.yml` under `app.initial-users` is stored as plaintext in the config file. This is acceptable for local development. For production deployment, use environment variable reference or an external secrets manager.
- The `sessionToWs` flip changes the `SessionService` API surface: callers that previously used `getSessionId(wsId)` now perform an O(n) scan of values. The frequency of these calls is low: once per `USER_INPUT`, once per `END_SESSION`, once per `afterConnectionClosed`. A future optimization can add a reverse index (`BiMap` via two `ConcurrentHashMap`s) if profiling shows it necessary.
- All existing entity relationships remain as plain String foreign keys — no JPA `@ManyToOne`/`@OneToMany` annotations are introduced. This preserves the codebase's flat, ORM-light style.
- The frontend remains vanilla HTML/JS/CSS with no build tools, no npm, no framework.
- This PRD implements decisions that evolved through 29 grilling questions covering authentication, multi-tenancy, WebSocket auth, multi-tab handling, test strategy, and frontend architecture. See the conversation context for the full decision tree.

