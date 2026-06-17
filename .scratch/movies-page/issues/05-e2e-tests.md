Status: ready-for-agent

## Parent

PRD: [Movies Page](../PRD.md)

## What to build

Add an E2E test covering the full Movies page flow using Playwright + WireMock, following the existing `MovieImportIT` and `E2ETestBase` patterns.

**Test file:** `src/test/java/com/hugosol/chatagent/e2e/MoviesPageIT.java`

**Test scenarios:**

1. **Page load**: Navigate to `/movies/index.html`, verify the page renders with toolbar (search input, sort dropdown, add/import buttons) and "暂无电影" empty state.
2. **Search**: Stub `GET /api/movies` with WireMock to return paginated results filtered by search query. Type in search input, wait for debounce, verify API call includes `search` param, verify movie list updates.
3. **Sort**: Stub sort variants. Change sort dropdown selection, verify API call includes correct `sort` param, verify list re-orders.
4. **Pagination**: Stub multi-page response. Verify pagination controls appear, click next page, verify API call includes `page=1`.
5. **TMDB search and add**: Open "添加电影" modal. Stub `POST /api/movies/search` to return candidates. Type query, verify candidates display. Click "添加", stub `POST /api/movies` success response, verify modal closes and list refreshes.
6. **CSV batch import**: Open "批量导入" modal. Use `page.setInputFiles()` to select a test CSV file. Verify parsing shows valid rows. Click upload, stub `POST /api/movies/import/batch`, verify result summary.
7. **Delete**: Click `[删除]` on a MovieBlock. Verify modal text includes title and line count. Click "删除", stub `DELETE /api/movies/{imdbId}`, verify movie removed from list.
8. **Subtitle retry**: Click `[重试]` on a FAILED movie. Verify retry modal text. Click "确认", stub `POST /api/movies/{imdbId}/download`, verify status refreshes.

**WireMock stubs needed:**

- `GET /api/movies` — return a `Page`-shaped JSON response with `content`, `totalPages`, `totalElements`, `number`, `size`.
- `POST /api/movies/search` — return candidate list.
- `POST /api/movies` — return single movie object.
- `POST /api/movies/import/batch` — return import result.
- `DELETE /api/movies/{imdbId}` — return success.
- `POST /api/movies/{imdbId}/download` — return success.

**Test data:**

Prepare a test CSV file at `src/test/resources/movies-test.csv` with a few rows:
```
imdbId,title,year
tt1375666,Inception,2010
tt0133093,The Matrix,1999
```

**Pattern to follow:**

- Extend `E2ETestBase` for auth bypass, base URL, and WireMock server.
- Use `@WithMockUser` or the `permit-all-paths: [/**]` E2E profile behavior.
- Use `data-testid` attributes for DOM selection (not CSS class names).
- Use `page.waitForResponse()` or `page.waitForRequest()` for API call verification.

## Acceptance criteria

- [ ] `MoviesPageIT` passes in `mvn verify` (failsafe plugin).
- [ ] Covers page load with empty state.
- [ ] Covers search with debounce and API call verification.
- [ ] Covers sort dropdown interaction.
- [ ] Covers pagination navigation.
- [ ] Covers TMDB search modal flow (open → search → add → close).
- [ ] Covers CSV import modal flow (open → select file → upload → result).
- [ ] Covers delete confirmation flow.
- [ ] Covers subtitle retry confirmation flow.

## Blocked by

- [01-browse-movie-list](01-browse-movie-list.md) — needs the full page with toolbar, list, blocks, pagination.
- [02-tmdb-search-modal](02-tmdb-search-modal.md) — needs TMDB search modal.
- [03-csv-import-modal](03-csv-import-modal.md) — needs CSV import modal.
- [04-delete-retry-modals](04-delete-retry-modals.md) — needs delete and retry modals.
