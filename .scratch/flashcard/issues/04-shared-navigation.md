# 04 — Shared Navigation Foundation

**Status:** `ready-for-agent`

## Parent

[PRD: 闪卡模块 Step 2 —— Deck 激活 + 卡片管理页面 + 导航统一](../PRD-step2.md)

## What to build

提取通用样式为 `shared/base.css`，构建 `shared/nav.js` 模块，为所有认证页面提供统一的 header 和 ☰ 导航侧边栏。Corrections 按钮从 header 中移除，改为在 Correction 侧边栏内部显示。聊天页 index.html 的 header 从硬编码改为占位注入。

本切片交付后，Learner 在聊天页点击 ☰ 可以看到 Chat / Cards 两个导航链接（Cards 跳转 /manage/ 此时 404 也可接受——后续切片才创建管理页）。导航侧边栏打开时自动关闭 Correction 侧边栏，两个侧边栏不同时可见。

### `shared/base.css`

从 `style.css` 中提取以下通用样式到一个独立 CSS 文件：

- `*` reset 和 `body` 基础样式（深色背景 `#1a1a2e`、safe-area、`#app` flex 布局）
- `header` 整体样式（flex、`#16213e` 背景、底部边框）
- `.btn-logout` 按钮样式
- `.token-bar-container` / `.token-bar` / `.token-bar-fill` 样式
- `.modal` / `.modal-content` / `.modal-header` 通用 Modal 样式（可供管理页复用）
- `.btn` 按钮基础样式
- `.chip` chip 通用样式
- `.flashcard-toast` toast 动画样式
- 暗色表单控件基础样式（input、textarea、select、checkbox）

提取原则：被至少两个页面共用的样式移入 `base.css`。聊天页专属样式（`.message`、`.correction-sidebar`、`.chat-area`、`.text-input-bar`、`.debug-panel` 等）保留在 `style.css` 中。

### `shared/nav.js`

独立的 JS 模块（IIFE 模式，`'use strict'`），负责两件事：

**① Header 注入**：
- 在页面 `<header>` 占位元素中注入实际 DOM：左侧 Logout 表单按钮、中间 Token 条（条件渲染）、右侧 ☰ 菜单按钮。
- Token 条通过 `data-show-token="true/false"` 属性控制显示。聊天页设为 `true`，管理页设为 `false`。
- Token 条需定时请求 `/api/user/tokens`（或沿用在 `app.js` 中已有的更新机制——从 `window` 暴露一个 `updateTokenBar` 函数供 nav.js 调用）。

**② ☰ 导航侧边栏**：
- 右侧 overlay 侧边栏（200px 宽、暗色主题，与 Correction 侧边栏视觉一致）。
- 包含两个链接：Chat（跳转 `/`）和 Cards（跳转 `/manage/`）。
- 通过 `window.location.pathname` 判断当前所在页面并高亮对应链接。
- `×` 按钮关闭侧边栏。☰ 按钮切换展开/收起。
- ☰ 按钮点击展开时，自动关闭 Correction 侧边栏：`document.getElementById('correctionSidebar')?.classList.add('collapsed')`。优雅降级——如果元素不存在则静默跳过。
- 登录页（`/login/main.html`）不引入 `nav.js`。

导出全局函数：
- `window.openNav()` — 展开导航侧边栏
- `window.closeNav()` — 收起导航侧边栏
- `window.updateTokenBar(percent)` — 供 `app.js` 更新 Token 条显示

### 聊天页 `index.html` 修改

- Header 区域从完整 HTML 替换为占位元素：`<header data-show-token="true"></header>` — nav.js 在初始化时注入内部 DOM。
- `<head>` 中新增 `<link rel="stylesheet" href="/shared/base.css">`。
- `<body>` 底部（在 `app.js` 之前）新增 `<script src="/shared/nav.js"></script>`。
- 移除原有的 `<form action="/logout">` logout 表单、token-bar 相关 div、`#correctionShowBtn` 按钮——它们都交给 nav.js 和 app.js 管理。

### Corrections 按钮迁移

- Header 中的 `#correctionShowBtn` 移除。Corrections 切换按钮改为 Correction 侧边栏内部的一个 header 按钮。
- `app.js` 中 `updateCorrectionBadge()` 函数的目标元素从 `#correctionBadge`（header 中）改为侧边栏内部的新 badge 元素（如 `#correctionSidebarBadge`）。
- Correction 侧边栏显示/隐藏逻辑不变——仍然由 `#correctionShowBtn`（现在在侧边栏内部）触发。侧边栏的 `.correction-header` 中新增这个按钮。

### 保持不变的

- 登录页 `/login/main.html` 不引入 `nav.js`，header 结构不变。
- `style.css` 保留所有聊天页专属样式。通用样式移到 `base.css` 之后在 `style.css` 中不再重复定义。

## Acceptance criteria

- [ ] 聊天页打开后，header 显示 Logout 按钮、Token 条、☰ 菜单按钮，三者布局正确（暗色背景、flex 排列）
- [ ] 点击 ☰ 按钮，右侧滑出导航侧边栏（200px 宽），显示 Chat（高亮当前页）和 Cards 两个链接
- [ ] 点击 `×` 关闭导航侧边栏；再次点 ☰ 重新展开
- [ ] 导航侧边栏打开时，如果 Correction 侧边栏正好打开，它自动关闭（collapsed 状态）
- [ ] 导航侧边栏打开时，点击 Chat 留在当前聊天页（确认不跳转），点击 Cards 跳转到 `/manage/`（此时页面 404 也可以接受——后续切片会创建）
- [ ] Corrections 按钮现在出现在 Correction 侧边栏内部（而非 header），点击可切换侧边栏显示/隐藏
- [ ] Correction badge 数字更新正确（例如纠错 3 条时侧边栏内显示 "Corrections 3"）
- [ ] Token 条在聊天页正常显示并随 LLM 调用更新（验证与 Slice 1 之前行为一致）
- [ ] Token 条在管理页不显示（后续切片通过 `data-show-token="false"` 实现）
- [ ] `mvn compile` 通过，无前端 JS 语法错误
- [ ] 现有 E2E 测试（`ChatAgentSessionIT` 等）不受影响——Correction 侧边栏内按钮功能正常

## Blocked by

None — can start immediately
