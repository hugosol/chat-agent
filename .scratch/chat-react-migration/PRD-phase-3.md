# PRD：Chat 页面 React 渐进迁移 —— Phase 3: MessageList + ChatInput + Footer 迁入 React

**Status**: `ready-for-agent`

## Problem Statement

Chat Agent 聊天页面的核心 UI —— 消息气泡列表（`#messages`）、文本输入栏（`#textInputBar`）、底部控制栏（Mode 选择 + Start/End 按钮）—— 当前全部由 `app.js` 的 vanilla JS 通过直接 DOM 操作渲染。这带来了三个问题：

**1. 双重渲染路径。** Phase 2 引入 `useReducer + context` 后，每条 WS 消息同时走两条路径：React reducer 更新 state → Header/CorrectionSidebar 读取，以及 vanilla callback → `app.js` 操作 DOM。消息渲染仍在 vanilla 侧，reducer 中的 `state.messages` 实际无人消费。两条路径并行运行，增加了理解和调试的成本。

**2. 状态分散。** Footer（Mode/Start/End 按钮）的 disabled 状态依赖 `sessionId != null` 判断，ChatInput 的 disabled 依赖 `streamInProgress` 判断，但这些状态散落在 `app.js` 的局部变量和 React context 之间，无法统一管理。"会话是否活跃"这个简单概念没有一个明确的承载点。

**3. Phase 2 胶水代码。** `ChatAgent.send()` 全局挂载属于 Phase 2 为 vanilla JS 提供的发送桥接 —— Phase 3 后所有发送由 React 接管，必须删除。但消息接收侧的桥接（`vanillaHandlers`、`registerHandler`、旁路分发）需要保留并改造 —— 因为 Report Modal、Status Bar、Debug Panel 仍在 `app.js` vanilla 处理中，必须继续接收 `SESSION_REPORT`、`ERROR`、`TOKEN_WARNING`、`STATE_UPDATE`、`WS_CLOSED` 五种消息。改造方向：旁路调用从"对所有消息广播"改为"仅对非 React 管辖的消息类型触发"。

Learner 看不到这些问题 —— 但对 Developer 来说，每次改动聊天 UI 都需要在 vanilla JS 和 React 之间切换心智模型，这是最大的维护瓶颈。

## Solution

将 MessageList（消息气泡列表 + 纠错气泡 + 流式渲染 + 折叠/展开）、ChatInput（文本输入 + 发送）、Footer（Mode Select + Start/End 按钮）全部迁移为 React 组件，通过 Portal 挂载到现有 DOM 节点，完整接入 `ChatProvider` 的 context 数据流。

核心技术决策：

- **ChatState 新增 `sessionStatus: 'idle' | 'active'`**，替代 `sessionId != null` 判断。`SESSION_STARTED` / `SESSION_RESUMED` → `'active'`，`SESSION_REPORT` / `WS_CLOSED` → `'idle'`。初始值由 `ChatProvider` 运行时从 `localStorage` 读取（有 sessionId 则为 `'active'`）。

- **新增 `USER_MESSAGE_SENT` action**（用户发送消息时 dispatch）和 **`SESSION_REPORT` action**（reducer 设置 `sessionStatus: 'idle'`）。

- **ChatProvider 内联 WS 生命周期**，将 `useChatWebSocket` Hook 的全部逻辑（WebSocket 连接、onmessage→dispatch、onopen RESUME_SESSION、onclose WS_CLOSED）搬入 `ChatProvider` 内部。Context value 新增 `send: (msg) => void` 供组件发送 WS 消息。移除独立的 `WsConnector` 组件。

- **Phase 2 胶水代码改造**：删除 `ChatAgent.send()` 全局挂载（发送全部由 React 接管）。`vanillaHandlers` Set 和 `registerHandler()` 保留并内化到 ChatProvider 内部（不再暴露为独立导出），旁路调用从"对所有消息广播"改为"仅对 5 种非 React 管辖的消息类型触发"（`SESSION_REPORT`、`ERROR`、`TOKEN_WARNING`、`STATE_UPDATE`、`WS_CLOSED`）。`useChatWebSocket.ts` 文件删除（WS 生命周期内联进 ChatProvider）。

- **`app.js` 裁剪**：删除所有消息渲染相关函数（`handleStreamDelta`、`handleStreamEnd`、`handleCorrectionResult`、`handleSessionResumed`、`rebuildMessage`、`createMessageElement`、`createCorrectionBubble`、`addPlayButton`、`showTextInput`、`sendTextInput`、`handleCollapse`、`sendStart`）及对应 Event Listener。保留 `SESSION_REPORT`、`ERROR`、`TOKEN_WARNING`、`STATE_UPDATE`、`WS_CLOSED` 的 vanilla callback 处理（报告弹窗、状态栏、debug log 仍走 vanilla）。

