# 06: E2E Test Adaptation

**Status**: `ready-for-agent`

## Parent

[PRD：React 全量迁移：聊天页面纯 React 化](../PRD.md)

## What to build

本次迁移后 `index.html` 的 DOM 完全由 React 生成，所有预置 HTML 元素（ID 选择器）消失。E2E 测试必须将所有 ID/CSS 选择器替换为 `data-testid` 选择器。WebSocket 协议、WireMock Stubs、服务端行为均不变——**仅选择器层面改动**。

共 7 个 IT 测试类，需改动 5 个文件（`ManagePageIT` 不受影响，`ChatAgentSessionIT`、`ChatAgentResumeIT`、`DailyTalkIT`、`ChatAgentMemoryCueIT` 仅通过继承的 Helper 交互，Helper 修好后自动恢复——不需直接改动）。

### E2ETestBase.java — 共享 Helper 函数（10 处改动）

**`startSession(String mode)`**：
```java
// Before:
page.locator("#modeSelect").selectOption(mode);
page.locator("#startBtn").click();
page.waitForFunction("() => !document.getElementById('endBtn').disabled");
// After:
page.locator("[data-testid=\"mode-select\"]").selectOption(mode);
page.locator("[data-testid=\"start-btn\"]").click();
page.waitForFunction(
    "() => document.querySelector('[data-testid=\"end-btn\"]') && !document.querySelector('[data-testid=\"end-btn\"]').disabled");
```
> 增加了 null guard（`&&`），防止 React 异步渲染导致元素尚未挂载。

**`sendMessage(String text)`**：
```java
// Before:
page.locator("#textInput").fill(text);
page.locator("#sendTextBtn").click();
// After:
page.locator("[data-testid=\"text-input\"]").fill(text);
page.locator("[data-testid=\"send-btn\"]").click();
```

**`waitForAgentResponse()`**：
```java
// Before (line 152):
page.waitForFunction("() => !document.getElementById('textInput').disabled");
// After:
page.waitForFunction(
    "() => document.querySelector('[data-testid=\"text-input\"]') && !document.querySelector('[data-testid=\"text-input\"]').disabled");
// Line 153-155 (correction bubble count) — UNCHANGED (already uses data-testid)
```

**`endSession()`**：
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
> React 不使用 `.hidden` CSS class 控制显隐，改用 `aria-hidden` 属性判断。

**`isReportModalVisible()`**：
```java
// Before: return page.locator("#reportModal").isVisible();
// After:  return page.locator("[data-testid=\"report-modal\"]").isVisible();
```

**`getReportModalText()`**：
```java
// Before: return page.locator("#reportContent").innerText();
// After:  return page.locator("[data-testid=\"report-content\"]").innerText();
```

### ChatAgentMemoryIT.java — 额外 3 处改动

该类在继承 Helper 之外，有几个直接 DOM 操作需要改：

**关闭报告按钮**：
```java
// Before: page.locator("#closeReportBtn").click();
// After:  page.locator("[data-testid=\"report-close-btn\"]").click();
```

**等待报告弹窗关闭**：
```java
// Before:
page.waitForFunction(
    "() => !document.getElementById('reportModal') || document.getElementById('reportModal').classList.contains('hidden')");
// After:
page.waitForFunction(
    "() => { const el = document.querySelector('[data-testid=\"report-modal\"]'); return !el || el.getAttribute('aria-hidden') === 'true'; }");
```

**会话 2 首轮等待输入框就绪**：
```java
// Before: page.waitForFunction("() => !document.getElementById('textInput').disabled");
// After:  page.waitForFunction(
//     "() => document.querySelector('[data-testid=\"text-input\"]') && !document.querySelector('[data-testid=\"text-input\"]').disabled");
```

### FlashcardIT.java — 全量选择器更新（16 处改动）

全部 16 个选择器从 ID/CSS class 改为 `data-testid`。测试逻辑和断言（H2 数据验证）**完全不变**。

```java
// 打开面板
// Before: page.locator("#flashcardToggle").click();
// After:  page.locator("[data-testid=\"flashcard-toggle\"]").click();

// 等待面板可见
// Before: page.waitForSelector("#flashcardPanel:not(.collapsed)");
// After:  page.waitForSelector("[data-testid=\"flashcard-panel\"][aria-expanded=\"true\"]");

// 填写 front
// Before: page.locator("#flashcardFront").fill("yesterday");
// After:  page.locator("[data-testid=\"flashcard-front\"]").fill("yesterday");

// 点击继续
// Before: page.locator("#flashcardContinue").click();
// After:  page.locator("[data-testid=\"flashcard-continue\"]").click();

// 等待 stage2
// Before: page.waitForSelector("#flashcardStage2:not(.hidden)");
// After:  page.waitForSelector("[data-testid=\"flashcard-stage2\"]:not([aria-hidden=\"true\"])");

// 填写 back
// Before: page.locator("#flashcardBack").fill("昨天");
// After:  page.locator("[data-testid=\"flashcard-back\"]").fill("昨天");

// tag 输入打开建议
// Before: page.locator("#flashcardTagInput").click();
//         page.waitForSelector("#flashcardTagSuggestions:not(.hidden)");
// After:  page.locator("[data-testid=\"flashcard-tag-input\"]").click();
//         page.waitForSelector("[data-testid=\"flashcard-tag-suggestions\"]:not([aria-hidden=\"true\"])");

// 点击建议项
// Before: page.locator(".tag-suggestion-item").first().click();
// After:  page.locator("[data-testid=\"tag-suggestion-item\"]").first().click();

// 等待 chip 出现
// Before: page.waitForSelector(".flashcard-chip");
// After:  page.waitForSelector("[data-testid=\"flashcard-chip\"]");

// 第二个 tag 添加（同上模式）
// Assert chip 数量：var chips = page.locator("[data-testid=\"flashcard-chip\"]");

// 点击保存
// Before: page.locator("#flashcardSave").click();
// After:  page.locator("[data-testid=\"flashcard-save\"]").click();

// 等待 toast
// Before: page.waitForSelector("#flashcardToast:not(.hidden)");
// After:  page.waitForSelector("[data-testid=\"flashcard-toast\"]:not([aria-hidden=\"true\"])");

// 等待面板关闭
// Before: page.waitForFunction(
//     "() => document.getElementById('flashcardPanel').classList.contains('collapsed')");
// After:  page.waitForFunction(
//     "() => { const el = document.querySelector('[data-testid=\"flashcard-panel\"]'); return el && el.getAttribute('aria-expanded') === 'false'; }");
```

