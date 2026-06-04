# 06: CardDisplay + RatingButtons 组件

## Parent

[PRD: 闪卡复习功能](../PRD.md)

## What to build

实现复习页面的两个核心 UI 组件。`CardDisplay`：显示卡片正面（Front），点击卡片区域翻面，显示背面（Back），翻面后 Front 和 Back 旁各出现 🔊 TTS 按钮（仅文本含英文时显示）。`RatingButtons`：四个等宽按钮（Again 红/Hard 橙/Good 绿/Easy 蓝），翻面后才出现，点击触发 `onRate(rating)` 回调。两个组件均为纯展示组件，不包含 API 调用逻辑。

## Acceptance criteria

- [ ] `CardDisplay` 组件：初始只显示 Front，大字体居中，卡片区域可点击
- [ ] 点击卡片区域后显示 Back（翻面动画：CSS transition 或简单显示切换）
- [ ] 翻面后 Front 和 Back 旁各出现 🔊 按钮，点击调用 `speakText(text)`
- [ ] TTS 按钮仅当 `englishOnly(text)` 返回非空字符串时显示
- [ ] `RatingButtons` 组件：四个等宽按钮，翻面后才出现
- [ ] Again 按钮：红色 `#e74c3c`，文字 "Again"
- [ ] Hard 按钮：橙色 `#e67e22`，文字 "Hard"
- [ ] Good 按钮：绿色 `#27ae60`，文字 "Good"
- [ ] Easy 按钮：蓝色 `#3498db`，文字 "Easy"
- [ ] 桌面端 hover 时按钮有视觉反馈；移动端使用 `@media (hover: hover)` 包裹 hover 样式（遵循 frontend-notes §2.1 模式）
- [ ] 所有交互元素添加 `data-testid`（`card-front`、`card-back`、`flip-card-btn`、`tts-btn-front`、`tts-btn-back`、`rating-again`、`rating-hard`、`rating-good`、`rating-easy`）
- [ ] CardDisplay Vitest 测试：翻面前后 DOM 变化、TTS 按钮条件渲染
- [ ] RatingButtons Vitest 测试：四个按钮渲染、颜色、点击回调

## Blocked by

- #01: rateCard API（RatingButtons 的 onRate 回调契约需要和 rateCard API 匹配，但组件本身可以独立开发——用 mock callback 测试即可）

Note: 虽然标记依赖 #01，但组件开发可用 mock `onRate` callback 完全独立进行，不需要真实 API。
