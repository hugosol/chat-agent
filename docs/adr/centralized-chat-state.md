# Chat 页面 React 集中状态管理

**Status**: accepted

## Considered Options

| 方案 | 描述 | 拒绝原因 |
|------|------|---------|
| Zustand | 轻量级外部状态管理库 | 额外依赖。本项目 React 用于组件封装而非 SPA，引入专用库收益有限 |
| Redux | 经典 Flux 架构 | 过度工程化。Reducer + Action + Store + Middleware 对当前规模过于复杂 |
| 无状态管理（各自订阅） | 每个组件独立管理自己关心的 WebSocket 事件 | props drilling 导致耦合。多个组件需要响应同一 WS 消息（如 CORRECTION_RESULT 同时影响 sidebar 列表和 message bubble），各自独立订阅会导致代码分散、难以追踪 |
| Jotai / Recoil | 原子化状态 | 额外依赖。原子化模型对当前需求过于精细 |

## Choice

- **`useReducer + context`**：React 18 原生 API，无额外依赖。一个 reducer 统一处理所有 WebSocket 消息，组件通过 context 读取 state。

### 三期路线图

| Phase | 内容 | 状态管理方式 | 命令式 API |
|-------|------|------------|-----------|
| 1 | CorrectionSidebar 独立模块 | 本地 `useState`（entry wrapper 内管理） | 有（`addCorrection`/`clear`/`getCount`） |
| 2 | 抽取 WebSocket 服务层 + 引入 `useReducer + context` | 一个 reducer 处理所有 WS 消息，context 下发 state | 保留为兼容路径 |
| 3 | MessageList、ReportModal、DebugPanel、InputBar 全部迁移为 React | 统一 React 树，WS context → 各组件 | 移除 |

### Phase 1 纯组件 + 连接器模式

Phase 1 不引入集中状态管理。CorrectionSidebar 为纯展示组件（props in, callbacks out），entry wrapper 通过本地 `useState` 管理状态并暴露命令式 API。此设计为 Phase 2 的 `useReducer + context` 做前向兼容：

- 纯组件 props 接口直接映射到 reducer state 的子集
- 命令式 API 的方法（`addCorrection`/`clear`）对应 reducer 的 action type
- Phase 2 引入 context 时，组件内部无需修改

## Non-Decisions

以下不是本次决策的内容：

- 不做状态持久化（localStorage 等）——CorrectionSidebar 数据由 WebSocket 消息驱动，页面刷新通过 RESUME_SESSION 恢复
- 不做跨组件状态共享的 infrastructure——Phase 2 时再做
- 不引入过量的抽象层（如 middleware）

## Consequences

- **可逆转**：纯组件 + 连接器模式为向后兼容设计。如果 Phase 2 时发现 `useReducer + context` 不合适，可直接替代 entry wrapper 而不影响组件
- **渐进式**：每期均可独立验证。Phase 1 的 CorrectionSidebar 可独立运行，Phase 2 引入 context 时不破坏现有功能
- **简单优先**：Phase 1 避免过度设计，保持命令式 API 的简洁性
