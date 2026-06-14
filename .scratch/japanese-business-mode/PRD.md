# PRD: Japanese Business Conversation Mode

**Status:** ready-for-agent

## Problem Statement

The Chat Agent currently supports only English conversation modes — Workplace Standup and Daily Talk. Users who want to practice Japanese (specifically business Japanese) have no dedicated mode, no appropriate prompt, no Japanese persona, and no Japanese-language report feedback.

## Solution

Add a `JAPANESE_BUSINESS` (ビジネス日本語) mode. When selected, the AI acts as a Japanese business client (取引先), engaging the user in natural business Japanese conversation. The AI gently corrects keigo and expressions inline through rephrasing — the same pattern as the English modes' conversational correction. A Japanese-language session report provides targeted feedback at session end.

## User Stories

1. As a Japanese learner, I want to select a ビジネス日本語 mode from the mode dropdown, so that I can choose what to practice.
2. As a Japanese learner, I want the AI to role-play as a Japanese business client (取引先), so that I can practice realistic business scenarios like 商談 and 打ち合わせ.
3. As a Japanese learner, I want the AI to naturally rephrase my unnatural Japanese as part of its reply, so that I learn correct expressions without breaking conversation flow.
4. As a Japanese learner, I want the AI to gently point out keigo (敬語) mistakes when they would be inappropriate for the business context, so that I learn business etiquette.
5. As a Japanese learner, I want the AI to match my Japanese level and occasionally use slightly more advanced vocabulary, so that I'm appropriately challenged.
6. As a Japanese learner, I want the AI to offer words when I pause or search for vocabulary, so that I can continue the conversation.
7. As a Japanese learner, I want a Japanese-language session report at the end of each session, so that I can review my progress with culture-appropriate feedback.
8. As a Japanese learner, I want the TTS feature to use a Japanese voice during Japanese mode sessions, so that I hear correct Japanese pronunciation.
9. As a user, I want my Japanese conversation memory to be isolated from my English conversation memory, so that topics and learning profiles don't leak across languages.
10. As a user, I want the mode dropdown to automatically include the new Japanese mode after deployment, so that no configuration is needed.
11. As a user, I want existing English modes (Workplace Standup, Daily Talk) to work exactly as before, so that the Japanese mode addition doesn't regress my existing practice.
12. As a developer, I want the Japanese mode prompt to have its own conversation system skeleton template, so that labels and structure can be language-appropriate.

## Implementation Decisions

### Mode Definition
- **Enum name**: `JAPANESE_BUSINESS`
- **Display name**: `ビジネス日本語`
- **Template path**: `japanese_business` (subdirectory under `prompts/`)

### Prompt Architecture
- **Skeleton template**: Each mode gets its own `conversation-system.txt` from `{templatePath}/`. Japanese mode uses Japanese labels (e.g., `ルール:` instead of `Rules:`). Fallback: if per-mode skeleton is missing, use root `conversation-system.txt`.
- **Mode descriptions/rules**: `{templatePath}/description.txt` and `{templatePath}/rules.txt`, all in Japanese. The persona is a Japanese business client (取引先の田中さん).
- **ConversationAgent**: The single `systemTemplate` field becomes `Map<AgentMode, String> modeTemplates`. Constructor loads per-mode skeleton with fallback logic. `buildSystemContent()` selects template by mode.
- **ReportAgent**: Becomes mode-aware with `Map<AgentMode, String>` report templates. Loads per-mode `report.txt`. Japanese report has the same 4-dimension structure (overall assessment, fluency score, error summary, key takeaway) but in Japanese.

### Correction & Memory — Skipped for Japanese Mode
- **Structured correction (CorrectionAgent)**: Skipped entirely. `TurnProcessor.processTurn()` adds a mode guard: Japanese mode does not call `startCorrection()`. No CorrectionNode invocation, no error records persisted, no correction bubbles sent to frontend.
- **MemoryCue generation**: Skipped. `SessionComplete.complete()` adds a mode guard: Japanese mode does not call `memoryCueService.generateCuesAsync()`.
- **LearningProfile generation**: Skipped. Same guard in `SessionComplete`: Japanese mode does not call `learningProfileService.generateLearningProfileAsync()`.
- **Injection side unaffected**: `resolveMemoryContext()` naturally returns empty `MemoryContent` for modes with no history — no code changes needed. `buildSystemContent()` already handles every null/empty placeholder gracefully.

### Data Isolation
- Mode is stored in `Session.mode` (VARCHAR, H2) — automatically compatible with new enum value.
- MemoryCue and LearningProfile already filter by `userId × AgentMode` — Japanese data is naturally isolated.
- `/api/modes` endpoint iterates `AgentMode.values()` — automatically includes the new mode.

### Frontend
- **ChatState** gains `mode: string` field. `chatReducer` stores it on `SESSION_STARTED` and `SESSION_RESUMED` actions.
- **TTS** (`speakText`) gains `mode?: string` parameter. When `mode === "JAPANESE_BUSINESS"`, sets `utterance.lang = "ja-JP"`.
- **Mode dropdown** (`Footer.tsx`): No code changes needed — auto-populates from `/api/modes` response.
- **Report modal**: No changes needed — same four-field structure renders Japanese text naturally.

