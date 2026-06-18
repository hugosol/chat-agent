Status: ready-for-agent

## Parent

PRD: `.scratch/card-enhance/PRD.md`

## What to build

Modify the review API endpoints (`GET /api/review/start` and `POST /api/review/next`) to include enhancement data in each card's response JSON, so the frontend can render enhanced cards without an extra API call on revisit.

### Changes to ReviewService

- Add `CardEnhancementRepository` as a constructor dependency
- In `getNextCardAndStats()` (or a new helper method), when a card is returned, fetch its `CardEnhancement` rows via `cardEnhancementRepository.findByCardId(cardId)`
- If the card has both `SUBTITLE` (status=SUCCESS) and `ETYMOLOGY` (status=SUCCESS) rows, build an enhancement object from their `data` fields
- If only partial enhancement exists (one type SUCCESS, other FAILED or missing), return what's available
- The enhancement data should be included in the returned DTO so `ReviewController.cardToMap()` can access it

### Changes to ReviewController

- Modify `cardToMap(Card card)` to accept enhancement data and include an `"enhancement"` key when data exists:
```java
if (enhancement != null) {
    Map<String, Object> enh = new HashMap<>();
    if (enhancement.movieQuote() != null) {
        enh.put("movieQuote", enhancement.movieQuote()); // { movieTitle, imdbId, quote, timestamp }
    }
    if (enhancement.sceneSummary() != null) {
        enh.put("sceneSummary", enhancement.sceneSummary());
    }
    if (enhancement.etymology() != null) {
        enh.put("etymology", enhancement.etymology());
    }
    map.put("enhancement", enh);
}
```
- Both `startReview()` and `processNextCard()` call `cardToMap` with enhancement data

### Changes to tests

- **ReviewServiceTest**: mock `CardEnhancementRepository` to return enhancement rows for a test card; assert `getNextCardAndStats` result includes enhancement data; assert cards without enhancement have null/absent enhancement
- **ReviewControllerTest**: assert JSON response for a card with enhancement includes the `enhancement` field with correct structure `{ movieQuote: {...}, sceneSummary: "...", etymology: "..." }`; assert card without enhancement omits the field

## Acceptance criteria

- [ ] `GET /api/review/start?deckId=X` returns cards with `enhancement` field when both SUBTITLE and ETYMOLOGY enhancements exist
- [ ] `POST /api/review/next` response includes `enhancement` in the next card when enhanced
- [ ] Cards without enhancement data omit the `enhancement` field (or return `null`)
- [ ] Enhancement structure: `{ movieQuote: { movieTitle, imdbId, quote, timestamp }, sceneSummary, etymology }`
- [ ] Existing review tests still pass (backward compatible)
- [ ] New/modified tests assert enhancement field behavior

## Blocked by

- `03-card-enhance-api.md`
