Status: ready-for-agent

## Parent

PRD: [Movies Page](../PRD.md)

## What to build

Deliver the complete movie browsing experience end-to-end: backend API upgrade for search/sort/pagination + frontend page with toolbar, movie list, status display, pagination, and Header navigation.

**Backend changes:**

- Extend `WatchedMovieRepository` to also implement `JpaSpecificationExecutor<WatchedMovie>`.
- Refactor `MovieService.listMovies()` to accept `search`, `sort`, and `Pageable` parameters, building `Specification` predicates:
  - `search`: case-insensitive LIKE on `title` (fuzzy match).
  - `sort`: supports `title`, `releaseYear`, `createTime` with asc/desc direction. Passed through to `Pageable` via Spring's `Sort` handling.
- Update `MovieController.listMovies()` to accept `@RequestParam(required = false) search`, `@RequestParam(required = false, defaultValue = "title,asc") sort`, and `Pageable pageable`. Return `ResponseEntity<Page<Map<String, Object>>>`.
- Map `WatchedMovie` fields into the response: `id`, `imdbId`, `title`, `releaseYear`, `subtitleStatus` (enum name), `subtitleLineCount`, `subtitleError`.

**Frontend changes:**

- Create `vite.config.movies.ts` following the existing multi-config pattern (`vite.config.manage.ts`), with entry `src/entry/movies-entry.tsx` and output `movies-bundle.js`/`movies-bundle.css`.
- Add `&& vite build --config vite.config.movies.ts` to the `"build"` script in `package.json`.
- Create `src/main/resources/static/movies/index.html` following the manage page pattern: viewport meta, base.css, movies-bundle.css, root div, React UMD globals, movies-bundle.js.
- Create `src/main/frontend/src/entry/movies-entry.tsx` following the `manage-entry.tsx` pattern — mount `MoviesApp` into `#root`.
- Build `MoviesApp` component in `src/main/frontend/src/components/movies/MoviesApp.tsx`:
  - State: `movies` list, `search` query, `sort` field+dir, `page`, `totalPages`, loading/error states.
  - On mount and on search/sort/page change: `GET /api/movies?search=...&sort=title,asc&page=0&size=10`.
  - Renders `MovieToolbar`, `MovieList`, and `Pagination`.
- Build `MovieToolbar` in `src/main/frontend/src/components/movies/MovieToolbar.tsx`:
  - Search input with 300ms debounce, placeholder "搜索电影标题...".
  - Sort dropdown: "名称 A→Z", "名称 Z→A", "年份 ↑", "年份 ↓", "添加时间 ↑", "添加时间 ↓" — mapping to `title,asc` / `title,desc` / `releaseYear,asc` / `releaseYear,desc` / `createTime,asc` / `createTime,desc`.
  - "添加电影" button and "批量导入" button (these open modals — modal state managed by MoviesApp, wired in later slices).
- Build `MovieList` + `MovieBlock`:
  - `MovieList` maps over movies rendering a `MovieBlock` per item.
  - `MovieBlock` displays: title + year, subtitle status with icon:
    - `PENDING` → ⏳ 等待下载
    - `DOWNLOADING` → ⏬ 下载中
    - `DONE` → ✓ N,NNN 行 (green)
    - `FAILED` → ✗ error message (red)
  - Action buttons: `[重试]` (only when FAILED), `[删除]` (always). Click handlers wired to modal open callbacks (modal components from later slices).
- Integrate shared `Pagination` component.
- Add `🎬 Movies` nav link to `Header.tsx` in the sidebar links, between Tune and 设置. New order: Chat → Review → Manage → Tune → **Movies** → 设置 → Profile.
  - Add `data-testid="nav-movies-link"` to the link.
  - Add `const isMoviesPage = currentPath.startsWith("/movies/")` and the corresponding `data-active` check.

## Acceptance criteria

- [ ] `GET /api/movies?search=Inception&sort=title,asc&page=0&size=5` returns paginated, filtered, sorted results.
- [ ] `GET /api/movies` without params returns first page of all movies ordered by `title,asc` (default sort).
- [ ] Movie list renders with title, year, subtitle status icon, and action buttons per row.
- [ ] Search input with debounce filters the list via API call.
- [ ] Sort dropdown changes ordering via API call.
- [ ] Pagination controls navigate between pages via API call.
- [ ] Empty state renders gracefully when no movies exist.
- [ ] Movies nav link appears in Header sidebar between Tune and 设置, active highlight works on `/movies/` paths.
- [ ] Backend tests: `MovieControllerTest` extended for pagination params, search query, sort combinations.
- [ ] Backend tests: `MovieServiceTest` extended for Specification query building.
- [ ] Frontend tests: `MoviesPage.test.tsx` covers rendering, search, sort, pagination, empty state, loading state.
- [ ] Frontend tests: `MovieBlock.test.tsx` covers row rendering, subtitle status icons for all four statuses, button visibility per status.
- [ ] Frontend tests: `MovieToolbar.test.tsx` covers search debounce, sort dropdown, add/import button triggers.
- [ ] Frontend tests: `Header.test.tsx` updated to verify Movies nav link presence and new nav order.

## Blocked by

None — can start immediately.
