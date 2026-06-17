Status: ready-for-agent

# 卡片背面增强数据自动展示 + 占位符 + 分割线

## Parent

PRD: `.scratch/enhance-movie-ui-optimization/PRD.md` — 改动项 #2

## What to build

修复增强数据从后端到前端的完整数据通路，使 Learner 翻到已增强过的卡片背面时自动看到电影台词和词源内容，且信息层次清晰。

### 后端行为变更

`ReviewService.buildEnhancementMap()` 的语义从"仅返回成功的增强数据"改为"返回增强记录的存在性 + 成功数据"：

- 查询 `card_enhancements` 表，检查是否有**任何**记录（不再过滤 `status=SUCCESS`）。
- 无任何记录 → 返回 `null`（前端据此显示放大镜按钮，由 Slice 3 实现）。
- 有记录 → 返回 map；`movieQuote` 和 `etymology` 字段仅在对应记录 `status=SUCCESS` 时填充实际数据，否则为 `null`。

`ReviewController.cardToMap()` 不变——继续将 `buildEnhancementMap()` 的返回值塞入 `enhancement` 字段。

### 前端数据流修复

`ReviewPage` 当前存在 bug：未将 `card.enhancement` 传递给 `CardDisplay` 的 `enhancement` prop，导致前端永远收到 null。需补充该 prop 传递。

### 前端渲染逻辑

`CardDisplay` 的渲染规则（覆盖 `enhancement != null` 分支）：

```
释义区（卡片背面文本）
  ↓ <hr> 分割线
电影台词区：
  有数据 → 片名 + 时间戳 + 台词 + 场景摘要
  无数据 → 【暂无电影台词数据】
  ↓ <hr> 分割线
词源区：
  有数据 → 词源文本
  无数据 → 【暂无词源数据】
不显示放大镜按钮（交给 Slice 3 处理 enhancement == null 分支）
```

## Acceptance criteria

- [ ] 翻到已增强成功的卡片背面，自动显示电影台词和词源内容（无需点击按钮）
- [ ] 翻到增强过但电影台词查询失败的卡片背面，电影台词区显示 `【暂无电影台词数据】` 占位符
- [ ] 翻到增强过但词源查询失败的卡片背面，词源区显示 `【暂无词源数据】` 占位符
- [ ] 释义区与电影台词区之间有 `<hr>` 分割线
- [ ] 电影台词区与词源区之间有 `<hr>` 分割线
- [ ] 翻到从未增强过的卡片背面，不显示增强区域（显示放大镜按钮——由 Slice 3 实现）
- [ ] `ReviewServiceTest`：`buildEnhancementMap_returnsNullWhenNoRecords`、`buildEnhancementMap_returnsPartialWhenOnlySubtitle`、`buildEnhancementMap_returnsPartialWhenOnlyEtymology`、`buildEnhancementMap_handlesFailedStatus`、`buildEnhancementMap_returnsNullWhenAllFailed`
- [ ] `CardDisplay.test.tsx`：覆盖 enhancement prop 有/无数据时的渲染分支、占位符显示、分割线存在性

## Blocked by

None — 可立即开始
