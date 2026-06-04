# 08: Header 导航 + 已有测试回归更新

## Parent

[PRD: 闪卡复习功能](../PRD.md)

## What to build

在 Header 组件（`Header.tsx` + `header-bundle.js`）的导航侧边栏中新增"📝 复习"链接，指向 `/review/`。更新三个已有 E2E 测试以适配 `firstReviewDate` 新字段和导航变化：`FlashcardIT` 新增 `firstReviewDate` 为 null 的断言；`FlashcardBatchIT` 的 CSV 数据新增 `firstReviewDate` 列并验证往返一致性；`ManagePageIT` 的导航链接计数从 2 更新为 3。

## Acceptance criteria

- [ ] Header 导航侧边栏新增 "📝 复习" 链接（`data-testid="nav-link"`），href 指向 `/review/`
- [ ] `FlashcardIT`：Card 断言新增 `card.getFirstReviewDate()` 为 null（新创建卡片未经复习）
- [ ] `FlashcardBatchIT`：CSV header 新增 `firstReviewDate` 列；创建的两张卡片 seed 数据加 `firstReviewDate`；导入后验证 `firstReviewDate` 正确还原
- [ ] `ManagePageIT.manageNavSidebar()`：导航链接 `data-testid="nav-link"` 数量从 2 改为 3
- [ ] `mvn test` 和 `mvn verify -Dtest=FlashcardIT,FlashcardBatchIT,ManagePageIT` 全部通过

## Blocked by

- #01: Card.firstReviewDate 字段（FlashcardIT 和 FlashcardBatchIT 的断言依赖该字段）
- #04: CSV 扩展（FlashcardBatchIT 的 CSV 内容变更依赖 #04 的导出实现）

Note: Header 导航变更可在 #01 和 #04 完成前开始——先加链接，测试更新等后端就绪后再做。
