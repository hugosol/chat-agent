# 12: ChatProvider WS 一体化 + vanilla 切离 + app.js 裁剪

**Status**: `ready-for-agent`

## Parent

[PRD：Chat 页面 React 渐进迁移 —— Phase 3: MessageList + ChatInput + Footer 迁入 React](../PRD-phase-3.md)

## What to build

这是 Phase 3 的"重构阶段"切片——完成 ChatProvider 的最终架构、切离 vanilla 消息渲染路径、删除 `app.js` 中已迁移的代码。前端行为不做任何变更，现有 E2E 测试持续通过。

### 一、ChatProvider WS 一体化

将 `useChatWebSocket` 的全部 WebSocket 生命周期逻辑内联进 `ChatProvider` 的 `useEffect`：

- WebSocket 连接创建（`ws://` / `wss://`）
- `onopen` → 自动 RESUME_SESSION（从 `localStorage.getItem('sessionId')`）
- `onmessage` → JSON 解析 → `toAction()` 转换 → `dispatch(action)`
- `onclose` → `dispatch({ type: 'WS_CLOSED' })` + vanilla 通知
- `onerror` → 静默忽略
- 清理函数：WebSocket 关闭

将 IS-09 中提供的 no-op `send` 替换为真实实现：

```typescript
const wsRef = useRef<WebSocket | null>(null);
const send = useCallback((msg: unknown) => {
  if (wsRef.current?.readyState === WebSocket.OPEN) {
    wsRef.current.send(JSON.stringify(msg));
  }
}, []);
```

`wsRef` 在 `useEffect` 的 `onopen` 中赋值，在 cleanup 中置 null。

**从 `chat-agent-entry.tsx` 中移除**：
- `WsConnector` 组件（整个组件删除）
- `useChatWebSocket` 的 import

### 二、vanilla 分发窄化

vanilla 回调桥接（`vanillaHandlers` Set + `registerHandler`）保留，但旁路分发从"对所有消息广播"改为"仅对 5 种非 React 管辖的消息类型触发"：

| 消息类型 | 是否触发 vanilla | Vanilla 消费者 |
|----------|------------------|---------------|
| `SESSION_STARTED` | 否 | React reducer 独占 |
| `AGENT_STREAM_DELTA` | 否 | React reducer 独占 |
| `AGENT_STREAM_END` | 否 | React reducer 独占 |
| `CORRECTION_RESULT` | 否 | React reducer 独占 |
| `SESSION_RESUMED` | 否 | React reducer 独占 |
| `SESSION_REPORT` | **是** | `app.js` → `showReport()` |
| `ERROR` | **是** | `app.js` → `setStatus()` + `debugLog()` |
| `TOKEN_WARNING` | **是** | `app.js` → `setStatus()` + `debugLog()` |
| `STATE_UPDATE` | **是** | `app.js` → `setStatus()` + `debugLog()` |
| `WS_CLOSED` | **是** | `app.js` → `resetUI()` |

`registerHandler` 继续通过 `window.ChatAgent.registerHandler` 暴露给 `app.js`（在 ChatProvider 的 `useEffect` 中挂载）。

### 三、ChatAgent.send() 移除

删除 `window.ChatAgent.send` 全局挂载。所有 WS 发送现在由 React 组件通过 `useChatContext().send` 完成。`app.js` 中不再依赖 `ChatAgent.send()`。

**删除 `useChatWebSocket.ts` 文件**（全部逻辑已内联进 ChatProvider）。

### 四、app.js 裁剪

**删除函数**（完整删除以下函数定义）：
- `handleStreamDelta`
- `handleStreamEnd`
- `handleCorrectionResult`
- `handleSessionResumed`
- `rebuildMessage`
- `createMessageElement`
- `createCorrectionBubble`
- `addPlayButton`
- `showTextInput`
- `sendTextInput`
- `handleCollapse`
- `sendStart`

