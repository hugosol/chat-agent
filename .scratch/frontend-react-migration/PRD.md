# PRD：前端 React + TypeScript 渐进迁移——首阶段 Nav 组件

## Problem Statement

当前 Chat Agent 的前端全部使用原生 HTML + Vanilla JS（5 个 JS 文件，总计约 1600 行），无类型安全、无组件隔离、无单元测试。随着闪卡模块、管理页面等功能的持续增长，DOM 操作分散在多个 IIFE 中，共享状态通过 `window` 全局变量传递，维护成本持续上升。Learner 需要更可靠的 UI 和更快的迭代速度，但一次性全量迁移风险过高。

## Solution

引入 React 18 + TypeScript + Vite 作为前端构建工具链，采用**渐进迁移**策略：
- 首阶段从最独立、最明确的模块切入——`shared/nav.js`（导航栏：logout 按钮、token 进度条、汉堡菜单侧边栏）。
- React 通过本地托管文件加载（不依赖 CDN），Vite Library Mode 将每个组件打包为独立 IIFE bundle。
- 组件使用 CSS Modules 隔离样式，Vitest + React Testing Library 编写单元测试。
- `mvn compile` 通过 `exec-maven-plugin` 自动触发前端构建，开发者仍需本地安装 Node.js。
- 不改动 WebSocket 协议、不受影响的 vanilla JS 模块保持不动。

## User Stories

1. 作为一名 Learner，我使用聊天页面和管理页面时，导航栏的外观和行为应保持不变——logout 按钮、token 进度条、汉堡菜单侧边栏照常工作。
2. 作为一名 Learner，当我在聊天页与管理页之间切换时，导航栏的菜单高亮应自动显示当前所在页面。
3. 作为一名 Learner，token 进度条在聊天页中应实时反映当前 token 用量百分比（0-100），并按绿/黄/红三段变色。
4. 作为一名 Learner，管理页面不应显示 token 进度条（因为管理页不消耗 LLM token）。
5. 作为一名 Developer，Nav 组件应使用 TypeScript 编写，props 接口类型安全，所有行为可通过 React Testing Library 单元测试验证。
6. 作为一名 Developer，Nav 组件的 CSS 样式应与项目中其他模块隔离，不会产生全局样式冲突。
7. 作为一名 Developer，前端构建应集成到 `mvn compile` 流程中，无需额外构建命令。
8. 作为一名 Developer，React 和 ReactDOM 文件应从项目自身服务器加载，不依赖外部 CDN，确保内网环境可用。
9. 作为一名 QA，E2E 测试的选择器应使用 `data-testid` 属性而非 CSS class 名，避免因 CSS Modules 哈希化导致测试失败。

## Implementation Decisions

### 构建工具链

- **Vite Library Mode**：将每个组件打包为独立 IIFE bundle，暴露全局命名空间（如 `window.ChatAgentNav`）。
- **React 本地托管**：`react.production.min.js` 和 `react-dom.production.min.js`（React 18.x）存放在 `static/shared/` 目录，HTML 页面通过 `<script src="/shared/...">` 引入。
- **Maven 集成**：`exec-maven-plugin` 在 `process-resources` 阶段执行 `npm run build`，产物输出到 `src/main/resources/static/shared/`。Node.js 由开发者自行安装。
- **TypeScript strict 模式**：`tsconfig.json` 启用 `strict: true`，从第一行代码起保证类型安全。

### 组件架构

- **纯 props-driven 组件**：`Nav.tsx` 接收 `{ tokenPercent?: number }` 作为唯一 prop。`showToken` 由组件内部通过 `location.pathname` 推导（`/` 或 `/index.html` 时显示，其他路径隐藏）。
- **CSS Modules**：`Nav.module.css` 从全局 `style.css` 拆出约 120 行 nav 相关样式，构建时自动哈希化 class 名。
- **入口薄层**：`nav-entry.tsx` 暴露 `window.ChatAgentNav = { mount(container, props) }`，不做业务逻辑。
- **向后兼容桥接**：HTML 页面的 inline script 中通过 `window.updateTokenBar(v)` 桥接与 `app.js` 的 token 更新通信。此桥接是临时措施，待 Chat 模块也 React 化后自然删除。

### 路由与状态

- 导航高亮由 Nav 组件内部读取 `window.location.pathname` 判断当前页面。
- 侧边栏展开/收起使用 `aria-expanded` 属性标识状态，替代 CSS class `.open`。
- 打开侧边栏时，通过 DOM 操作自动收起纠错侧边栏（`#correctionSidebar`）——此逻辑保留在 Nav 组件内，不改动 `app.js`。

