# 01: 后端 — 实体字段变更 + computeTodayStart 重写 + 重复代码清理

**Status:** `ready-for-agent`

## Parent

[PRD: Review 统计时区统一 + 凌晨边界修复 + 重复代码清理](../PRD.md)

## What to build

一次后端变更覆盖以下所有 concerns：

### 实体字段：timezone → utcOffset

`UserPreferences` 的 `timezone`（String 列）更名为 `utcOffset`（Integer 列，`@Column name = "utc_offset"`）。getter/setter 同步重命名。默认值 `null` 时 fallback 到 `8`（UTC+8）。

### computeTodayStart() 重写 + 凌晨边界修复

`ReviewService.computeTodayStart()` 中：
- 用 `ZoneOffset.ofHours(utcOffset)` 替换原有的 `ZoneId.of(timezone)` 逻辑
- 新增边界判断：当 `currentHour < dayStartHour` 时，`today = today.minusDays(1)`

这样凌晨 0:00-5:59 复习的卡片不会落入"真空地带"（之前 `todayStart` 指向当天 06:00 但当前时间还未到）。

### 删除重复代码

- 删除 `ReviewController.computeTodayStart()` 整个方法（与 Service 层完全重复的 12 行）
- 删除 `ReviewController.getStats()` 中 mode 为空时的手动 stats 计算代码块。`getStats()` 统一调 `reviewService.computeReviewStats()`，空 mode 传入 `"STANDARD"` 作为 fallback
- `ReviewController.getPreferences()` / `savePreferences()` 中 JSON key 从 `timezone` 改为 `utcOffset`

### 数据库迁移

`DataInitializer.run()` 末尾新增：`ALTER TABLE user_preferences DROP COLUMN IF EXISTS timezone`。旧 String 列不再映射，新 Integer 列由 Hibernate `ddl-auto: update` 自动添加。

### 测试

- `ReviewServiceTest`：`defaultPreferences()` 中加 `setUtcOffset(8)`；`computeReviewStats_*` 测试改用 `ArgumentCaptor<Instant>` 捕获 `todayStart` 并断言 `isBefore(NOW)` + 小时 = `dayStartHour`
- `ReviewServiceTest` **新增** 3 个 `computeTodayStart` 边界测试：
  1. 正常时段（hour ≥ dayStartHour）→ `todayStart` 为今天
  2. 凌晨窗口（hour < dayStartHour）→ `todayStart` 为昨天 + dayStartHour
  3. `dayStartHour=0` → 不触发 `minusDays`
- `ReviewControllerTest`：`getPreferences_*` 测试加 `setUtcOffset(8)` 和 `jsonPath("$.utcOffset")` 断言
- `UserPreferencesServiceTest`：`save_updatesPreferences` 中 `setTimezone("America/New_York")` → `setUtcOffset(-5)`，对应断言

## Acceptance criteria

- [ ] `mvn compile` 编译通过
- [ ] `mvn test` 全部后端单元测试通过
- [ ] `computeTodayStart()` 在 `dayStartHour=6, utcOffset=8` 且当前时间为凌晨 5:30 时，`todayStart` 指向昨天 06:00 而非今天 06:00
- [ ] `ReviewController.getStats()` mode 为空时正确调用 `reviewService.computeReviewStats(deckId, "STANDARD", userId)`
- [ ] 旧 `timezone` 列在 `user_preferences` 表中已删除
- [ ] `GET /api/user/preferences` 返回 `utcOffset` 而非 `timezone`
- [ ] `PUT /api/user/preferences` 接受 `utcOffset` 而非 `timezone`

## Blocked by

None - can start immediately.
