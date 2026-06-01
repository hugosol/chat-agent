# 01 — Build Toolchain + Tracer Bullet

**Status**: `completed`

## Parent

[PRD: 前端 React + TypeScript 渐进迁移——首阶段 Nav 组件](../PRD.md)

## What to build

搭建 React 18 + TypeScript + Vite 构建工具链，并集成到 Maven 编译流程中，用一个最小 React 组件证明整条管线（npm → Vite → Maven → 浏览器）跑通。不修改任何现有代码。

具体交付：

**`src/main/frontend/` 目录**（当前不存在，从零创建）：
- `package.json`：依赖 `react`、`react-dom`（皆是 18.x），`typescript`、`vite`、`@vitejs/plugin-react`、`vitest`、`@testing-library/react`、`jsdom`。scripts 含 `build`（vite build）和 `test`（vitest run）。
- `tsconfig.json`：`strict: true`，`jsx: "react-jsx"`，target ES2020，module ESNext。
- `vite.config.ts`：Library Mode，输出 IIFE 格式，入口 `src/entry/` 下每个文件一个 bundle，产物输出到 `../resources/static/shared/`。

**React 18 本地托管文件**：
- `react.production.min.js` 和 `react-dom.production.min.js`（React 18.x）下载并放置在 `src/main/resources/static/shared/`。

**Maven 集成**：
- `pom.xml` 中新增 `exec-maven-plugin`，绑定 `process-resources` 阶段，执行 `npm install && npm run build`，工作目录 `src/main/frontend/`。

**Smoke Test 组件**：
- 一个最小 React 组件（如 `<HelloWorld />`）挂载到 `index.html` 末尾的一个隐藏 `<div>`，证明 React 在浏览器中正常渲染。
- 一个最小 Vitest 测试（`toBeInTheDocument()`）证明测试框架正常工作。

## Acceptance criteria

- [ ] `src/main/frontend/` 下 `npm install` 成功，无报错
- [ ] `npm run build` 成功，产物生成到 `src/main/resources/static/shared/`
- [ ] `npm test` 成功（至少 1 个 vitest 测试通过）
- [ ] `mvn compile` 触发前端构建，无报错
- [ ] 启动应用后浏览器加载页面，React 组件正常渲染（无控制台错误）
- [ ] `react.production.min.js` 和 `react-dom.production.min.js` 从项目自身服务器加载（非 CDN）
- [ ] 所有现有单元测试和 E2E 测试仍通过（`mvn test && mvn verify`）

## Blocked by

None — can start immediately.
