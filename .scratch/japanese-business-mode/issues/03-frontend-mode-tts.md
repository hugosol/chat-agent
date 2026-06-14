# 03: Frontend — mode state + TTS language routing

**Status:** `ready-for-agent`

## Parent

PRD: `.scratch/japanese-business-mode/PRD.md`

## What to build

Add `mode` field to the frontend ChatState, store it on session start/resume actions, and route Japanese TTS to `ja-JP` language. No changes to Footer (mode dropdown) or ReportModal — both render new content automatically from API responses and existing report structure.

### ChatState — add `mode` field

Add `mode: string` to the `ChatState` interface. Update `initialState` to include `mode: ""` (empty string default before a session starts).

The `Action` union type already includes `mode` on `SESSION_STARTED` — no action shape changes needed:
```typescript
| { type: "SESSION_STARTED"; sessionId: string; mode: string }
```

### chatReducer — store mode on session events

Update the `SESSION_STARTED` case: spread `mode: action.mode` into the returned state (alongside existing `...initialState` and `appStatus: "UserTurn"`).

Update the `SESSION_RESUMED` case: the action does NOT currently carry `mode`. To support mode on resume, either:
- **Preferred**: Add `mode: string` to the `SESSION_RESUMED` action type AND update the backend `ChatMessageHandler` to include `mode` in the `SESSION_RESUMED` WebSocket payload (small protocol addition). This is the correct long-term fix and requires a minor backend change in `ChatMessageHandler.onResumeSession()`.
- **Alternative**: Use `mode: ""` as default on resume (no backend change). The mode is not used on resume in the current feature set, but this leaves a gap.

The reducer stores `mode` from the action payload in the returned state for both cases.

### TTS — Japanese voice routing

Add optional `mode?: string` parameter to `speakText()`:

```typescript
export function speakText(text: string, mode?: string): void {
  speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  if (mode === "JAPANESE_BUSINESS") {
    utterance.lang = "ja-JP";
  } else {
    utterance.lang = "en-US";
  }
  utterance.rate = 0.95;
  speechSynthesis.speak(utterance);
}
```

Then find all callers of `speakText()` in the chat frontend and pass the current `mode` from ChatState. The mode is available via `useChatContext()` in any component that calls `speakText()`.

### Footer / ReportModal — no changes needed

- **Footer mode dropdown**: Already populated from `/api/modes` API response — `ビジネス日本語` appears automatically.
- **ReportModal**: Same 4-field structure renders Japanese text naturally.

### Files to modify

- `src/main/frontend/src/state/chatState.ts`
- `src/main/frontend/src/state/chatReducer.ts`
- `src/main/frontend/src/shared/tts.ts`
- `src/main/frontend/src/components/chat/Footer/Footer.tsx` (or whichever component calls `speakText()` — pass `mode` parameter)

### Files possibly needing minor backend change

- `src/main/java/com/hugosol/chatagent/websocket/ChatMessageHandler.java` — add `mode` to `SESSION_RESUMED` payload (if taking the preferred approach for resume)

## Acceptance criteria

- [ ] `ChatState` interface includes `mode: string`; `initialState` includes `mode: ""`
- [ ] `chatReducer` stores `mode` from `SESSION_STARTED` action payload in returned state
- [ ] `chatReducer` stores `mode` from `SESSION_RESUMED` action payload in returned state (or defaults to `""` if backend not yet updated)
- [ ] `speakText()` accepts optional `mode` parameter; sets `utterance.lang = "ja-JP"` when mode is `"JAPANESE_BUSINESS"`; sets `"en-US"` otherwise
- [ ] All `speakText()` call sites in chat components pass the current `mode` from chat state
- [ ] Footer mode dropdown shows `ビジネス日本語` as a selectable option (no code changes needed — auto-populated from API)
- [ ] `mvn test` passes — `chatReducer.test.ts` updated to include `mode` in `SESSION_STARTED` expected state and `initialState`; `tts.test.ts` has 1 new test (JAPANESE_BUSINESS mode sets `lang = "ja-JP"`); existing tests updated for the new `mode` parameter signature
- [ ] `npx vitest run` in frontend passes all tests

## Blocked by

- `01-mode-foundation` — requires `JAPANESE_BUSINESS` mode to exist in backend for end-to-end verification
