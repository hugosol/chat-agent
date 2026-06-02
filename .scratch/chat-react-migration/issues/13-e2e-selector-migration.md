# 13: E2E 选择器迁移 + 回归验证

**Status**: `ready-for-agent`

## Parent

[PRD：Chat 页面 React 渐进迁移 —— Phase 3: MessageList + ChatInput + Footer 迁入 React](../PRD-phase-3.md)

## What to build

CSS Modules 哈希化后，E2E 测试中的 CSS class 选择器失效。将 `E2ETestBase.java` 及相关 IT 测试类中的消息相关选择器从 CSS class 迁移到 `data-testid` 属性选择器，确保所有 7 个 E2E 测试通过。

### 选择器迁移表

| 原选择器 | 新选择器 |
|----------|----------|
| `.message.user` | `[data-testid="message"][data-role="user"]` |
| `.message.agent` | `[data-testid="message"][data-role="agent"]` |
| `.message.correction-bubble` | `[data-testid="correction-bubble"]` |
| `.correction-bubble .content-text` | `[data-testid="correction-bubble"] [data-testid="message-content"]` |

### 受影响的 `E2ETestBase.java` 方法

需要修改以下 7 个 helper 方法的内部选择器：

| 方法 | line | 当前选择器 | 新选择器 |
|------|------|-----------|----------|
| `countUserMessages()` | 168-170 | `.message.user` | `[data-testid="message"][data-role="user"]` |
| `countAgentMessages()` | 172-174 | `.message.agent` | `[data-testid="message"][data-role="agent"]` |
| `countCorrectionBubbles()` | 176-178 | `.message.correction-bubble` | `[data-testid="correction-bubble"]` |
| `hasCorrectionBubbleWith(text)` | 180-184 | `.correction-bubble .content-text` | `[data-testid="correction-bubble"] [data-testid="message-content"]` |
| `reloadPage()` | 163-166 | `.message.user` | `[data-testid="message"][data-role="user"]` |
| `waitForAgentResponse()` | 150-155 | `.correction-bubble` | `[data-testid="correction-bubble"]` |

### 不受影响的选择器

Footer/Input 的 ID 选择器（`#modeSelect`、`#startBtn`、`#endBtn`、`#textInputBar`、`#textInput`、`#sendTextBtn`）Portal 挂载后 HTML 保留这些 ID，无需变更。

### 回归验证

执行 `mvn verify`，确认 7 个 IT 测试类全部通过：
- `ChatAgentSessionIT`
- `ChatAgentResumeIT`
- `ChatAgentMemoryIT`
- `DailyTalkIT`
- `ChatAgentMemoryCueIT`
- `ManagePageIT`
- `FlashcardIT`

各 IT 类可能内部也有直接使用 `.message` 选择器的断言逻辑，需要一并检查和迁移。

## Acceptance criteria

- [ ] `E2ETestBase.java` 中 6 个方法的选择器已迁移为 `data-testid` 属性选择器
- [ ] 所有 IT 测试类的内部断言（如有）已更新选择器
- [ ] `mvn verify` 全部 7 个 E2E 测试通过
- [ ] 不新增 E2E 测试，不改变断言逻辑

## Blocked by

- [10: MessageList 组件](./10-messagelist-component.md)
- [11: ChatInput + Footer 组件](./11-chatinput-footer-components.md)
- [12: ChatProvider WS 一体化 + vanilla 切离 + app.js 裁剪](./12-chatprovider-ws-vanilla-cutover-appjs-pruning.md)
