# 06: `useChatWebSocket` Hook + `ChatProvider` + `app.js` 集成 + Vite 构建 + Toast

**Status**: `ready-for-agent`

## Parent

[PRD：Chat 页面 React 渐进迁移 —— Phase 2: WebSocket 服务层 + 集中状态管理](../PRD-phase-2.md)

## What to build

Phase 2 的核心架构切片——将 WebSocket 管理从 `app.js` 的全局 IIFE 提取到 React 服务层，建立 `useReducer + context` 集中状态管道，同时保留对 vanilla JS（app.js）的双重路径桥接。

这是 Phase 2 的最小可验证单元——`mvn verify` 全部 E2E 通过即为验收。

### `src/hooks/useChatWebSocket.ts` — WS Hook

在 `useEffect` 中建立 WebSocket 连接、在 cleanup 中关闭。连接建立后自动从 `localStorage` 读取 sessionId 发送 RESUME_SESSION。

收到每条服务端消息后同时执行两条路径：
1. **dispatch 进 reducer**（通过 `ChatContext`）
2. **遍历 vanilla 回调** → `vanillaHandlers.forEach(fn => fn(msg))`

Hook 内部 4 处 Phase 2 专职胶水代码（`// Phase 2 compat — Phase 3 移除` 注释标注）：
- `vanillaHandlers` Set 声明
- 每条 WS 消息的 `vanillaHandlers.forEach(fn => fn(msg))` 旁路调用
- `window.ChatAgent.registerHandler(fn)` 回调注册函数
- `window.ChatAgent.send(msg)` 发送函数

消息分发逻辑覆盖全部 11 种消息类型，按以下规则分流：

| WS 消息 | dispatch 进 reducer | 走 vanilla 回调 | 备注 |
|---------|--------------------|----------------|------|
| AGENT_STREAM_DELTA | 创建/追加 message text | 创建/追加 DOM bubble | ← |
| AGENT_STREAM_END | 锁定 message，更新 tokenUsage | 替换全文，TTS，showTextInput | ← |
| CORRECTION_RESULT | 追加 corrections | 创建 correction bubble DOM | Sidebar 仍走命令式 API |
| SESSION_STARTED | 重置全部 state | 清空 DOM，按钮状态，showTextInput | ← |
| SESSION_RESUMED | 批量重建 state | 重建全部消息 DOM | ← |
| SESSION_REPORT | —（Phase 3 再做） | showReport DOM，按钮状态 | ← |
| STATE_UPDATE | tokenUsage | status 文字 | ← |
| TOKEN_WARNING | — | —（改为 Toast） | Hook 内直接 `showToast` |
| ERROR（LLM 运行时异常） | — | —（改为 Toast） | Hook 内直接 `showToast`，红色 |
| ERROR（校验错误） | — | status 文字 | 走 vanilla 回调 |
| WS_CLOSED（合成） | 重置全部 state，connectionStatus='disconnected' | resetUI | ← |

断连时合成 `WS_CLOSED` 消息。TOKEN_WARNING 和 LLM 运行时异常在 Hook 内部直接调用 `showToast(message)` 展示 Toast（红色底色，使用现有 `Toast.tsx` 组件中已有的 `type: 'error'` 支持），不走 vanilla 回调路径。

不进 reducer 的字段（留在 Hook 内 `useRef`）：`sessionId`、`mode`、`messageCount`——这些不驱动渲染，仅用于 WS 发送。

### `src/state/ChatContext.tsx` — Provider + Context

- `ChatContext` 通过 `React.createContext` 创建
- `ChatProvider` 组件：内部调用 `useChatWebSocket` Hook，将返回值（`chatState` + `dispatch`）通过 context 下发
- `useChatContext()` hook 供子组件消费

### `src/entry/chat-agent-entry.tsx` — 新入口

创建 React root 挂载到 `document.body`（或专用容器），渲染 `<ChatProvider>` 包裹现有组件。

**注意**：本期 entry 仍通过命令式 API 挂载 Header 和 CorrectionSidebar（`window.ChatAgent.mountHeader` / `mountCorrectionSidebar`），暂不改为 JSX 子节点——留给 Issue 07。本期 entry 的职责是启动 WS Hook + Provider 管道。

### `vite.config.chat.ts` — 第三个 Vite 构建配置

