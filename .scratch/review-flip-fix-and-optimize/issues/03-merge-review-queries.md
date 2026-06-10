Status: `ready-for-agent`

## What to build

Reduce database round-trips in the `/review/next` (and `/review/start`) request path by merging `getNextCard` + `computeReviewStats` into a single `getNextCardAndStats` method that shares one `learnedToday` COUNT and one `todayStart` calculation. Also consolidate the two separate COUNT queries in STANDARD mode's `computeRemaining` into one combined JPQL query.

**Current behavior:** Each rating action calls `rateCard` → `getNextCard` → `computeReviewStats`. `getNextCard` computes `todayStart` and queries `learnedToday` (for new-card limit check). `computeReviewStats` independently computes `todayStart` and queries `learnedToday` again, plus runs 2–3 additional COUNT queries. Total: duplicated work + up to 5 COUNTs per rating.

**Target behavior:** One method call produces both the next card and stats, computing `todayStart` once and querying `learnedToday` once. STANDARD mode's `dueCount` + `newCount` are fetched in a single combined JPQL query.

### Backend changes

**New DTO (`dto/NextCardAndStats.java`):**

```java
public record NextCardAndStats(Optional<Card> card, ReviewStats stats) {}
```

**CardRepository — new query:**

```java
@Query("SELECT COUNT(CASE WHEN c.cardState <> 0 AND c.due <= :now THEN 1 END), " +
       "COUNT(CASE WHEN c.cardState = 0 THEN 1 END) " +
       "FROM Card c JOIN c.tags t WHERE t.id = :deckId AND c.userId = :userId")
Object[] countDueAndNewByDeckId(@Param("deckId") String deckId, @Param("now") Instant now, @Param("userId") String userId);
```

**ReviewService — refactoring:**

1. Extract `getNextCardInternal(deckId, mode, userId, prefs, now, todayStart, learnedToday)` — package-private, contains the mode-switch logic. Existing `getNextCard` becomes a thin delegate.
2. `isNewCardLimitExceeded` changes signature to accept `learnedToday: long` — eliminates its internal COUNT call.
3. `computeRemaining` STANDARD branch: replace `countDueCardsByTagsId` + `countByTagsIdAndCardState` with `countDueAndNewByDeckId`. Extract `Object[]` result: `dueCount = (Long) result[0]`, `newCount = (Long) result[1]`.
4. New `getNextCardAndStats(deckId, mode, userId)` — computes `todayStart` and `learnedToday` once, calls `getNextCardInternal` + `computeStatsInternal`, returns `NextCardAndStats`.

**ReviewController — update two endpoints:**

`POST /review/next` (`processNextCard`) — replace:
```java
var card = reviewService.getNextCard(request.deckId(), request.mode(), userId);
var stats = reviewService.computeReviewStats(request.deckId(), request.mode(), userId);
```
with:
```java
var result = reviewService.getNextCardAndStats(request.deckId(), request.mode(), userId);
var card = result.card();
var stats = result.stats();
```

`GET /review/start` (`startReview`) — same replacement.

`GET /review/stats` (`getStats`) — unchanged (the original `computeReviewStats` public method is preserved as-is for this independent endpoint).

### Test changes

| File | What to add/change |
|------|-------------------|
| `ReviewServiceTest.java` | 6–8 new tests for `getNextCardAndStats`: verify card+stats returned together in all 4 modes; verify `learnedToday` consistency between card-pick and stats; verify empty deck returns `Optional.empty()` with correct stats |
| `ReviewControllerTest.java` | Update `startReview` and `nextReview` mocks: mock `getNextCardAndStats` instead of separate `getNextCard` + `computeReviewStats` |
| `CardRepositoryIsolationTest.java` | New: 3 tests for `countDueAndNewByDeckId` — due+new counts correct, empty deck returns 0+0, userId isolation |

**Tests that stay unchanged:**
- `ReviewServiceTest.java` existing `rateCard_*`, `getNextCard_*`, `computeReviewStats_*` — method signatures unchanged
- `ReviewIT.java` — all E2E scenarios unaffected

## Acceptance criteria

- [ ] `getNextCardAndStats` returns the same card and stats as calling `getNextCard` + `computeReviewStats` separately
- [ ] All 4 review modes (STANDARD / REVIEW_ONLY / NEW_ONLY / CRAM) tested for `getNextCardAndStats`
- [ ] `countDueAndNewByDeckId` correctly returns `(dueCount, newCount)` for a deck with mixed card states
- [ ] `learnedToday` value used for new-card limit check in card selection is the same value reported in stats
- [ ] Existing public methods `getNextCard` and `computeReviewStats` still work (no breaking change for `/review/stats` or other callers)
- [ ] Controller tests pass with updated mock expectations
- [ ] All existing unit + E2E tests pass

## Blocked by

None — can start immediately. Independent of frontend changes.