- **`messages` 与 `corrections` 数据层独立存储**，MessageList 渲染时按 `messageId` 分组、在 user 消息后插入纠错气泡（纯渲染行为）。

- **E2E 选择器迁移**：CSS Modules 哈希化后，4 个核心 CSS class 选择器（`.message.user`、`.message.agent`、`.message.correction-bubble`、`.correction-bubble .content-text`）改为 `data-testid` 属性选择器。

## User Stories

1. 作为一名 Learner，在聊天过程中，我和 Agent 的消息应正常显示为气泡（user 靠右蓝色、agent 靠左深色、流式消息带闪烁光标）。
2. 作为一名 Learner，当 Agent 回复到达时，流式消息气泡应逐 token 追加，完成后自动显示 🔊 播放按钮并播放 TTS。
3. 作为一名 Learner，当纠错到达时，对应的 user 消息气泡下方应插入编号纠错摘要气泡。
4. 作为一名 Learner，当消息数量超过 10 条时，旧消息应折叠，出现"Show earlier messages"按钮，点击后展开全部。
5. 作为一名 Learner，消息列表在收到新消息时应自动滚动到底部。
6. 作为一名 Learner，在会话活跃期间，文本输入框应可用，按 Enter 或点击 Send 发送消息。
7. 作为一名 Learner，在 Agent 回复进行中（streamInProgress）或会话未开始时，文本输入框应禁用。
8. 作为一名 Learner，开始新会话后，所有消息、纠错、折叠状态应立刻清空。
9. 作为一名 Learner，通过页面刷新恢复会话（RESUME_SESSION）后，所有历史消息和纠错气泡应正确重建。
10. 作为一名 Learner，会话活跃期间，Mode Select 应禁用、Start 应禁用、End 应可用。
11. 作为一名 Learner，会话结束或未开始期间，Mode Select 应可用、Start 应可用、End 应禁用。
12. 作为一名 Learner，在新标签页打开页面时，Footer 应根据 localStorage 中是否有 sessionId 来判断初始状态（有则为 active），并在 RESUME_SESSION 成功后确认恢复。
13. 作为一名 Learner，点击 Start Session 后，Footer 发送 START_SESSION 并等待服务端确认。
14. 作为一名 Learner，点击 End & Report 后，Footer 发送 END_SESSION，报告弹窗仍由原有的 vanilla JS 控制显示。
15. 作为一名 Developer，`chatReducer` 的 `USER_MESSAGE_SENT`、`SESSION_REPORT` 和所有 `sessionStatus` 变更应通过 Vitest 单元测试覆盖。
16. 作为一名 Developer，MessageList 组件应可通过 Vitest 测试验证消息气泡渲染、流式追加、纠错分组插入、折叠/展开。
17. 作为一名 Developer，ChatInput 组件应可通过 Vitest 测试验证 disabled 状态、Enter 发送、nextId 推导。
18. 作为一名 Developer，Footer 组件应可通过 Vitest 测试验证 Start/End disabled 逻辑、Mode 切换。
19. 作为一名 Developer，ChatProvider 的 send 应通过 context 暴露，任何 `<ChatProvider>` 树内组件均可使用 `useChatContext().send` 发送 WS 消息。
20. 作为一名 Developer，`ChatAgent.send()` 全局挂载应移除，WS 发送全部由 React 组件通过 `useChatContext().send` 完成。vanilla callback 桥接机制应改造为仅对非 React 管辖的消息类型触发，不再对所有消息广播。
21. 作为一名 Developer，`app.js` 的消息渲染逻辑应全部移除，只保留报告弹窗、状态栏、debug log、WS_CLOSED 重置的 vanilla 处理。
22. 作为一名 QA，所有现有 E2E 测试应通过（`mvn verify`），消息相关选择器已从 CSS class 迁移到 data-testid。

## Implementation Decisions

### ChatState 扩展

新增字段 `sessionStatus: 'idle' | 'active'`，替代原有 `sessionId != null` 的隐含判断。Footer、ChatInput 的 disabled 逻辑统一读此字段。

`streamInProgress` 继续负责"Agent 正在回复中"的粒度，和 `sessionStatus` 组合覆盖三种 UI 状态：idle（Start 可用、End 禁用、输入隐藏）、active+not streaming（输入可用）、active+streaming（输入禁用）。

