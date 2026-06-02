# 05: Entry Point + Panel Coordination + Build + Vanilla Removal

**Status**: `ready-for-agent`

## Parent

[PRD：React 全量迁移：聊天页面纯 React 化](../PRD.md)

## What to build

这是整个 Phase 4 的集成点——完成所有组件整合、入口切换、构建更新和旧文件删除，使聊天页面成为纯 React 原生页面。

### 新 App 组件 + 面板协调

创建新的 App 组件（在 `chat-entry.tsx` 中），管理四个面板互斥：

```ts
type PanelType = "menu" | "correction" | "debug" | "flashcard" | null;
```

App 层 `useState<PanelType>(null)` 管理 `activePanel`。

`togglePanel(panel)` 逻辑：`setActivePanel(prev => prev === panel ? null : panel)`。

### 新 chat-entry.tsx

单一 `<div id="root">` 渲染，无 Portal：

```tsx
function App() {
  const [activePanel, setActivePanel] = useState<PanelType>(null);
  const togglePanel = (panel: PanelType) => setActivePanel(prev => prev === panel ? null : panel);

  return (
    <ChatProvider>
      <div id="app" className={styles.chatPage}>
        <Header
          tokenPercent={/* derive from context */}
          activePanel={activePanel}
          onTogglePanel={togglePanel}
        />
        <main>
          <div className={styles.mainLayout}>
            <div id="chatArea">
              <MessageList />
            </div>
            <CorrectionSidebar isOpen={activePanel === "correction"} onToggle={() => togglePanel("correction")} />
          </div>
          <StatusBar />
          <ChatInput />
        </main>
        <Footer />
      </div>
      <ReportModal />
      <DebugPanel isOpen={activePanel === "debug"} onToggle={() => togglePanel("debug")} />
      <FlashcardPanel isOpen={activePanel === "flashcard"} onToggle={() => togglePanel("flashcard")} />
    </ChatProvider>
  );
}
```

- ChatInput、Footer 直接返回 JSX，不使用 `createPortal`
- MessageList 也去掉 Portal，直接渲染在 `<div id="chatArea">` 内部

### Header 组件修改

新增可选 props：
- `activePanel?: PanelType`
- `onTogglePanel?: (panel: PanelType) => void`

- Manage 页面不传这些 props 时，Header 回退本地 `useState`（保持现有行为）
- Chat 页面传入后：hamburger 按钮调用 `onTogglePanel("menu")`；菜单项的 nav link 点击时关闭面板
- 保留所有已有 `data-testid`：`nav-menu-btn`、`nav-sidebar`（`aria-expanded`）、`nav-link`（`data-active`）、`nav-sidebar-close`

### CorrectionSidebar 组件修改

- `collapsed: boolean` prop → `isOpen: boolean` prop
- 内部行为改为：`isOpen` 为 true 时展开，为 false 时收起
- 保留所有已有 `data-testid`：`correction-item`、`correction-toggle`、`correction-sidebar`（`aria-expanded`）、`correction-sidebar-close`

CorrectionSidebar 的 badging/toggle 逻辑：浮动 ⚠️ N 徽章的切换行为改为调用 `onToggle()`（从 `isOpen`/`onToggle` props 控制）。

### ChatPage 页面骨架 CSS

新建 `ChatPage.module.css`：
- 整体布局（`#app` / `main` 层）：flex column、height: 100dvh、安全区适配
- 主布局（`.mainLayout`）：聊天区 + 侧边栏的 flex row
- 提取自 `style.css` 中 #app/main 相关全局规则

### 共享控件 CSS

新建 `shared/controls.module.css`：
- `.btn`、`.btn-primary`、`.btn-danger` 按钮样式
- `.controls` 控件容器
- `select` 下拉框样式
- 提取自 `style.css` 中 .btn/button 相关规则

### 删除旧文件

