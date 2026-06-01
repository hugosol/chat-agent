# 05: `chatReducer` 纯函数 + Vitest 单元测试

**Status**: `ready-for-agent`

## Parent

[PRD：Chat 页面 React 渐进迁移 —— Phase 2: WebSocket 服务层 + 集中状态管理](../PRD-phase-2.md)

## What to build

实现 Phase 2 集中状态管理的纯逻辑基础件：类型定义 + reducer 纯函数 + 完整的 Vitest 单元测试。这是 4 个切片中唯一零依赖的独立单元——不涉及 React、DOM、WebSocket。

**新建文件 `src/state/chatState.ts`**：定义以下类型和初始状态：

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

`initialState` 常量导出：`messages: []`、`corrections: []`、`tokenUsage: 0`、`connectionStatus: 'connecting'`、`streamInProgress: false`。

**新建文件 `src/state/chatReducer.ts`**：导出一个 reducer 纯函数 `chatReducer(state: ChatState, action: Action): ChatState`。

Action 类型覆盖以下 7 种 WebSocket 消息（外加 `WS_CLOSED` 合成消息）：

| Action | 状态变换 |
|--------|---------|
| `AGENT_STREAM_DELTA` | 首次 delta 创建 `Message{ id, role:'agent', text:delta, streaming:true }` 并 push 到 `messages`；后续 delta 追加 text；首 delta 设置 `streamInProgress=true` |
| `AGENT_STREAM_END` | 找到对应 message，替换 `text` 为 `fullText`，设置 `streaming=false`、`streamInProgress=false`；更新 `tokenUsage` |
| `CORRECTION_RESULT` | `corrections` 追加整个 corrections 数组；同时找到对应 user message，在它之后插入一条标记 message（Phase 3 渲染用） |
| `SESSION_STARTED` | 重置全部 state 为 `initialState`，`connectionStatus='connected'` |
| `SESSION_RESUMED` | 批量重建 `messages`（从 payload）、`corrections`、`tokenUsage`，`connectionStatus='connected'` |
| `STATE_UPDATE` | 更新 `tokenUsage` |
| `WS_CLOSED` | 重置全部 state 为 `initialState`，`connectionStatus='disconnected'` |

messages 和 corrections 是两个独立数组，reducer 中互不感知——显示层合并留给 Phase 3 的 MessageList。

**新建文件 `src/__tests__/state/chatReducer.test.ts`**：使用 Vitest `describe/it/expect` 模式，参照 `src/__tests__/correction-sidebar/CorrectionSidebar.test.tsx`。

测试覆盖所有 7 种 action 类型：

- AGENT_STREAM_DELTA：首次 delta 创建 message + 追加 text；后续 delta 追加 text；首 delta 设置 streamInProgress=true
- AGENT_STREAM_END：锁定 message 全文 + streaming=false；设置 streamInProgress=false；更新 tokenUsage
- CORRECTION_RESULT：追加 corrections 数组
- SESSION_STARTED：重置全部 state 为初始值，connectionStatus='connected'
- SESSION_RESUMED：批量重建 messages、corrections、tokenUsage
- STATE_UPDATE：更新 tokenUsage
- WS_CLOSED：重置全部 state，connectionStatus='disconnected'

不测试：`toAction()` 消息映射函数（纯映射，无业务逻辑）、`CorrectionData` 类型（已在 `shared/types.ts` 定义）。

**非目标**：不写 WS Hook 测试、不写 React 组件测试、不写 E2E 测试。

## Acceptance criteria

- [ ] `src/state/chatState.ts` 存在，导出 `Message`、`ChatState`、`Action` 类型和 `initialState`
- [ ] `src/state/chatReducer.ts` 存在，导出 `chatReducer` 纯函数，处理全部 7 种 action
- [ ] `mvn test` 中 Vitest 步骤通过（`npm test` 成功），`chatReducer.test.ts` 全部用例通过
- [ ] TypeScript 编译无错误（`npx tsc --noEmit` 通过）
- [ ] 不破坏现有任何测试

## Blocked by

无 — 可立即开始
