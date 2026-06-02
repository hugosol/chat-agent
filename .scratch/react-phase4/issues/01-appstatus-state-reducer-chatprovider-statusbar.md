# 01: AppStatus State + ChatProvider Rewire + StatusBar

**Status**: `ready-for-agent`

## Parent

[PRD：React 全量迁移：聊天页面纯 React 化](../PRD.md)

## What to build

完成统一的"状态管道"——从类型定义到 StatusBar 的可见输出。这是整个 Phase 4 的基础，所有后续 Issue 都依赖它。

### AppStatus 类型 + ChatState + Action

`ChatState` 中 `connectionStatus`、`sessionStatus` 及所有 status 相关局部字段合并为单一 `appStatus: AppStatus`：

```ts
type AppStatus =
  | "Connecting"
  | "Connected"
  | "UserTurn"
  | "Processing"
  | "Warning"
  | "Error"
  | "Disconnected";
```

`"Speaking"` 值被弃用——服务端 STATE_UPDATE "SPEAKING" 直接映射为 "UserTurn"。

Warning 和 Error 的动态消息文本存储在 `statusPayload: string | null` 中。这两种状态是持久化的——显示在 StatusBar 上直到下一状态变化覆盖它们，不再使用 Toast。

`streamInProgress` 保留为独立字段——与 `appStatus` 正交。

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

新增 Action: `SET_APP_STATUS`：

```ts
| { type: "SET_APP_STATUS"; appStatus: AppStatus; statusPayload?: string | null }
```

### Server message → Action 映射（ChatProvider onmessage 中实现）

| 事件 | dispatch 的 Action | 说明 |
|------|-------------------|------|
| `ws.onopen` | `SET_APP_STATUS: "Connected"` | WS 握手成功 |
| `SESSION_STARTED` | `SESSION_STARTED` | reducer 设 `appStatus: "UserTurn"` |
| `USER_MESSAGE_SENT` | `USER_MESSAGE_SENT` + `SET_APP_STATUS: "Processing"` | 用户发消息后进入处理状态 |
| `AGENT_STREAM_DELTA` | `AGENT_STREAM_DELTA` | 不变 |
| `AGENT_STREAM_END` | `AGENT_STREAM_END` | 停止 streaming；不改变 appStatus |
| `STATE_UPDATE` | `STATE_UPDATE`（更新 tokenUsage）；serverState `"PROCESSING"` → `SET_APP_STATUS: "Processing"`；`"SPEAKING"` → `SET_APP_STATUS: "UserTurn"` | |
| `TOKEN_WARNING` | `SET_APP_STATUS: { appStatus: "Warning", statusPayload }` | 不再 Toast |
| `ERROR` | `SET_APP_STATUS: { appStatus: "Error", statusPayload }` | 不再 Toast |
| `SESSION_REPORT` | `SESSION_REPORT` | reducer：重置 + `appStatus: "Connected"` + store report |
| `SESSION_RESUMED` | `SESSION_RESUMED` | reducer 设 `appStatus: "UserTurn"` |
| `ws.onclose` | `WS_CLOSED` | reducer：重置 + `appStatus: "Disconnected"` |

### Reducer 修改

- 删除 `connectionStatus`、`sessionStatus` 字段处理
- 新增 `SET_APP_STATUS` case：设置 `appStatus` + `statusPayload`
- 所有已有 case（`SESSION_STARTED`、`AGENT_STREAM_DELTA`、`AGENT_STREAM_END`、`CORRECTION_RESULT`、`STATE_UPDATE`、`SESSION_RESUMED`、`WS_CLOSED`、`USER_MESSAGE_SENT`、`SESSION_REPORT`）按上表调整
- `initialState` 更新：`appStatus: "Connecting"`、`statusPayload: null`、`report: null`

### 工具函数

新增两个共享工具函数（放入 `shared/` 或 `state/`，供多个组件导入）：

```ts
function deriveStatus(appStatus: AppStatus, statusPayload: string | null): { message: string; type: string }

function isSessionActive(appStatus: AppStatus): boolean
// 返回 ["UserTurn", "Processing", "Warning", "Error"].includes(appStatus)
```

`deriveStatus` 为 7 个状态各返回对应显示文字和 CSS 类名。

### ChatProvider 清理（本轮完成的部分）

- 删除 `VANILLA_TYPES` Set、`vanillaHandlers` Set、`registerHandler()` 函数
- 删除 `window.ChatAgent.send` 全局暴露
- 删除 TOKEN_WARNING 和 ERROR 的 `showToast()` 调用
- 新增 `visibilitychange` 事件监听器 → 自动 RESUME_SESSION（页面可见 + 有 sessionId 时）
- `ws.onclose` 中追加 `speechSynthesis.cancel()`

### StatusBar 组件 + CSS Module

- 零 props，全部从 `useChatContext()` 读取
- 调用 `deriveStatus(state.appStatus, state.statusPayload)` 推导显示信息
- 7 个 CSS 类名：`.connecting` / `.connected` / `.userturn` / `.processing` / `.warning` / `.error` / `.disconnected`
- Warning/Error 状态显示动态 payload 文本
- 颜色值从旧 `style.css` 中的对应规则迁移到新 CSS Module

### 单元测试

**`chatReducer.test.ts`（重写）**：
- 全部 reducer case 重写（ChatState 形状全变）
- 新增 `SET_APP_STATUS` 测试
- `deriveStatus()` 7 种状态各一个 test case
- `isSessionActive()` 对 7 种状态各一个 test case

**`StatusBar.test.tsx`（新建）**：
- 7 种 appStatus 值各自渲染正确文字 + CSS 类
- Warning/Error 验证 statusPayload 显示

## Acceptance criteria

- [ ] `AppStatus` 类型定义 7 个值，`ChatState` 使用新结构（`appStatus` + `statusPayload` + `report`）
- [ ] 新增 `SET_APP_STATUS` Action
- [ ] `chatReducer` 全部 case 按上表调整，`initialState` 更新为 `appStatus: "Connecting"`
- [ ] `deriveStatus()` 和 `isSessionActive()` 工具函数实现正确
- [ ] ChatProvider `onmessage` 中 10 种 server 事件按上表映射 dispatch
- [ ] ChatProvider 删除 `VANILLA_TYPES`、`vanillaHandlers`、`registerHandler`、`window.ChatAgent.send`、Toast 调用
- [ ] ChatProvider 新增 `visibilitychange` → RESUME_SESSION + `speechSynthesis.cancel()`
- [ ] StatusBar 组件渲染正确文字 + CSS 类（零 props，从 context 消费）
- [ ] `chatReducer.test.ts` 重写完成，全部 case 覆盖
- [ ] `StatusBar.test.tsx` 新建完成，7 种状态 + Warning/Error payload 全覆盖
- [ ] `npm test` 全绿
- [ ] `mvn compile` 成功

## Blocked by

None - can start immediately
