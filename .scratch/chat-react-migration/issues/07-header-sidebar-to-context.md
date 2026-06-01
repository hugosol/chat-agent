# 07: Header + CorrectionSidebar 迁移到 ChatContext

**Status**: `ready-for-agent`

## Parent

[PRD：Chat 页面 React 渐进迁移 —— Phase 2: WebSocket 服务层 + 集中状态管理](../PRD-phase-2.md)

## What to build

将 Header（token bar）和 CorrectionSidebar 两个 React 模块从命令式 API 驱动迁移到 ChatContext 自主读取——这是 Phase 1 "纯组件 + 连接器"模式向 Phase 2 `useReducer + context` 架构的最终整合。完成后，所有 React 组件成为自身数据的唯一消费者，不再依赖外部 `window.updateTokenBar()` / `window.__sidebarApi` 推送。

### Header Token Bar 改造

**`Header.tsx`**：改为从 `ChatContext` 读取 `tokenUsage`，替代现有的 `tokenPercent?: number` prop。

- 导入 `useChatContext`
- 在组件内部计算 `tokenPercent = tokenUsage / TOKEN_MAX * 100`（`TOKEN_MAX = 128000`，与后端 `ChatWebSocketHandler` 保持一致）
- 删除 `HeaderProps.tokenPercent` 字段

**`header-entry.tsx`**：移除 `props?: { tokenPercent?: number }` 参数，`mountHeader()` 不再传递 props。

- `mountHeader` 内部渲染 `<ChatProvider><Header /></ChatProvider>` —— 统一由 entry 管理 context 消费

### CorrectionSidebar 连接器简化

**`correction-sidebar-entry.tsx`**：从 `ChatContext` 读取 `corrections` 列表，删除本地 `useState`、删除命令式 API。

- 导入 `useChatContext`
- `corrections` 直接从 context 读取
- 删除 `addCorrection()` / `clear()` / `getCount()` 函数
- `mountCorrectionSidebar()` 不再返回 `CorrectionSidebarAPI` 对象
- `collapsed` 状态保留在连接器内（纯 UI 状态，不进 reducer）
- 渲染 `<CorrectionSidebar corrections={corrections} collapsed={collapsed} onToggle={() => setCollapsed(!collapsed)} />`

**`CorrectionSidebar.tsx` 纯组件**：Props 接口不变（`CorrectionSidebarProps { corrections, collapsed, onToggle }`），内部逻辑零改动。

### `chat-agent-entry.tsx` — 统一入口

将 Header 和 CorrectionSidebar 的渲染从各自的 inline mount 脚本迁移到统一入口：

```tsx
// chat-agent-entry.tsx 内部
function App() {
  return (
    <ChatProvider>
      <Header />                           // 改自 mountHeader(headerEl, { tokenPercent })
      <CorrectionSidebarConnector />        // 改自 mountCorrectionSidebar(root)
    </ChatProvider>
  );
}
```

`ChatProvider` 在入口层只渲染一次，Header 和 Sidebar 作为其子组件自然订阅 context。

### `app.js` 变更

**删除**：
- `handleCorrectionResult()` 中的 `window.__sidebarApi.addCorrection(c)` 调用（每个 correction 循环中的 push）
- 所有 `window.__sidebarApi.clear()` 调用（在 `resetUI()` 和 SESSION_STARTED handler 中）

`CorrectionData.messageId` 匹配逻辑保留在 reducer 中（CORRECTION_RESULT dispatch 时更新 messages 数组，Phase 3 渲染时用）。

**保留不变**：其他 handleMessage 分支的内部逻辑、correction bubble 的 DOM 创建（`handleCorrectionResult` 中创建摘要 bubble 的 DOM 操作保留）。

### `index.html` — 清理内联脚本

**删除**：
- `window.updateTokenBar = function(pct) { ... }` 全局函数定义
- `_mountHeader()` 和 `_mountSidebar()` 调用逻辑（即整个内联 `<script>` 块中与两种组件 mount 相关的代码）
- Header 和 CorrectionSidebar 的 `<script>` 加载（`header-bundle.js`、`correction-sidebar-bundle.js`）移除——由 `chat-bundle.js` 内部统一加载

**注意**：不删除 `react.production.min.js` / `react-dom.production.min.js` 加载和 `chat-bundle.js` 加载。`flashcard.js` 加载保持不变。

## Acceptance criteria

- [ ] `Header.tsx` 从 `ChatContext` 读取 `tokenUsage`，不再通过 props 接收 `tokenPercent`
- [ ] `header-entry.tsx` 的 `mountHeader()` 不再接受 props 参数
- [ ] `correction-sidebar-entry.tsx` 从 `ChatContext` 读取 `corrections`，删除 `addCorrection()` / `clear()` / `getCount()` 命令式 API
- [ ] `CorrectionSidebar.tsx` 纯组件零改动
- [ ] `chat-agent-entry.tsx` 在 `ChatProvider` 内直接渲染 `<Header />` 和 `<CorrectionSidebarConnector />`
- [ ] `index.html` 移除 `window.updateTokenBar()` 和内联 mount 脚本
- [ ] `app.js` 移除 `sidebar.addCorrection()` 和 `sidebar.clear()` 调用
- [ ] Token 使用进度条随对话自动更新（无需页面刷新）
- [ ] 纠错列表随 CORRECTION_RESULT 自动更新，Badge 计数自动 +1
- [ ] RESUME_SESSION 后 Header token bar 和 Sidebar 正确恢复
- [ ] SESSION_STARTED 后 Header token bar 和 Sidebar 正确清零
- [ ] `mvn compile` 成功，三个 Vite 构建全部产出
- [ ] `npm test` 全部单元测试通过（含现有 Header/Sidebar 测试的适配更新）
- [ ] `mvn verify` 全部 7 个 E2E IT 测试通过

## Blocked by

- [06: useChatWebSocket Hook + ChatProvider + app.js 集成 + Vite 构建 + Toast](./06-ws-hook-chat-provider-appjs-vite-toast.md)
