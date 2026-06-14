# PRD: 卡片 Tag 级别防重 + 牌组完成时间估算

**Triage: `ready-for-agent`**

## Problem Statement

### Problem 1: 卡片跨牌组同名被误拦

当前创建卡片（`POST /api/cards/add`）时，系统全局检查 `front` 唯一性。如果用户在不同的 Deck 中需要两张同名卡片（例如不同的 back 内容代表不同领域的知识），系统会拒绝创建。与此同时，CSV 批量导入却按 `(front, tag)` 做重复检查，同一个 Deck 内的同名才拦截。两个入口行为不一致。

此外，当前 `updateCard` 改 front 和改 tagIds 时也使用全局唯一性检查，用户编辑卡片可能因跨 Deck 同名而被误拦。

### Problem 2: 缺少牌组完成时间估算

DeckPicker 页面当前显示"今日已学新卡: N"和"剩余 N 张"，用户无法直观了解按当前进度还需要多少天才能学完牌组中所有新卡。

## Solution

### Solution 1: 卡片 `(front, tag)` 级别唯一性

**核心规则**: 同一用户下，`(front, tag)` pair 唯一（大小写不敏感）。同一 Deck 内不允许两张 front 相同的卡片；不同 Deck 允许各自独立的同名卡片。

用户视角三种使用方式：
- **共享进度**: 同一张卡片通过多个 tag 关联到不同 Deck，记忆进度共享（复习一次，所有关联 Deck 同步更新）。
- **独立调度**: 在不同 Deck 中创建同名但 back 不同的卡片，各自拥有独立的 FSRS 调度状态，适合不同领域知识。
- **批量导入**: CSV 导入不阻止其他 Deck 已存在的卡片，仅校验文件内及目标 Deck 内的重复。

**单卡添加**: 后端区分硬冲突和软冲突。同 Deck 内已有同名 → 422 硬拒绝并提示冲突 Deck 名称。跨 Deck 已有同名 → 前端弹出二次确认框"卡片 'xxx' 已在牌组 'yyy' 中存在，确认添加？"，用户确认后继续创建。

**编辑卡片**: 改 front 或改 tagIds 路径同样执行 `(front, tag)` 级别检查（排除自身），防止编辑后产生同一 Deck 内的同名冲突。

### Solution 2: 牌组新卡完成天数估算

**后端**: `ReviewStats` 新增 `totalNewCards` 字段（牌组中 cardState=0 的卡片总数）。

**前端 DeckPicker**: 在"今日已学新卡: N"旁显示"预计还需 X 天学完"。计算公式：`ceil(totalNewCards / learnedToday)`，当 `learnedToday = 0` 时显示"暂无数据"。

> 精确仿真"全部卡片达到稳定掌握状态"所需时间（需遍历所有卡片的未来 FSRS 调度路径）另行评估，本次 PRD 仅覆盖简单估算。

## User Stories

### 卡片防重

1. As a Learner, I want to create a card with the same front text in a different Deck, so that I can learn the same word in different contexts with different back content.
2. As a Learner, I want to create a card that belongs to multiple Decks at once, so that my review progress is shared across those Decks.
3. As a Learner, when I try to create a card that already exists in the selected Deck, I want to see which Deck has the conflict, so that I know why creation failed.
4. As a Learner, when I try to create a card that already exists in other Decks (but not the current one), I want to see a confirmation dialog listing those Decks, so that I can decide whether to create an independent copy.
5. As a Learner, I want to edit a card's front text without being blocked by cards in other Decks, so that I can fix typos freely.
6. As a Learner, I want to edit a card's tags without accidentally creating a duplicate within the same Deck, so that my card list stays clean.
7. As a Learner, I want CSV import to keep working as before — blocking only duplicates within the target Deck, not cards that exist in other Decks.

### 牌组完成估算

8. As a Learner, when I look at the Deck selector, I want to see how many new cards I still need to learn today, so that I can plan my study session.
9. As a Learner, I want to see an estimated number of days to finish learning all new cards in the Deck at my current pace, so that I can gauge my learning progress.

## Implementation Decisions

### Card uniqueness rule

- Uniqueness is enforced per `(userId, front, tag)` tuple, case-insensitive on `front`.
- A Card can have many tags (Many-to-Many), any number of which can be deck-type tags.
- The system returns conflicting tag names in error messages to aid user decisions.

### Conflict detection API

