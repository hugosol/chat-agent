# React 全量迁移：聊天页面纯 React 化

**Status**: ready-for-agent

## Problem Statement

聊天页面 (`index.html`) 当前处于 React 渐进迁移的中间状态——Header、CorrectionSidebar、MessageList、ChatInput、Footer 已迁入 React（Phase 1-3），但 StatusBar、Report Modal、Debug Panel、Flashcard Panel 仍由普通 JS（`app.js` 121 行 + `flashcard.js` 272 行）管理。两个技术栈共存带来维护问题：

- `ChatState` 中 `connectionStatus` 和 `sessionStatus` 分散在两个字段，StatusBar、ChatInput、Footer 需要组合多个字段才能判断显示状态
- `app.js` 的普通 JS 桥接（`ChatAgent.registerHandler()` + `VANILLA_TYPES`）存在 5 种消息类型的双重分发——既 dispatch React reducer，又调用普通 JS handler
- Portal 模式是为了渐进迁移而存在的折中方案，现已无必要
- `style.css` 616 行与各组件 CSS Module 并存，样式混乱

## Solution

完成 Phase 4 迁移：将 StatusBar、Report Modal、Debug Panel、Flashcard Panel 全部迁入 React，消除 `app.js` 和 `flashcard.js`，使聊天页面成为纯 React 原生页面。

核心架构变化：

1. **Portal 退出**：React 接管完整 DOM 树，`index.html` 只保留单一 `<div id="root">`
2. **状态统一**：`connectionStatus` + `sessionStatus` + status 相关字段合并为单一 `appStatus` 枚举（7 个值），StatusBar 通过 `deriveStatus()` 工具函数推导 UI 显示
3. **普通 JS 桥接消除**：`VANILLA_TYPES`、`vanillaHandlers`、`registerHandler` 全部删除，所有 WebSocket 消息仅通过 React reducer dispatch
4. **面板互斥统一**：Hamburger 菜单、Correction Sidebar、Debug Panel、Flashcard Panel 四个面板由 App 层 `activePanel` state 互斥管理
5. **Debug 独立管道**：Debug 日志使用模块级单例（发布-订阅模式），完全独立于 React Context 树，挂掉不影响主流程
6. **CSS 化整为零**：每个组件独立 CSS Module + 页面骨架 CSS Module + 共享控件 CSS Module，`style.css` 完全删除

## User Stories

1. As a learner, I want the status bar to always show the current session state (Connected / UserTurn / Processing / Warning / Error / Disconnected), so that I know whether the system is ready for my input
2. As a learner, I want a unified status lifecycle where Warning and Error persist on the status bar until the next state change, so that I don't miss transient notifications
3. As a learner, I want the session report modal to show up after ending a session with fluency score and error summary, so that I can review my performance
4. As a learner, I want the fluency score hidden when the report is degraded (fluencyScore < 0), so that I'm not shown meaningless scores on failed sessions
5. As a learner, I want the debug panel to show timestamped WebSocket/state/error logs independently of the main chat state, so that debugging information doesn't interfere with or crash the main UI
6. As a learner, I want the flashcard input panel (two-stage: front → back + tags) to work within the chat page using existing tag autocomplete components, so that I can create flashcards without leaving the conversation
7. As a learner, I want only one panel (hamburger menu, correction sidebar, debug panel, or flashcard panel) to be open at any time, so that the page layout stays clean and I'm not overwhelmed by overlapping panels
8. As a learner, I want my tab to auto-resume the session when I switch back to it (via Page Visibility API), so that my conversation state is restored after being away
9. As a developer, I want ChatState to have a single `appStatus` field instead of multiple scattered status-related fields, so that status transitions are predictable and easy to reason about
10. As a developer, I want no vanilla JS bridge code remaining on the chat page, so that there is a single code path for all WebSocket message handling
11. As a developer, I want each component to own its own CSS Module, so that styles are scoped, hash-encapsulated, and never conflict with each other
12. As a developer, I want `index.html` to be minimal (just `<div id="root">` + script tags), so that all DOM structure lives in React and is type-safe

## Implementation Decisions

### 1. Single React Root

Portal 模式退出。React 通过 `ReactDOM.createRoot(document.getElementById("root"))` 渲染整个页面结构。`index.html` 中所有预置 HTML 容器（`<header>`、`#messages`、`#statusBar`、`#textInputBar`、`<footer>`、`#reportModal`、`#flashcardPanel`、`#debugPanel` 等）全部删除。

