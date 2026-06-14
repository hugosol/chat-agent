# 01: Japanese mode foundation — enum + prompt files + backend agents

**Status:** `ready-for-agent`

## Parent

PRD: `.scratch/japanese-business-mode/PRD.md`

## What to build

Add `JAPANESE_BUSINESS` enum value to `AgentMode`, create 4 Japanese-language prompt files, and make `ConversationAgent` + `ReportAgent` mode-aware so the Japanese mode works end-to-end for conversation and session reports.

### AgentMode enum

Add `JAPANESE_BUSINESS("ビジネス日本語", "japanese_business")` to `AgentMode.java`. The `displayName` uses the Japanese name; the `templatePath` is `japanese_business`.

### Prompt files (4 new files under `prompts/japanese_business/`)

**`description.txt`** — Persona + scenario. The AI role-plays as 田中さん, a Japanese business client (取引先). Scenario is practicing business conversation (商談練習、打ち合わせ). Written entirely in Japanese.

**`rules.txt`** — Behavioral rules in Japanese. Key rules:
- Stay in character as a Japanese business client (取引先)
- Natural Japanese conversation matching the user's level, occasionally using slightly more advanced vocabulary
- When the user's keigo (敬語) is incorrect for the business context, gently rephrase as part of the reply — no numbered lists, no explanations
- When the user pauses or searches for a word, offer the word naturally
- Keep replies 2-4 sentences, match real conversation rhythm
- No markdown or special formatting

**`conversation-system.txt`** — Japanese-label skeleton template. Same placeholder structure as the root `conversation-system.txt` but with Japanese section labels (e.g. `ルール:` instead of `Rules:`). Contains: `{Description}`, `{Rules}`, `{lastConversation}`, `{memoryCues}`, `{learningProfile}`, `{activeEngagement}`.

**`report.txt`** — Japanese report template. Same 4-dimension structure (overall assessment, fluency score, error summary, key takeaway) but the prompt instructs the LLM to output in Japanese. The `overallAssessment` and `keyTakeaway` fields are Japanese text; `fluencyScore` remains integer (1-10).

### ConversationAgent — per-mode skeleton template

Currently `ConversationAgent` has a single `systemTemplate` field loaded from root `conversation-system.txt`. Change to `Map<AgentMode, String> modeTemplates`:

- Constructor: for each `AgentMode`, try loading `{templatePath}/conversation-system.txt`. If the per-mode file is missing, fall back to the root `conversation-system.txt`.
- `buildSystemContent()`: select the template by `mode` from the map instead of using the single field.

The per-mode `description.txt` and `rules.txt` already load correctly in the existing `EnumMap` loop — no change needed there.

### ReportAgent — mode-aware report template

Currently `ReportAgent` loads a single `report.txt` from root. Change to `Map<AgentMode, String>` report templates with the same fallback logic: try `{templatePath}/report.txt`, fall back to root `report.txt`. Use the mode-appropriate template when registering the TaskDefinition (or pass mode through to select at request time).

Since the `generate()` method already receives `TaskContext` containing `mode`, the mode is available at call time to select the right template. The simplest approach: pre-register one TaskDefinition per mode, or load templates into a map and select at request time.

### Files to create

- `src/main/resources/prompts/japanese_business/description.txt`
- `src/main/resources/prompts/japanese_business/rules.txt`
- `src/main/resources/prompts/japanese_business/conversation-system.txt`
- `src/main/resources/prompts/japanese_business/report.txt`

### Files to modify

- `src/main/java/com/hugosol/chatagent/model/AgentMode.java`
- `src/main/java/com/hugosol/chatagent/agent/ConversationAgent.java`
- `src/main/java/com/hugosol/chatagent/agent/ReportAgent.java`

### Files unchanged

- `ModeController.java` — `/api/modes` iterates `AgentMode.values()` automatically
- `Session.java` / DB schema — `mode` VARCHAR auto-compatible
- `ChatWebSocketHandler.java`, `ChatMessageHandler.java` — protocol layer mode-agnostic
- All E2E tests — target WORKPLACE_STANDUP / DAILY_TALK only

## Acceptance criteria

- [ ] `AgentMode` enum contains `JAPANESE_BUSINESS("ビジネス日本語", "japanese_business")` — `mvn compile` passes
- [ ] All 4 Japanese prompt files exist under `prompts/japanese_business/` with Japanese content
- [ ] `conversation-system.txt` uses Japanese section labels (e.g. `ルール:` not `Rules:`)
- [ ] `report.txt` instructs LLM to output Japanese report text
- [ ] `ConversationAgent` loads per-mode skeleton with fallback: Japanese mode gets Japanese skeleton, English modes keep root skeleton
- [ ] `ConversationAgent.buildSystemContent()` uses Japanese-labels skeleton for JAPANESE_BUSINESS mode — verify system prompt content contains `ルール:` not `Rules:`
- [ ] `ReportAgent` loads per-mode report template with fallback: Japanese mode uses Japanese report template
- [ ] `/api/modes` response includes `"JAPANESE_BUSINESS"` with display name `"ビジネス日本語"`
- [ ] Existing WORKPLACE_STANDUP / DAILY_TALK modes continue to use English skeletons unchanged
- [ ] `mvn test` passes — `ConversationAgentTest` has 3 new tests (Japanese skeleton loads Japanese labels, Japanese description/rules injected into system content, empty mode falls back to root skeleton); `ReportAgentTest` has 1 new test (Japanese report template loaded and used for Japanese mode); all existing tests unchanged
- [ ] `mvn verify` E2E tests pass — no regressions in existing WORKPLACE_STANDUP / DAILY_TALK flows

## Blocked by

None — can start immediately.
