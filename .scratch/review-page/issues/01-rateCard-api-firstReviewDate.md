# 01: rateCard API — Card.firstReviewDate + ReviewService + ReviewController

## Parent

[PRD: 闪卡复习功能](../PRD.md)

## What to build

Card 实体新增 `firstReviewDate` 字段（Instant，nullable），首次评分时写入当前日期。新建 ReviewService（纯 Service，含 `rateCard` 方法）和 ReviewController（`POST /api/review/rate` 端点）。评分请求只含 `cardId` 和 `rating`，服务端自行获取 `Instant.now()` 调用 `FsrsScheduler.repeat()`，更新 Card 全部 FSRS 字段并实时 `save()` 到数据库。响应返回更新后的 Card 数据（含 front/back/tags/FSRS 状态）和简要统计。

## Acceptance criteria

- [ ] `Card.java` 新增 `firstReviewDate` 字段（Instant, nullable），JPA 自动加列（`ddl-auto: update`）
- [ ] `ReviewService.rateCard(cardId, rating, now)` 方法：调用 `FsrsScheduler.repeat()`，首次评分写入 `firstReviewDate`，实时 `CardRepository.save()`
- [ ] `POST /api/review/rate` 端点：接收 `{ "cardId": "uuid", "rating": "GOOD" }`，返回 `{ "card": {...}, "stats": { "reviewedToday": N, "remaining": M, "learnedToday": X, "dailyLimit": Y } }`
- [ ] 评分后 Card 状态正确更新：cardState 从 0→1（Learning）、stability/difficulty 初始化、due 按 FSRS 学习步进计算
- [ ] `firstReviewDate` 仅在 `hasStability == false`（首次评分）时写入
- [ ] 认证走 JSESSIONID cookie（与 FlashcardController 一致），`/api/review/**` CSRF 豁免
- [ ] `FsrsSchedulerTest` 所有 12 个已有测试仍然通过
- [ ] ReviewService 单元测试：mock CardRepository，验证 rateCard 写入 firstReviewDate 和 FSRS 字段

## Blocked by

None — can start immediately.