### 2. Unified AppStatus (7 values)

`ChatState` 中 `connectionStatus`、`sessionStatus` 及所有 status 相关局部字段合并为单一 `appStatus: AppStatus`：

```ts
type AppStatus =
  | "Connecting"     // page load, WS handshake
  | "Connected"      // ws.onopen, no active practice session
  | "UserTurn"       // session active, ready for learner input ("Type your message")
  | "Processing"     // learner sent message, waiting for Agent reply ("Processing...")
  | "Warning"        // TOKEN_WARNING, shows dynamic payload ("Warning: {message}")
  | "Error"          // ERROR from server, shows dynamic payload ("Error: {message}")
  | "Disconnected";  // ws.onclose
```

"Speaking" 值被弃用——服务端 STATE_UPDATE "SPEAKING" 直接映射为 "UserTurn"。

Warning 和 Error 的动态消息文本存储在 `statusPayload: string | null` 中。这两种状态是持久的——显示在 StatusBar 上直到下一状态变化覆盖它们，不再使用 Toast。

`streamInProgress` 保留为独立字段——与 `appStatus` 正交，用于 MessageList 流式光标和 ChatInput 禁用逻辑。

### 3. ChatState & Action Redesign

新 `ChatState` 结构：

```ts
interface ChatState {
  appStatus: AppStatus;
  statusPayload: string | null;
  messages: Message[];
  corrections: CorrectionData[];
  tokenUsage: number;
  streamInProgress: boolean;
  report: SessionReport | null;
}
```

新增 Action: `SET_APP_STATUS` 用于统一设置 `appStatus` + `statusPayload`。

Server message → Action mapping:
- `ws.onopen` → `SET_APP_STATUS: "Connected"`
- `SESSION_STARTED` → `SESSION_STARTED` (reducer sets `appStatus: "UserTurn"`)
- `USER_MESSAGE_SENT` → `USER_MESSAGE_SENT` + `SET_APP_STATUS: "Processing"`
- `AGENT_STREAM_DELTA` → `AGENT_STREAM_DELTA` (unchanged)
- `AGENT_STREAM_END` → `AGENT_STREAM_END` (stops streaming; does NOT change appStatus)
- `STATE_UPDATE` → `STATE_UPDATE` (updates `tokenUsage`); serverState `"PROCESSING"` → `SET_APP_STATUS: "Processing"`; `"SPEAKING"` → `SET_APP_STATUS: "UserTurn"`
- `TOKEN_WARNING` → `SET_APP_STATUS: { appStatus: "Warning", statusPayload }` (no more Toast)
- `ERROR` → `SET_APP_STATUS: { appStatus: "Error", statusPayload }` (no more Toast)
- `SESSION_REPORT` → `SESSION_REPORT` (reducer: reset to initial + `appStatus: "Connected"` + store report)
- `SESSION_RESUMED` → `SESSION_RESUMED` (reducer sets `appStatus: "UserTurn"`)
- `ws.onclose` → `WS_CLOSED` (reducer: reset + `appStatus: "Disconnected"`) + `speechSynthesis.cancel()`

### 4. StatusBar Display Derivation

StatusBar 不从 `ChatState` 直接读入显示字符串。它使用工具函数 `deriveStatus(appStatus, statusPayload)` 推导 `{ message: string; type: string }`。7 个值各对应一个 CSS 类（`.connecting` / `.connected` / `.userturn` / `.processing` / `.warning` / `.error` / `.disconnected`），颜色值从旧 `style.css` 迁移。

共享工具 `isSessionActive(appStatus)`：`["UserTurn", "Processing", "Warning", "Error"]` 为活跃状态。ChatInput 和 Footer 用它替代 `sessionStatus === "active"`。

### 5. Panel Coordination via activePanel

App 层 `useState<PanelType>(null)` 管理四个面板互斥：

```ts
type PanelType = "menu" | "correction" | "debug" | "flashcard" | null;
```

`togglePanel(panel)`：`setActivePanel(prev => prev === panel ? null : panel)`。

- Header 接收 `activePanel` / `onTogglePanel` props（Manage 页面不传时回退本地 useState）
- CorrectionSidebar：`isOpen` 替代旧 `collapsed` prop
- DebugPanel 和 FlashcardPanel：`isOpen` prop + `onToggle()` callback

### 6. Debug Log — Module-Level Singleton