`sessionStatus` 初始值：ChatProvider 在 `useReducer` 初始化时，检测 `localStorage.getItem('sessionId')` 是否存在 —— 存在则乐观设为 `'active'`（不验证是否过期）。若另一标签页已打开，WS 连接后自动发送 RESUME_SESSION 将校正到正确状态。

新增两个 Action：

```typescript
| { type: "USER_MESSAGE_SENT"; messageId: number; text: string }
| { type: "SESSION_REPORT"; report: Record<string, unknown> }
```

Reducer 中 `USER_MESSAGE_SENT` append 用户消息到 messages 数组；`SESSION_REPORT` 设置 `sessionStatus: 'idle'`、`streamInProgress: false`。

### messageId 分配

用户发送消息时，`messageId` 从 `state.messages` 派生：`messages.filter(m => m.role === 'user').length + 1`。不需要额外计数器字段。`SESSION_RESUMED` 重建 messages 后，nextId 自然正确。

### ChatProvider 重构 —— WS 生命周期内联

将 `useChatWebSocket` Hook 的全部逻辑（WebSocket 连接、onmessage→dispatch、onopen → 自动 RESUME_SESSION、onclose → dispatch WS_CLOSED）搬入 `ChatProvider` 内部 `useEffect`。

`send` 函数通过 `useCallback` 包装，以 `wsRef` 持有 WS 实例，加入 context value。Context 类型变为 `{ state, dispatch, send }`。

移除独立的 `WsConnector` 组件和 `useChatWebSocket` Hook 文件。`chat-agent-entry.tsx` 中不再 import `useChatWebSocket`。

### Phase 2 胶水代码改造

`useChatWebSocket.ts` 中 4 处 `// Phase 2 compat — Phase 3 移除` 标记的处理：

| # | 胶水代码 | Phase 3 处理 | 原因 |
|---|---|---|---|
| 1 | `vanillaHandlers` Set | **保留**，移入 ChatProvider 内部 | `app.js` 的 `handleMessage` 仍需注册回调 |
| 2 | `vanillaHandlers.forEach(fn => fn(msg))` 旁路调用 | **改造**，仅对 5 种消息类型触发 | 避免双重渲染（消息已由 React 渲染） |
| 3 | `registerHandler()` | **保留**，移入 ChatProvider 内部（注册入口仍需暴露给 `app.js` 初始化） | 不改动 `app.js` 的初始化代码 |
| 4 | `send()` 导出到 `window.ChatAgent` | **删除** | 发送全部由 React 接管 |

旁路调用的 5 种触发类型（对应未迁移模块）：

| 消息类型 | Vanilla 处理 | 所属未迁移模块 |
|---|---|---|
| `SESSION_REPORT` | `showReport()` | Report Modal |
| `ERROR` | `setStatus()` + `debugLog()` | Status Bar + Debug Panel |
| `TOKEN_WARNING` | `setStatus()` + `debugLog()` | Status Bar + Debug Panel |
| `STATE_UPDATE` | `setStatus()` + `debugLog()` | Status Bar + Debug Panel |
| `WS_CLOSED` | `resetUI()`（`synth.cancel()` + 本地变量） | 胶水逻辑 |

`app.js` 的 `handleMessage` 函数继续作为 vanilla callback 注册，但 case 分支仅保留上述 5 种。消息渲染 case（`SESSION_STARTED`、`AGENT_STREAM_DELTA`、`AGENT_STREAM_END`、`CORRECTION_RESULT`、`SESSION_RESUMED`）删除 —— 这些由 React reducer 独占处理。

### 组件架构

三个新组件均为 `<ChatProvider>` 树内的直接子节点，通过 Portal 挂载到现有 DOM：

**MessageList** → Portal 到 `#messages`
- 消费 `state.messages`、`state.corrections`、`state.streamInProgress`
- 渲染算法：Option A 统一循环 —— 遍历 messages，每遇 `role: 'user'` 后按 `messageId` 查找对应 corrections 插入纠错气泡，继续下一消息
- 流式消息渲染闪烁光标（`streaming: true`），`AGENT_STREAM_END` 后移除光标、显示 🔊 播放按钮
- 折叠/展开：内部 `useState`，超过 10 条消息时折叠旧消息，显示"Show earlier"按钮
- Auto-scroll：`useEffect` 监听 messages 变化，滚动 `#chatArea` 到底部
- TTS：`useEffect` 监听消息从 `streaming: true` → `streaming: false` 转换时，调用 `speakText()`（复用 `src/shared/tts.ts`）
- 纠错气泡编号摘要 `1. original → corrected`