- **Hard conflict (same deck)**: The `createCard` and `updateCard` service methods throw `ResponseStatusException(422)` with the conflicting tag name(s) in the message.
- **Soft conflict (cross deck)**: A new `POST /api/cards/check` endpoint accepts the same payload as `addCard` and returns `{ "conflicts": [{ "tagId": "...", "tagName": "..." }] }`. The frontend uses this to show a confirmation dialog before calling `addCard`.
- **updateCard**: Both front-change and tag-change paths perform the same conflict check, excluding the card's own ID from the lookup.

### ReviewStats extension

- `ReviewStats` record gains `long totalNewCards` field.
- Computed via `COUNT(c) WHERE c.cardState = 0 AND EXISTS (SELECT 1 FROM c.tags t WHERE t.id = :deckId)`.
- In CRAM mode, `totalNewCards` returns `-1` as sentinel (consistent with `remaining` and `learnedToday`).
- The frontend computes estimated days with `Math.ceil(totalNewCards / Math.max(learnedToday, 1))`.
- When `learnedToday` is `0`, the frontend displays "暂无数据" instead of a computed number.

### No schema migration needed

- The uniqueness rule change is a validation constraint, not a schema change.
- `totalNewCards` in `ReviewStats` is derived, not persisted.
- No new database tables or columns.

## Testing Decisions

### What makes a good test

- Test external behavior (API responses, UI rendering), not implementation details.
- For conflict detection: verify the HTTP status code and error message content, not the internal query.
- For the estimate display: verify the rendered text matches expected computation, not the internal formula.

### Backend unit tests to adapt / add

| Scope | Test |
|-------|------|
| `FlashcardServiceTest` | Adapt `createCard_savesCardWithFsrsInitAndDeckTag`, `createCard_acceptsBothDeckAndNormalTags`, `createCard_missingDeckTag_throws422` — update mock from global uniqueness check to tag-scoped check. |
| `FlashcardServiceTest` | Rewrite `createCard_duplicate_throwsConflict` — verify same-deck hard conflict with tag name in error. |
| `FlashcardServiceTest` | **New**: `createCard_crossDeckConflict_returnsSoftWarning` — cross-deck conflict allows creation. |
| `FlashcardServiceTest` | **New**: `updateCard_frontConflict_throws422` and `updateCard_tagConflict_throws422`. |
| `FlashcardControllerTest` | Adapt `addCard_duplicate_returnsConflict` — updated error message. |
| `FlashcardControllerTest` | **New**: `checkCard_returnsConflictTags` — test `POST /api/cards/check`. |
| `ReviewServiceTest` | All ~8 tests constructing `new ReviewStats(...)` need `totalNewCards` parameter added. |
| `ReviewControllerTest` | `startReview_includesPreviewField`, `nextReview_includesPreviewField` — add `totalNewCards` to mock. |

### Frontend unit tests to adapt / add

| Scope | Test |
|-------|------|
| `reviewTypes.ts` | Add `totalNewCards: number` to `ReviewStats` interface. |
| `DeckPicker.test.tsx` | Add `totalNewCards` to mock fetch data; assert estimated days display. |
| `StatsBar.test.tsx` | Add `totalNewCards` to mock stats. |
| `CompletePage.test.tsx` | Add `totalNewCards` to mock stats. |
| `ReviewPage.tsx` | Add `totalNewCards: 0` to initial stats state. |

### E2E tests

| Scope | Test |
|-------|------|
| `FlashcardIT` | No change needed (no duplicate scenario). |
| `ManagePageIT` | No change needed (no duplicate scenario). |
| `FlashcardBatchIT` | No change needed (import already tag-scoped). |
| `ReviewIT` | No structural change needed (stats are rendered, not asserted in detail). |

### Prior art

- `FlashcardControllerTest.addCard_duplicate_returnsConflict` — existing pattern for testing 422 with mock service throw.
- `ReviewServiceTest.computeReviewStats_*` — existing pattern for ReviewStats assertions.

## Out of Scope

- Precise FSRS simulation: estimating when all cards in a Deck reach mastery (stability threshold across all cards) requires per-card future scheduling simulation and is deferred to a separate effort.
- UI for the soft-conflict confirmation dialog: the frontend modal that prompts user confirmation for cross-deck conflicts is defined here but implementation details belong to the frontend issue ticket.
- Any changes to the `sessions` or `conversation` domain.

## Further Notes

- The existing CSV import already enforces `(front, tag)` uniqueness via `findExistingFrontsByTag`. No change needed there.
- The `Tag` entity's Many-to-Many relationship with `Card` stays unchanged. Cards can still belong to multiple Decks.
- README.md should be updated with user-facing card usage rules (a new section "闪卡使用规则" under 核心模块).
- CONTEXT.md should be updated with a "Card uniqueness" glossary entry.
