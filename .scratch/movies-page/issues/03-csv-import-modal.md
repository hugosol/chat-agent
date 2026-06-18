Status: ready-for-agent

## Parent

PRD: [Movies Page](../PRD.md)

## What to build

Add a modal for batch importing movies via CSV file upload. Triggered from the "批量导入" button in MovieToolbar. The modal lets the Learner select a CSV file, validates rows in the frontend, uploads, and displays a result summary.

**Frontend changes:**

- Create `MovieImportModal` in `src/main/frontend/src/components/movies/MovieImportModal.tsx`:
  - Props: `open`, `onClose`, `onImported` (callback to refresh movie list).
  - Uses the shared `Modal` component with `title="批量导入电影"`.
  - **Stage 1 — Select:** File input accepting `.csv`. Shows the fixed three-column format hint:
    ```
    格式：imdbId,title,year
    tt1375666,Inception,2010
    tt0133093,The Matrix,1999
    ```
  - **Stage 2 — Parse & validate (frontend):**
    - Read file with `FileReader.readAsText()`.
    - Split by newlines, skip empty lines.
    - First row: if `imdbId` value is non-numeric (does not start with `tt`), treat as header and skip.
    - Each row: split by comma, extract imdbId (col 0), title (col 1), year (col 2).
    - Validation per row:
      - imdbId must be non-empty and match `/^tt\d+$/`.
      - title must be non-empty.
      - year must be empty or parseable as integer.
    - Collect valid rows and error rows with row number and reason.
    - If zero valid rows: show error, stay on stage 1.
  - **Stage 3 — Upload:** Show loading spinner. `POST /api/movies/import/batch` with the array of valid rows (each as `{ imdbId, title, year }`). The backend endpoint returns `{ "imported": N, "status": "ok" }`.
  - **Stage 4 — Result:**
    - Show "成功导入 N 部电影" if any succeeded.
    - If any rows were skipped in frontend validation: list them with row numbers and reasons.
    - If any rows were skipped by backend (duplicates): shown via a second pass or inferred (backend currently returns total imported count; the frontend can compare with valid row count).
  - "关闭" button returns to stage 1 (closes modal on second close).
- Wire the modal into `MoviesApp`: open state managed by parent, "批量导入" button in `MovieToolbar` calls `onImportMovies` prop.

**Backend:** No changes needed — `POST /api/movies/import/batch` already exists. The frontend sends rows from positional parsing (not keyed by column name). The backend `importBatch()` method receives `List<Map<String, String>>` and reads `row.get("imdbId")`, `row.get("title")`, `row.get("year")`.

**CSV parsing details:**

- Column order is fixed positional: column 0 = imdbId, column 1 = title, column 2 = year.
- Delimiter: comma. Simple split — no quoted-field support needed (titles don't contain commas in practice).
- Header detection: if first row's column 0 value does not match `/^tt\d+$/`, skip it.

## Acceptance criteria

- [ ] Clicking "批量导入" opens the CSV import modal with file selector and format hint.
- [ ] Selecting a valid CSV parses it: header row auto-skipped, valid rows extracted.
- [ ] Invalid rows are reported with row number and reason (missing imdbId, missing title, invalid year).
- [ ] Clicking upload sends valid rows to `POST /api/movies/import/batch` with loading spinner.
- [ ] Result shows count of successfully imported movies.
- [ ] Errors from backend or frontend validation are displayed clearly.
- [ ] After successful import, the movie list refreshes.
- [ ] Selecting a non-CSV file or empty file shows appropriate error.
- [ ] Frontend test: `MovieImportModal.test.tsx` covers file selection, CSV parsing (header skip, valid rows, invalid rows), upload, success/failure result display.

## Blocked by

- [01-browse-movie-list](01-browse-movie-list.md) — needs MovieToolbar "批量导入" button and MoviesApp state management.
