# PRD：Chat 页面 React 渐进迁移 —— Phase 2: WebSocket 服务层 + 集中状态管理

**Status**: `ready-for-agent`

## Problem Statement

当前 Chat Agent 的聊天页面中，WebSocket 连接管理、消息流式渲染、纠错侧边栏更新、token bar 更新、状态指示器全部耦合在 `app.js`（500 行）的单一 IIFE 中。状态散落在：

- `sessionId`、`messageCount`、`turnCounter`、`streamBubbles` 等全局变量
- CorrectionSidebar 通过命令式 API `addCorrection()` / `clear()` 推送数据
- Header token bar 通过 `window.updateTokenBar(pct)` 全局函数重新 mount 整个 Header 组件

当一个 WebSocket 消息需要同时影响多处 UI 时（如 CORRECTION_RESULT 既要更新 sidebar 纠错列表，又要插入消息气泡），各消费端独立处理，数据流难以追踪。Learner 不应该感知这些——但开发者每次修改聊天逻辑都需跨多个模块协调状态。

Phase 1 已将 Header 和 CorrectionSidebar 迁移为独立 React 模块，但它们的运行状态仍由 `app.js` 通过命令式桥接驱动。Phase 2 的目标是将状态管理集中化，使 React 组件成为自身数据的唯一消费者，不再依赖外部命令式推送。

## Solution

引入 React 18 原生 `useReducer + context` 集中状态管理——这是之前 ADR `centralized-chat-state.md` 确定的方案。核心技术决策：

- **一个 reducer** 统一处理所有 WebSocket 消息（`toAction(msg)` 映射），同时更新多个关联 UI
- **一个 context** 下发状态，Header（token bar）、CorrectionSidebar（纠错列表）、未来 MessageList（消息气泡）均从同一数据源读取
- **WebSocket Hook** 封装连接生命周期，负责 dispatch 进 reducer 并保留对 vanilla JS 的桥接通道
- **双重路径兼容**：余下的 vanilla `app.js` 渲染逻辑（消息气泡 DOM、报告弹窗、调试面板、输入框）通过回调继续运行，不受影响——Phase 3 时删除

Reducer state 字段遵循"只放 UI 需要显示的或需要编辑的数据"原则：`messages`、`corrections`、`tokenUsage`、`connectionStatus`、`streamInProgress`。`sessionId`、`mode`、`messageCount` 留在 WS Hook 内部（不驱动渲染）。

`messages` 和 `corrections` 在数据层独立存储，显示层通过 `useMemo` 派生合并——因为 correction bubble 需要插入到聊天流中对应消息之后，但这只是渲染需求，不应污染数据模型。

流式渲染（AGENT_STREAM_DELTA）每 token dispatch 一次，React 18 VDOM diffing 仅原地更新变化了的文本节点（O(1)），不重建列表 DOM，性能无实际影响。

TOKEN_WARNING 和 LLM 运行时异常改为 Toast（红色底色）通知——此前它们显示在 status bar 但几乎立即被后续 STATE_UPDATE 覆盖，Learner 根本看不到。

## User Stories

1. 作为一名 Learner，在聊天过程中，Header 的 token 使用进度条应自动更新，无需页面刷新。
2. 作为一名 Learner，在聊天过程中收到纠错时，CorrectionSidebar 应自动更新纠错列表（类型、原文→修正、解释），浮动 Badge 计数自动 +1。
3. 作为一名 Learner，恢复会话（页面刷新 / 标签切换 RESUME_SESSION）后，CorrectionSidebar 和历史消息应正确重建。
4. 作为一名 Learner，开启新会话后，所有状态（纠错、token、消息）应立即清空。
5. 作为一名 Learner，当 token 使用量接近上限（80%）时，应看到红色 Toast 通知提示尽快结束会话。
6. 作为一名 Learner，当 LLM 调用发生运行时错误时，应看到红色 Toast 通知错误信息。
7. 作为一名 Learner，会话处理状态（PROCESSING / SPEAKING / Type your message）应在状态栏正常切换。
8. 作为一名 Learner，WebSocket 断连或连接错误时，UI 状态应重置（按钮、输入框、纠错清空），重新连接后通过 RESUME_SESSION 恢复。
9. 作为一名 Learner，打字输入、会话开始/结束、页面可见性恢复等所有功能应与迁移前完全一致。
10. 作为一名 Developer，chatReducer 纯函数应可通过 Vitest 单元测试独立验证每条 WS 消息的状态变换。
11. 作为一名 Developer，WS Hook 内的 Phase 2 兼容胶水代码应有明确注释标注对应 `app.js` 功能，Phase 3 可精确删除。
12. 作为一名 Developer，`app.js` 的 `connect()` 和 `ws.onopen/onmessage/onclose/onerror` 应被移除，改为通过 `ChatAgent.registerHandler()` 注册消息回调和 `ChatAgent.send()` 发送消息。
13. 作为一名 Developer，`window.updateTokenBar()` 全局函数应退役，Header 直接从 context 读取 `tokenUsage`。
14. 作为一名 Developer，`window.__sidebarApi` 中的 `addCorrection()` / `clear()` / `getCount()` 应退役，CorrectionSidebar 直接从 context 读取 corrections 列表。
15. 作为一名 QA，所有 E2E 测试应通过（`mvn verify`），前端行为与迁移前完全一致。

