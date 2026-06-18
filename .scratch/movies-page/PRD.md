# PRD: Movies Page — 电影上传和管理页面

Status: ready-for-agent

## Problem Statement

Learner 需要导入和管理自己看过的电影列表，以便系统在复习闪卡时能从电影字幕中查找单词出现的真实场景，展示电影台词增强记忆。当前后端 API 已完备（`MovieController` + `MovieService`），但缺少前端界面——Learner 无法自行上传电影列表，只能由开发者直接操作数据库。

## Solution

新增独立的 Movies 页面（`/movies/`），仿照现有 Manage 页的 CardsTab 模式，提供：

- 电影列表（分页、搜索、排序）
- 单部电影添加（TMDB 搜索 → 选中添加）
- CSV 批量导入（固定三列格式）
- 字幕状态查看与重试
- 删除电影（含字幕数据警告）

## User Stories

1. As a Learner, I want to see a list of all my imported movies with pagination, so that I can browse my collection even with many movies.
2. As a Learner, I want to search movies by title, so that I can quickly find a specific movie.
3. As a Learner, I want to sort movies by title, year, or import time, so that I can organize my view.
4. As a Learner, I want to add a single movie by searching TMDB, so that I can import movies without preparing a CSV file.
5. As a Learner, I want to see TMDB search results with title, year, and IMDb ID before adding, so that I can confirm I'm selecting the correct movie.
6. As a Learner, I want to batch import movies via CSV file upload, so that I can quickly add my entire watch history.
7. As a Learner, I want the CSV import to accept a simple three-column format (imdbId, title, year), so that I don't need complex column mapping.
8. As a Learner, I want to see the import result immediately (success count, skipped count, error details), so that I know which movies were added and which failed.
9. As a Learner, I want to see each movie's subtitle download status (pending, downloading, done, failed) at a glance, so that I know which movies are ready to enhance my flashcard reviews.
10. As a Learner, I want to see the subtitle line count for movies with completed downloads, so that I know how much subtitle data is available.
11. As a Learner, I want to retry subtitle download for failed movies, so that I can recover from temporary download failures.
12. As a Learner, I want to see a confirmation dialog before retrying subtitle download, reminding me that existing data will be cleared, so that I don't accidentally lose subtitle data.
13. As a Learner, I want to delete a movie from my list, so that I can remove movies I no longer want.
14. As a Learner, I want the delete confirmation to warn me how many subtitle lines will be cascaded, so that I understand the impact of deletion.
15. As a Learner, I want to navigate to the Movies page from the Header sidebar, so that I can access it from any page.

## Implementation Decisions

### Page Architecture

- New independent page at `/movies/index.html`, following the existing multi-page Vite pattern (entry TSX → Vite config → bundle JS → static HTML).
- Navigation link added to the Header sidebar with icon 🎬, label "Movies".
- New navigation order: Chat → Review → Manage → Tune → Movies → 设置 → Profile.

### Backend Changes

- `GET /api/movies` upgraded to support `search` (title fuzzy match), `sort` (title/year/createTime asc/desc), and Spring Data `Pageable` (page/size), returning `Page<Map>` — mirroring `GET /api/cards`.
- `WatchedMovieRepository` extended with `JpaSpecificationExecutor<WatchedMovie>`.
- `MovieService.listMovies()` refactored to build `Specification` predicates for search + sort.
- `MovieController.listMovies()` signature updated to accept `@RequestParam search`, `sort`, and `Pageable`.
- No new endpoint needed — existing `POST /api/movies/import/batch`, `POST /api/movies/search`, `POST /api/movies`, `DELETE /api/movies/{imdbId}`, and `POST /api/movies/{imdbId}/download` are sufficient.

### Frontend Components

New components under `src/main/frontend/src/components/movies/`:

- **MoviesApp** — Top-level component: state management (movies list, search, sort, page, modals), data fetching.
- **MovieToolbar** — Search input (debounced), sort dropdown (title Aa↑/Aa↓, year ↑/↓, add time ↑/↓), "添加电影" button (opens TMDB search modal), "批量导入" button (opens CSV import modal).
- **MovieList** — Maps movies to MovieBlock rows + Pagination component.
- **MovieBlock** — Single movie row displaying:
  - Title + year
  - Subtitle status with icon: ⏳ PENDING / ⏬ DOWNLOADING / ✓ N,NNN 行 (DONE, green) / ✗ error message (FAILED, red)
  - Actions: [重试] (FAILED only, with confirm dialog), [删除] (always, with confirm dialog)
