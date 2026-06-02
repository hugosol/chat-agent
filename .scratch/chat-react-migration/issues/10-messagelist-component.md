# 10: MessageList 组件（Portal → #messages）

**Status**: `ready-for-agent`

## Parent

[PRD：Chat 页面 React 渐进迁移 —— Phase 3: MessageList + ChatInput + Footer 迁入 React](../PRD-phase-3.md)

## What to build

将消息气泡列表渲染迁移到 React。MessageList 组件通过 Portal 挂载到 `#messages` DOM 节点，完整消费 `ChatProvider` context。这是 Phase 3 的第一个组件切面——消息渲染从 vanilla `app.js` 迁移到 React。

### 组件契约

MessageList 从 context 消费 `state.messages`、`state.corrections`、`state.streamInProgress`。不消费 `send`（纯展示组件）。

### 渲染算法

Option A 统一循环：

```
遍历 messages 数组
  如果 role === 'user'：
    渲染 user 气泡
    查找该 messageId 对应的 corrections（filter by messageId）
    如果有，每个 correction 渲染纠错气泡（编号摘要 "1. original → corrected"）
  如果 role === 'agent'：
    渲染 agent 气泡
    如果 streaming === true：渲染闪烁光标 <span class="stream-cursor">|</span>
    如果 streaming === false：渲染 🔊 播放按钮
```

### 消息气泡

- User 气泡：添加 `data-testid="message"` + `data-role="user"` 属性
- Agent 气泡：添加 `data-testid="message"` + `data-role="agent"` 属性
- 纠错气泡：添加 `data-testid="correction-bubble"` 属性，内部文本容器 `data-testid="message-content"`
- TTS 播放按钮：复用 `src/shared/tts.ts` 的 `speakText()`

### 折叠/展开

内部 `useState` 管理折叠状态。当 `messages.length > 10` 时，旧消息折叠（只显示最近 10 条），顶部显示 "Show earlier messages" 按钮。点击后展开全部。

**注意**：折叠逻辑在 `SESSION_STARTED` 时自动重置——`state.messages` 被 reducer 清空后，`useState` 的折叠标记自然复位（因为 `messages.length <= 10`）。

### Auto-scroll

`useEffect` 监听 `state.messages` 变化，将 `#chatArea` 滚动到底部。

### TTS 自动播放

`useEffect` 监听 agent 消息从 `streaming: true` 变为 `streaming: false` 时，调用 `speakText(text)`。

### CSS 迁移

将 `style.css` 中约 51 行消息相关规则（`.message`、`.message.user`、`.message.agent`、`.message.collapsed`、`.message .role`、`.btn-play`、`.stream-cursor`、`@keyframes blink`、`.correction-bubble` 等）迁移到 `MessageList.module.css`（CSS Modules 哈希化）。

**不删除 `style.css` 中的规则**——等 IS-12（切离）时一起删除，因为切离前 vanilla 渲染仍在运行。

### 入口集成

在 `chat-agent-entry.tsx` 的 `App` 组件中添加 `<MessageList />`，使用 `createPortal` 渲染到 `document.getElementById('messages')`。

### 单元测试

新增 `src/__tests__/chat/MessageList.test.tsx`，覆盖：

- 消息气泡渲染：user/agent 角色、文本内容、data-testid 属性
- 流式消息：`streaming: true` 时闪烁光标存在，`streaming: false` 时光标不存在、播放按钮存在
- 纠错气泡分组插入：messageId 关联，编号摘要格式
- 折叠/展开：超过 10 条消息时
- TTS 播放按钮点击

测试模式参考先例：`src/__tests__/header/Header.test.tsx`（Vitest + ChatProvider 包裹 + Portal）。

## Acceptance criteria

- [ ] MessageList 组件通过 Portal 渲染到 `#messages`，消费 `ChatProvider` context
- [ ] User 消息渲染为 `<div data-testid="message" data-role="user">` 气泡
- [ ] Agent 消息渲染为 `<div data-testid="message" data-role="agent">` 气泡
- [ ] 流式消息（`streaming: true`）显示闪烁光标
- [ ] 流式完成（`streaming: false`）移除光标，显示 🔊 播放按钮
- [ ] 纠错气泡（`data-testid="correction-bubble"`）在对应 user 消息后插入，显示编号摘要
- [ ] 超过 10 条消息时旧消息折叠，显示 "Show earlier messages" 按钮，点击后展开
- [ ] 新消息到达时自动滚动 `#chatArea` 到底部
- [ ] Agent 消息流式完成时自动调用 `speakText()` 播放 TTS
- [ ] `MessageList.module.css` 包含所有消息相关样式
- [ ] Vitest 测试覆盖所有渲染场景（至少 5 个 test case）
- [ ] `npm test` 全绿
- [ ] `mvn compile` 成功
- [ ] 浏览器中可见双重渲染（React 渲染在 vanilla 渲染之上，双重气泡是预期临时状态）

## Blocked by

- [09: ChatState 类型扩展 + reducer + context 类型脚手架](./09-chatstate-sessionstatus-reducer-context-scaffold.md)
