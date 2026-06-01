# 11: ChatInput + Footer 组件（Portal → #textInputBar / footer）

**Status**: `ready-for-agent`

## Parent

[PRD：Chat 页面 React 渐进迁移 —— Phase 3: MessageList + ChatInput + Footer 迁入 React](../PRD-phase-3.md)

## What to build

将文本输入栏和底部会话控制栏迁移到 React。ChatInput 和 Footer 两个组件分别通过 Portal 挂载到 `#textInputBar` 和 `<footer>` DOM 节点，完整消费 `ChatProvider` context 的 `send` 函数。

### ChatInput 组件

Portal 到 `#textInputBar`。消费 `state.streamInProgress`、`state.sessionStatus`、`send`（来自 context）。

**disabled 逻辑**：
```
disabled = sessionStatus !== 'active' || streamInProgress
```
- idle 时输入框隐藏/禁用
- active + streaming 时输入框禁用（显示 "Agent is typing..." 占位符）
- active + not streaming 时输入框可用

**Enter 发送流程**：
1. 从 `state.messages` 派生 `nextId = messages.filter(m => m.role === 'user').length + 1`
2. `send({ type: 'USER_INPUT', text, messageId: nextId })`
3. `dispatch({ type: 'USER_MESSAGE_SENT', messageId: nextId, text })`

**Send 按钮**：点击触发相同的 Enter 逻辑。

**注意**：ChatInput 的 HTML 结构应保留 `#textInput`（input）和 `#sendTextBtn`（button）的 ID，以确保 E2E 测试继续工作。

### Footer 组件

Portal 到 `<footer>`。消费 `state.sessionStatus`、`send`（来自 context）。

**Mode Select**（`WORKPLACE_STANDUP` / `DAILY_TALK`）：
- `sessionStatus === 'active'` 时禁用
- `sessionStatus === 'idle'` 时可用
- 使用内部 `useState` 管理选中值（不进 reducer）

**Start Session 按钮**：
- `sessionStatus === 'active'` 时禁用
- 点击 → `send({ type: 'START_SESSION', mode: selectedMode })`
- HTML ID 保留 `#startBtn`

**End & Report 按钮**：
- `sessionStatus === 'idle'` 时禁用
- 点击 → `send({ type: 'END_SESSION' })`
- HTML ID 保留 `#endBtn`、`#modeSelect`

**初始状态**：Footer 挂载时检测 `localStorage.getItem('sessionId')`——如果存在则乐观设为 `'active'`（通过 dispatch WS_CLOSED 后 reducer 的初始状态由 ChatProvider 在启动时设置——见 IS-12）。如果另一标签页之前已打开同一个 session，WS 连接后自动 RESUME_SESSION 会修正状态。

### CSS

`ChatInput.module.css` 和 `Footer.module.css` 各新建，从 `style.css` 提取对应规则（输入栏 + footer 控制栏样式）。

**不删除 `style.css` 中的规则**——等 IS-12 统一清理。

### 入口集成

`chat-agent-entry.tsx` 的 `App` 组件内添加：
```tsx
{createPortal(<ChatInput />, document.getElementById('textInputBar')!)}
{createPortal(<Footer />, document.querySelector('footer')!)}
```

### 单元测试

**ChatInput 测试**（`src/__tests__/chat/ChatInput.test.tsx`）：
- `sessionStatus === 'idle'` 时输入框 disabled
- `streamInProgress === true` 时输入框 disabled
- Enter 键触发 send + dispatch，payload 携带正确的 messageId 和 text
- messageId 从 messages 数组正确派生

**Footer 测试**（`src/__tests__/chat/Footer.test.tsx`）：
- `sessionStatus === 'active'` 时 Start、Mode Select 禁用，End 可用
- `sessionStatus === 'idle'` 时 Start、Mode Select 可用，End 禁用
- 点击 Start 调用 `send({ type: 'START_SESSION', mode })`
- 点击 End 调用 `send({ type: 'END_SESSION' })`

测试模式参考先例：`src/__tests__/header/Header.test.tsx`（MockChatProvider + vitest.fn() for send）。

## Acceptance criteria

- [ ] ChatInput 通过 Portal 渲染到 `#textInputBar`，保留 `#textInput` 和 `#sendTextBtn` ID
- [ ] `sessionStatus !== 'active' || streamInProgress` 时 ChatInput 禁用
- [ ] Enter 键和 Send 按钮正确发送 USER_INPUT + dispatch USER_MESSAGE_SENT
- [ ] messageId 从 `state.messages` 正确派生（`user 消息数 + 1`）
- [ ] Footer 通过 Portal 渲染到 `<footer>`，保留 `#startBtn`、`#endBtn`、`#modeSelect` ID
- [ ] `sessionStatus === 'active'` 时 Mode Select 和 Start 禁用，End 可用
- [ ] `sessionStatus === 'idle'` 时 Mode Select 和 Start 可用，End 禁用
- [ ] 点击 Start 发送 `{ type: 'START_SESSION', mode }`
- [ ] 点击 End 发送 `{ type: 'END_SESSION' }`
- [ ] 初始状态：localStorage 有 sessionId 时乐观为 active
- [ ] 两套 Vitest 测试全部通过（至少各 4 个 test case）
- [ ] `npm test` 全绿
- [ ] `mvn compile` 成功

## Blocked by

- [09: ChatState 类型扩展 + reducer + context 类型脚手架](./09-chatstate-sessionstatus-reducer-context-scaffold.md)