- **MovieSearchModal** — Modal for TMDB single-movie add: search input (debounced) → candidate list (imdbId, title, year) → "添加" button per candidate → POST /api/movies.
- **MovieImportModal** — Modal for CSV batch import:
  - Stage 1: File selector (.csv)
  - Stage 2: Loading spinner + upload
  - Stage 3: Result display (success count, errors with row numbers)
  - Frontend parses CSV: first row skipped if header-like (imdbId is non-numeric); rows parsed as imdbId, title, year. Validated rows sent to POST /api/movies/import/batch.
- **MovieDeleteModal** — Confirm dialog: "确定删除 Inception (2010)？该电影的所有字幕数据（3,421 行）将被一并删除。"
- **MovieRetryModal** — Confirm dialog for subtitle re-download: "重新下载 \"Inception\" 的字幕？将清除现有字幕数据并重新获取。"

### CSV Format

Fixed three-column format, no column detection needed:
```
imdbId,title,year
tt1375666,Inception,2010
tt0133093,The Matrix,1999
```
- Column order: imdbId, title, year (mandatory, positional).
- First row is auto-skipped if imdbId value is non-numeric (header detection).
- Empty or invalid rows report errors per-row in the result display.

### Modals

All modals use the shared `Modal` component, following the pattern established by `BatchOperationModal` and CardsTab create/edit/delete modals.

## Testing Decisions

### What makes a good test

- Test external behavior observable by the user, not implementation details.
- Use `data-testid` for DOM selection (CSS Modules class names are hashed).
- Mock API calls with `vi.mock()` or Mockito; never test real network calls in unit tests.

### Backend Tests

| Test Class | Status | Coverage |
|-----------|--------|----------|
| `MovieControllerTest` | Extend existing | Pagination params, search query, sort (title/year/createTime asc/desc) |
| `MovieServiceTest` | New | Specification query building, importBatch CSV row processing, addMovie duplicate detection |

### Frontend Tests (Vitest)

| Test File | Coverage |
|-----------|----------|
| `movies/MoviesPage.test.tsx` | Page rendering, search, pagination, sort, empty state, loading state |
| `movies/MovieBlock.test.tsx` | Row rendering, subtitle status icons, button visibility per status |
| `movies/MovieToolbar.test.tsx` | Search input debounce, sort dropdown, add/import button triggers |
| `movies/MovieImportModal.test.tsx` | File selection, CSV parsing, success/failure result display |
| `movies/MovieSearchModal.test.tsx` | TMDB search debounce, candidate list rendering, add callback |
| `movies/MovieDeleteModal.test.tsx` | Confirm dialog text includes subtitle line count |
| `header/Header.test.tsx` | Updated: Movies nav link presence, new nav order verification |

### E2E Tests (Playwright + WireMock)

| Test Class | Coverage |
|-----------|----------|
| `MoviesPageIT.java` | Full flow: page load → search → sort → pagination → TMDB search add → CSV batch import → delete confirm → subtitle retry |

## Out of Scope

- Movie poster/cover images (TMDB poster API)
- Movie detail page (subtitle browsing, scene viewing)
- Subtitle export
- Batch delete movies
- Movie list export
- Movie edit (imdbId/title/year are immutable once added)
- Subtitle download progress indicator (current backend is synchronous, status only)

## Further Notes

- The existing `SubtitleStatus.DOWNLOADING` state is set synchronously by `SubtitleService.downloadSubtitles()` — in practice, download completes within the same HTTP request, so the DOWNLOADING state is rarely visible to the user. This PRD does not change this behavior.
- The CSV column detection logic (`CsvColumnDetector`) exists in the backend but is not used by this feature — frontend does fixed-position parsing instead.
- The `POST /api/movies/import/batch` endpoint triggers subtitle download for each new movie asynchronously but within the same transaction boundary. Subtitle download failures are logged per-movie but do not block the batch.