**`handleMessage` 的 switch 分支删除**：
- `case 'SESSION_STARTED'`（lines 51-65）
- `case 'AGENT_STREAM_DELTA'`（lines 67-69）
- `case 'AGENT_STREAM_END'`（lines 71-73）
- `case 'CORRECTION_RESULT'`（lines 75-77）
- `case 'SESSION_RESUMED'`（lines 89-91）

**保留的 case 分支**（继续使用）：
- `case 'TOKEN_WARNING'`
- `case 'STATE_UPDATE'`
- `case 'SESSION_REPORT'`
- `case 'ERROR'`
- `case 'WS_CLOSED'`（调用 `resetUI()`）

**删除 Event Listener**：
- `#sendTextBtn` click
- `#textInput` keydown
- `#startBtn` click
- `#endBtn` click
- `#showEarlierBtn` click
- `#newSessionBtn` click

**保留的 Event Listener**：
- `#closeReportBtn` click
- `#debugToggle` click
- `#debugClear` click
- `document visibilitychange` 监听

**`resetUI()` 裁剪**：只保留 `synth.cancel()` 和本地变量置 null。删除消息清除、streamBubbles 清除等 React 管辖的 DOM 操作。

### 五、HTML 模板清理

`index.html` 中删除 `#newSessionBtn` 的 HTML 元素（`<button id="newSessionBtn">Start New Session</button>`）。

### 六、CSS 清理

从 `style.css` 中删除约 51 行消息相关规则（已在 IS-10 / IS-11 中迁移到 CSS Modules）：
- `.message` 系列（lines 108-148）
- `.btn-play`（lines 150-165）
- `.message.correction-bubble` 系列（lines 167-182）
- `.stream-cursor` + `@keyframes blink`（lines 184-197）

### 七、ChatProvider 初始 sessionStatus 乐观赋值

ChatProvider 在 `useReducer` 初始化时，检测 `localStorage.getItem('sessionId')` 是否存在——如果存在则覆盖 `initialState.sessionStatus` 为 `'active'`（乐观赋值，WS 连接后 RESUME_SESSION 失败会自然修正）。

## Acceptance criteria

- [ ] ChatProvider 的 `useEffect` 管理 WebSocket 完整生命周期
- [ ] `send` 通过 context 暴露，所有组件可使用 `useChatContext().send` 发送 WS 消息
- [ ] `WsConnector` 组件已删除
- [ ] `useChatWebSocket.ts` 文件已删除
- [ ] vanilla 分发仅对 5 种消息类型（SESSION_REPORT、ERROR、TOKEN_WARNING、STATE_UPDATE、WS_CLOSED）触发
- [ ] `window.ChatAgent.send()` 已移除
- [ ] `window.ChatAgent.registerHandler()` 保留（仍供 app.js 初始化使用）
- [ ] `app.js` 中 12 个消息渲染函数 + 5 个 switch case + 6 个 Event Listener 已删除
- [ ] `app.js` 的 SESSION_REPORT、ERROR、TOKEN_WARNING、STATE_UPDATE、WS_CLOSED 处理保留且功能正常
- [ ] `resetUI()` 只保留 `synth.cancel()`，删除 React 管辖的 DOM 操作
- [ ] `index.html` 中 `#newSessionBtn` 元素已删除
- [ ] `style.css` 中约 51 行消息相关 CSS 已删除
- [ ] localStorage 有 sessionId 时 ChatProvider 乐观设置 `sessionStatus: 'active'`
- [ ] 报告弹窗（Report Modal）正常显示
- [ ] 状态栏（Status Bar）正常更新
- [ ] Debug 面板正常 toggle/clear
- [ ] Flashcard 面板互斥正常
- [ ] `visibilitychange` 监听正常
- [ ] `npm test` 全绿
- [ ] `mvn verify` 全部 7 个 E2E 通过（或功能等价，若 E2E 选择器在 IS-13 迁移）

## Blocked by

- [09: ChatState 类型扩展 + reducer + context 类型脚手架](./09-chatstate-sessionstatus-reducer-context-scaffold.md)
- [10: MessageList 组件](./10-messagelist-component.md)
- [11: ChatInput + Footer 组件](./11-chatinput-footer-components.md)
