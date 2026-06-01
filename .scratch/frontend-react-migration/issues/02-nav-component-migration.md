# 02 — Nav Component Migration + Docs

**Status**: `completed`

## Parent

[PRD: 前端 React + TypeScript 渐进迁移——首阶段 Nav 组件](../PRD.md)

## What to build

用 React + TypeScript + CSS Modules 重写 `shared/nav.js`，替换为 Nav 组件。包含 token 进度条、logout 按钮、汉堡菜单侧边栏，通过 `window.ChatAgentNav` 全局命名空间挂载。同时适配 E2E 测试选择器到 `data-testid`，并验证文档一致性。

具体交付：

**组件代码**（`src/main/frontend/src/components/Nav/`）：
- `Nav.tsx`：纯 props-driven 组件，接收 `{ tokenPercent?: number }`。`showToken` 由组件内部通过 `location.pathname` 推导（路径为 `/` 或 `/index.html` 时显示 token 进度条，其他路径隐藏）。token 进度条按 0-49 绿、50-79 黄、80-100 红三段变色。logout 按钮使用 `<form action="/logout" method="post">`。汉堡按钮 `aria-expanded` 跟踪侧边栏状态。打开侧边栏时收起纠错侧边栏（`#correctionSidebar`）。导航链接使用 `data-testid="nav-link"` + `data-active` 标识高亮。
- `Nav.module.css`：从 `shared/base.css`（约 84 行：`.nav-sidebar` / `.nav-sidebar-header` / `.nav-link` / `.nav-menu-btn`）和 `style.css`（约 35 行：`.token-bar-container` / `.token-bar-fill` / `.token-bar-pct`）提取 nav 相关样式，合计约 120 行。
- `Nav.test.tsx`：Vitest + React Testing Library，约 10 个测试用例：token 0/50/80 时颜色正确、非聊天页不渲染 token 条、汉堡按钮点击展开侧边栏、关闭按钮点击收起侧边栏、Chat 页面链接高亮、Manage 页面链接高亮、logout 按钮 form action 正确、`data-testid` 属性存在、`aria-expanded` 正确跟踪状态。

**入口薄层**（`src/main/frontend/src/entry/nav-entry.tsx`）：
- 暴露 `window.ChatAgentNav = { mount(container: HTMLElement, props?: { tokenPercent?: number }): void }`，内部调用 `createRoot(container).render(<Nav {...props} />)`。

**HTML 页面集成**：
- `index.html`：`<header>` 变为空元素（移除 `data-show-token`），添加 `<script src="/shared/react.production.min.js">` + `<script src="/shared/react-dom.production.min.js">` + `<script src="/shared/nav-bundle.js">`。inline mount 脚本创建 `ChatAgentNav.mount(header)`。额外 inline script 暴露 `window.updateTokenBar = (pct) => { /* re-render Nav with new tokenPercent */ }` 作为与 `app.js` 的临时桥接。
- `manage/index.html`：同上，但 mount 脚本不传 `tokenPercent`。

**Token 桥接**：
- `app.js` 中 3 处 `updateTokenBar()` 调用改为调用 `window.updateTokenBar(pct)`，删除 `app.js` 内部直接操作 `#tokenBar` / `#tokenPct` DOM 的代码（`app.js` 内 `updateTokenBar` 函数本身、`tokenBar` 和 `tokenPct` 的 `getElementById` 缓存）。

**CSS 清理**：
- `shared/base.css`：删除 `.nav-sidebar` / `.nav-sidebar-header` / `.nav-link` / `.nav-menu-btn` 相关约 84 行。
- `style.css`：删除 `.token-bar-container` / `.token-bar-fill` / `.token-bar-pct` 相关约 35 行（`shared/base.css` 中也有重复的 token bar 样式，一并删除）。

**旧代码删除**：
- 删除 `src/main/resources/static/shared/nav.js`。

**E2E 适配**（`ManagePageIT.java`）：
- 15 处 CSS class 选择器替换为 `data-testid` 选择器（如 `.nav-menu-btn` → `[data-testid="nav-menu-btn"]`、`.nav-link.active` → `[data-testid="nav-link"][data-active="true"]`）。
- 2 处 `classList.contains('open')` JS eval 替换为 `getAttribute('aria-expanded') === 'true'`。

**文档验证**：
- 确认 `README.md` 技术栈表准确、无 "no npm/webpack" 过时声明。
- 确认 `AGENTS.md` 前端构建命令描述与实际一致。
- 确认 `docs/architecture.md` 决策 #11、#14、#48 反映实际实现。
- `docs/adr/frontend-react-migration.md` 已存在且 accepted，无需新建。

## Acceptance criteria

- [ ] `npm test` 全部通过（约 10 个 Nav 组件单元测试）
- [ ] `mvn compile` 成功，包括前端构建
- [ ] `mvn verify` 全部通过（7 个 E2E IT 类，ManagePageIT 选择器已适配，其余 6 个不受影响）
- [ ] 聊天页：logout 按钮、token 进度条（绿/黄/红三段色）、汉堡菜单侧边栏正常工作
- [ ] 聊天页：token 进度条实时反映 LLM token 用量
- [ ] 管理页：logout 按钮、汉堡菜单侧边栏正常工作，**不显示** token 进度条
- [ ] 侧边栏 Chat/Manage 链接高亮随当前页面自动切换
- [ ] 打开导航侧边栏时纠错侧边栏自动收起
- [ ] `aria-expanded` 正确跟踪侧边栏展开/收起状态
- [ ] 所有 `data-testid` 属性存在于关键交互元素
- [ ] React 和 ReactDOM 从本地服务器加载（非 CDN）
- [ ] `shared/nav.js` 已删除，CSS 中 nav 相关样式已移除
- [ ] 文档（README、AGENTS.md、architecture.md）一致且准确

## Blocked by

- [01 — Build Toolchain + Tracer Bullet](./01-build-toolchain-tracer-bullet.md)