**ChatInput** → Portal 到 `#textInputBar`
- 消费 `state.streamInProgress`、`state.sessionStatus`、`send`
- `disabled` 条件：`sessionStatus !== 'active' || streamInProgress`
- Enter 调用 `handleSend`：从 `state.messages` 派生 `nextId` → `send({ type: 'USER_INPUT', text, messageId: nextId })` → `dispatch({ type: 'USER_MESSAGE_SENT', messageId: nextId, text })`

**Footer** → Portal 到 `<footer>`
- 消费 `state.sessionStatus`、`send`
- Mode Select（`WORKPLACE_STANDUP` / `DAILY_TALK`）：`sessionStatus === 'active'` 时禁用
- Start：`send({ type: 'START_SESSION', mode })`，`sessionStatus === 'active'` 时禁用
- End：`send({ type: 'END_SESSION' })`，`sessionStatus === 'idle'` 时禁用

### `app.js` 保留/删除清单

**删除：**
- 函数：`handleStreamDelta`、`handleStreamEnd`、`handleCorrectionResult`、`handleSessionResumed`、`rebuildMessage`、`createMessageElement`、`createCorrectionBubble`、`addPlayButton`、`showTextInput`、`sendTextInput`、`handleCollapse`、`sendStart`
- `handleMessage` 中的 `SESSION_STARTED`、`AGENT_STREAM_DELTA`、`AGENT_STREAM_END`、`CORRECTION_RESULT`、`SESSION_RESUMED` case
- `startBtn`、`endBtn`、`sendTextBtn`、`textInput`、`newSessionBtn`、`showEarlierBtn` 的 Event Listener 绑定
- `resetUI()` 中 6 行 React 管辖的 DOM 操作（只保留 `synth.cancel()`）
- `newSessionBtn` HTML 元素（`index.html` 模板中删除）

**保留：**
- `handleMessage` 函数 + vanilla callback 注册 — `app.js` 的 `handleMessage` 继续注册为 vanilla callback，但 ChatProvider 内部旁路分发仅对 5 种非 React 管辖消息触发，不再对所有消息广播
- Debug panel toggle/clear
- Flashcard 面板互斥（`window.activePanel`）
- `closeReportBtn` Event Listener
- `visibilitychange` 监听
- `escapeHtml` 工具函数

### CSS 迁移

`style.css` 中约 110 行消息相关规则（`.message`、`.message.user`、`.message.agent`、`.message.collapsed`、`.message .role`、`.btn-play`、`.stream-cursor`、`@keyframes blink`、`.correction-bubble` 等）迁移到 `MessageList.module.css`（CSS Modules 哈希化）。

`ChatInput.module.css`、`Footer.module.css` 新建。

### E2E 选择器迁移

CSS Modules 哈希化后，以下选择器必须改为 `data-testid`：

| 原选择器 | 新选择器 |
|---|---|
| `.message.user` | `[data-testid="message"][data-role="user"]` |
| `.message.agent` | `[data-testid="message"][data-role="agent"]` |
| `.message.correction-bubble` | `[data-testid="correction-bubble"]` |
| `.correction-bubble .content-text` | `[data-testid="correction-bubble"] [data-testid="message-content"]` |

影响 `E2ETestBase.java` 中 7 个 helper 方法：`countUserMessages`、`countAgentMessages`、`countCorrectionBubbles`、`hasCorrectionBubbleWith`、`reloadPage`、`waitForAgentResponse` 及相关 IT 测试类。

Footer/Input 的 ID 选择器（`#modeSelect`、`#startBtn`、`#endBtn`、`#textInputBar`、`#textInput`、`#sendTextBtn`）Portal 挂载后 HTML 保留这些 ID，无需变更。

### 构建

无需新增 Vite 配置。MessageList、ChatInput、Footer 均为 `chat-agent-entry.tsx` 的依赖，由现有 `vite.config.chat.ts` 打包进 `chat-bundle.js` + `chat-bundle.css`。

### 文档更新（实现完成后写入）

以下为具体变更内容，暂不执行写入：

**`docs/adr/frontend-react-migration.md`** — Implementation Notes 追加：
- ChatProvider 依赖约束（`initialState` 不可依赖模块级 localStorage，由 ChatProvider 运行时覆盖）
- 组件依赖关系表（Header / CorrectionSidebar 双模式 + MessageList / ChatInput / Footer 的 ChatProvider 依赖及消费字段）
- Phase 3 新 Gotchas：`useChatWebSocket` 移除、`send` 进 context、E2E 选择器迁移、CSS Modules 样式迁移

