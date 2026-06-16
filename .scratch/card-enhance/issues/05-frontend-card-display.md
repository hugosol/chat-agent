Status: ready-for-agent

## Parent

PRD: `.scratch/card-enhance/PRD.md`

## What to build

Refactor the `CardDisplay` React component and its CSS module to support the three-zone enhanced card back: definition + movie quote (with scene summary) + etymology. Add the "Card Enhance" button, loading overlay, and scrollable back panel.

### Changes to `reviewTypes.ts`

Add optional `enhancement` field to the `ReviewCard` interface:

```ts
enhancement?: {
  movieQuote?: { movieTitle: string; imdbId: string; quote: string; timestamp: string } | null;
  sceneSummary?: string | null;
  etymology?: string | null;
};
```

### Changes to `CardDisplay.tsx`

New props or derived from existing `ReviewCard` data:

**When flipped AND no enhancement data** (from props):
- Render existing back text area (unchanged layout)
- Below it, render a small "Card Enhance" button (secondary style, small font)

**When "Card Enhance" button is clicked**:
1. Set `enhancing` state to `true`
2. `POST /api/cards/{cardId}/enhance` with `credentials: "include"` (for JSESSIONID cookie)
3. Show a loading overlay: semi-transparent dark mask covering the card back area, centered spinner
4. On success: store enhancement data in local state, hide overlay
5. On error: show error message in the overlay, allow retry via button

**When flipped AND enhancement data is present** (from props or from successful fetch), render three zones:

| Zone | Condition | Content |
|------|-----------|---------|
| 释义 | Always | The existing `back` text (unchanged rendering) |
| 电影台词 | `movieQuote` present | Film name + timestamp + original English quote line in italics + Chinese `sceneSummary` below it |
| 词源 | `etymology` present | Word root breakdown text |

Zones separated by `<hr>` or equivalent dividers.

**Scrollable back**: The card back container gets `overflow-y: auto` with `max-height: 60vh` (or a value that fits within the viewport without pushing the rating buttons off-screen). Ensure this works on iOS Safari.

**Mid-load navigation**: If the user flips back or navigates away while `POST /api/cards/{id}/enhance` is in flight, the fetch may be aborted by the browser on unmount. The server persists `CardEnhancement` rows regardless. On next visit (via review API), the card response includes `enhancement` → CardDisplay renders three zones directly, no button needed.

### Changes to `CardDisplay.module.css`

Add styles for: `.enhanceBtn` (small, secondary), `.loadingOverlay` (absolute, semi-transparent, flex-centered), `.spinner` (CSS spinner), `.enhanceSection` (padding, typography), `.movieQuote` (italic), `.sceneSummary` (Chinese text, smaller font), `.divider` (horizontal rule), `.scrollableBack` (overflow-y: auto, max-height).

### Tests

**CardDisplay.test.tsx** (using existing vitest + @testing-library/react patterns):
- Shallow: card flipped without enhancement → "Card Enhance" button rendered
- Shallow: card flipped with enhancement → three zones rendered, **no** button
- Shallow: movieQuote present → film name, timestamp, quote, sceneSummary visible
- Shallow: movieQuote null → zone not rendered or shows "not found" message
- Shallow: etymology present → word root text visible
- Shallow: etymology null → zone not rendered
- Interaction: click button → fetch called with correct URL and credentials
- Interaction: during fetch → loading overlay visible
- Interaction: fetch resolves → overlay gone, three zones appear
- Interaction: fetch rejects → error message in overlay, retry available
- Layout: card back content area has `overflow-y: auto` style
- Mock `global.fetch` (existing project pattern)

**ReviewApp.test.tsx** (if it mounts CardDisplay):
- Card data with enhancement prop → CardDisplay renders three zones without button

## Acceptance criteria

- [ ] Flipped card without enhancement shows "Card Enhance" button below the back text
- [ ] Clicking "Card Enhance" shows loading overlay, then three zones appear (definition + movie quote + etymology)
- [ ] Flipped card with existing enhancement data (from review API response) shows three zones directly, no button
- [ ] Card back area scrolls vertically when content exceeds container height
- [ ] Movie quote zone shows: film name, timestamp, original English line in italics, Chinese scene summary
- [ ] Etymology zone shows word root breakdown text
- [ ] Wiktionary failure (etymology null) → only two zones shown, no error message
- [ ] No subtitle match (movieQuote null) → appropriate message shown instead of quote
- [ ] Loading overlay dismisses correctly on both success and error
- [ ] All frontend unit tests pass
- [ ] Works in iOS Safari (no CSS issues per existing conventions in `docs/frontend-notes.md`)

## Blocked by

- `04-review-api-enhancement.md`
