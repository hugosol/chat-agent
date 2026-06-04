# 05: 复习页面脚手架 + DeckPicker 组件

## Parent

[PRD: 闪卡复习功能](../PRD.md)

## What to build

新建复习页面的前端基础设施：`static/review/index.html`（独立 HTML 入口，含 viewport meta 和 React CDN）、Vite Library Mode 构建配置 `vite.config.review.ts`（输出 `review-bundle.js` + `review-bundle.css` 到 `static/shared/`）、React 入口 `review-entry.tsx`（IIFE 挂载到 `window.ChatAgent.mountReviewApp`）。实现 DeckPicker 组件：Deck 列表（GET /api/review/decks）、模式选择器（4 个 radio）、每日新卡上限输入框（仅 STANDARD/NEW_ONLY 模式可见）、开始按钮。纯前端 `useState` 管理三阶段状态机。记住上次选择的 Deck 和模式（读写 UserPreferences 的 lastDeckId/lastMode）。

## Acceptance criteria

- [ ] `static/review/index.html`：独立 HTML，包含 `<div id="review-root">`、viewport meta、React CDN、review-bundle 加载
- [ ] `vite.config.review.ts`：IIFE 格式、React externalized、CSS Modules camelCaseOnly、`emptyOutDir: false`
- [ ] `review-entry.tsx`：调用 `ChatAgent.mountReviewApp()` 渲染 `<ReviewApp>` 到 `#review-root`
- [ ] `ReviewApp` 组件：三阶段状态机 `"deck-picker" | "reviewing" | "complete"`（useState）
- [ ] `DeckPicker` 组件：GET `/api/review/decks` 加载 Deck 列表，每项显示名称 + 卡片数
- [ ] `ModeSelector` 组件：4 个 radio（标准复习、仅复习、仅新卡、速通），每个带简短说明
- [ ] 选择 STANDARD 或 NEW_ONLY 模式时，展开每日新卡上限输入框（type=number, min=0, default=20）
- [ ] 选择仅复习或速通模式时，上限输入框隐藏
- [ ] "开始复习"按钮：未选 Deck 或未选模式时 disabled
- [ ] 开始复习前校验上限："已学新卡数 >= 上限"时弹出确认对话框，确认后继续但不引入新卡
- [ ] 上次选择的 Deck 和模式从 UserPreferences 读取并自动回填（GET preferences），未设置过则空白
- [ ] DeckPicker Vitest 单元测试：Deck 列表渲染、模式切换、上限显示/隐藏、开始按钮状态

## Blocked by

- #02: UserPreferences + getDecks API（需要 /api/review/decks 和 preferences 读写）
- #03: getNextCard API（开始按钮点击时需要校验"是否还有卡片"）
