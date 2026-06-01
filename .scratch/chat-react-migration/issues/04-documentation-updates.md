# 04: 文档更新

**Status**: `ready-for-agent`

## Parent

[PRD：Chat 页面 React 渐进迁移 —— Phase 1: CorrectionSidebar](../PRD.md)

## What to build

更新项目文档以反映 Phase 1 CorrectionSidebar 迁移后的新前端架构状态。涉及 5 个文档文件，按依赖关系分为两组：

**可立即开始的前置设计文档**（不依赖 Issue 01-03 的代码变更）：

1. **新增 `docs/adr/centralized-chat-state.md`**：记录 Chat 页面 React 集中状态管理的选型决策和三期路线图。
   - 记录备选方案及拒绝原因：Zustand（额外依赖、Redux 范式）、各自独立订阅（props drilling 导致耦合）、Redux（过度工程化）
   - 记录选择：`useReducer + context`（无额外依赖、与 React 18 原生集成、支持渐进采用）
   - 记录三期路线图：Phase 1 命令式桥接 → Phase 2 useReducer + context → Phase 3 统一 React 树
   - State shape 草案（供 Phase 2 参考，不强制实现细节）

2. **修订 `docs/architecture.md`**：
   - 新增决策 #49「Chat 页面 React 集中状态管理」——引用 `docs/adr/centralized-chat-state.md` 作为详细理由
   - 修订决策 #48「引入 React + TypeScript + Vite 前端工具链」——在实施记录中追加：多入口构建从推论变为已验证实践；纯组件 + 连接器模式（command & query separation）作为稳定的迁移模式

**应在 Issue 02 合并后更新或由人工在全部 Issue 完成后统一定稿**：

3. **更新 `README.md`**：
   - 「Quick Reference」部分新增 `npm test` 说明（已有 `mvn test`，前端测试随 Maven 自动触发）
   - 「Key Facts」中 "Frontend" 段更新：迁移状态从 "Phase 1: Header + shared utilities" 改为 "Phase 2: Chat 页面模块化迁移进行中（CorrectionSidebar 已完成）"
   - 「Tech Stack」表 frontend 行更新：`/ React 18 + TypeScript + Vite (Library Mode) / static/shared/` 下产物列表追加 `correction-sidebar-bundle.js` 和 `correction-sidebar-bundle.css`
   - 「Project Structure」中 `static/shared/` 下产物列表追加 correction-sidebar 相关文件
   - 「Gotchas」部分追加 CSS Modules 的 `data-testid` E2E 测试适配说明（如尚未有）

4. **更新 `AGENTS.md`**：
   - 「Frontend」段更新：迁移状态从 "Header component + shared utility layer" 扩展为 "Header + CorrectionSidebar + shared utility layer"
   - 追加多入口构建说明：Vite Library Mode 现已支持多入口（header + correction-sidebar），每个入口产出独立 IIFE + CSS 文件
   - 追加纯组件 + 连接器模式说明：组件通过 props 接收数据、通过命令式 API 暴露外部操作，为后续 useReducer + context 做前向兼容
   - Gotchas 新增：CSS Modules 的通过 `data-testid` 的 E2E 定位（CSS selectors are hashed），在 `index.html` 中添加 `<link>` 标签引入每个 bundle 的 CSS 文件
   - Project Structure 更新：`src/main/frontend/src/` 下追加 `components/CorrectionSidebar/`、`entry/correction-sidebar-entry.tsx`、`shared/types.ts`、`__tests__/correction-sidebar/`

5. **更新 `docs/adr/frontend-react-migration.md`**：
   - Implementation Notes 追加：多入口构建（从单入口扩展为多入口时的注意事项）——入口配置模式、CSS 文件名稳定性（`build.lib.cssFileName`）、emptyOutDir 的旧产物残留风险、页面引用加载对应 bundle 的约定
   - Consequences 追加：CorrectionSidebar 作为第二个迁移案例验证了 Vite Library Mode 模式的复用性
   - Status 保持不变（accepted）

**无需变更**：
- **`CONTEXT.md`**：纠错侧边栏已在领域词汇表中定义（"Correction sidebar — floating ⚠️ badge + 260px overlay for error display"），纯技术迁移不引入新领域概念

## Acceptance criteria

- [ ] `docs/adr/centralized-chat-state.md` 存在，包含选型理由和三期路线图
- [ ] `docs/architecture.md` 决策 #49 已添加，决策 #48 已修订
- [ ] `docs/adr/frontend-react-migration.md` Implementation Notes 已追加多入口构建说明
- [ ] `README.md` 已更新（迁移阶段、技术栈、项目结构、Gotchas）
- [ ] `AGENTS.md` 已更新（迁移状态、多入口构建、纯组件+连接器模式、Gotchas、项目结构）
- [ ] 人工审核通过

## Blocked by

无硬阻塞——ADR 和 architecture 文档可立即开始。但 `README.md` 和 `AGENTS.md` 的最终文件结构描述依赖于 Issue 01-03 的合并结果，建议：
- ADR + architecture 变更：立即进行，在 Issue 01 编码开始前完成设计文档定稿
- README + AGENTS 变更：待 Issue 02 合并后根据实际文件结构定稿，或在本 Issue 中预写草案并在 Issue 02 后校对
