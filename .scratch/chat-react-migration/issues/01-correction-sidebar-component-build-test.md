# 01: CorrectionSidebar 组件 + 多入口构建 + 单元测试

**Status**: `ready-for-agent`

## Parent

[PRD：Chat 页面 React 渐进迁移 —— Phase 1: CorrectionSidebar](../PRD.md)

## What to build

将纠错侧边栏实现为独立的 React + TypeScript 纯展示组件，扩展 Vite 构建为多入口 Library Mode，并编写完整的单元测试。

这是第一个真正的垂直切片——从类型定义到构建产物，切过所有层次：

**shared/types.ts**：新增 `ErrorType` 和 `CorrectionData` 类型，与后端 Java 的 `ErrorType` 枚举和 `CorrectionData` DTO 保持一致（5 种错误类型：`GRAMMAR | WORD_CHOICE | CHINGLISH | PRONUNCIATION | FLUENCY`）。

**vite.config.ts**：从单入口 `header-entry.tsx` 扩展为多入口 Library Mode，新增 `correction-sidebar-entry.tsx` 入口，产出独立的 IIFE bundle + CSS 文件（`correction-sidebar-bundle.js` + `correction-sidebar-bundle.css`）。保持现有 `emptyOutDir: false` 和其他所有配置不变。

**CorrectionSidebar.tsx**：纯展示组件，无副作用，无网络请求。Props 接口：

```typescript
interface CorrectionSidebarProps {
  corrections: CorrectionData[];
  collapsed: boolean;
  onToggle: () => void;
}
```

组件内部职责：
- 渲染时通过 `corrections.length` 驱动 Badge 计数和空状态占位文字（"No corrections yet."）
- `collapsed` 通过 `aria-expanded` 属性驱动侧边栏可见性（`aria-expanded="false"` 时 `display: none`），替代旧的 CSS class toggle 模式
- 浮动 Badge 按钮（⚠️ N ◂）：`corrections.length === 0` 时隐藏，点击触发 `onToggle`
- 侧边栏头部 ▸ 按钮：点击触发 `onToggle`
- 纠错条目列表：每条渲染类型标签、原文（删除线样式）→ 修正文字、解释文字

所有交互元素使用 `data-testid` 属性：

| 元素 | data-testid |
|------|------------|
| 侧边栏容器 | `correction-sidebar` |
| 浮动 Badge 按钮 | `correction-toggle` |
| Badge 计数 | `correction-badge` |
| 侧边栏收起按钮 | `correction-sidebar-close` |
| 纠错条目 | `correction-item` |

**CorrectionSidebar.module.css**：将 `style.css` 中约 80 行 sidebar 相关样式迁移为 CSS Modules。包含：侧边栏容器定位、折叠状态（基于 `[aria-expanded="false"]`）、浮动 Badge、纠错条目卡片、类型标签、原文/箭头/修正文字、解释文字、移动端响应式宽度（200px @ max-width: 700px）。

**CorrectionSidebar.test.tsx**：约 10 个测试用例，使用 Vitest + React Testing Library，遵循 `Header.test.tsx` 的模式（render + fireEvent + data-testid 断言）。测试范围仅限 `CorrectionSidebar.tsx` 纯组件，不测 `correction-sidebar-entry.tsx`（编排层）和 `shared/types.ts`（类型定义）。

**correction-sidebar-entry.tsx**：最小骨架入口。仅创建 `mountCorrectionSidebar()` 函数挂载到 `window.ChatAgent`，渲染一个无 props 的 `<CorrectionSidebar />` 用于验证构建产出。命令式 API 留到 Issue 02 实现。

## Acceptance criteria

- [ ] `shared/types.ts` 存在，导出 `ErrorType` 和 `CorrectionData` 类型
- [ ] `mvn compile` 成功，产出 `static/shared/correction-sidebar-bundle.js` 和 `correction-sidebar-bundle.css`
- [ ] `header-bundle.js` 和 `header-bundle.css` 继续正常产出（不破坏已有入口）
- [ ] `CorrectionSidebar.test.tsx` 覆盖以下场景：
  - [ ] 空纠错列表渲染 "No corrections yet." 占位文字
  - [ ] 单条纠错渲染类型标签、原文（删除线）、箭头、修正文字
  - [ ] 多条纠错按顺序渲染
  - [ ] 包含 explanation 字段时渲染解释文字
  - [ ] `collapsed=true` 时 `aria-expanded="false"`
  - [ ] `collapsed=false` 时 `aria-expanded="true"`
  - [ ] Badge 显示正确纠错数量
  - [ ] 纠错数量为 0 时 Badge 隐藏
  - [ ] 点击 Toggle 按钮触发 `onToggle`
  - [ ] 点击 Close 按钮触发 `onToggle`
- [ ] 所有现有 Header 和 shared 测试继续通过
- [ ] `style.css` 中无回归——sidebar 样式暂不移除（Issue 02 处理）

## Blocked by

无 — 可立即开始