Debug 日志不使用 React Context。它使用模块级发布-订阅单例：

- `debugLog(message)` — 创建带时间戳的条目，通知所有监听器
- `subscribeDebug(fn)` — 注册监听器，返回取消订阅函数
- 所有操作由 try/catch 静默吞错误——Debug 挂掉不影响任何功能

ChatProvider 在 `onmessage`/`onopen`/`onclose` 中直接 `import { debugLog }` 调用（零 Context 依赖）。DebugPanel 组件通过 `subscribeDebug` 接收条目并管理自己的内部 `useState`。

### 7. Component Props Contracts

**Header**: 新增 `activePanel?: PanelType`、`onTogglePanel?: (panel: PanelType) => void`。Manage 页面不传这些 props → Header 回退本地 `useState`。

**CorrectionSidebar**: `collapsed: boolean` → `isOpen: boolean`。

**ChatInput / Footer**: 使用 `isSessionActive(appStatus)` 替代 `sessionStatus`。不再使用 Portal，直接返回 JSX DOM 结构。

**StatusBar**: 零 props，全部从 `useChatContext()` 读取。

**ReportModal**: 零 props，从 `useChatContext()` 读 `state.report`。`report === null` 时不渲染。`fluencyScore >= 0` 显示评分行。Close 按钮 dispatch 清 report。

**DebugPanel**: 接收 `isOpen: boolean` + `onToggle: () => void` props。内部管理 entries state，零 ChatContext 依赖。

**FlashcardPanel**: 接收 `isOpen: boolean` + `onToggle: () => void` props。本地管理两阶段 state（front/back/chips）。复用已有 `ChipInput` 和 `useTagAutocomplete` 组件。POST `/api/cards/add` 保存。零 ChatContext 依赖。

### 8. CSS Strategy

- **每个组件独立 CSS Module**：ChatInput.module.css、Footer.module.css、StatusBar.module.css、ReportModal.module.css、DebugPanel.module.css、FlashcardPanel.module.css
- **页面骨架**：ChatPage.module.css（body/#app/main 层布局）
- **共享控件**：shared/controls.module.css（`.btn`、`.btn-primary`、`.btn-danger`、`.controls`、`select`）
- **全局 reset**：保留 `/shared/base.css`
- **`style.css`**：完全删除（616 行）

### 9. Vite Build

- 删除 `vite.config.correction-sidebar.ts`
- `vite.config.chat.ts` 入口改为新 `chat-entry.tsx`
- `vite.config.ts` 保留产出 `header-bundle.js/.css` 给 Manage 页面
- `package.json` build 脚本: `"build": "vite build && vite build --config vite.config.chat.ts"`（2 步）

### 10. index.html Final Form

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
    <title>Chat Agent</title>
    <link rel="stylesheet" href="/shared/base.css">
    <link rel="stylesheet" href="/shared/chat-bundle.css">
</head>
<body>
    <div id="root"></div>
    <script src="/shared/react.production.min.js"></script>
    <script src="/shared/react-dom.production.min.js"></script>
    <script src="/shared/chat-bundle.js"></script>
</body>
</html>
```

删除 `app.js`、`flashcard.js`、`style.css`、所有预置 HTML 容器、两个 `<template>` 标签。

### 11. ChatProvider Cleanup

- 删除 `VANILLA_TYPES` Set、`vanillaHandlers` Set、`registerHandler()` 函数
- 删除 `window.ChatAgent.send` 全局暴露
- 删除 TOKEN_WARNING 和 ERROR 的 `showToast()` 调用
- 新增 `visibilitychange` 事件监听器 → 自动 RESUME_SESSION
- `ws.onclose` 中追加 `speechSynthesis.cancel()`
- 所有消息类型在 `onmessage` 中追加 `debugLog(...)` 调用

### 12. E2E Test Adaptation — Full Plan

本次迁移后 `index.html` 的 DOM 完全由 React 生成，所有预置 HTML 元素（ID 选择器）消失。E2E 测试必须将所有 ID/CSS 选择器替换为 `data-testid` 选择器。WebSocket 协议、WireMock Stubs、服务端行为均不变——**仅选择器层面改动**。

共 7 个 IT 测试类，6 个受影响（`ManagePageIT` 不受影响）。

#### 12.1 E2ETestBase.java — 共享 Helper 函数（10 处改动）

这是改动最集中的文件——所有测试类继承它使用的 Helper。

**`startSession(String mode)`** (lines 138-143):
```java
// Before:
page.locator("#modeSelect").selectOption(mode);
page.locator("#startBtn").click();
page.waitForFunction(
    "() => !document.getElementById('endBtn').disabled");
