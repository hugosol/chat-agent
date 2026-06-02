# 02: 集成 CorrectionSidebar 到聊天页面 + app.js 适配 + style.css 清理

**Status**: `ready-for-agent`

## Parent

[PRD：Chat 页面 React 渐进迁移 —— Phase 1: CorrectionSidebar](../PRD.md)

## What to build

将 Issue 01 产出的 CorrectionSidebar 组件接入聊天页面，替换 app.js 中约 80 行 vanilla JS sidebar 逻辑，并清理 style.css 中的旧全局样式。

这是本次迁移的核心集成切片——侧边栏所有权从 vanilla JS 完全转移到 React。

**correction-sidebar-entry.tsx**：完善为带命令式 API 的入口。`mountCorrectionSidebar()` 返回 `CorrectionSidebarAPI` 对象：

```typescript
interface CorrectionSidebarAPI {
  addCorrection(c: CorrectionData): void;
  clear(): void;
  getCount(): number;
}
```

Entry 内部使用 `useState` 管理 `corrections` 数组和 `collapsed` 状态。`addCorrection` 通过命令式回调 push 到数组，触发 React 重新渲染。`clear()` 重置为空数组。`getCount()` 返回 `corrections.length`。

**index.html** 改动：
- 将 `<div id="correctionSidebar" ...>` 及其所有子元素（7 行）替换为 `<div id="correction-sidebar-root"></div>`
- 将 `<button id="correctionSidebarToggle" ...>` 及其子元素（3 行）移除——浮动 Badge 由 React 组件统一渲染
- 移除 `<template id="correctionSidebarItemTemplate">`（10 行）——不再使用
- 在 `<link>` / `<script>` 区域添加 `correction-sidebar-bundle.css` 和 `correction-sidebar-bundle.js` 的加载
- 在 `<script>` 区域添加 mount 脚本（参照 Header 的 `_mountHeader()` 模式）

**app.js** 改动：
- 移除 `var correctionCount = 0`
- 从 `els` 对象中移除 6 个 sidebar 相关引用（`correctionSidebar`、`correctionSidebarContent`、`correctionSidebarToggle`、`correctionSidebarClose`、`correctionBadge`、`correctionBadgeHeader`）
- 移除函数：`addCorrectionSidebarItem()`、`updateCorrectionBadge()`、`toggleCorrectionSidebar()`
- 移除 sidebar 相关事件监听器（3 行）
- 在 `handleCorrectionResult()` 的消息气泡逻辑之后，增加 `sidebar.addCorrection(c)` 调用（保持已有 correction bubble 渲染代码不变）
- 在 `handleSessionResumed()` 的消息重建逻辑之后，增加 `sidebar.clear()` + 逐条 `addCorrection(c)` 调用
- `SESSION_STARTED` handler：将 `correctionCount = 0` + `innerHTML` 重置替换为 `sidebar.clear()`
- `newSessionBtn` click handler：将 `correctionCount = 0` + `innerHTML` 重置替换为 `sidebar.clear()`
- 移除 `escapeHtml()` 函数定义（`app.js:440-445`）——sidebar 是唯一调用方，`correction-bubble` 构建本身已用 `innerHTML` 赋值而不调用该函数

**关于 `speakText()`**：不在本 Issue 范围内移除。`speakText()` 仍被 TTS 按钮使用（`app.js:314`），其重复定义将在 Phase 2（WebSocket 服务层抽取）时随 `shared/tts.ts` 全局暴露一并处理。

**style.css** 改动：移除约 80 行 correction sidebar 相关全局样式规则。具体选择器清单：
- `.correction-sidebar`（absolute + width + z-index）
- `.correction-sidebar.collapsed`
- `.correction-sidebar-header`
- `.correction-sidebar-header button` / `:hover`
- `.correction-sidebar-toggle-btn` / `:hover` / `.hidden`
- `#correctionBadge, #correctionBadgeHeader`
- `.correction-sidebar-content`
- `.correction-sidebar-empty`
- `.correction-item`
- `.correction-type`
- `.correction-detail`
- `.correction-original`
- `.correction-arrow`
- `.correction-corrected`
- `.correction-explanation`
- `@media (max-width: 700px) .correction-sidebar`

**保留不变**：`.correction-bubble` 及相关样式（约 16 行，`style.css:167-182`）——属于消息渲染管道，非 sidebar 范围。

## Acceptance criteria

- [ ] `mvn compile` 成功
- [ ] `index.html` 中 sidebar 静态 HTML 被 `<div id="correction-sidebar-root">` 替换
- [ ] `index.html` 中 `<template id="correctionSidebarItemTemplate">` 已移除
- [ ] `index.html` 正确加载 `correction-sidebar-bundle.css` 和 `correction-sidebar-bundle.js`
- [ ] `app.js` 中以下已移除：`correctionCount` 变量、6 个 sidebar els 引用、`addCorrectionSidebarItem()`、`updateCorrectionBadge()`、`toggleCorrectionSidebar()`、sidebar 事件监听器、`escapeHtml()` 函数
- [ ] `handleCorrectionResult()` 仍正常创建 correction bubble 在聊天流中，并额外调用 `sidebar.addCorrection(c)`
- [ ] `style.css` 中移除约 80 行 sidebar 全局样式，`.correction-bubble` 样式保留不变
- [ ] 手动验证清单（以下功能路径应在手动测试中验证，E2E 在 Issue 03 后恢复覆盖）：
  - [ ] 开始会话 → 发送消息 → 收到纠错 → Badge 出现并显示正确计数 → 展开/收起侧边栏正常
  - [ ] 多轮对话 → Badge 计数递增正确
  - [ ] 结束会话 → 查看报告 → 点 "New Session" → sidebar 清空 → Badge 隐藏
  - [ ] 刷新页面 → RESUME_SESSION → sidebar 恢复所有历史纠错 → 计数正确
  - [ ] Token bar 更新正常（不受影响）
  - [ ] 模式选择下拉框 → 开始/结束会话按钮正常（不受影响）
  - [ ] 文本输入 → Enter 发送正常（不受影响）
  - [ ] TTS 🔊 按钮正常（不受影响）

## Blocked by

- [01: CorrectionSidebar 组件 + 多入口构建 + 单元测试](./01-correction-sidebar-component-build-test.md)