H2 断言（`cardRepository.findAll()`、`card.getFront/Tags/Stability` 等）**完全不变**。

### CSS 类名变化注意事项

当前 E2E 多处 `waitForFunction` 依赖 `.hidden` / `.collapsed` CSS class。迁移后 React 组件不使用这些全局 class 名，改用 `aria-hidden` / `aria-expanded` 属性判断可见性：

| 当前 | 迁移后 |
|------|--------|
| `.classList.contains('hidden')` | `getAttribute('aria-hidden') === 'true'` |
| `:not(.hidden)` | `:not([aria-hidden="true"])` |
| `.classList.contains('collapsed')` | `getAttribute('aria-expanded') === 'false'` |
| `:not(.collapsed)` | `[aria-expanded="true"]` |

### null guard 模式

所有 `waitForFunction` 中 `document.querySelector()` 需追加 null 检查（React 异步渲染可能导致元素尚未挂载）：

```java
// 旧模式（ID 选择器，元素在 HTML 中预置始终存在）：
"() => !document.getElementById('textInput').disabled"

// 新模式（data-testid 选择器，React 异步渲染）：
"() => document.querySelector('[data-testid=\"text-input\"]') && !document.querySelector('[data-testid=\"text-input\"]').disabled"
```

### 需保留的已有 data-testid

以下 `data-testid` 属性在 React 组件中已存在，迁移后**必须保持不变**：

| 属性 | 所在组件 |
|------|---------|
| `[data-testid="message"][data-role="user"]` | MessageList |
| `[data-testid="message"][data-role="agent"]` | MessageList |
| `[data-testid="correction-bubble"]` | MessageList |
| `[data-testid="message-content"]` | MessageList |
| `[data-testid="correction-item"]` | CorrectionSidebar |
| `[data-testid="correction-toggle"]` | CorrectionSidebar |
| `[data-testid="correction-sidebar"]` + `aria-expanded` | CorrectionSidebar |
| `[data-testid="correction-sidebar-close"]` | CorrectionSidebar |
| `[data-testid="nav-menu-btn"]` | Header |
| `[data-testid="nav-sidebar"]` + `aria-expanded` | Header |
| `[data-testid="nav-link"]` + `data-active` | Header |
| `[data-testid="nav-sidebar-close"]` | Header |

### 不受影响的文件

- **WireMockStubs.java** — HTTP 层拦截，零影响
- **application-e2e.yml** — 服务端配置，零影响
- **ManagePageIT.java** — Manage 页面不在迁移范围
- **ChatAgentSessionIT.java** — 零直接改动（依赖 Helper）
- **ChatAgentResumeIT.java** — 零直接改动（依赖 Helper）
- **DailyTalkIT.java** — 零直接改动（依赖 Helper）
- **ChatAgentMemoryCueIT.java** — 零直接改动（依赖 Helper）
- **`page.evaluate("localStorage.getItem('sessionId')")`** — 协议层，零影响

## Acceptance criteria

- [ ] `E2ETestBase.java`：全部 6 个 Helper 方法选择器更新（startSession / sendMessage / waitForAgentResponse / endSession / isReportModalVisible / getReportModalText）
- [ ] 所有 `waitForFunction` 中 `document.getElementById()` → `document.querySelector([data-testid])`，含 null guard
- [ ] `.hidden` → `aria-hidden`、`.collapsed` → `aria-expanded`（所有 `waitForFunction` 和 `waitForSelector`）
- [ ] `ChatAgentMemoryIT.java`：3 处选择器更新（close btn / waitForFunction / input ready）
- [ ] `FlashcardIT.java`：16 处选择器全量替换，H2 断言不变
- [ ] 已有 12 个 `data-testid` 保持不变
- [ ] `mvn verify` 全绿（全部 7 个 IT 类通过）
- [ ] E2E 截图保存在 `target/e2e-screenshots/`，可正常查看

## Blocked by

- [05: Entry Point + Panel Coordination + Build + Vanilla Removal](./05-entry-point-panel-coordination-build-vanilla-removal.md)
