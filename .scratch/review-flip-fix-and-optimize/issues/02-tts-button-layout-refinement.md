Status: `ready-for-agent`

## What to build

Refine the TTS (text-to-speech) button layout so it no longer sits inside the card front before flipping. When the card is un-flipped, a single TTS button appears **below** the card for front-text pronunciation. After flipping, that below-card button disappears, and an inline TTS button appears on the front text row, next to the back text row's existing TTS button.

**Current behavior:** The front TTS button (`🔊`) is always visible inside `.cardFront`, even before flipping — it competes for visual attention and is redundant with the "Tap to reveal" hint.

**Target behavior:**
- Card un-flipped: one TTS button below the card (new `data-testid="tts-btn-below"`), reads the front text
- Card flipped: below-card TTS hidden; front-row inline TTS and back-row inline TTS visible as before

### TTS rendering rules

| Card state | Front word | TTS below card | TTS inline (front row) | TTS inline (back row) |
|---|---|---|---|---|
| Not flipped | English | ✅ | ❌ | ❌ |
| Not flipped | Non-English | ❌ | ❌ | ❌ |
| Flipped | English | ❌ | ✅ | (depends on back) |
| Flipped | Non-English | ❌ | ❌ | (depends on back) |

### Frontend changes

**CardDisplay.tsx:**
- Existing front TTS (`data-testid="tts-btn-front"`): change render condition from `showTtsFront` to `flipped && showTtsFront`
- Existing back TTS (`data-testid="tts-btn-back"`): unchanged (already gated on `flipped` via `.cardBack` parent)
- New "below-card" TTS: render a button with `data-testid="tts-btn-below"` inside `.cardArea` but **outside** `.card`, when `!flipped && showTtsFront`. Same `onClick` behavior as the inline front TTS (`speakText(englishOnly(front))`).

### Test changes

**CardDisplay.test.tsx:**
- Update TTS visibility assertions: when `flipped=false`, `tts-btn-front` must NOT be in the DOM; `tts-btn-below` must be present (for English front)
- When `flipped=true`, `tts-btn-below` must NOT be in the DOM; `tts-btn-front` must be present (for English front)

No other test files affected.

## Acceptance criteria

- [ ] Card un-flipped, English front: `tts-btn-below` visible below card; `tts-btn-front` NOT visible
- [ ] Card un-flipped, non-English front: no TTS button visible anywhere
- [ ] Card flipped, English front: `tts-btn-below` hidden; `tts-btn-front` visible in card front row
- [ ] Card flipped, English back: `tts-btn-back` visible in card back row (unchanged)
- [ ] Clicking `tts-btn-below` triggers speech synthesis for front text
- [ ] All existing CardDisplay tests and new TTS tests pass

## Blocked by

- `01-eliminate-dual-flipped-state` — this issue assumes CardDisplay already accepts `flipped: boolean` prop
