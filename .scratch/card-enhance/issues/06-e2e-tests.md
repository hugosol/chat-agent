Status: ready-for-agent

## Parent

PRD: `.scratch/card-enhance/PRD.md`

## What to build

Two Playwright E2E test classes using the existing WireMock setup to stub all external APIs. These tests verify the full card enhancement flow and movie import pipeline end-to-end through all layers (HTTP → Service → DB → Frontend).

### CardEnhanceIT.java — full user enhancement journey

**WireMock stubs** (configured in test setup):
- Wyzie Subs: returns a realistic SRT file containing the target word
- TMDB search: returns movie candidates matching the search query
- Wiktionary REST API: returns JSON with `etymology` text for the target word
- DeepSeek chat completions: returns a short Chinese scene summary

**Test steps**:
1. Import a watched movie via `POST /api/movies/import/batch` with a test CSV
2. Verify `GET /api/movies` returns the movie with `subtitleStatus: DONE`
3. Verify `SubtitleLine` rows exist in H2 for the imported movie
4. Create a flashcard (via `POST /api/cards`) whose front word appears in the imported subtitle
5. Navigate browser to `/review` → select deck → start review
6. Flip the card → verify "Card Enhance" button is visible on the card back
7. Click "Card Enhance" → verify loading overlay appears
8. Wait for loading to finish → verify three zones rendered:
   - Definition zone: the card's back text
   - Movie quote zone: film name, timestamp, original English line, Chinese scene summary
   - Etymology zone: word root breakdown text
9. Rate the card (e.g., click "Good" button)
10. Request next card, then end the review session
11. Start a new review for the same deck → flip the same card → verify enhancement is shown directly (no "Card Enhance" button)
12. Query H2 directly: verify `CardEnhancement` table has rows with `status = 'SUCCESS'` for both SUBTITLE and ETYMOLOGY types

### MovieImportIT.java — import pipeline verification

**WireMock stub**:
- Wyzie Subs: returns SRT files for each imported movie

**Test steps**:
1. Upload CSV in Douban format → verify `GET /api/movies` returns all movies with correct title/year/imdbId
2. Upload CSV in Letterboxd format → verify column auto-detection works (different column names map correctly)
3. Upload CSV in Trakt format → verify column auto-detection works
4. Upload CSV in IMDb format → verify column auto-detection works
5. After each import, verify `SubtitleLine` rows are populated in H2 for each movie
6. Verify `WatchedMovie.subtitleLineCount` matches actual SubtitleLine row count per movie
7. Delete a movie via `DELETE /api/movies/{imdbId}` → verify both `WatchedMovie` and `SubtitleLine` rows are removed
8. Manually trigger subtitle retry via `POST /api/movies/{imdbId}/download` → verify old SubtitleLine rows are cleared and new ones are written

### Test configuration

- Follow existing project E2E patterns (Failsafe plugin, `*IT.java` suffix, `mvn verify`)
- WireMock configuration follows existing project conventions for stubbing external APIs
- Browser automation uses the existing Playwright setup
- H2 verification queries use Spring's `JdbcTemplate` or `DataSource` injected into test

## Acceptance criteria

- [ ] `CardEnhanceIT` passes: full user flow from movie import → card enhance → rating → revisit works end-to-end
- [ ] `MovieImportIT` passes: all four CSV formats (Douban/Letterboxd/Trakt/IMDb) import correctly
- [ ] Subtitle download and retry work correctly in E2E (Wyzie stubs returning real SRT content)
- [ ] WireMock stubs cover all external API calls: Wyzie Subs, TMDB, Wiktionary, DeepSeek
- [ ] H2 verification queries confirm database state matches expected outcomes
- [ ] Both tests run as part of `mvn verify` without interfering with other E2E tests

## Blocked by

- `05-frontend-card-display.md`
