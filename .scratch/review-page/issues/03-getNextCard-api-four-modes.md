# 03: getNextCard API — 四种模式查询 + ReviewController

## Parent

[PRD: 闪卡复习功能](../PRD.md)

## What to build

在 ReviewService 中新增 `getNextCard(deckId, mode, limit, userId)` 方法，实现四种模式的查询逻辑：STANDARD（新卡+到期卡按 due ASC）、REVIEW_ONLY（仅已学到期卡）、NEW_ONLY（仅 cardState=0 按 createTime ASC，受上限约束）、CRAM（全 Deck 随机顺序，忽略 due）。在 ReviewController 中新增 `GET /api/review/next` 端点。每日新卡上限的判断依赖 UserPreferences 的 dayStartHour 和 timezone 计算"今日已学新卡数"。

## Acceptance criteria

- [ ] `ReviewService.getNextCard(deckId, mode, limit, userId)` 返回 `Optional<Card>`，四种模式逻辑正确
- [ ] STANDARD 模式：`WHERE deckId = ? AND due <= now ORDER BY due ASC LIMIT 1`，`cardState=0` 的新卡如果已达上限则排除
- [ ] REVIEW_ONLY 模式：`WHERE deckId = ? AND cardState != 0 AND due <= now ORDER BY due ASC LIMIT 1`
- [ ] NEW_ONLY 模式：`WHERE deckId = ? AND cardState = 0 ORDER BY createTime ASC LIMIT 1`，受上限约束
- [ ] CRAM 模式：`WHERE deckId = ? ORDER BY RAND() LIMIT 1`，不受 due、不受上限约束（注意 H2 的随机函数）
- [ ] 上限计算：`learnedToday`（今日 firstReviewDate >= 今日开始时刻的新卡数）≥ limit 时，STANDARD 和 NEW_ONLY 不再引入 cardState=0 的卡片
- [ ] `GET /api/review/next?deckId=X&mode=STANDARD&limit=20` 返回 `{ "card": {...} }` 或 `{ "card": null }`（无更多卡片）
- [ ] ReviewService 单元测试：mock Repository，验证四种模式的查询条件和上限逻辑

## Blocked by

- #01: rateCard API（需要 firstReviewDate 字段来计算 learnedToday）
- #02: UserPreferences API（需要 dayStartHour 和 timezone 来计算今日边界）