### 数据流

```
app.js (vanilla JS)
  → window.updateTokenBar(pct)          // 临时桥接（Chat React 化后删除）
    → inline script 更新 tokenPercent 状态
      → ReactDOM.createRoot.render(<Nav tokenPercent={pct} />)
        → Nav 组件渲染 token 进度条
```

### 文件结构

```
src/main/frontend/
├── package.json
├── tsconfig.json
├── vite.config.ts
└── src/
    ├── components/
    │   └── Nav/
    │       ├── Nav.tsx
    │       ├── Nav.module.css
    │       └── Nav.test.tsx
    └── entry/
        └── nav-entry.tsx
```

### 页面集成方式

两个 HTML 页面（`index.html` 和 `manage/index.html`）的 `<header>` 标签变为空元素（去除 `data-show-token` 属性），页面末尾引用 React 本地托管文件 + `nav-bundle.js` + inline mount 脚本。聊天页额外含 token 桥接 script 块。

### E2E 测试适配

Nav 组件的关键交互元素添加 `data-testid` 属性：
- `nav-menu-btn`（汉堡菜单按钮）
- `nav-link`（导航链接，`data-active` 属性标识高亮）
- `nav-sidebar-close`（侧边栏关闭按钮）

`ManagePageIT.java` 中 15 处 CSS class 选择器替换为 `data-testid` 选择器，JS eval 中的 `classList.contains('open')` 替换为 `getAttribute('aria-expanded') === 'true'`。

### 文档更新

- README.md：移除"no npm/webpack"声明，更新技术栈表。
- AGENTS.md：更新前端技术描述，新增前端构建命令说明。
- docs/architecture.md：更新决策 #11 和 #14，新增决策 #48（React 渐进迁移）。
- CONTEXT.md：无需修改（无术语涉及前端技术栈）。
- 新增 ADR：`docs/adr/frontend-react-migration.md`，记录迁移决策的 why/choice/non-choice。

## Testing Decisions

### 单元测试

- **测试范围**：仅 `Nav.tsx` 组件（M2），测试纯外部行为。
- **测试工具**：Vitest + React Testing Library + jsdom。
- **测试原则**：只测渲染输出和用户交互结果，不测内部 state 结构或 useEffect 时序。
- **测试用例**（约 10 个）：
  - tokenPercent=0/50/80 时进度条颜色（绿/黄/红）
  - 非聊天页路径不渲染 token 条
  - 汉堡按钮点击展开侧边栏
  - 关闭按钮点击收起侧边栏
  - Chat 页面链接高亮
  - Manage 页面链接高亮
  - Logout 按钮存在且 form action 正确
  - `data-testid` 属性存在
  - `aria-expanded` 正确地跟踪侧边栏状态
- **不测试**：`nav-entry.tsx`（纯胶水代码，无逻辑）、HTML inline script（配置层）。

### E2E 测试

- `ManagePageIT.java` 中 nav 相关选择器适配 `data-testid`（15 处改动）。
- 其他 6 个 E2E IT 类不涉及 nav 元素，零改动。
- E2E 测试由 `mvn verify` 触发，前端构建产物已在 `mvn compile` 阶段生成。

## Out of Scope

- **不迁移 chat 模块**（`app.js`）：WebSocket 通信、消息流式处理、纠错侧边栏保持 vanilla JS。
- **不迁移 flashcard 模块**（`flashcard.js`）：两阶段录入面板保持不变。
- **不迁移 manage 模块**（`manage/card.js`、`manage/tag.js`、`manage/modal.js`）：卡片管理 CRUD 保持不变。
- **不迁移 login 模块**（`login/main.js`）：登录表单提交保持不变。
- **不引入路由库**（React Router 等）：多页面通过传统 HTML 导航，非 SPA。
- **不引入状态管理库**（Redux/Zustand 等）：组件状态由 props + local state 管理。
- **不改变 `style.css` 中非 nav 部分**：全局样式保留，仅删除 nav 相关约 120 行。
- **不改变 WebSocket 协议**：前后端通信完全不变。
- **不修改 Spring Boot 后端代码**：Java 层零改动。

## Further Notes

- 此 PRD 遵循 13 轮 grilling 决策，所有技术选型均有明确理由。
- 首阶段 nav 迁移完成后，后续阶段可按相同模式迁移 `flashcard.js`、`manage/` 模块、`app.js`（Chat 模块）。
- 当 Chat 模块迁移完成时，`window.updateTokenBar` 桥接自然删除，token 状态提升至 Chat 组件树内部。
- CSS Modules 的 `:global()` 语法保留为后续可能需要的全局样式钩子，首阶段不启用。