// After:
page.locator("[data-testid=\"mode-select\"]").selectOption(mode);
page.locator("[data-testid=\"start-btn\"]").click();
page.waitForFunction(
    "() => document.querySelector('[data-testid=\"end-btn\"]') && !document.querySelector('[data-testid=\"end-btn\"]').disabled");
```
> 注意：`waitForFunction` 中增加了 null guard（`&&`），因为 React 异步渲染可能导致元素尚未挂载。

**`sendMessage(String text)`** (lines 145-149):
```java
// Before:
page.locator("#textInput").fill(text);
page.locator("#sendTextBtn").click();
// After:
page.locator("[data-testid=\"text-input\"]").fill(text);
page.locator("[data-testid=\"send-btn\"]").click();
```

**`waitForAgentResponse()`** (lines 151-156):
```java
// Before (line 152):
page.waitForFunction("() => !document.getElementById('textInput').disabled");
// After:
page.waitForFunction(
    "() => document.querySelector('[data-testid=\"text-input\"]') && !document.querySelector('[data-testid=\"text-input\"]').disabled");
// Line 153-155 (correction bubble count check) — UNCHANGED (already uses data-testid)
page.waitForFunction(
    "expected => document.querySelectorAll('[data-testid=\"correction-bubble\"]').length >= expected",
    turnNumber);
```

**`endSession()`** (lines 158-162):
```java
// Before:
page.locator("#endBtn").click();
page.waitForFunction(
    "() => !document.getElementById('reportModal').classList.contains('hidden')");
// After:
page.locator("[data-testid=\"end-btn\"]").click();
page.waitForFunction(
    "() => document.querySelector('[data-testid=\"report-modal\"]') && document.querySelector('[data-testid=\"report-modal\"]').getAttribute('aria-hidden') !== 'true'");
```
> 由于 React 不使用 `.hidden` CSS class 控制显隐，改用 `aria-hidden` 属性判断。

**`isReportModalVisible()`** (lines 191-193):
```java
// Before:
return page.locator("#reportModal").isVisible();
// After:
return page.locator("[data-testid=\"report-modal\"]").isVisible();
```

**`getReportModalText()`** (lines 195-197):
```java
// Before:
return page.locator("#reportContent").innerText();
// After:
return page.locator("[data-testid=\"report-content\"]").innerText();
```

#### 12.2 ChatAgentSessionIT.java — 零直接改动（依赖 Helper）

该类完全通过继承的 Helper 函数交互，无自己的 DOM 选择器。Helper 修好后自动恢复。

#### 12.3 ChatAgentResumeIT.java — 零直接改动（依赖 Helper）

同上。`reloadPage()` 中的 `page.waitForSelector("[data-testid=\"message\"][data-role=\"user\"]")` 已经是 `data-testid`，不变。`localStorage` 操作不变。

#### 12.4 DailyTalkIT.java — 零直接改动（依赖 Helper）

同上。唯一的特殊操作是 `WireMockStubs.registerDailyTalkStubs()`（HTTP 层），不变。

#### 12.5 ChatAgentMemoryCueIT.java — 零直接改动（依赖 Helper）

同上。`Thread.sleep(500)` 等待异步 MemoryCue 生成不变。

#### 12.6 ChatAgentMemoryIT.java — 额外 3 处改动

该类在继承 Helper 之外，有两个直接 DOM 操作需要改：

**Line 56 — 关闭报告按钮**:
```java
// Before:
page.locator("#closeReportBtn").click();
// After:
page.locator("[data-testid=\"report-close-btn\"]").click();
```

**Lines 57-58 — 等待报告弹窗关闭**:
```java
// Before:
page.waitForFunction(
    "() => !document.getElementById('reportModal') || document.getElementById('reportModal').classList.contains('hidden')");
// After:
page.waitForFunction(
    "() => { const el = document.querySelector('[data-testid=\"report-modal\"]'); return !el || el.getAttribute('aria-hidden') === 'true'; }");
