Status: ready-for-agent

## Parent

PRD: `.scratch/card-enhance/PRD.md`

## What to build

The `POST /api/cards/{id}/enhance` endpoint that enriches a flashcard with movie subtitle quotes and word etymology. This is the core "Card Enhance" feature.

### CardEnhanceService

Service that orchestrates the enhancement pipeline:

1. Receive `cardId`, look up the Card by ID to get the front word
2. Check existing `CardEnhancement` rows — if both `SUBTITLE` and `ETYMOLOGY` are `SUCCESS`, return cached data immediately (idempotent)
3. Run two parallel tasks:
   - **Subtitle search**: Query `SubtitleLineRepository.findByImdbIdInAndWordsLowerLike` with the user's watched movie IMDB IDs and LIKE pattern for the word. Take first match. Fetch surrounding lines (same imdbId, lineIndex ± 2, up to 5 total). Send context + movie title to DeepSeek LLM for a 1-2 sentence Chinese scene summary using the prompt template below. Save `CardEnhancement(type=SUBTITLE, data=JSON { movieTitle, imdbId, quote, timestamp, sceneSummary })`. If no match found, save `CardEnhancement(status=SUCCESS, data=JSON { found: false })`.
   - **Etymology**: `GET https://en.wiktionary.org/api/rest_v1/page/definition/{word}` → extract `etymology` text from JSON response. Save `CardEnhancement(type=ETYMOLOGY, data=plain text)`. If the API returns no etymology or errors, save `CardEnhancement(status=FAILED, error=message)`.
4. Return combined result: `{ movieQuote: {...} | null, sceneSummary: "..." | null, etymology: "..." | null }`
5. Graceful degradation: Wiktionary failure does not block subtitle results. Mid-request abort (e.g. user navigates away) → already-persisted `CardEnhancement` rows remain in DB.

### Scene summary prompt template

```
以下是一段来自电影《{movieTitle}》的台词及其前后文。请用一两句中文字描述这句话发生的情境：

台词上下文：
{context lines, marking the target word}

场景摘要：
```

### CardEnhanceController

- `POST /api/cards/{id}/enhance` → returns `{ movieQuote: { movieTitle, imdbId, quote, timestamp } | null, sceneSummary: string | null, etymology: string | null }`
- Extract userId via `SecurityContextHolder` (existing pattern)
- Validate card exists (404 if not)

### Wiktionary client

Simple HTTP client, no authentication needed. Parse the JSON response to extract the `etymology` field (may be an array — join or take first).

### LLM integration

Use LangChain4j's existing `ChatLanguageModel` bean (the one configured for DeepSeek). The scene summary call is separate from the chat LLM call logging system — do NOT persist to `LlmCallLog`.

### Tests

- **CardEnhanceServiceTest**: full success (subtitle match + etymology), subtitle-only (etymology fails), etymology-only (no subtitle match → found: false), idempotent (second call returns cached data), card not found (throws 404), partial persistence on simulated mid-request abort
- **CardEnhanceControllerTest** (`@WebMvcTest`): 200 with full enhancement body, 200 with partial (etymology null), 200 idempotent, 404 for unknown card

## Acceptance criteria

- [ ] `POST /api/cards/{id}/enhance` with word appearing in a watched movie → returns `movieQuote` (film name, timestamp, original English line) + `sceneSummary` (1-2 sentence Chinese) + `etymology` (word root text)
- [ ] Word not found in any watched movie subtitle → `movieQuote` is null, response indicates no match
- [ ] Wiktionary has no entry → `etymology` is null, `movieQuote` still returned if available
- [ ] Calling enhance on an already-enhanced card returns same data without re-calling external APIs (idempotent)
- [ ] Calling enhance for a non-existent card returns HTTP 404
- [ ] `CardEnhancement` rows are persisted to H2 and survive service restart
- [ ] All unit and controller tests pass

## Blocked by

- `02-movie-management.md`
