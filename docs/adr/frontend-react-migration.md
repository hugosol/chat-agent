# 前端 React + TypeScript 渐进迁移

Chat Agent 的前端当前全部使用原生 HTML + Vanilla JS（无类型安全、无单元测试、DOM 操作分散在 5 个 IIFE 中）。我们决定引入 React 18 + TypeScript + Vite 作为前端构建工具链，采用**渐进迁移**策略——首阶段从最独立的 `shared/nav.js` 切入，后续逐步覆盖所有模块。

**Status**: accepted

## Considered Options

| 方案 | 描述 | 拒绝原因 |
|------|------|---------|
| 保持 Vanilla JS | 不引入任何框架 | 类型缺失导致重构风险高，1600 行 JS 已出现维护瓶颈 |
| 全量 SPA 重写 | 用 React Router 做单页应用 | 风险过高，需同时改动所有模块、WebSocket 连接、多标签协调 |
| CDN 外部加载 React | 通过 unpkg/jsdelivr 加载 | 内网环境不可用，引入外部依赖风险 |
| CSS-in-JS（Emotion） | 样式嵌在组件内 | 额外依赖、DevTools 调试不如 CSS Modules 直观。运行时 CSS-in-JS 在 React 生态中处于下坡 |

## Choice

- **Vite Library Mode**：每个组件打包为独立 IIFE bundle，暴露全局命名空间。不接管 HTML 文件管理。
- **React 18 + TypeScript strict**：本地托管 React 文件到 `static/shared/`，CSS Modules 隔离样式。
- **Maven 集成**：`exec-maven-plugin` 在 `process-resources` 阶段自动触发 `npm run build`。开发者自行管理 Node.js。
- **渐进路线**：每改一个模块，对应 HTML 页面添加一段 inline mount 脚本。不引入路由库，不引入状态管理库。

## Non-Decisions

以下不是本次决策的内容，而是**明确排除的方向**：

- 不做 SPA（单页应用）——保留 Spring Boot 管理的多 HTML 页面架构。
- 不同时迁移所有模块——首阶段仅 `nav.js`，后续按模块逐一迁移。
- 不改 Spring Boot 后端代码——Java 层零改动。
- 不改变 WebSocket 协议——前后端通信完全不变。

## Consequences

- **可逆转**：删除 `src/main/frontend/` 目录 + 恢复 `shared/nav.js` 即可回滚，后续模块越迁越多时回滚成本递增。
- **构建依赖**：开发环境需要 Node.js（本地开发和 CI 均需），但不要求特定版本管理器。
- **产物体积**：每个组件 bundle 约 4KB（不含 React），React 本地托管文件约 130KB（全站共享一次加载）。
- **文档同步**：README.md、AGENTS.md、docs/architecture.md 需更新以反映新前端技术栈。
- **E2E 测试**：CSS class 选择器不再可靠（CSS Modules 哈希化），使用 `data-testid` 属性替代。

## Implementation Notes

以下是在 Nav 组件迁移过程中踩过的坑，后续模块迁移时务必注意：

### CSS 产物分离

Vite Library Mode（包括 IIFE 格式）**总是**将 CSS 提取为独立文件，不会内联到 JS bundle 中。后果：

- HTML 页面必须同时加载 JS bundle 和 CSS 文件：`<script src="xxx-bundle.js">` + `<link href="xxx-bundle.css">`
- `vite.config.ts` 中用 `build.lib.cssFileName` 固定 CSS 文件名，避免基于 `package.json#name` 的随机命名
- `emptyOutDir: false` 导致旧构建产物残留（如 `chat-agent-frontend.css`），需手动清理

### `process.env.NODE_ENV` 条件 define

`vite.config.ts` 中的 `define` 会对**所有模式**生效（含 Vitest）。如果把 `process.env.NODE_ENV` 替换为 `"production"`，Vitest 会加载 React 生产版（无 `act()`），导致 `@testing-library/react` 全部测试失败。

**正确做法**：仅在非 test 环境替换。

```ts
define:
  typeof process !== "undefined" && process.env?.NODE_ENV === "test"
    ? {}
    : { "process.env.NODE_ENV": JSON.stringify("production") },
```

### React 根元素在 flex 容器内

当 mount 的目标元素本身是 flex 容器（如 `<header>` 有 `display: flex`），React 组件渲染的根 `<div>` 作为唯一 flex 子元素无法自动撑满宽度。需要给根元素设置 `flex: 1; min-width: 0`，内部子元素的 `flex` 和 `margin-left: auto` 才能正常生效。

### 多入口构建

Vite Library Mode 的 IIFE 格式不支持 `build.lib.entry` 对象形式。多入口构建方案：每个入口一个独立 Vite 配置文件，`package.json` build 脚本串联执行：

```json
"build": "vite build && vite build --config vite.config.correction-sidebar.ts"
```

- **产物命名**：`{entry-name}-bundle.js` + `{entry-name}-bundle.css`
- **全局命名空间**：所有入口共享 `window.ChatAgent`，通过 `ChatAgent || {}` 模式累加挂载方法

### 纯组件 + 连接器模式

CorrectionSidebar 迁移引入的组件架构模式：纯展示组件通过 props 接收数据、通过 callbacks 发出事件，entry wrapper 通过本地 `useState` 管理状态并暴露命令式 API（`addCorrection`/`clear`/`getCount`）。此模式为后续 `useReducer + context` 集中状态管理做前向兼容。详见 ADR `centralized-chat-state.md`。
