# 09: MovieToolbar Card-Style Layout

**Status:** `ready-for-agent`

## Problem

电影工具栏排序使用原生 `<select>`，添加和批量导入使用文字按钮——与卡片工具栏的 `DropdownMenu` + 图标按钮风格不一致。

## Approach

**排序：原生 `<select>` → `<DropdownMenu>`**

- 复用 `manage/DropdownMenu` 组件
- 6 个选项不变：名称 A→Z / Z→A / 年份 ↑ / ↓ / 添加时间 ↑ / ↓
- `label` 动态显示当前选中项（如 "名称 A→Z"）
- `testId`: `"movies-sort-btn"`，option: `"movies-sort-option"`

**添加电影：文字 → "+" 图标**

- 图标 "+"，tooltip "添加电影"，`testId` 保留 `"movies-add-btn"`

**批量导入：文字 → "📄" 图标**

- 图标 "📄"，tooltip "批量导入"，`testId` 保留 `"movies-import-btn"`
- 直接触发导入（无需下拉菜单——电影只有导入没有导出）

**CSS 布局参考 CardToolbar：**

- `toolbarRow` 布局：`display: flex; gap: 8px; align-items: center`
- `searchInput`：`flex: 1`
- `actions`：`flex-shrink: 0`，图标按钮样式参考 `CardToolbar.createBtn`

## Files

- `src/main/frontend/src/components/movies/MovieToolbar.tsx`
- `src/main/frontend/src/components/movies/MovieToolbar.module.css`
- `src/main/frontend/src/__tests__/movies/MovieToolbar.test.tsx`
- `src/main/frontend/src/__tests__/movies/MoviesPage.test.tsx`
- `src/test/java/com/hugosol/chatagent/e2e/MoviesPageIT.java`

## Acceptance

- 排序控件为 DropdownMenu，点击展开 6 个选项
- 排序按钮文案动态显示当前选中项
- 选择选项后触发 onSortChange
- 添加按钮显示为 "+" 图标，点击触发 onAddMovie
- 批量导入按钮显示为 "📄" 图标，点击触发 onImportMovies
- 图标悬停显示 tooltip
- 布局与 CardToolbar 视觉风格一致
- 所有相关测试通过

## Comments