```
> 原逻辑：「元素不存在 或 拥有 `.hidden` class」→ 新逻辑：「元素不存在 或 `aria-hidden === 'true'`」。

**Line 66 — 会话 2 首轮等待输入框就绪**:
```java
// Before:
page.waitForFunction("() => !document.getElementById('textInput').disabled");
// After:
page.waitForFunction(
    "() => document.querySelector('[data-testid=\"text-input\"]') && !document.querySelector('[data-testid=\"text-input\"]').disabled");
```

#### 12.7 FlashcardIT.java — 全量选择器更新（16 处改动）

这是改动最多的测试类——全部 16 个选择器从 ID/CSS class 改为 `data-testid`。测试逻辑和断言（H2 数据验证）**完全不变**。

```java
// Line 29 — 打开面板
// Before: page.locator("#flashcardToggle").click();
// After:  page.locator("[data-testid=\"flashcard-toggle\"]").click();

// Line 30 — 等待面板可见
// Before: page.waitForSelector("#flashcardPanel:not(.collapsed)");
// After:  page.waitForSelector("[data-testid=\"flashcard-panel\"][aria-expanded=\"true\"]");

// Line 32 — 填写 front
// Before: page.locator("#flashcardFront").fill("yesterday");
// After:  page.locator("[data-testid=\"flashcard-front\"]").fill("yesterday");

// Line 33 — 点击继续
// Before: page.locator("#flashcardContinue").click();
// After:  page.locator("[data-testid=\"flashcard-continue\"]").click();

// Line 34 — 等待 stage2
// Before: page.waitForSelector("#flashcardStage2:not(.hidden)");
// After:  page.waitForSelector("[data-testid=\"flashcard-stage2\"]:not([aria-hidden=\"true\"])");

// Line 36 — 填写 back
// Before: page.locator("#flashcardBack").fill("昨天");
// After:  page.locator("[data-testid=\"flashcard-back\"]").fill("昨天");

// Line 38-39 — tag 输入打开建议
// Before:
//   page.locator("#flashcardTagInput").click();
//   page.waitForSelector("#flashcardTagSuggestions:not(.hidden)");
// After:
//   page.locator("[data-testid=\"flashcard-tag-input\"]").click();
//   page.waitForSelector("[data-testid=\"flashcard-tag-suggestions\"]:not([aria-hidden=\"true\"])");

// Line 40 — 点击建议项
// Before: page.locator(".tag-suggestion-item").first().click();
// After:  page.locator("[data-testid=\"tag-suggestion-item\"]").first().click();

// Line 41 — 等待 chip 出现
// Before: page.waitForSelector(".flashcard-chip");
// After:  page.waitForSelector("[data-testid=\"flashcard-chip\"]");

// Lines 43-45 — 第二个 tag 添加
// Before:
//   page.locator("#flashcardTagInput").click();
//   page.waitForSelector("#flashcardTagSuggestions:not(.hidden)");
//   page.locator(".tag-suggestion-item").last().click();
// After:
//   page.locator("[data-testid=\"flashcard-tag-input\"]").click();
//   page.waitForSelector("[data-testid=\"flashcard-tag-suggestions\"]:not([aria-hidden=\"true\"])");
//   page.locator("[data-testid=\"tag-suggestion-item\"]").last().click();

// Line 47 — 断言 chip 数量（CSS class → data-testid）
// Before: var chips = page.locator(".flashcard-chip");
// After:  var chips = page.locator("[data-testid=\"flashcard-chip\"]");

// Line 50 — 点击保存
// Before: page.locator("#flashcardSave").click();
// After:  page.locator("[data-testid=\"flashcard-save\"]").click();

// Line 52 — 等待 toast
// Before: page.waitForSelector("#flashcardToast:not(.hidden)");
// After:  page.waitForSelector("[data-testid=\"flashcard-toast\"]:not([aria-hidden=\"true\"])");

// Lines 54-55 — 等待面板关闭
// Before:
//   page.waitForFunction(
//       "() => document.getElementById('flashcardPanel').classList.contains('collapsed')");
// After:
//   page.waitForFunction(
//       "() => { const el = document.querySelector('[data-testid=\"flashcard-panel\"]'); return el && el.getAttribute('aria-expanded') === 'false'; }");
```

H2 断言（lines 57-74：`cardRepository.findAll()`、`card.getFront/Tags/Stability` 等）**完全不变**。

#### 12.8 ManagePageIT.java — 零改动

Manage 页面 (`manage/index.html`) 不在本次迁移范围，仍使用普通 HTML + `header-bundle.js`。所有 `#cardsTab`、`.manage-tab-btn`、`#newTagName` 等选择器不变。

