# 08: MovieBlock Icon Buttons

**Status:** `ready-for-agent`

## Problem

MovieBlock 行内使用中文文字按钮（"下载字幕"/"重试"/"删除"），占据大量水平空间，导致电影名称列过窄，长电影名被截断。

## Approach

| 状态 | 旧按钮 | 新图标 | tooltip |
|------|--------|--------|---------|
| PENDING | 下载字幕 | ⬇️ | 下载字幕 |
| FAILED | 重试 | 🔄 | 重试 |
| 任何状态 | 删除 | 🗑️ | 删除 |

- 按钮使用 `data-testid` 不变（`movie-download-btn`、`movie-delete-btn`）
- 点击交互逻辑不变（仍弹出确认弹窗）
- CSS 新增图标按钮样式：最小 32×32px touch target，透明背景，hover 变色

## Files

- `src/main/frontend/src/components/movies/MovieBlock.tsx`
- `src/main/frontend/src/components/movies/MovieBlock.module.css`
- `src/main/frontend/src/__tests__/movies/MovieBlock.test.tsx`（无需改动——使用 data-testid）

## Acceptance

- PENDING 状态显示 ⬇️ 图标
- FAILED 状态显示 🔄 图标
- DONE 状态不显示下载/重试按钮
- 所有状态显示 🗑️ 图标
- 鼠标悬停图标显示 tooltip 文字
- 点击 ⬇️/🔄 弹出 MovieRetryModal
- 点击 🗑️ 弹出 MovieDeleteModal
- 电影名称列水平空间增加，长名称不被截断
- 现有 MovieBlock 测试通过

## Comments

