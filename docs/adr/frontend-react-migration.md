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