### Files Created
- `prompts/japanese_business/description.txt` — persona + scenario (取引先, 商談練習)
- `prompts/japanese_business/rules.txt` — behavior rules (敬語指導、自然な言い換え)
- `prompts/japanese_business/conversation-system.txt` — Japanese-label skeleton
- `prompts/japanese_business/report.txt` — Japanese report template

### Files Modified
- `model/AgentMode.java` — add `JAPANESE_BUSINESS` enum value
- `agent/ConversationAgent.java` — `systemTemplate` → `Map<AgentMode, String>` with per-mode loading + fallback
- `agent/ReportAgent.java` — mode-aware template loading
- `service/TurnProcessor.java` — mode guard to skip correction
- `service/SessionComplete.java` — mode guard to skip MemoryCue + LearningProfile
- `frontend/state/chatState.ts` — add `mode` field + `initialState` default
- `frontend/state/chatReducer.ts` — store `mode` on SESSION_STARTED / SESSION_RESUMED
- `frontend/shared/tts.ts` — add `mode` parameter, route `ja-JP` based on mode

### Files Unchanged
- `CorrectionAgent.java`, `CorrectionNode.java`, `ErrorType.java` — no changes; just skipped at call site
- `ModeController.java` — auto-enumerates
- `Session.java`, DB schema — `mode` VARCHAR auto-compatible
- `ChatWebSocketHandler.java`, `ChatMessageHandler.java` — protocol layer mode-agnostic
- `MemoryCueService.java`, `LearningProfileService.java` — skipped at call site, no code change
- `ReportModal.tsx`, `Footer.tsx` — auto-render new content
- All E2E tests — continue to target WORKPLACE_STANDUP / DAILY_TALK, guards only affect JAPANESE_BUSINESS

## Testing Decisions

### What Makes a Good Test
- Test external behavior, not implementation plumbing. Assert system prompt content (description/rules present), not internal Map structure.
- For mode guards, verify that services are **not called** (`verify(..., never())`) rather than inspecting internal state.
- Frontend tests assert reducer output shape and TTS lang routing, not component internals.

### Backend Unit Tests Affected
- **ConversationAgentTest**: 3 new tests — Japanese skeleton loads Japanese labels, Japanese description/rules injected into system content, empty mode still falls back to root skeleton. Existing 16 tests unaffected (use WORKPLACE_STANDUP / DAILY_TALK).
- **ReportAgentTest**: 1 new test — Japanese report template loaded and used for Japanese mode. Existing 8 tests unaffected.
- **TurnProcessorTest**: 1 new test — Japanese mode does not invoke correction. Existing tests unaffected (mock WORKPLACE_STANDUP).
- **SessionCompleteTest**: 2 new tests — Japanese mode skips `generateLearningProfileAsync()` and `generateCuesAsync()`. Existing 5 tests unaffected.
- **ChatMessageHandlerTest**: No changes. Error message assertion uses `contains()`, not exact enum count.

### Frontend Unit Tests Affected
- **chatReducer.test.ts**: Update `SESSION_STARTED` expected state to include `mode` field. Update `initialState` to include `mode: ""`.
- **tts.test.ts**: Add `mode` parameter to existing call. 1 new test — JAPANESE_BUSINESS mode sets `lang = "ja-JP"`.

### E2E Tests
- All 5 existing E2E tests unaffected (hardcoded to WORKPLACE_STANDUP / DAILY_TALK, guards only skip for JAPANESE_BUSINESS).
- New `JapaneseBusinessIT.java` deferred to follow-up iteration.

### Prompt File Dependency
- Both `ConversationAgentTest` and `ReportAgentTest` constructors iterate `AgentMode.values()` and load per-mode prompt files from classpath. The prompt files under `src/main/resources/prompts/japanese_business/` **MUST exist before tests run** — otherwise `setUp()` throws `RuntimeException`.

## Out of Scope

- **Structured Japanese correction (CorrectionAgent)**: Japanese mode has no per-message CorrectionAgent analysis, no error type enumeration, no correction sidebar items. Conversational correction happens entirely through prompt rules (inline rephrasing).
- **MemoryCue generation for Japanese mode**: No RAG memory cues generated at session end. No cross-session memory retrieval for Japanese topics in MVP.
- **LearningProfile generation for Japanese mode**: No cross-session learning profile accumulation.
- **Japanese E2E test (`JapaneseBusinessIT.java`)**: Deferred to follow-up iteration.
- **Multi-language CorrectionAgent architecture**: ErrorType enum remains English-only for now.

## Further Notes

- DeepSeek model (`deepseek-v4-flash`) supports Japanese natively — no model change required.
- Browser SpeechSynthesis requires explicit `lang` attribute — Chrome/Edge include Japanese voices by default on Windows.
- The `conversation-system-japanese.txt` skeleton should use Japanese section labels rather than English ones. The fallback strategy ensures the root English skeleton is used if a per-mode file is missing — safe for future modes.
- All mode guards use `if (mode != JAPANESE_BUSINESS)` pattern — additive, zero impact on existing modes. Future Japanese sub-modes can be added to the guard condition.
