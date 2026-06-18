Status: ready-for-agent

## Parent

PRD: [Movies Page](../PRD.md)

## What to build

Add confirmation dialogs for deleting a movie and retrying subtitle download. These are shown as modals when the Learner clicks `[删除]` or `[重试]` on a MovieBlock row.

**Frontend changes:**

- Create `MovieDeleteModal` in `src/main/frontend/src/components/movies/MovieDeleteModal.tsx`:
  - Props: `open`, `movie` (the WatchedMovie data), `onClose`, `onDeleted` (callback).
  - Uses the shared `Modal` component with `title="删除电影"`, `danger={true}`, `saveLabel="删除"`.
  - Body text: `确定删除 {title} ({year})？该电影的所有字幕数据（{lineCount} 行）将被一并删除。`
    - If `lineCount` is 0 or null: "该电影暂无字幕数据。"
  - On confirm: `DELETE /api/movies/{imdbId}`. On success: calls `onDeleted()` and closes.
  - On error: shows error inline, does not close.

- Create `MovieRetryModal` in `src/main/frontend/src/components/movies/MovieRetryModal.tsx`:
  - Props: `open`, `movie` (the WatchedMovie data with title + imdbId), `onClose`, `onRetried` (callback).
  - Uses the shared `Modal` component with `title="重新下载字幕"`, `saveLabel="确认"`.
  - Body text: `重新下载 "{title}" 的字幕？将清除现有字幕数据并重新获取。`
  - On confirm: `POST /api/movies/{imdbId}/download`. On success: calls `onRetried()` and closes.
  - On error: shows error inline, does not close.

- Wire both modals into `MoviesApp`:
  - `selectedMovie` state tracks which movie is targeted by a modal action.
  - `deleteModalOpen` / `retryModalOpen` states control visibility.
  - `MovieBlock` `[删除]` and `[重试]` buttons set `selectedMovie` and open the corresponding modal.
  - After successful delete/retry, refresh the movie list.

**Backend:** No changes needed — `DELETE /api/movies/{imdbId}` and `POST /api/movies/{imdbId}/download` already exist.

## Acceptance criteria

- [ ] Clicking `[删除]` on a MovieBlock opens MovieDeleteModal with movie title, year, and subtitle line count in the confirmation text.
- [ ] Clicking "删除" in the modal sends `DELETE` request, closes modal, and removes the movie from the list.
- [ ] Clicking `[重试]` on a FAILED MovieBlock opens MovieRetryModal with movie title in the confirmation text.
- [ ] Clicking "确认" in the modal sends `POST /api/movies/{imdbId}/download`, closes modal, and refreshes the movie's subtitle status.
- [ ] `[重试]` button is only visible when `subtitleStatus === "FAILED"`.
- [ ] `[删除]` button is always visible.
- [ ] API errors are surfaced inline in the modal without closing it.
- [ ] Frontend test: `MovieDeleteModal.test.tsx` covers confirm dialog text (including subtitle line count), delete API call, error handling.

## Blocked by

- [01-browse-movie-list](01-browse-movie-list.md) — needs MovieBlock with action buttons and MoviesApp state management.
