# 14: 文档更新（Phase 3 完成）

**Status**: `ready-for-agent`

## Parent

[PRD：Chat 页面 React 渐进迁移 —— Phase 3: MessageList + ChatInput + Footer 迁入 React](../PRD-phase-3.md)

## What to build

Phase 3 全部实现完成后，更新 4 份项目文档以反映新的架构状态。

### 1. `docs/adr/frontend-react-migration.md` — Implementation Notes 追加

在 Implementation Notes 区域追加以下内容：

- **ChatProvider 依赖约束**：`initialState` 不可依赖模块级 `localStorage`，由 ChatProvider 运行时覆盖。
- **组件依赖关系表**：

| 组件 | 依赖 | 消费字段 |
|------|------|---------|
| Header | ChatProvider | `tokenUsage` |
| CorrectionSidebar | ChatProvider | `corrections` |
| MessageList | ChatProvider | `messages`, `corrections`, `streamInProgress` |
| ChatInput | ChatProvider | `streamInProgress`, `sessionStatus`, `send` |
| Footer | ChatProvider | `sessionStatus`, `send` |

- **Phase 3 新 Gotchas**：
  1. `useChatWebSocket` 已移除——WS 生命周期在 `ChatProvider` 内管理
  2. `send` 进 context——`useChatContext().send`，不再是 `window.ChatAgent.send()`
  3. E2E 选择器从 CSS class 迁移到 `data-testid` 属性
  4. CSS Modules 哈希类名——消息相关样式全部在 `MessageList.module.css`

### 2. `README.md` — Note block 更新

- `"Phase 2 complete"` → `"Phase 3 complete"`
- 列出新迁移的组件：MessageList、ChatInput、Footer
- 说明 `app.js` 保留模块（Report Modal、Status Bar、Debug Panel）和 `ChatAgent.send()` 已移除

### 3. `AGENTS.md` — "Frontend" 段落更新

- Phase 状态从 Phase 2 更新为 Phase 3
- `useChatWebSocket`、`ChatAgent.send()` 已移除说明
- WS 生命周期在 `ChatProvider` 内管理
- vanilla callback 桥接保留（仅对 5 种非 React 管辖消息类型触发）

### 4. `docs/architecture.md` — 决策 #48、#63 更新

- 决策 #48 末尾：Phase 3 从"计划中"改为"已完成"
- 决策 #63：Phase 3 状态从"剩余模块全部迁入 React"改为"已完成（MessageList + ChatInput + Footer）"

## Acceptance criteria

- [ ] `docs/adr/frontend-react-migration.md` Implementation Notes 包含依赖关系表和 Phase 3 Gotchas
- [ ] `README.md` Note block 更新为 "Phase 3 complete"，列出新组件
- [ ] `AGENTS.md` Frontend 段落反映当前架构（WS 内联、vanilla 桥接 5 种类型）
- [ ] `docs/architecture.md` 决策 #48 和 #63 标记 Phase 3 为已完成
- [ ] `CONTEXT.md` 确认无需更新

## Blocked by

- [09: ChatState 类型扩展 + reducer + context 类型脚手架](./09-chatstate-sessionstatus-reducer-context-scaffold.md)
- [10: MessageList 组件](./10-messagelist-component.md)
- [11: ChatInput + Footer 组件](./11-chatinput-footer-components.md)
- [12: ChatProvider WS 一体化 + vanilla 切离 + app.js 裁剪](./12-chatprovider-ws-vanilla-cutover-appjs-pruning.md)
- [13: E2E 选择器迁移 + 回归验证](./13-e2e-selector-migration.md)
