# 08: 文档更新

**Status**: `ready-for-agent`

## Parent

[PRD：Chat 页面 React 渐进迁移 —— Phase 2: WebSocket 服务层 + 集中状态管理](../PRD-phase-2.md)

## What to build

更新以下 6 份文档，反映 Phase 2 完成后的架构变更：

1. **`docs/adr/centralized-chat-state.md`** — 更新 Status 行和 Phase 2 状态。将 Phase 2 行从"计划中"更新为"已完成"，补充实际实现后的关键细节（reducer action 类型列表、context 结构、双重路径兼容的实际机制）。

2. **`docs/adr/frontend-react-migration.md`** — 更新 Implementation Notes 节，记录 Phase 2 新增的 `chat-bundle.js`（第三个 Vite library mode bundle）、`src/state/`（类型 + reducer）、`src/hooks/`（useChatWebSocket）目录结构。更新 package.json build 脚本说明（三入口串联）。补充新 Gotcha：`ChatProvider` 包裹顺序、`vanillaHandlers` 必须在 reducer dispatch 之后调用。

3. **`README.md`** — 更新项目结构图 `com.hugosol.chatagent/` 下的前端结构，反映新增的 `state/`（chatState.ts, chatReducer.ts）和 `hooks/`（useChatWebSocket.ts）目录。更新 Quick Reference 中 `mvn compile` / `mvn test` 的产物说明（从两个 bundle 改为三个）。

4. **`AGENTS.md`** — 更新 "Frontend" 段落：React 模块列表从 Header + CorrectionSidebar 扩展为 Header + CorrectionSidebar + ChatProvider（集中状态管理）。新增 `useReducer + context` 架构说明（一个 reducer、一个 context、WS Hook 双重路径）。更新 Vite 构建说明（三个 config）、文件路径引用。

5. **`CONTEXT.md`** — 在现有 "Runtime State" 节（或其他合适位置）新增 Phase 2 引入的前端状态相关术语（如有必要，如 ChatContext、reducer、WS Hook）。如果属于实现细节而非核心领域概念（大多数情况），则只添加最少量的领域相关条目。

6. **`docs/architecture.md`** — 更新前端架构图，反映 `useReducer + context` 集中状态管理替代旧的命令式 API 模式。Phase 2 完成后，前端状态流为：`WebSocket → useChatWebSocket Hook → dispatch(chatReducer) → ChatContext → Header / CorrectionSidebar`。

## Acceptance criteria

- [ ] `docs/adr/centralized-chat-state.md` Phase 2 行标记为已完成
- [ ] `docs/adr/frontend-react-migration.md` Implementation Notes 包含 Phase 2 新增文件/目录说明
- [ ] `README.md` 项目结构图包含 `state/` 和 `hooks/` 目录
- [ ] `AGENTS.md` 前端段落描述 `useReducer + context` 架构
- [ ] `CONTEXT.md` 包含 Phase 2 前端状态的领域术语（如有）
- [ ] `docs/architecture.md` 前端架构图更新
- [ ] 所有文档中的文件路径与实际代码一致

## Blocked by

- [07: Header + CorrectionSidebar 迁移到 ChatContext](./07-header-sidebar-to-context.md)
