# 02: UserPreferences 实体 + getDecks + getStats API

## Parent

[PRD: 闪卡复习功能](../PRD.md)

## What to build

新建 `UserPreferences` JPA 实体（继承 BaseEntity，字段：userId、newCardDailyLimit=20、dayStartHour=6、timezone、lastDeckId、lastMode），与 User 一对一。新建 UserPreferencesRepository 和 UserPreferencesService（get/save）。在 ReviewController 中新增两个 GET 端点：`GET /api/review/decks`（返回用户的 Deck 标签列表及卡片数）和 `GET /api/review/stats`（返回今日复习统计，含 nextDueAt）。stats 的"今日"边界由 UserPreferences 的 dayStartHour 和 timezone 计算。

## Acceptance criteria

- [ ] `UserPreferences` 实体：继承 BaseEntity，字段 userId(unique)、newCardDailyLimit(default 20)、dayStartHour(default 6)、timezone、lastDeckId、lastMode
- [ ] `UserPreferencesRepository`：`findByUserId(String userId)` 返回 Optional
- [ ] `UserPreferencesService`：`get(userId)` 返回 preferences（不存在则创建默认值并 save），`save(preferences)` 更新
- [ ] `GET /api/review/decks`：返回 `[{ "id": "uuid", "name": "Daily English", "type": "deck", "cardCount": 45 }]`
- [ ] `GET /api/review/stats?deckId=X&mode=STANDARD&limit=20`：返回 `{ "reviewedToday": 12, "remaining": 5, "learnedToday": 3, "dailyLimit": 20, "nextDueAt": "2025-06-05T06:00:00Z" }`
- [ ] `reviewedToday`：今日 `firstReviewDate >= 今日开始时刻` 的卡片计数（含所有评分，不区分新卡/旧卡）
- [ ] `learnedToday`：今日 `firstReviewDate >= 今日开始时刻` 且为非 AGAIN 评分的卡片计数（仅统计首次学习）
- [ ] `remaining`：`cardState != 0 AND due <= now AND deckId = ?` 的卡片计数
- [ ] `nextDueAt`：Deck 中最早 `due > now` 的时间，如果全为 null 则返回 null
- [ ] 今日开始时刻 = `dayStartHour` 在 `timezone` 时区下转换为 UTC 的 LocalDateTime
- [ ] Deck 过滤：仅返回 `Tag.type = "deck"` 的标签

## Blocked by

None — can start immediately. (独立于 #01，不依赖 Card 字段变更)