**`README.md`** — 第 35 行 Note block 更新：
- "Phase 2 complete" → "Phase 3 complete"
- 列出新迁移的组件（MessageList、ChatInput、Footer）
- 说明 `app.js` 保留模块和 `ChatAgent.send()` 已移除

**`AGENTS.md`** — "Frontend" 段落（第 43 行）更新：
- Phase 状态更新
- `useChatWebSocket`、`ChatAgent.send()` 已移除说明
- WS 生命周期在 `ChatProvider` 内管理
- `ChatAgent.send()` 全局挂载已移除；vanilla callback 桥接保留（仅对 5 种非 React 管辖消息类型触发）

**`docs/architecture.md`** — 决策 #48、#63 更新：
- 决策 #48 末尾：Phase 3 从"计划中"改为"已完成"
- 决策 #63：Phase 3 状态从"剩余模块全部迁入 React"改为"已完成（MessageList + ChatInput + Footer）"

**`CONTEXT.md`** — 无需更新（无新领域概念引入）。

## Testing Decisions

### 什么构成一个好的测试

测试外部行为而非实现细节。对于 reducer：给定 state + action，断言返回的新 state 形状。对于组件：给定 props/context，渲染 → 断言 DOM 中的可见内容（文本、disabled 属性、data-testid 存在性）。不过度 mock 内部状态。

### 测试模块

- **`chatReducer`（扩展测试）**：新增 `USER_MESSAGE_SENT`、`SESSION_REPORT`、各 action 的 `sessionStatus` 变更。纯函数，Vitest 直接调用。
- **`MessageList`（新增）**：消息气泡渲染（user/agent 角色、文本内容、data-testid）、流式追加（闪烁光标存在性）、纠错气泡分组插入（messageId 关联）、折叠/展开逻辑、TTS 播放按钮渲染。WIP ChatProvider 提供 state + dispatch + send。
- **`ChatInput`（新增）**：disabled 状态（`sessionStatus === 'idle'`、`streamInProgress`）、Enter 键触发 send + dispatch、send 被调用时携带正确的 USER_INPUT payload 和 messageId。
- **`Footer`（新增）**：Start/End 按钮 disabled 逻辑、Mode 切换 disabled 逻辑、按钮点击时 send 被调用。

### 测试参考先例

`src/__tests__/state/chatReducer.test.ts` — 纯函数 reducer 测试模式。
`src/__tests__/correction-sidebar/CorrectionSidebar.test.tsx` — React 组件测试模式（Vitest + describe/it + ChatProvider 包裹）。
`src/__tests__/header/Header.test.tsx` — 相同模式，Portal 组件测试。

### E2E 测试

不新增 E2E 测试。现有 7 个 IT 类通过 `mvn verify` 回归验证。选择器迁移通过逐方法替换静态选择器完成，不改变断言逻辑。

## Out of Scope

- **不迁移 Report modal**：`showReport()` 保留在 `app.js` 中（vanilla handler 接收 `SESSION_REPORT`）。
- **不迁移 Debug panel**：`debugLog()` 保留在 `app.js` 中。
- **不迁移 Status bar**：`setStatus()` 保留在 `app.js` 中（vanilla handler 接收 `STATE_UPDATE`）。
- **不迁移 Page Visibility auto-resume**：原 `visibilitychange` 监听保留在 `app.js`，暂不迁入 React。用户可通过手动刷新恢复。
- **不迁移 flashcard 模块**：`flashcard.js` 保持不变。
- **不迁移 manage 页面**：`manage/*.js` 保持不变。
- **不迁移 login 页面**：`login/main.js` 保持不变。
- **不改变 WebSocket 协议**：前后端通信完全不变。
- **不修改 Spring Boot 后端代码**：Java 层零改动。

## Further Notes

- 此 PRD 基于 12 轮 grilling 决策，覆盖组件边界、Portal 挂载方式、sessionStatus 设计、messageId 分配、WS send 机制、messages/corrections 数据分离、渲染算法、折叠/展开归属、auto-scroll 策略、CSS 迁移方案、E2E 选择器迁移、Phase 2 胶水代码改造（保留 vanilla 桥接但缩窄触发范围）、app.js 裁剪、ChatProvider 重构、组件依赖约束、文档更新等全部决策分支。
- Phase 3 完成后，Chat 页面的核心交互（消息渲染、输入、会话控制）全部由 React 统一管理。剩余的 Report modal、Debug panel、Flashcard panel 可在后续独立迁移，不再受架构约束。
- 实现完成后需更新 4 份文档（建议在 commit message 中标注，避免遗漏）：`docs/adr/frontend-react-migration.md`、`README.md`、`AGENTS.md`、`docs/architecture.md`。
