Status: `ready-for-agent`

## What to build

Eliminate the dual `flipped` state that causes a bug where clicking the card's surrounding whitespace shows rating buttons without flipping the card. Make `flipped` a single source of truth in `ReviewPage`, with `CardDisplay` as a controlled component.

**Current behavior (bug):** ReviewPage and CardDisplay each maintain independent `flipped` state. ReviewPage's `.content` div has an `onClick` that sets ReviewPage's `flipped` — but CardDisplay's internal `flipped` only changes when the user clicks the card itself. Clicking the whitespace between card and edge sets ReviewPage flipped → rating buttons appear → card still shows "Tap to reveal".

**Target behavior:** Only the card itself triggers a flip. Clicking anywhere else in the review page does nothing. CardDisplay receives `flipped` from its parent and calls `onFlip` on card click.

### Frontend changes

**CardDisplay.tsx:**
- Add `flipped: boolean` prop (controlled)
- Add `onFlip?: () => void` prop (callback on card click)
- Remove internal `const [flipped, setFlipped] = useState(false)`
- `handleFlip` calls `onFlip?.()` instead of `setFlipped(true)`
- `editing` state stays internal (unaffected)

**ReviewPage.tsx:**
- Remove `onClick={!flipped && !editing ? handleFlip : undefined}` from `.content` div
- Pass `flipped={flipped}` and `onFlip={handleFlip}` to `<CardDisplay>`
- `handleFlip` stays in ReviewPage (no change needed)

### Test changes

**CardDisplay.test.tsx:** All existing tests must pass `flipped` and `onFlip` props. For flip tests, verify `onFlip` is called rather than asserting internal DOM state change.

**ReviewApp.test.tsx:** Add scenario: clicking the `.content` area (outside `.card`) does NOT trigger a flip.

**Existing tests that stay unchanged:** `ReviewIT.java` (E2E clicks the card itself via `data-testid="flip-card-btn"`, unaffected by `.content` onClick removal).

## Acceptance criteria

- [ ] Clicking the card itself flips the card and shows the back face + rating buttons
- [ ] Clicking the empty area outside the card (`.content` background) does nothing — card stays un-flipped, no rating buttons appear
- [ ] `RatingButtons` is gated on `ReviewPage.flipped` (existing behavior, unchanged)
- [ ] Switching to the next card resets `flipped` to `false` via `key={card.id}` remount (existing behavior, unchanged)
- [ ] `editing` state remains internal to CardDisplay (textarea, inline back edit) — still notifies ReviewPage via `onEditingChange` to disable rating buttons
- [ ] All existing Vitest tests pass with updated props
- [ ] New ReviewApp test verifies ".content click does not flip"

## Blocked by

None — can start immediately.
