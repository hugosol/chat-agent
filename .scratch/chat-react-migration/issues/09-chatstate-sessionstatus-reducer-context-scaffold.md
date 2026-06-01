# 09: ChatState 类型扩展 + reducer + context 类型脚手架

**Status**: `ready-for-agent`

## Parent

[PRD：Chat 页面 React 渐进迁移 —— Phase 3: MessageList + ChatInput + Footer 迁入 React](../PRD-phase-3.md)

## What to build

扩展 `ChatState`、reducer 和 `ChatContext` 的类型定义，为 Phase 3 三个新组件（MessageList、ChatInput、Footer）提供类型基础。这是一个纯粹的类型 + 纯函数层变更，不修改任何运行时行为。

### ChatState 新增字段

`sessionStatus: 'idle' | 'active'` —— 替代 `app.js` 中 `sessionId != null` 的隐含判断。Footer 和 ChatInput 的 disabled 逻辑统一读取此字段。

初始值：`'idle'`（`initialState` 中的默认值）。启动时的乐观赋值由 ChatProvider 在 `useReducer` 初始化时检测 `localStorage` 完成（后续 IS-10 内实现）。

### 新增两个 Action

```typescript
| { type: "USER_MESSAGE_SENT"; messageId: number; text: string }
| { type: "SESSION_REPORT"; report: Record<string, unknown> }
```

### Reducer 变更

**现有 action 增加 `sessionStatus` 转换**：

| Action | sessionStatus 转换 |
|--------|---------------------|
| `SESSION_STARTED` | → `'active'` |
| `SESSION_RESUMED` | → `'active'` |
| `WS_CLOSED` | → `'idle'` |

**新 action 实现**：

- `USER_MESSAGE_SENT`：在 `messages` 数组末尾追加一条 `{ id: action.messageId, role: "user", text: action.text, streaming: false }`。不改变其他字段。
- `SESSION_REPORT`：设置 `sessionStatus: 'idle'`、`streamInProgress: false`。

### ChatContext 类型扩展

`ChatContextValue` 新增 `send` 字段：

```typescript
interface ChatContextValue {
  state: ChatState;
  dispatch: Dispatch<Action>;
  send: (msg: unknown) => void;
}
```

`ChatProvider` 提供 no-op 实现：`const send = useCallback((_msg: unknown) => {}, []);`。真正的 WS send 由 IS-12 替换。

### 单元测试

扩展 `chatReducer.test.ts`，为每个新行为和 `sessionStatus` 转换增加测试用例：

- `SESSION_STARTED` → `sessionStatus: 'active'`
- `SESSION_RESUMED` → `sessionStatus: 'active'`
- `WS_CLOSED` → `sessionStatus: 'idle'`
- `USER_MESSAGE_SENT` → messages 数组追加 user 消息
- `SESSION_REPORT` → `sessionStatus: 'idle'` + `streamInProgress: false`

## Acceptance criteria

- [ ] `ChatState` 新增 `sessionStatus: 'idle' | 'active'`，`initialState` 包含 `sessionStatus: 'idle'`
- [ ] `Action` 类型新增 `USER_MESSAGE_SENT` 和 `SESSION_REPORT` 两个 variant
- [ ] `chatReducer` 中 `SESSION_STARTED` 设置 `sessionStatus: 'active'`
- [ ] `chatReducer` 中 `SESSION_RESUMED` 设置 `sessionStatus: 'active'`
- [ ] `chatReducer` 中 `WS_CLOSED` 设置 `sessionStatus: 'idle'`
- [ ] `chatReducer` 中 `USER_MESSAGE_SENT` 正确处理 append
- [ ] `chatReducer` 中 `SESSION_REPORT` 正确设置 `sessionStatus` 和 `streamInProgress`
- [ ] `ChatContextValue` 新增 `send` 字段，`ChatProvider` 提供 no-op 实现
- [ ] `chatReducer.test.ts` 对应测试全部通过（至少 5 个新增/修改的 test case）
- [ ] `npm test` 全绿
- [ ] `mvn compile` 成功

## Blocked by

None - can start immediately
