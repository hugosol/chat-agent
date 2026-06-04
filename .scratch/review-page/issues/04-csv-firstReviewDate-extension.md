# 04: CSV 导入导出 — firstReviewDate 字段扩展

## Parent

[PRD: 闪卡复习功能](../PRD.md)

## What to build

扩展 CSV 导入导出功能，支持 `firstReviewDate` 字段。导出时在 CSV header 和数据行中新增该列（格式：ISO 日期 `2025-06-04`，空值不写）。导入时 `CardCsvParser` 按 header 名称匹配该列，不存在或为空则设为 null（向后兼容存量 CSV）。`FsrsFields` 记录新增 `Instant firstReviewDate` 字段。`CardBatchService` 导入时正确写入该字段。

## Acceptance criteria

- [ ] `CardCsvParser.FsrsFields` 记录新增 `Instant firstReviewDate` 字段
- [ ] `CardCsvParser` 解析逻辑：按 header 名称 `"firstReviewDate"` 匹配列（大小写不敏感），值解析为 `Instant`，列不存在或值为空则设为 null
- [ ] `CardBatchService` 导出 CSV header 包含 `firstReviewDate`，值格式为 `YYYY-MM-DD`
- [ ] `CardBatchService` 导入时正确写入 `firstReviewDate` 到 Card 实体
- [ ] `CardBatchService` 导入时如果 `firstReviewDate` 为 null 且 `cardState != 0`，使用 `createTime` 作为回填值
- [ ] 存量 CSV（不含 `firstReviewDate` 列）导入不报错，`firstReviewDate` 自动为 null
- [ ] 单元测试：CSV 含/不含 `firstReviewDate` 列两种场景

## Blocked by

- #01: rateCard API（需要 Card.firstReviewDate 字段已存在）
