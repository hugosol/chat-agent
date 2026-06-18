Status: ready-for-agent

## Parent

PRD: `.scratch/card-enhance/PRD.md`

## What to build

Create the three JPA entities that form the data foundation for card enhancement, plus their enums and Spring Data JPA repositories. Hibernate `ddl-auto: update` handles table creation.

### Entities

**CardEnhancement** — one row per external API call for a card. Fields: `id` (UUID, `@GeneratedValue`), `cardId` (String — soft-link to cards table, no FK), `type` (enum: `SUBTITLE` / `ETYMOLOGY`), `status` (enum: `PENDING` / `SUCCESS` / `FAILED`), `data` (`@Column(columnDefinition = "TEXT")` — JSON for subtitle, plain text for etymology), `error` (TEXT), `requestUrl` (TEXT). Extends `BaseEntity` for `createTime`/`updateTime`.

**WatchedMovie** — learner's watched movie list. Fields: `id` (UUID), `userId` (String, `@Column(nullable = false)`), `imdbId` (String), `title` (String), `year` (Integer), `subtitleStatus` (enum: `PENDING` / `DOWNLOADING` / `DONE` / `FAILED`), `subtitleLineCount` (Integer), `subtitleError` (TEXT). Extends `BaseEntity`.

**SubtitleLine** — parsed SRT subtitle rows. Fields: `id` (UUID), `imdbId` (String), `movieTitle` (String), `startTime` (String), `endTime` (String), `text` (`@Column(columnDefinition = "TEXT")` — original subtitle text), `wordsLower` (`@Column(columnDefinition = "TEXT")` — lowercase, punctuation-stripped for LIKE matching), `lineIndex` (Integer). Extends `BaseEntity`.

### Enums

- `EnhancementType`: `SUBTITLE`, `ETYMOLOGY`
- `EnhancementStatus`: `PENDING`, `SUCCESS`, `FAILED`
- `SubtitleStatus`: `PENDING`, `DOWNLOADING`, `DONE`, `FAILED`

### Repositories

- `CardEnhancementRepository`: `findByCardId(String cardId)` → `List<CardEnhancement>`
- `WatchedMovieRepository`: `findByUserId(String userId)` → `List<WatchedMovie>`; `findByUserIdAndImdbId(String userId, String imdbId)` → `Optional<WatchedMovie>`
- `SubtitleLineRepository`: `findByImdbIdInAndWordsLowerLike(Collection<String> imdbIds, String pattern)` → `List<SubtitleLine>` (ORDER BY imdbId, lineIndex); `deleteByImdbId(String imdbId)`; `countByImdbId(String imdbId)` → `int`

## Acceptance criteria

- [ ] All three entities compile and Hibernate auto-creates tables on startup (`ddl-auto: update`)
- [ ] All repository custom query methods compile with correct Spring Data JPA derived query syntax
- [ ] Entity unit tests verify field accessors, enum values, and `BaseEntity` inheritance (`createTime`/`updateTime` are non-null after persist)
- [ ] `SubtitleLineRepository.findByImdbIdInAndWordsLowerLike` correctly matches words with LIKE pattern wrapping (`% word %` equivalent)

## Blocked by

None — can start immediately.
