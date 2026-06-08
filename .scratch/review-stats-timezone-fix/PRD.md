# PRD: Review 统计时区统一 + 凌晨边界修复 + 重复代码清理

**Status:** `ready-for-agent`

## Problem Statement

Learner 在 Review 流程中看到"今日已复习"和"今日已学新卡"两个统计数字存在以下问题：

1. **凌晨窗口统计错误**：当 Learner 在 `dayStartHour` 之前复习时（如 `dayStartHour=6`，凌晨 0:00-5:59），`computeTodayStart()` 计算出的是当天 06:00 的 UTC Instant——此时尚未到达。结果是 `reviewedToday=0`、`learnedToday=0`，凌晨复习的卡片被丢弃在两个复习日之间的"真空地带"。这不影响 `dayStartHour=0` 的用户，但对绝大多数使用默认 6 点的用户，只要在凌晨 0:00-5:59 复习就会触发。

2. **时区转换为 IANA ZoneId 字符串**：`UserPreferences.timezone` 存储 `"Asia/Shanghai"` 等 IANA 字符串，但前端是自由文本输入无校验，拼写错误时 `ZoneId.of()` 静默回退到系统默认时区且用户无感知。此外该字段实际只用于计算 UTC 偏移量，ZoneId 的 DST 等特性从未被利用。

3. **`computeTodayStart()` 重复实现**：完全相同的方法存在于 `ReviewController` 和 `ReviewService` 中各一份（12 行）。Controller 的 `getStats()` 在 mode 为空时还有一套近乎重复的 stats 计算内联代码，且 `remaining` 的计算与 Service 层不一致（裸 `countDueCardsByTagsId()` vs `computeRemaining()`）。

4. **测试漏洞**：所有 `computeReviewStats_*` 测试使用 `any(Instant.class)` 匹配 `todayStart`，不验证实际传给 repository 的时间点。`computeTodayStart()` 没有独立单元测试。凌晨边界 bug 因此从未被检测到。

## Solution

一次性三条战线：

| 问题 | 修复 | 关键改动 |
|------|------|---------|
| 凌晨统计错误 | `today` 回退一天 | `computeTodayStart()` 中 `if (hour < dayStartHour) today = today.minusDays(1)` |
| 时区字段冗余 | String → Integer，ZoneId → ZoneOffset | `UserPreferences.timezone` → `utcOffset`，`ZoneOffset.ofHours()` |
| 重复代码 | 删除 Controller 中的副本 | 移除 `ReviewController.computeTodayStart()` 和空 mode stats 路径 |

## User Stories

1. 作为一名 Learner，当我凌晨 5:30 复习卡片时，"今日已复习"显示从昨日 6:00 至今（含当前凌晨）的所有复习数，而非为 0。
2. 作为一名 Learner，当我在凌晨 5:30 学习新卡时，新卡每日上限正确判断已学数量——不会因 `learnedToday=0` 而无限出新卡。
3. 作为一名 Learner，在设置页面通过 +/- 按钮调整 UTC 偏移后，Review 统计立即使用新时区。
4. 作为一名 Learner，设置为 UTC+8 且 dayStartHour=6 时，6 月 8 日 10:00 看到的"今日"覆盖 6 月 8 日 06:00 至今的复习数量。
5. 作为一名 Learner，设置为 UTC+8 且 dayStartHour=6 时，6 月 9 日 02:00（熬夜）看到的"今日"覆盖 6 月 8 日 06:00 至今的复习数量——而非 6 月 9 日 06:00（尚未到来）。
6. 作为一名 Learner，首次登录时系统自动检测浏览器 UTC 偏移并保存，无需手动配置。
7. 作为一名 Developer，`computeTodayStart()` 在 Service 层有独立单元测试，覆盖正常时段、凌晨边界、dayStartHour=0 三种场景。
8. 作为一名 Developer，所有 `computeReviewStats_*` 测试验证传给 repository 的 `todayStart` 是合理值（早于当前时间，且小时 = dayStartHour）。