## Implementation Decisions

### Phase 2 / Phase 3 衔接：双重路径

WS Hook 收到每条服务端消息后，同时执行两条路径：

1. **dispatch 进 reducer** → React 组件通过 context 读取（Header、CorrectionSidebar、未来 MessageList）
2. **遍历 vanilla 回调** → `app.js` 的 `handleMessage()` 继续操作 DOM（消息气泡渲染、报告弹窗、调试面板、输入框）

Hook 内部有 4 处 Phase 2 专职胶水代码，Phase 3 删除：

- `vanillaHandlers` Set 声明（存储 `app.js` 注册的回调）
- 每条 WS 消息的 `vanillaHandlers.forEach(fn => fn(msg))` 旁路调用
- `registerHandler()` 回调注册函数
- `send()` 暴露至 `window.ChatAgent`

`send()` 在 Phase 3 不再暴露但保留在 Hook 内部，由 InputBar 组件直接调用。

### WebSocket 生命周期

WS 连接在 `useChatWebSocket` Hook 的 `useEffect` 中建立、在 cleanup 中关闭。连接建立后自动从 localStorage 读取 sessionId 发送 RESUME_SESSION。断连时合成 `WS_CLOSED` 消息通知 reducer 和 vanilla 回调同时执行重置。

### Reducer State 形状

```typescript
interface Message {
  id: number;
  role: 'user' | 'agent';
  text: string;
  streaming?: boolean;
}

interface ChatState {
  messages: Message[];
  corrections: CorrectionData[];
  tokenUsage: number;
  connectionStatus: 'connecting' | 'connected' | 'disconnected';
  streamInProgress: boolean;
}
```

不进 reducer 的字段（留在 Hook 内 `useRef`）：`sessionId`、`mode`、`messageCount`——这些不驱动渲染，仅用于 WS 发送。

### messages 与 corrections 数据分离

两个独立数组在 reducer 中互不感知。MessageList 使用 `useMemo` 将两者合并为有序显示列表——属于这条消息的 correction bubble 插入在对应消息之后。本次 Phase 2 暂不实现 MessageList，但数据模型已为 Phase 3 做好准备。

### 消息归属表

| WS 消息 | Reducer dispatch | Vanilla 回调 | 备注 |
|---------|-----------------|-------------|------|
| AGENT_STREAM_DELTA | 创建/追加 message text | 创建/追加 DOM bubble | Phase 3 去 vanilla |
| AGENT_STREAM_END | 锁定 message，更新 tokenUsage | 替换全文，加🔊按钮，TTS，showTextInput | Phase 3 去 vanilla |
| CORRECTION_RESULT | 追加 corrections，更新 messages | 创建 correction bubble DOM | Sidebar 改走 React |
| SESSION_STARTED | 重置全部 state | 清空 DOM，按钮状态，showTextInput | — |
| SESSION_RESUMED | 批量重建 state | 重建全部消息 DOM | Phase 3 去 vanilla |
| SESSION_REPORT | —（Phase 3 再做） | showReport DOM，按钮状态 | Phase 3 迁移 report modal |
| STATE_UPDATE | tokenUsage | status 文字 | — |
| TOKEN_WARNING | — | —（改 toast） | Hook 内直接 push toast，不走回调 |
| ERROR（LLM 异常） | — | —（改 toast） | 同上 |
| ERROR（校验错误） | — | status 文字 | — |
| WS_CLOSED（合成） | 重置全部 state | resetUI | — |

### Toast 扩展

现有 `Toast.tsx` 组件支持 `type: 'success' | 'error'`，`error` 类型底色红色。TOKEN_WARNING 和 LLM 运行时异常在 WS Hook 内部直接 dispatch toast，不走 vanilla 回调路径。

### CorrectionSidebar 连接器简化

连接器改为从 `ChatContext` 读取 `corrections`，删除本地 `useState`、删除 `addCorrection()` / `clear()` / `getCount()` 命令式 API。纯组件 `CorrectionSidebar.tsx` 的 props 接口不变。`collapsed` 状态保留在连接器内（纯 UI 状态，不进 reducer）。

### Header Token Bar 改造