- `src/main/resources/static/app.js`（121 行）
- `src/main/resources/static/flashcard.js`（272 行）
- `src/main/resources/static/style.css`（616 行）
- `src/main/resources/static/index.html` 中所有预置 HTML 容器（`<header>`、`#messages`、`#statusBar`、`#textInputBar`、`<footer>`、`#reportModal`、`#flashcardPanel`、`#debugPanel`、`#earlierMarker`、`#correction-sidebar-root`、2 个 `<template>` 标签）

### index.html 最终形态

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
    <title>Chat Agent</title>
    <link rel="stylesheet" href="/shared/base.css">
    <link rel="stylesheet" href="/shared/chat-bundle.css">
</head>
<body>
    <div id="root"></div>
    <script src="/shared/react.production.min.js"></script>
    <script src="/shared/react-dom.production.min.js"></script>
    <script src="/shared/chat-bundle.js"></script>
</body>
</html>
```

### Vite Build 变更

- 删除 `vite.config.correction-sidebar.ts`
- `vite.config.chat.ts` 入口改为新 `chat-entry.tsx`（废弃旧 `chat-agent-entry.tsx`，重命名或新建）
- `vite.config.ts` 保留产出 `header-bundle.js/.css` 给 Manage 页面
- `package.json` build 脚本: `"build": "vite build && vite build --config vite.config.chat.ts"`（2 步构建）

> 注意：`vite.config.chat.ts` 需要确认所有引入的组件路径在 `chat-entry.tsx` 中正确导入。

### 单元测试

**`Header.test.tsx`（修改）**：
- 新增 `activePanel`/`onTogglePanel` props 测试
- `onTogglePanel` 被调用时验证参数正确
- 不传 props 时回退本地 useState（现有行为不变）

**`CorrectionSidebar.test.tsx`（修改）**：
- `collapsed: boolean` → `isOpen: boolean`
- 验证 `isOpen` 为 true/false 时正确展开/收起

## Acceptance criteria

- [ ] 新 App 组件：`activePanel: PanelType` 互斥管理 + `togglePanel()`
- [ ] 新 `chat-entry.tsx`：单一 `createRoot(root)` 渲染，无 Portal
- [ ] Header：新增 `activePanel`/`onTogglePanel` props，Manage 页面不传时回退本地 useState
- [ ] CorrectionSidebar：`isOpen: boolean` 替代 `collapsed: boolean`
- [ ] ChatInput、Footer、MessageList 去掉 Portal，直接返回 JSX
- [ ] `ChatPage.module.css` 页面骨架样式
- [ ] `shared/controls.module.css` 共享控件样式
- [ ] 删除 `app.js`、`flashcard.js`、`style.css`
- [ ] `index.html` → 极简形式（`<div id="root">` + scripts + `<link>`）
- [ ] 删除 `index.html` 中所有预置 HTML 容器和 `<template>` 标签
- [ ] 删除 `vite.config.correction-sidebar.ts`
- [ ] `vite.config.chat.ts` 入口改为新 `chat-entry.tsx`
- [ ] `package.json` build 脚本改为 2 步构建
- [ ] `Header.test.tsx` 修改通过（新增 activePanel/onTogglePanel 测试）
- [ ] `CorrectionSidebar.test.tsx` 修改通过（isOpen 替代 collapsed）
- [ ] `npm test` 全绿
- [ ] `npm run build` 成功（产出 chat-bundle.js/.css + header-bundle.js/.css）
- [ ] `mvn compile` 成功
- [ ] 浏览器打开聊天页面：StatusBar、ReportModal、DebugPanel、FlashcardPanel 全部在 React 中正常渲染

## Blocked by

- [01: AppStatus State + ChatProvider Rewire + StatusBar](./01-appstatus-state-reducer-chatprovider-statusbar.md)
- [02: ReportModal + ChatInput/Footer Adaptation](./02-reportmodal-chatinput-footer-adaptation.md)
- [03: Debug Pipeline](./03-debug-pipeline.md)
- [04: FlashcardPanel React Migration](./04-flashcard-panel-react-migration.md)