## Implementation Decisions

### 1. 时区字段：String → Integer

- `UserPreferences.timezone`（`String`）更名为 `utcOffset`（`Integer`），列名 `utc_offset`。
- 默认值：`null` 时 fallback 到 `8`（UTC+8），符合中文 Learner 预期。
- 旧列 `timezone` 通过 `DataInitializer` 执行 `ALTER TABLE user_preferences DROP COLUMN IF EXISTS timezone` 清理。已有 Learner 的 `utc_offset` 为 `null`，自动使用默认值 8，需手动重新设置才能反映其真实时区。
- `computeTodayStart()` 中 `ZoneId.of(timezone)` 改为 `ZoneOffset.ofHours(utcOffset)`；`ZonedDateTime.now(zoneId)` 改为 `Instant.now().atZone(offset)`。
- 放弃 DST 支持——该应用以中文用户为主，固定偏移量足够。

### 2. 凌晨边界修复

`computeTodayStart()` 中新增逻辑：

```
if (currentHourInZone < dayStartHour) {
    today = today.minusDays(1);
}
```

推理链（以 UTC+8、dayStartHour=6 为例）：

| 当前时间 | today | 修正前 todayStart | 修正后 todayStart | 覆盖范围 |
|---------|-------|------------------|------------------|---------|
| 6月8日 10:00 | 6月8日 | 6月8日 06:00 | 6月8日 06:00（不变） | 6月8日 06:00 至今 |
| 6月8日 05:30 | 6月8日 | 6月8日 06:00（未来） | **6月7日 06:00** | 6月7日 06:00 至今 |
| 6月9日 02:00 | 6月9日 | 6月9日 06:00（未来） | **6月8日 06:00** | 6月8日 06:00 至今 |

### 3. 删除重复代码

- **移除** `ReviewController.computeTodayStart()`（12 行）。
- **移除** `ReviewController.getStats()` 中 mode 为空时的手动 stats 计算块（第 101-117 行），改为统一调用 `reviewService.computeReviewStats(deckId, "STANDARD", userId)`。
- 注意：该空 mode 路径本身是死代码——前端 `DeckPicker` 永远携带 mode 参数（初始值 `"STANDARD"`），从未触发过。保留 `"STANDARD"` fallback 是防御性处理。

### 4. 前端时区输入 UI

- `SettingsPage`：自由文本 `<input>` 改为 `+`/`-` 按钮 + 数字输入框，范围 -12 到 +14。
- 字段名统一为 `utcOffset`（前端 `SettingsFields.utcOffset`，后端 JSON key `utcOffset`）。
- 前端校验：非整数或超出 -12~+14 范围时显示错误提示。

### 5. Header 自动检测

- `Intl.DateTimeFormat().resolvedOptions().timeZone`（返回 `"Asia/Shanghai"` 等 IANA 字符串）改为 `-(new Date().getTimezoneOffset() / 60)`（返回整数小时）。
- 向后兼容：`prefs.utcOffset != null` 时跳过（替代原来的 `prefs.timezone` 检查）。

### 6. API 契约变更

JSON key 全面从 `timezone` 改为 `utcOffset`：

- `GET /api/user/preferences` 返回字段 `utcOffset: 8 | null`
- `PUT /api/user/preferences` 接受 `utcOffset: 8`
- `ReviewStats` 相关端点（`/api/review/start`、`/api/review/next`、`/api/review/stats`）的 `reviewedToday`、`learnedToday`、`remaining` 字段不受影响。

### 7. 数据库 DDL

- `spring.jpa.hibernate.ddl-auto: update` 下，Hibernate 自动为 `user_preferences` 添加 `utc_offset INTEGER` 列。
- `DataInitializer.run()` 中执行 `ALTER TABLE user_preferences DROP COLUMN IF EXISTS timezone` 清理旧列。
- 已有行 `utc_offset` 为 `null` → 代码 fallback 到 `8`。