#### 12.9 不受影响的组件

- **WireMockStubs.java** — HTTP 层拦截，服务端 Mock，零影响
- **application-e2e.yml** — 服务端配置（`permit-all-paths: [/**]`、WireMock base-url），零影响
- **`page.evaluate("localStorage.getItem('sessionId')")`** — 协议层，零影响
- **`Thread.sleep(500)`**（MemoryCueIT）— 服务端异步等待，零影响

#### 12.10 需要保留的已有 `data-testid`

以下 `data-testid` 属性在当前 React 组件中已存在，迁移后**必须保持不变**：

| 属性 | 所在组件 | 测试使用者 |
|------|---------|-----------|
| `[data-testid="message"][data-role="user"]` | MessageList | SessionIT, ResumeIT, MemoryIT 等 |
| `[data-testid="message"][data-role="agent"]` | MessageList | 同上 |
| `[data-testid="correction-bubble"]` | MessageList | 同上 |
| `[data-testid="message-content"]` | MessageList | 同上 |
| `[data-testid="correction-item"]` | CorrectionSidebar | 同上 |
| `[data-testid="correction-toggle"]` | CorrectionSidebar | SessionIT |
| `[data-testid="correction-sidebar"]` + `aria-expanded` | CorrectionSidebar | SessionIT |
| `[data-testid="correction-sidebar-close"]` | CorrectionSidebar | SessionIT |
| `[data-testid="nav-menu-btn"]` | Header | ManagePageIT |
| `[data-testid="nav-sidebar"]` + `aria-expanded` | Header | ManagePageIT |
| `[data-testid="nav-link"]` + `data-active` | Header | ManagePageIT |
| `[data-testid="nav-sidebar-close"]` | Header | ManagePageIT |

#### 12.11 需要新增的 `data-testid`

新组件和改造后的组件必须渲染以下 `data-testid`：

| 组件 | data-testid | 用途 |
|------|-----------|------|
| Footer | `mode-select` | 替代 `#modeSelect` |
| Footer | `start-btn` | 替代 `#startBtn` |
| Footer | `end-btn` | 替代 `#endBtn` |
| ChatInput | `text-input` | 替代 `#textInput` |
| ChatInput | `send-btn` | 替代 `#sendTextBtn` |
| ReportModal | `report-modal` | 替代 `#reportModal`；需支持 `aria-hidden` |
| ReportModal | `report-content` | 替代 `#reportContent` |
| ReportModal | `report-close-btn` | 替代 `#closeReportBtn` |
| FlashcardPanel | `flashcard-toggle` | 替代 `#flashcardToggle`（在 DebugPanel 中） |
| FlashcardPanel | `flashcard-panel` | 替代 `#flashcardPanel`；需支持 `aria-expanded` |
| FlashcardPanel | `flashcard-front` | 替代 `#flashcardFront` |
| FlashcardPanel | `flashcard-continue` | 替代 `#flashcardContinue` |
| FlashcardPanel | `flashcard-stage2` | 替代 `#flashcardStage2`；需支持 `aria-hidden` |
| FlashcardPanel | `flashcard-back` | 替代 `#flashcardBack` |
| FlashcardPanel | `flashcard-tag-input` | 替代 `#flashcardTagInput` |
| FlashcardPanel | `flashcard-tag-suggestions` | 替代 `#flashcardTagSuggestions`；需支持 `aria-hidden` |
| FlashcardPanel | `tag-suggestion-item` | 替代 `.tag-suggestion-item` |
| FlashcardPanel | `flashcard-chip` | 替代 `.flashcard-chip` |
| FlashcardPanel | `flashcard-save` | 替代 `#flashcardSave` |
| FlashcardPanel | `flashcard-toast` | 替代 `#flashcardToast`；需支持 `aria-hidden` |

> **共计 20 个新 `data-testid` + 12 个已保留 = 32 个总 data-testid。**

#### 12.12 CSS 类名变化注意事项

当前 E2E 多处 `waitForFunction` 依赖 `.hidden` / `.collapsed` CSS class。迁移后 React 组件不使用这些全局 class名，改用 `aria-hidden` / `aria-expanded` 属性判断可见性。这是一个**行为变更**：

| 当前 | 迁移后 |
|------|--------|
| `.classList.contains('hidden')` | `getAttribute('aria-hidden') === 'true'` |
| `:not(.hidden)` | `:not([aria-hidden="true"])` |
| `.classList.contains('collapsed')` | `getAttribute('aria-expanded') === 'false'` |
| `:not(.collapsed)` | `[aria-expanded="true"]` |

