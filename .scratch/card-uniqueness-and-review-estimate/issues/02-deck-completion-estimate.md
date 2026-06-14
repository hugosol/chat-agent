# 02: 牌组新卡完成天数估算

**Status:** `ready-for-agent`

## Parent

[PRD: 卡片 Tag 级别防重 + 牌组完成时间估算](../PRD.md)

## What to build

在 DeckPicker 页面显示"按当前进度预计还需 X 天学完牌组中所有新卡"的估算。

**后端**: `ReviewStats` record 新增 `long totalNewCards` 字段，表示牌组中 `cardState = 0`（新卡）的卡片总数。CRAM 模式返回 `-1` 作为 sentinel（与现有 `remaining` 和 `learnedToday` 一致）。

**前端 DeckPicker**: 在"今日已学新卡: N"旁边追加显示"预计还需 X 天学完"。计算公式 `ceil(totalNewCards / learnedToday)`。当 `learnedToday = 0` 时显示"暂无数据"。

**不改的范围**: 精确 FSRS 仿真（遍历所有卡片的未来调度路径估算全部达到稳定掌握的时间）不在本次范围。

## Acceptance criteria

- [ ] `ReviewStats` record 包含 `totalNewCards` 字段
- [ ] STANDARD / REVIEW_ONLY / NEW_ONLY 模式下 `totalNewCards` 为牌组中 `cardState=0` 的卡片总数
- [ ] CRAM 模式下 `totalNewCards` 返回 `-1`
- [ ] DeckPicker 在选定牌组后显示"今日已学新卡: N，预计还需 X 天学完"
- [ ] 当 `learnedToday = 0` 时显示"暂无数据"
- [ ] CRAM 模式下不显示估算天数（sentinel `-1` 处理）
- [ ] 后端单元测试: `ReviewServiceTest` 全部 ~8 个 `computeReviewStats` 测试适配新字段
- [ ] 后端 Controller 测试: `ReviewControllerTest` 适配 `ReviewStats` 新字段
- [ ] 前端单元测试: `DeckPicker.test.tsx` 验证估算天数渲染；`StatsBar.test.tsx`、`CompletePage.test.tsx`、`ReviewPage.tsx` 适配 `totalNewCards`
- [ ] `mvn test` 全部通过

## Blocked by

None — can start immediately.
