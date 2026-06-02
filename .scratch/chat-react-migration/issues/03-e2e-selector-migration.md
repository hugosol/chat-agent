# 03: E2E 测试 data-testid 选择器迁移

**Status**: `ready-for-agent`

## Parent

[PRD：Chat 页面 React 渐进迁移 —— Phase 1: CorrectionSidebar](../PRD.md)

## What to build

将 E2E 测试中所有 correction sidebar 相关选择器从 CSS class/ID 迁移为 `data-testid` 属性选择器，确保 CSS Modules 哈希化后测试仍然可定位元素。

这是 Issue 02 的验证切片——Issue 02 完成后，旧的 CSS 选择器（如 `#correctionSidebar`、`.correction-item`）在 E2E 测试中将无法定位元素（sidebar 的 class 已被 CSS Modules 哈希化），需要更新为 Issue 01 中 React 组件定义的 `data-testid` 值。

**选择器迁移映射表**（完整列表，共 7 个映射）：

| 旧选择器 | 新选择器 | 涉及文件 |
|---------|---------|---------|
| `.correction-item` | `[data-testid="correction-item"]` | E2ETestBase.java |
| `#correctionSidebarToggle:not(.hidden)` | `[data-testid="correction-toggle"]`（元素不存在时自动隐藏，无需 `:not(.hidden)`） | ChatAgentSessionIT.java |
| `#correctionSidebarToggle` | `[data-testid="correction-toggle"]` | ChatAgentSessionIT.java |
| `#correctionSidebar.classList.contains('collapsed')` | `[data-testid="correction-sidebar"]` 的 `aria-expanded` 属性 | ChatAgentSessionIT.java |
| `#correctionSidebarClose` | `[data-testid="correction-sidebar-close"]` | ChatAgentSessionIT.java |

**注意**：以下选择器**不受影响**，无需修改：
- `.correction-bubble` —— 属于消息渲染管道，由 `app.js` 保留处理
- `.message.user` / `.message.agent` —— 非 sidebar 范围
- `#textInput` / `#sendTextBtn` / `#endBtn` / `#reportModal` 等 —— 非 sidebar 范围

**涉及文件**（4 个）：

1. **`E2ETestBase.java`**（`src/test/java/com/hugosol/chatagent/e2e/helper/E2ETestBase.java`）：
   - `countCorrectionSidebarItems()`（line 187）：`.correction-item` → `[data-testid="correction-item"]`
   - `countCorrectionBubbles()`（line 177）：无需修改（`.correction-bubble` 不变）
   - `hasCorrectionBubbleWith()`（line 181-183）：无需修改（`.correction-bubble .content-text` 不变）

2. **`ChatAgentSessionIT.java`**（`src/test/java/com/hugosol/chatagent/e2e/ChatAgentSessionIT.java`）：
   - Line 38：`countCorrectionSidebarItems()` 断言不变（方法内部选择器已在 Base 中更新）
   - Line 40：`page.waitForSelector("#correctionSidebarToggle:not(.hidden)")` → 不等待 `.hidden` class，改为等待 `[data-testid="correction-toggle"]` 存在于 DOM
   - Line 41：`page.locator("#correctionSidebarToggle")` → `page.locator("[data-testid='correction-toggle']")`
   - Line 42-43：`document.getElementById('correctionSidebar').classList.contains('collapsed')` → `document.querySelector('[data-testid="correction-sidebar"]').getAttribute('aria-expanded') === 'true'`
   - Line 44：`page.locator("#correctionSidebarClose")` → `page.locator("[data-testid='correction-sidebar-close']")`
   - Line 45-46：`document.getElementById('correctionSidebar').classList.contains('collapsed')` → `document.querySelector('[data-testid="correction-sidebar"]').getAttribute('aria-expanded') === 'false'`

3. **`ChatAgentResumeIT.java`**（`src/test/java/com/hugosol/chatagent/e2e/ChatAgentResumeIT.java`）：
   - Line 30、35、48：`countCorrectionSidebarItems()` 调用不变（方法内部选择器已在 Base 中更新）

4. **`DailyTalkIT.java`**（`src/test/java/com/hugosol/chatagent/e2e/DailyTalkIT.java`）：
   - Line 40：`countCorrectionSidebarItems()` 调用不变（方法内部选择器已在 Base 中更新）

**Toggle 按钮可见性变更说明**：旧代码中 toggle 按钮通过 `.hidden` class 控制显示/隐藏（`display: none`），E2E 使用 `:not(.hidden)` 等待按钮可见。Issue 01 的 React 组件中，Badge 在 `corrections.length === 0` 时不渲染（而非隐藏），因此需调整等待策略——改为等待 `[data-testid="correction-toggle"]` 出现在 DOM 中。

## Acceptance criteria

- [ ] `E2ETestBase.java` 中 `countCorrectionSidebarItems()` 使用 `[data-testid="correction-item"]` 选择器
- [ ] `ChatAgentSessionIT.java` 中所有 sidebar 选择器已迁移为 `data-testid`
- [ ] `ChatAgentSessionIT.java` 中 toggle 按钮等待策略适配（不再依赖 `.hidden` class）
- [ ] `ChatAgentSessionIT.java` 中 collapsed 状态检查适配（使用 `aria-expanded` 而非 CSS class）
- [ ] `ChatAgentResumeIT.java` 和 `DailyTalkIT.java` 无选择器需变更（仅依赖 Base 方法，已间接更新）
- [ ] `mvn verify` 全部 7 个 E2E IT 测试通过
- [ ] `mvn test` 全部 Java 单元测试 + Vitest 前端测试继续通过
- [ ] 不新增 E2E 测试文件——仅做选择器适配

## Blocked by

- [02: 集成 CorrectionSidebar 到聊天页面 + app.js 适配 + style.css 清理](./02-integrate-into-chat-page.md)
