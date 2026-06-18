Status: ready-for-agent

# 字幕下载 Modal 内显示 Loading 状态

## Parent

PRD: `.scratch/enhance-movie-ui-optimization/PRD.md` — 改动项 #5

## What to build

当 Learner 点击电影的字幕下载按钮并确认后，在 `MovieRetryModal` 内部显示 loading 反馈，替代当前 Modal 关闭后 UI 完全静默的体验。

### 前端改动

`MovieRetryModal` 增加 loading 状态渲染：

- 当 `retrying === true` 时：
  - Modal body 显示 spinner + "下载中..." 文字
  - 确认按钮 `disabled`
- 当 `retrying === false` 且有错误时：显示错误提示（已有逻辑，不变）
- 请求成功后 `onRetried()` 回调自动关闭 Modal，电影列表刷新显示最新字幕状态（已有逻辑，不变）

### 后端不变

`SubtitleService.downloadSubtitles()` 保持同步阻塞。前端 Modal 在 HTTP 请求 pending 期间展示 loading，请求返回后自动关闭——无需后端异步化。

## Acceptance criteria

- [ ] 点击下载按钮并确认后，Modal 内显示 loading spinner 和 "下载中..." 文字
- [ ] Loading 期间确认按钮处于 disabled 状态，不可重复点击
- [ ] 下载成功后 Modal 自动关闭，电影列表刷新显示字幕已下载状态
- [ ] 下载失败后 Modal 内 spinner 消失，确认按钮恢复可用，显示错误信息
- [ ] 取消按钮在 loading 期间仍可点击（允许放弃等待）
- [ ] `MovieRetryModal.test.tsx`（新建）：确认后 spinner 显示、按钮禁用、成功自动关闭、失败显示错误

## Blocked by

None — 可立即开始