参照现有 `vite.config.ts`（header-entry）的模式：
- 入口：`src/entry/chat-agent-entry.tsx`
- 输出：`chat-bundle.js` + `chat-bundle.css` → `src/main/resources/static/shared/`
- `build.lib.name: "ChatAgent"`（与其他 bundle 共享全局命名空间）
- `formats: ["iife"]`，`external: ["react", "react-dom"]`
- `emptyOutDir: false`
- 同现有的 `define` 条件判断（test 模式下不设置 `process.env.NODE_ENV`）

### `package.json` — 构建脚本

`build` 脚本串联三个 Vite 构建：
```json
"build": "vite build && vite build --config vite.config.correction-sidebar.ts && vite build --config vite.config.chat.ts"
```

### `index.html` — 加载新 bundle

在现有 `<script>` 加载顺序中插入新 bundle：
1. `react.production.min.js`
2. `react-dom.production.min.js`
3. `chat-bundle.js`（**新增**——必须在 header-bundle 和 correction-sidebar-bundle 之前加载，因为它建立 `ChatAgent.send` / `registerHandler`）
4. `header-bundle.js`
5. `correction-sidebar-bundle.js`

新增 `<link>` 加载 `chat-bundle.css`。

保留现有内联 mount 脚本（Issue 07 再清理 `window.updateTokenBar` 和 inline mount）。

### `app.js` 变更

**删除**：
- `connect()` 函数定义（第 51-81 行）及所有 `ws.onopen/onmessage/onclose/onerror` 回调
- `ws` 全局变量（`var ws = null`）
- 文件末尾 `connect()` 调用（第 499 行）
- `updateTokenBar()` 函数定义（第 364 行）
- 所有 `ws.send(...)` 调用（4 处：START_SESSION、RESUME_SESSION、USER_INPUT、END_SESSION）

**新增**：
- 在 IIFE 顶部调用 `ChatAgent.registerHandler(handleMessage)` 注册消息回调（在 `handleMessage` 定义之后、`connect()` 之前的位置）
- 所有 `ws.send(msg)` 替换为 `ChatAgent.send(msg)`
- `handleMessage` 的 switch 中新增 `case 'WS_CLOSED': resetUI(); break;`

**完全不改**：`handleStreamDelta` / `handleStreamEnd` / `handleCorrectionResult` / `handleSessionResumed` / `showReport` / `setStatus` / `showTextInput` 的内部逻辑代码。`handleCorrectionResult` 中 `sidebar.addCorrection()` 调用保留（Issue 07 移除）。

### Toast 扩展（tokens 警告 + LLM 异常）

WS Hook 中 TOKEN_WARNING 和 LLM ERROR 分支直接调用 `showToast(message)`（导入自 `src/shared/Toast.tsx`）。现有 `Toast` 组件已支持 `type: 'error'`（红色底色），无需修改 Toast 组件本身。`showToast` 目前声明的是 `(message: string, duration?: number)` 签名——如需传递 `type` 参数，调整为 `showToast(message: string, type?: 'success' | 'error', duration?: number)`，不传 type 时默认 `'success'`。

## Acceptance criteria

- [ ] `src/hooks/useChatWebSocket.ts` 存在，完整实现 WS 连接生命周期和双重路径分发
- [ ] `src/state/ChatContext.tsx` 存在，导出 `ChatProvider` 和 `useChatContext`
- [ ] `src/entry/chat-agent-entry.tsx` 存在，启动 ChatProvider + WS Hook
- [ ] `vite.config.chat.ts` 存在，产出 `static/shared/chat-bundle.js` + `chat-bundle.css`
- [ ] `mvn compile` 成功，三个 Vite 构建全部产出
- [ ] `index.html` 正确加载 `chat-bundle.js` 和 `chat-bundle.css`
- [ ] `app.js` 中 `connect()` 函数和 `ws` 全局变量已移除
- [ ] `app.js` 通过 `ChatAgent.registerHandler(handleMessage)` 注册回调
- [ ] `app.js` 中所有 `ws.send()` 替换为 `ChatAgent.send()`
- [ ] 4 处 Phase 2 胶水代码均有 `// Phase 2 compat — Phase 3 移除` 注释
- [ ] TOKEN_WARNING 和 LLM ERROR 以红色 Toast 展示
- [ ] STATE_UPDATE 的 status 文字不受影响（仍走 vanilla 回调）
- [ ] `mvn verify` 全部 7 个 E2E IT 测试通过
- [ ] `npm test` 全部单元测试通过（含 Issue 05 的 chatReducer 测试）

## Blocked by

- [05: chatReducer 纯函数 + Vitest 单元测试](./05-chat-reducer-unit-tests.md)
