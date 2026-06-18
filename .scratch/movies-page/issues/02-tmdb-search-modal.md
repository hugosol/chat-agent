Status: ready-for-agent

## Parent

PRD: [Movies Page](../PRD.md)

## What to build

Add a modal for searching TMDB and adding a single movie. Triggered from the "添加电影" button in MovieToolbar. The modal lets the Learner search for a movie, see candidate results with title/year/imdbId, and add one to their collection.

**Frontend changes:**

- Create `MovieSearchModal` in `src/main/frontend/src/components/movies/MovieSearchModal.tsx`:
  - Props: `open`, `onClose`, `onAdded` (callback to refresh movie list).
  - Search input with 300ms debounce. On input change: `POST /api/movies/search { "query": "..." }`.
  - Results list: each candidate shows imdbId, title, year, and an "添加" button.
  - Clicking "添加" calls `POST /api/movies { "imdbId": "...", "title": "...", "year": N }`.
  - On success: calls `onAdded()` and closes. On error: shows error message inline.
  - Uses the shared `Modal` component with `title="添加电影"`.
  - Empty query: show placeholder "输入电影名称搜索 TMDB...".
  - No results: show "未找到匹配的电影".
  - Loading state: show spinner/progress indicator during API calls.
- Wire the modal into `MoviesApp`: open state managed by parent, "添加电影" button in `MovieToolbar` calls `onAddMovie` prop.

**Backend:** No changes needed — `POST /api/movies/search` and `POST /api/movies` already exist.

## Acceptance criteria

- [ ] Clicking "添加电影" opens the TMDB search modal.
- [ ] Typing a query triggers debounced TMDB search, results display with title, year, imdbId.
- [ ] Clicking "添加" on a candidate calls `POST /api/movies` and the modal closes.
- [ ] After successful add, the movie list refreshes and the new movie appears.
- [ ] Empty query shows placeholder text, no API call is made.
- [ ] No results shows "未找到匹配的电影".
- [ ] Error during search or add is surfaced to the user.
- [ ] Frontend test: `MovieSearchModal.test.tsx` covers TMDB search debounce, candidate list rendering, add callback, empty/no-results/error states.

## Blocked by

- [01-browse-movie-list](01-browse-movie-list.md) — needs MovieToolbar "添加电影" button and MoviesApp state management.