`Header` 从 `ChatContext` 读取 `tokenUsage`（替代 props 中的 `tokenPercent`）。`window.updateTokenBar()` 全局函数和 `mountHeader(props)` 的 props 参数退役。

### app.js 变更

删除：`connect()` 函数（含所有 `ws.onopen/onmessage/onclose/onerror` 回调）、`ws` 全局变量、末尾 `connect()` 调用、`updateTokenBar()` 函数、`handleCorrectionResult` 中的 `sidebar.addCorrection()`、所有 `sidebar.clear()` 调用。

新增：用 `ChatAgent.registerHandler(handleMessage)` 注册消息回调。用 `ChatAgent.send(msg)` 替换所有 `ws.send()`。`handleMessage` 新增 `case 'WS_CLOSED': resetUI(); break`。

`handleStreamDelta` / `handleStreamEnd` / `handleCorrectionResult` / `handleSessionResumed` / `showReport` / `setStatus` / `showTextInput` 内部逻辑代码**完全不改**。

### 构建

新增 Vite 配置文件打包 ChatProvider + reducer + WS Hook + Header + CorrectionSidebar 连接器为一个 IIFE bundle。`package.json` build 脚本串联三个 Vite 构建。`index.html` 加载新 bundle 的 `<script>` 和 `<link>`。

## Testing Decisions

### 单元测试范围

- **chatReducer 纯函数**：测试每条 WS 消息到 state 变换的正确性。纯函数，无 DOM、无网络依赖，Vitest 直接调用。
- **不测试**：WS Hook（依赖原生 WebSocket mock，收益有限，E2E 已有覆盖）、ChatContext（薄封装）、CorrectionSidebar 连接器（已有组件测试覆盖）、Header（已有组件测试覆盖）、Toast 扩展（已有测试覆盖）。

### 测试用例（chatReducer）

- AGENT_STREAM_DELTA：首次 delta 创建 message 并追加 text；后续 delta 追加 text；首 delta 设置 streamInProgress=true
- AGENT_STREAM_END：锁定 message 全文 + streaming=false；设置 streamInProgress=false；更新 tokenUsage
- CORRECTION_RESULT：追加 corrections 数组
- SESSION_STARTED：重置全部 state 为初始值
- SESSION_RESUMED：批量重建 messages、corrections、tokenUsage
- STATE_UPDATE：更新 tokenUsage
- WS_CLOSED：重置全部 state 为初始值，connectionStatus='disconnected'

### 测试参考先例

`src/__tests__/correction-sidebar/CorrectionSidebar.test.tsx`——相同模式：Vitest + describe/it 块，纯函数/组件测试。

### E2E 测试

不新增 E2E 测试。现有 7 个 IT 类通过 `mvn verify` 回归验证，确保聊天功能完全不变。

## Out of Scope

- **不迁移 MessageList 渲染**：消息气泡的 DOM 渲染（`handleStreamDelta` / `handleStreamEnd` / `handleCorrectionResult` / `handleSessionResumed` / `rebuildMessage`）保留在 `app.js` 中（Phase 3）。
- **不迁移 Report modal**：`showReport()` 保留在 `app.js` 中（Phase 3）。
- **不迁移 Debug panel**：`debugLog()` 保留在 `app.js` 中（Phase 3）。
- **不迁移 InputBar**：`sendTextInput()` / `showTextInput()` 保留在 `app.js` 中（Phase 3）。`send()` 通过 `ChatAgent.send()` 桥接。
- **不迁移 Status bar**：`setStatus()` 保留在 `app.js` 中（Phase 3），通过 vanilla 回调接收 STATE_UPDATE。
- **不迁移 flashcard 模块**：`flashcard.js` 保持不变。
- **不迁移 manage 页面**：`manage/*.js` 保持不变。
- **不迁移 login 页面**：`login/main.js` 保持不变。
- **不改变 WebSocket 协议**：前后端通信完全不变。
- **不修改 Spring Boot 后端代码**：Java 层零改动。
- **不做状态持久化**：reducer state 不写入 localStorage，页面刷新通过 RESUME_SESSION 全量恢复。

## Further Notes

- 此 PRD 基于 9 轮 grilling 决策，覆盖 WS Hook 集成方式、reducer state 字段、messages/corrections 数据分离、流式渲染性能、双重路径兼容、Toast 迁移、app.js 适配、CorrectionSidebar 连接器简化、Header token bar 改造等全部决策分支。
- Phase 2 是整个 Chat 页面迁移的架构转折点——一旦 `useReducer + context` 就位，Phase 3 的 MessageList / ReportModal / DebugPanel / InputBar 可在统一数据流上并行迁移。
- 实现完成后需更新 6 份文档：`docs/adr/centralized-chat-state.md`、`docs/adr/frontend-react-migration.md`、`README.md`、`AGENTS.md`、`CONTEXT.md`、`docs/architecture.md`。
