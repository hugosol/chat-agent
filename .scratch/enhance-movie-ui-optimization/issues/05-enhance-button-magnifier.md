Status: ready-for-agent

# Enhance 按钮改为放大镜图标 + 确认弹窗 + Loading

## Parent

PRD: `.scratch/enhance-movie-ui-optimization/PRD.md` — 改动项 #3

## What to build

将卡片背面的"Card Enhance"文字按钮改为 🔍 放大镜图标，增加确认弹窗和 loading 反馈，防止 Learner 误触。

### 前端改动

`CardDisplay` 中 enhancement == null（无任何 `CardEnhancement` 记录）时的渲染：

- 显示 🔍 放大镜图标（替代 "Card Enhance" 文字按钮）。
- 点击放大镜 → 弹出内联确认弹窗，文案"是否获取更多信息？"。
- 确认 → 执行 `handleEnhance()`：卡片背面覆盖 loading spinner，发起 API 请求，返回后自动展示增强数据（由 Slice 2 的渲染逻辑接管）。
- 取消 → 关闭弹窗，不调 API。

### 范围限定

- 按钮显示条件严格为 `enhancement == null`（语义由 Slice 2 保证）。
- **暂不支持重试**：有记录但某类型失败（enhancement != null 但有 null 字段）时，显示占位符而非重试按钮。重试逻辑属于后续迭代范围。
- 确认弹窗使用内联 UI 而非 `window.confirm`，保持与现有 Modal 模式一致。

## Acceptance criteria

- [ ] 从未增强过的卡片背面显示 🔍 放大镜图标，不显示 "Card Enhance" 文字
- [ ] 点击放大镜图标弹出确认弹窗，文案"是否获取更多信息？"
- [ ] 点击弹窗中的"确认"后，卡片背面显示 loading spinner，放大镜消失
- [ ] Loading 期间弹窗关闭，不可重复触发
- [ ] 增强数据加载完毕后自动展示（电影台词 + 词源或占位符）
- [ ] 点击弹窗中的"取消"后，弹窗关闭，不发起 API 请求，放大镜保持可见
- [ ] 已增强过的卡片（有记录，无论成功或失败）背面不显示放大镜图标
- [ ] `CardDisplay.test.tsx`（扩展）：放大镜渲染、确认弹窗交互（确认/取消）、loading 分支

## Blocked by

- `02-enhancement-auto-display.md` — 依赖其修复的 enhancement prop 传递和新的 `enhancement == null` 语义
