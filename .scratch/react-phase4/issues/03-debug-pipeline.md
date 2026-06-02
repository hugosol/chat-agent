# 03: Debug Pipeline

**Status**: `ready-for-agent`

## Parent

[PRD：React 全量迁移：聊天页面纯 React 化](../PRD.md)

## What to build

实现独立的 Debug 日志管道（模块级发布-订阅单例）和 DebugPanel React 组件，完全独立于 React Context 树。

### 模块级 debugLog 单例

新建 `src/main/frontend/src/debugLog.ts`，使用发布-订阅模式：

```ts
// 创建带时间戳的日志条目，通知所有订阅者
function debugLog(message: string): void

// 注册监听器，返回取消订阅函数
function subscribeDebug(fn: (entry: DebugEntry) => void): () => void

interface DebugEntry {
  timestamp: Date;
  message: string;
}
```

关键约束：
- 所有操作由 try/catch 静默吞错误——Debug 挂掉不影响任何主流程功能
- 模块级变量不依赖任何 React 机制

### ChatProvider 打点

在 ChatProvider 的以下位置直接 `import { debugLog }` 并调用（零 Context 依赖）：

- `ws.onopen` → `debugLog("WS: connected")`
- `ws.onmessage` → 每条收到的消息 `debugLog("WS ← {type}: {payload摘要}")`
- `ws.onclose` → `debugLog("WS: disconnected")`
- `ws.onerror` → `debugLog("WS: error")`
- 每次 dispatch action → `debugLog("State: {action.type}")`
- visibilitychange 触发 RESUME_SESSION → `debugLog("Resume: tab activated")`

### DebugPanel 组件 + CSS Module

- Props: `isOpen: boolean` + `onToggle: () => void`
- 零 ChatContext 依赖
- 挂载时 `subscribeDebug` 注册监听器，unmount 时取消订阅
- 内部管理 `entries: DebugEntry[]` state
- 渲染日志列表（时间戳 + 消息），自动滚动到最新
- Clear 按钮：清空 entries
- Toggle 按钮：调用 `onToggle()`（由 App 层管理面板互斥）
- 面板使用 `aria-hidden` 控制可见性

### 单元测试

**`DebugPanel.test.tsx`（新建）**：
- subscribe 接收条目并渲染
- Toggle 按钮调用 `onToggle`
- Clear 按钮清空所有条目
- Unmount 时取消订阅
- debugLog 异常不影响主流程（被 try/catch 静默吞掉）

## Acceptance criteria

- [ ] `debugLog.ts` 模块单例实现（发布-订阅 + try/catch 静默）
- [ ] ChatProvider 所有关键点（onopen/onmessage/onclose/onerror/dispatch/visibilitychange）调用 `debugLog()`
- [ ] DebugPanel 组件：通过 `subscribeDebug` 接收条目，内部管理 entries state
- [ ] DebugPanel 渲染时间戳 + 消息、自动滚动、Clear 清空
- [ ] DebugPanel 使用 `isOpen`/`onToggle` props，零 ChatContext 依赖
- [ ] Unmount 时取消订阅（无内存泄漏）
- [ ] Debug 挂掉不影响主流程（所有操作在 try/catch 内）
- [ ] `DebugPanel.test.tsx` 新建通过（至少 5 个 test case）
- [ ] `npm test` 全绿
- [ ] `mvn compile` 成功

## Blocked by

- [01: AppStatus State + ChatProvider Rewire + StatusBar](./01-appstatus-state-reducer-chatprovider-statusbar.md)（ChatProvider 中追加 debugLog 调用需要 Issue 1 已完成的 ChatProvider 结构）
