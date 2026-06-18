Status: ready-for-agent

# Movies 页面加入 Header 导航栏

## Parent

PRD: `.scratch/enhance-movie-ui-optimization/PRD.md` — 改动项 #1

## What to build

在 Movies 页面复用现有 `<Header />` 组件，使 Learner 在 Movies 页面可以像其他页面（Chat、Review、Manage、Tune、Settings、Profile）一样通过导航栏跳转。

`<Header />` 已内置 `isMoviesPage` 判断逻辑：当页面是 Movies 时自动隐藏 token 进度条和面板切换按钮，仅显示左侧导航按钮和用户名。因此只需在 `MoviesApp` 组件树中插入 `<Header />` 即可，不传任何 props，不新建 Header 变体，不修改 Header 组件本身。

## Acceptance criteria

- [ ] Movies 页面顶部渲染与 Chat、Review、Manage 等页面一致的 Header 导航栏
- [ ] Header 在 Movies 页面不显示 token 进度条
- [ ] Header 在 Movies 页面不显示面板切换按钮（Flashcard 等）
- [ ] 左侧导航按钮可正常跳转到其他页面
- [ ] 用户名显示正常
- [ ] 现有 Movies 页面功能（电影列表、搜索、导入、删除、字幕下载）不受影响
- [ ] `MoviesPage.test.tsx` 验证 Header 存在于 DOM 中

## Blocked by

None — 可立即开始
