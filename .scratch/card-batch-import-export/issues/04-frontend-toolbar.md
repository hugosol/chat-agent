# 04 — 前端工具栏改造

**Status:** `ready-for-agent`

## Parent

[PRD: 闪卡批量导入/导出](../PRD.md)

## What to build

改造 Manage 页面 Cards Tab 工具栏，将原有的两个独立排序按钮（`Aa` / `T`）替换为下拉菜单按钮，并新增文件操作按钮。

**新建 `DropdownMenu` 通用组件**（`components/manage/DropdownMenu.tsx` + 对应 `.module.css`）：
- Props：`label`（按钮显示文字）、`items`（`{label, value, onClick}[]`）、`selectedValue`（当前选中值）
- 按钮展示当前选中项的 label
- 点击按钮展开纵向菜单（绝对定位在下拉层），列出所有 items
- 点击菜单项：触发 `onClick(value)` + 关闭菜单
- 点击菜单外部区域：关闭菜单
- 与现有 dark theme 风格一致（深蓝背景 + 红色高亮，复用 `CardToolbar` 中的颜色变量）

**修改 `CardToolbar` 组件**：
- 移除现有 `.sortBtns` 区域（两个 `<button>` Aa/T）和相关 CSS
- 移除 `handleSortClick` 回调和相关 `nameArrow`/`timeArrow` 状态
- 新增：
  - **排序下拉按钮**：
    - 默认初始状态显示 `Aa ↑`（`selectedValue` 为当前 `sort` prop，默认 `"front,asc"`，与现有 `CardsTab` 中 `useState("front,asc")` 一致）
    - items: `[{label:"Aa ↑", value:"front,asc"}, {label:"Aa ↓", value:"front,desc"}, {label:"T ↑", value:"createTime,asc"}, {label:"T ↓", value:"createTime,desc"}]`
    - `onClick(value)` → `onSortChange(value); setPage(0)`
    - `data-testid="sort-dropdown-btn"`，菜单项 `data-testid="sort-option"`
  - **文件图标按钮**（在排序按钮右侧、`+` 按钮左侧）：
    - 使用 Unicode 文件图标字符（如 `📄`）或 SVG 内联图标
    - items: `[{label:"导出", value:"export"}, {label:"导入", value:"import"}]`
    - `onClick(value)` → 调用新增的 `onBatchOpen(mode)` prop
    - `data-testid="batch-dropdown-btn"`，菜单项 `data-testid="batch-option"`
- Props 接口新增：`onBatchOpen: (mode: "import" | "export") => void`
- 布局：`[搜索框] [排序▾] [📄▾] [+]`

**修改 `CardToolbar.module.css`**：
- 移除 `.sortBtns`、`.sortBtn`、`.activeSort` 样式
- 新增下拉菜单相关样式（菜单容器、菜单项、悬停高亮、选中态）

**修改 `CardsTab`**：
- 传入 `onBatchOpen` prop（当前先接一个 console.log 占位，Issue 05 替换为打开 modal 的逻辑）

## Acceptance criteria

- [ ] 工具栏显示三个按钮：[排序▾] [📄▾] [+]，不再有 Aa/T 独立按钮
- [ ] 点击排序按钮展开 4 选项菜单（Aa ↑/Aa ↓/T ↑/T ↓），纵向排列
- [ ] 选择排序选项后：菜单关闭，按钮标签更新为选项文字，卡片列表重新排序
- [ ] 点击文件按钮展开 2 选项菜单（导出/导入），纵向排列
- [ ] 点击"导出"或"导入"触发 `onBatchOpen` 回调（Issue 05 实现）
- [ ] 点击菜单外部区域菜单关闭
- [ ] 移动端 390px 宽度工具栏布局正常，按钮不换行
- [ ] 暗色主题样式与现有工具栏风格一致
- [ ] `data-testid` 属性正确设置（供 E2E 定位）

## Blocked by

None — can start immediately（纯前端，可以用 mock 回调验证）。

> **注意**：本 Issue 只做工具栏 UI 和交互，不实现实际的导入/导出逻辑。`onBatchOpen` 的回调在 Issue 05 中连接到 `BatchOperationModal`。