受影响的 `waitForFunction` 调用（均在 `E2ETestBase` 和 `ChatAgentMemoryIT`）：
- `endSession()` — report modal visible check
- `ChatAgentMemoryIT` line 57-58 — report modal dismiss check
- `FlashcardIT` line 30, 34, 54-55 — flashcard panel stage transitions

#### 12.13 null guard 模式

所有 `waitForFunction` 中从 `document.getElementById()` 改为 `document.querySelector()` 后，React 异步渲染可能导致元素尚未挂载。需要在所有条件判断前增加 null 检查：

```java
// 旧模式（ID 选择器，元素在 HTML 中预置始终存在）：
"() => !document.getElementById('textInput').disabled"

// 新模式（data-testid 选择器，React 异步渲染）：
"() => document.querySelector('[data-testid=\"text-input\"]') && !document.querySelector('[data-testid=\"text-input\"]').disabled"
```

受影响的 Helper 函数：`startSession()`、`waitForAgentResponse()`。已验证不受影响的：`waitForFunction` 中已有条件判断或元素必然存在的场景。

## Testing Decisions

### Test Philosophy

- 只测试外部行为，不测试实现细节
- Reducer 通过纯函数输入-输出测试
- 组件通过 Testing Library 渲染 + 用户交互模拟测试
- 多组件配合的正确性由 reducer 纯函数保证 + E2E 做集成验证

### Test Files

**需重写（3 个）**：
- `chatReducer.test.ts` — ChatState 形状全变，每个 reducer case 重写，新增 `SET_APP_STATUS` 测试
- `ChatInput.test.tsx` — `sessionStatus` → `appStatus`，删除 Portal 目标 DOM 创建
- `Footer.test.tsx` — 同上

**需修改（2 个）**：
- `Header.test.tsx` — 新增 `activePanel`/`onTogglePanel` props 测试
- `CorrectionSidebar.test.tsx` — `collapsed: boolean` → `isOpen: boolean`

**新建（4 个）**：
- `StatusBar.test.tsx` — 7 个 appStatus 值各自渲染正确文字+CSS 类；Warning/Error 验证 payload
- `ReportModal.test.tsx` — 空 report 不渲染；渲染各字段；fluencyScore 条件显示
- `DebugPanel.test.tsx` — subscribe 接收 entries；toggle/clear；unmount 取消订阅
- `FlashcardPanel.test.tsx` — 两阶段流程；tag 自动完成；save API 调用；422 错误处理

**不变（3 个）**：`MessageList.test.tsx`、`utils.test.ts`、`tts.test.ts`

## Out of Scope

- Manage 页面 (`manage/index.html`) 的 React 迁移
- Manage 页面相关测试 (`ManagePageIT`) 的修改
- 后端代码改动（Java 层零改动）
- WebSocket 协议改动
- 闪卡模块 REST API 改动（`FlashcardController`、`FlashcardService`）
- 报告生成逻辑改动（`ReportAgent`、`SessionComplete`）
- E2E 测试流程/断言逻辑改动（**仅选择器替换**，测试步骤和 H2 验证不变）
- 闪卡复习/调度功能（FSRS repeat）

## Further Notes

- 本次迁移是 ADR `frontend-react-migration.md` 的 Phase 4，也是聊天页面的最终阶段
- `app.js` 和 `flashcard.js` 的**所有**功能在 React 端有 1:1 替换，逐项对账完成（见 grilling session 功能审计表）
- 实施顺序：Phase 1 (状态层) → Phase 2 (ChatProvider) → Phase 3 (组件) → Phase 4 (入口+构建) → Phase 5 (测试) → Phase 6 (E2E 适配)
- 构建验证：`mvn test` (Java + Vitest)、`mvn verify` (E2E)
- E2E 适配专项：需同步修改 7 个文件（`E2ETestBase` + 5 个 IT 类 + 1 个全量替换），新增 20 个 `data-testid` 属性到 React 组件
- `waitForFunction` 中 `.hidden` / `.collapsed` CSS class 依赖需替换为 `aria-hidden` / `aria-expanded` 属性判断
- React 组件中所有 `waitForFunction` 的 `document.querySelector` 需加 null guard（`&&` 短路）防止 React 异步渲染竞态
