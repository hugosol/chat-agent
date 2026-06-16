Status: ready-for-agent

## Parent

PRD: `.scratch/card-enhance/PRD.md`

## What to build

Complete movie management backend — CSV import with auto column detection for 4 platforms, TMDB search for manual add, and subtitle download pipeline via Wyzie Subs API. All 6 `MovieController` REST endpoints.

### Components

**CsvColumnDetector** — utility class that normalizes CSV headers (lowercase, strip spaces/underscores) and matches against known column aliases:
- IMDB ID: `imdbid` / `imdb_id` / `imdb id` / `const`
- Title: `title` / `name` / `film` / `movie`
- Year: `year`
Throws clear error on missing required columns.

**Wyzie Subs client** — HTTP client calling `https://sub.wyzie.io` to fetch subtitle download links by IMDB ID. Follow redirects to download SRT file bytes.

**SRT parser** — parses SRT format into `SubtitleLine` objects: extracts index, startTime, endTime, text. Strips HTML tags from text. Generates `wordsLower` by lowercasing and removing punctuation. Assigns sequential `lineIndex` starting from 1.

**SubtitleService** — orchestrates download + parse + persist for a single movie:
- Sets `WatchedMovie.subtitleStatus` to `DOWNLOADING` before starting
- On retry: deletes all existing `SubtitleLine` rows for that `imdbId` before re-downloading
- On success: sets status to `DONE`, updates `subtitleLineCount`
- On failure: sets status to `FAILED`, stores error in `subtitleError`

**MovieService** — orchestrates movie management operations:
- CSV batch import: receive list of movies `[{imdbId, title, year}]`, save `WatchedMovie` rows, trigger subtitle download for each via `SubtitleService`
- TMDB search: call TMDB API, parse results, return candidate list
- Manual add: accept movie picked from TMDB results, save `WatchedMovie`, trigger download
- CRUD: list by userId, delete by imdbId (removes both WatchedMovie and SubtitleLine rows)

**MovieController** (`@RestController`, `@RequestMapping("/api")`):
- `GET /api/movies` → list all movies for current user with `subtitleStatus`, `subtitleLineCount`, `subtitleError`
- `POST /api/movies/import/batch` → receive `List<{imdbId, title, year}>`, save + download subtitles
- `DELETE /api/movies/{imdbId}` → delete movie and its `SubtitleLine` rows
- `POST /api/movies/search` → search TMDB by movie name (body: `{ query: "..." }`)
- `POST /api/movies` → add single movie (body: `{ imdbId, title, year }`)
- `POST /api/movies/{imdbId}/download` → retry subtitle download, clear old data first

Auth: extract userId via `SecurityContextHolder.getContext().getAuthentication()` (existing pattern).

### Tests

Use existing test patterns. Mock external HTTP clients (Wyzie, TMDB).

- **CsvColumnDetectorTest**: four platform formats (Douban/Letterboxd/Trakt/IMDb), missing column exception, empty CSV
- **SubtitleServiceTest**: SRT download success → SubtitleLine rows created, download failure → FAILED status, retry clears old data then re-downloads
- **MovieServiceTest**: CSV parsing with column matching, TMDB search result parsing, CRUD
- **MovieControllerTest** (`@WebMvcTest`): all 6 endpoints with mocked services, auth user extraction

## Acceptance criteria

- [ ] Upload CSV with Douban format → all movies saved with correct imdbId/title/year
- [ ] Upload CSV with Letterboxd format → column auto-detection works
- [ ] Upload CSV with Trakt format → column auto-detection works
- [ ] Upload CSV with IMDb format → column auto-detection works
- [ ] After CSV import, `GET /api/movies` returns movies with `subtitleStatus` reflecting download outcome
- [ ] Subtitle download failure shows `FAILED` status with error message; manual retry via `POST /api/movies/{imdbId}/download` clears old data and retries
- [ ] TMDB search returns candidate movies; selecting one and posting to `POST /api/movies` adds it with auto subtitle download
- [ ] Deleting a movie via `DELETE /api/movies/{imdbId}` removes both `WatchedMovie` and its `SubtitleLine` rows
- [ ] All unit and controller tests pass

## Blocked by

- `01-data-model-foundation.md`