## Testing Decisions

### 测试原则

- `computeTodayStart()` 的单元测试直接构造 `UserPreferences` 对象并调用方法，不 mock repository。验证返回的 `Instant` 在合理时间范围内。
- `computeReviewStats_*` 测试使用 `ArgumentCaptor<Instant>` 捕获传给 repository 的 `todayStart`，验证其 `isBefore(NOW)` 且小时分量 = `dayStartHour`。
- 前端测试不在时区输入上耦合具体实现（如 `+`/`-` 按钮的内部 state），只验证点击后 input 值 +1/-1 且不越界。

### 需要修改的测试

**后端单元测试（4 个文件 + 3 个新用例）：**

| 文件 | 改动 |
|------|------|
| `ReviewServiceTest` | `defaultPreferences()` 加 `setUtcOffset(8)`；`computeReviewStats_*` 测试用 `ArgumentCaptor<Instant>` 捕获并验证 `todayStart` |
| `ReviewServiceTest`（新增） | 3 个 `computeTodayStart` 边界测试：① 正常时段（hour ≥ dayStartHour）② 凌晨窗口（hour < dayStartHour，验证 minusDays）③ dayStartHour=0（不触发回退） |
| `ReviewControllerTest` | `getPreferences_*` 测试：`new UserPreferences` 上设置 `utcOffset`，断言 `jsonPath("$.utcOffset")` |
| `UserPreferencesServiceTest` | `save_updatesPreferences` 中 `setTimezone("America/New_York")` → `setUtcOffset(-5)`，对应断言 |
| `DataInitializerTest` | 不受影响（JdbcTemplate 为 null 时迁移代码在 try-catch 中被吞掉，与现有迁移模式一致） |

**前端单元测试（2 个文件）：**

| 文件 | 改动 |
|------|------|
| `Header.test.tsx` | `timezone` → `utcOffset`；mock 返回 JSON key 和断言的 body key 同步更新 |
| `SettingsPage.test.tsx` | `mockPrefs.timezone: "Asia/Shanghai"` → `utcOffset: 8`；`settings-timezone` testid 更新；新增 +/- 按钮点击测试和范围边界测试 |

**E2E 测试：无需变更**

`SettingsPageIT` 不测试 timezone 字段，`ReviewIT` 仅验证 `stats-reviewed` 文本内容存在且不涉及边界计算。

## Out of Scope

- DST（夏令时）支持——固定 UTC 偏移量不支持自动切换，该应用以中文用户为主，影响极小。
- `dayStartHour` 的 0:00 前置偏移逻辑（即“如果当前时间 >= dayStartHour，todayStart 就是今天 6 点，而不是昨天 6 点后”）——当前修复只处理了 hour < dayStartHour 的 case，反向 scene 已经正确（hour ≥ dayStartHour 时 today = 当天日期）。
- 多时区用户同时使用同一部署实例——每个 Learner 独立设置自己的 `utcOffset`，方案已天然支持。
- `ReviewLog.reviewedAt` 的时区转换——`ReviewLog` 存储 `Instant`，统计时直接比较，不受时区影响。
- `Card.createTime` 的时区展示——前端 `new Date(isoString)` 已在 UTC 下正确解析。

## Further Notes

- 凌晨边界 bug 一直未被发现的原因有三个：(1) 默认 `dayStartHour=6`，绝大多数 Learner 在 6 点后复习不会触发；(2) 触发时 `reviewedToday=0` 显示为"新的一天还没开始复习"，用户不会怀疑是 bug；(3) 所有相关测试用 `any(Instant.class)` 跳过了 `todayStart` 的参数验证。
- 该方案与已完成的 `timezone-instant-cache` PRD 互补——那边解决了"时区从哪来"（自动检测 + 缓存），这边解决了"时区怎么用"（字段类型 + 边界逻辑 + 重复代码）。
