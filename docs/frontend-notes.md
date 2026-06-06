# Frontend Notes

> 前端架构决策见 [docs/adr/frontend-react-migration.md](adr/frontend-react-migration.md)，中心化状态管理见 [docs/adr/centralized-chat-state.md](adr/centralized-chat-state.md)，测试清单见 [docs/tests.md](tests.md)。

Chat Agent 前端实现笔记：浏览器兼容性问题、CSS 模式、可复用惯例。与 `docs/architecture.md`（架构决策）和 `docs/adr/`（架构决策记录）互补——本文档记录实现层面的模式和陷阱，而非架构选择。

---

## 一、设计原则

| 原则 | 说明 |
|------|------|
| **非 SPA 多页面** | 保留 Spring Boot 管理的多 HTML 页面，不引入路由库（React Router）。每页通过 Vite Library Mode 构建为独立 IIFE bundle。现有页面：Chat（`index.html`）、Manage（`manage/index.html`）、Review（`review/index.html`）、Login（`login/index.html`）。Settings（`/settings`）为 planned 新页面 |
| **集中状态管理** | Chat 页面使用 React Context + useReducer（`ChatProvider` + `chatReducer`），不引入 Redux/MobX 等外部状态库 |
| **Manage 页面本地状态** | Manage 页面每个 Tab（CardsTab / TagsTab）使用本地 `useState` + REST API 调用，不跨 Tab 共享状态 |
| **CSS Modules 隔离** | 所有组件样式通过 `.module.css` 哈希化，E2E 测试使用 `data-testid` 替代 CSS class 选择器 |
| **Portal 替代方案** | 不使用 `createPortal`；Toast 通过动态创建 DOM 节点 + `createRoot` 渲染；其他组件直接挂载到页面容器 |
| **渐进迁移完成** | Phase 4 已完成：Chat 和 Manage 页面 100% React，全部 vanilla JS 文件已删除 |

---

## 二、浏览器兼容性

### 2.1 iOS Safari sticky hover

**问题：** iOS Safari 的 hover 机制与桌面浏览器不同。touch 设备没有 hover 能力，Safari 采用启发式规则模拟——tap 时触发 `:hover` 伪类，手指离开后 `:hover` 会**粘住不消失**，直到滚动页面或 tap 另一个元素才清除。

**症状：** 用户 tap chip 取消筛选后，chip 仍然显示 active 色（红色），直到滚动页面才恢复。实际上 JS 状态已正确更新（数据已刷新），但视觉上 `:hover` 伪类残留遮盖了真实状态。

**根本原因：** `.deckChip:hover` 和 `.activeDeck` 使用了完全相同的样式（背景色、文字色、边框色），用户无法区分 "真正激活" 和 "sticky hover 残留"。

**修复模式：** 用 `@media (hover: hover)` 包裹 hover 样式，让 touch 设备跳过所有 hover 伪类规则。

```css
/* ❌ 问题写法：hover 和 active 共享样式，iOS 上无法区分 */
.deckChip:hover,
.activeDeck {
  background: #e94560;
}

/* ✅ 正确：hover 仅对鼠标设备生效，active 始终由 class 控制 */
@media (hover: hover) {
  .deckChip:hover {
    background: #e94560;
  }
}
.activeDeck {
  background: #e94560;
}
```

**原理：** `hover: hover` 是 CSS Level 4 媒体查询，浏览器根据主输入设备（primary pointing device）的支持能力匹配：

| `hover` 值 | 含义 | 典型设备 |
|---|---|---|
| `hover` | 主输入设备能悬停 | 鼠标、触控板 |
| `none` | 主输入设备不能悬停 | iPhone/iPad 触摸屏 |

- **桌面 Chrome**：有鼠标 → 匹配 `@media (hover: hover)` → `:hover` 正常生效
- **iPhone Safari**：触摸屏 → 不匹配该查询 → `:hover` 规则被跳过，永不触发

**适用场景：** 任何需要 hover 和 active 状态有**视觉差异**的交互元素——chip 按钮、sort 按钮、导航链接等。如果一个元素 active 后用户还要能点击它来做 toggle，就必须用此模式。

**相关修复：** `CardToolbar.module.css`（deck chip + sort 按钮均应用此模式）

**参考：** 这是 iOS Safari 的已知行为，不限于本项目。MDN 文档：[The `pointer` and `hover` media features](https://developer.mozilla.org/en-US/docs/Web/CSS/@media/hover)

---

### 2.2 iOS Safe Area（刘海屏/状态栏适配）

iOS 设备顶部的 notch（刘海）和底部的 Home Indicator 会覆盖 Web 内容。使用 CSS 环境变量适配：

```css
/* Chat 页面容器避开 notch（配合 position: fixed 定位，见 §2.5） */
.app {
  position: fixed;
  top: env(safe-area-inset-top);
  bottom: 0;
  left: 0;
  right: 0;
}

/* 底部栏避开 Home Indicator */
.footer {
  padding-bottom: calc(16px + env(safe-area-inset-bottom, 0px));
}
```

- `env(safe-area-inset-top)` — notch/状态栏高度（iPhone 13: 47px）
- `env(safe-area-inset-bottom)` — Home Indicator 高度（iPhone 13: 34px）
- 必须配合 `<meta name="viewport" content="viewport-fit=cover">` 使用

**使用位置：**
- `ChatPage.module.css` — `.app { top: env(safe-area-inset-top); }`
- `Footer.module.css` — `.footer { padding-bottom: calc(... + env(safe-area-inset-bottom, 0px)); }`
- `ManageApp.module.css` — `.app { height: calc(100vh - env(safe-area-inset-top)); }`

---

### 2.3 TTS 用户手势要求

iOS Safari **禁止**无用户手势触发的音频播放（`SpeechSynthesis`、`Audio` 元素）。自动播放将被静音拦截。

**实现策略：**

| 场景 | 触发方式 | 是否满足手势要求 |
|------|---------|:---:|
| Agent 消息到达后自动 TTS | `AGENT_STREAM_END` 回调 + `document.visibilityState === "visible"` 检查 | ✅ 首次 WS 消息可视为用户交互链路 |
| 用户主动播放 | 点击消息气泡的 🔊 按钮 → `speakText(text)` | ✅ 用户手势直接触发 |
| Card 详情页面 TTS | 点击 Card detail modal 中的 🔊 按钮 | ✅ 用户手势直接触发 |

**实现：** `src/main/frontend/src/shared/tts.ts`
```typescript
export function speakText(text: string): void {
  speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = "en-US";
  utterance.rate = 0.95;
  speechSynthesis.speak(utterance);
}
```

**E2E 测试：** Playwright 使用 `page.evaluate()` 注入 `SpeechSynthesisUtterance` mock，绕过实际音频播放。

---

### 2.4 移动端 viewport 设置

所有 HTML 页面应包含以下 meta 标签以确保移动端渲染正确：

```html
<meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
```

Playwright E2E 测试使用 **Mobile Safari 390×844** 视口 + `setIsMobile(true)` 模拟 iPhone 13。

---

### 2.5 iOS Safari 的 100vh 陷阱

**问题：** iOS Safari 中 `100vh` 包含地址栏区域，导致容器高度 > 实际可见区域。当 `#app` 容器使用 `height: 100vh` 时，页面底部内容（Footer、DebugPanel）被推到屏幕下方不可见区域。点击输入框触发键盘弹起后，视口重新计算，布局暂时恢复正常。

**四轮尝试与最终方案：**

| 轮次 | 方案 | 失败原因 |
|------|------|---------|
| 1 | CSS `100dvh` | Safari 15.4 以下不支持，降级回 `100vh` 依然错误 |
| 2 | `-webkit-fill-available` + `min-height` | `min-height` 不约束容器上限，内容撑出视口 |
| 3 | `visualViewport.height` + JS `resize` 事件监听 | 键盘弹出时 `resize` 触发，JS 将容器缩到键盘上方，flex 布局压缩崩溃 |
| **4** | `position: fixed; inset: 0` | ✅ 最终方案 — 无 JS，纯 CSS 原生行为 |

**原理：** iOS Safari 中有两套视口坐标系：

| | `100vh` | `position: fixed; inset: 0` |
|---|---|---|
| 参照的是什么 | CSS 规范定义的视口高度（含地址栏区域） | 渲染引擎的实际可见像素区域 |
| 地址栏展开时 | 高度值超出屏幕，内容被挤到下方不可见 | 始终填满可见区域 |
| 键盘弹出时 | 视口缩小，flex 布局被动压缩 | 浏览器自动调整 fixed 元素的包含块 |
| JS 依赖 | 无（但结果错误） | 无 |

**最终代码**（`ChatPage.module.css`）：
```css
.app {
  position: fixed;
  top: env(safe-area-inset-top);
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  max-width: 1000px;
  margin: 0 auto;
}
```

**关键教训：** `position: fixed` 的 `inset` 参照的是浏览器渲染引擎内部的"布局视口"——实际绘制像素区域，不含地址栏、不被键盘影响。所有 CSS 视口单位（`vh`、`dvh`、`svh`、`lvh`）都是 CSS 规范层的抽象值，与渲染引擎的真实视口是两套独立的坐标系。在 iOS Safari 这类地址栏动态变化的浏览器中，只有 `position: fixed` 能可靠地获取真实可见区域。

---

## 三、CSS 约定

### 3.1 CSS Modules

所有组件样式使用 CSS Modules，Vite 配置 `localsConvention: "camelCaseOnly"`。

```tsx
// 导入
import styles from "./ComponentName.module.css";

// 使用（单一类名）
<div className={styles.toolbar}>

// 使用（动态组合 + 全局类名混搭）
<span className={`${styles.deckChip}${isActive ? " " + styles.activeDeck : ""}`}>
<button className="btn btn-primary">  // 全局类名（来自 base.css）
```

**禁止直接使用 CSS class 选择器做 E2E 断言**——CSS Modules 在生产构建中会哈希化 class 名。始终用 `data-testid`。

---

### 3.2 composes 模式

通过 CSS Modules 的 `composes` 复用全局基础样式。这种方法避免 JavaScript 中拼接多个 class 字符串：

```css
/* Footer.module.css */
.startBtn {
  composes: btn btnPrimary;
  /* ... 额外样式 */
}

/* FlashcardPanel.module.css */
.continueBtn {
  composes: btn btnPrimary;
  /* ... 额外样式 */
}
```

**已定义的全局基础 class（在 `base.css` 中）：**
- `.btn` — 按钮基础样式
- `.btn-primary` — 主色调按钮
- `.chip` — 标签 chip 样式
- `.modal` — 弹窗容器（React `Modal` 组件引用此 class）
- `.pagination` — 分页容器

---

### 3.3 状态驱动样式

不通过动态 className 字符串拼接表达状态，而是使用 `aria-expanded` 和 `data-active` 属性驱动 CSS：

```css
/* CorrectionSidebar.module.css */
.sidebar[aria-expanded="false"] {
  display: none;
}

/* Header.module.css */
.navSidebar[aria-expanded="true"] {
  transform: translateX(0);
}

.navLink[data-active="true"] {
  color: #e94560;
}

/* CardToolbar.module.css */
.deck-chip[data-active="true"] {
  background: #e94560;
}
```

**好处：** E2E 测试可以同时用这些属性做断言（`[data-active="true"]`），不依赖哈希化的 class 名。

---

### 3.4 面板定位策略（fixed vs flow）

面板组件的定位方式选择原则：

| 面板类型 | 定位方式 | 原因 |
|---------|---------|------|
| **浮层面板**（需覆盖在聊天内容之上） | `position: fixed` | FlashcardPanel 的两阶段录入面板需浮于消息列表上方 |
| **辅助面板**（不遮挡主内容，属于页面布局的一部分） | 流式布局 | DebugPanel 放在 Footer 之后，自然跟随页面流 |

**DebugPanel 从 fixed 改为流式：**

| | 改前 | 改后 |
|---|---|---|
| 定位 | `position: fixed; bottom: 0; z-index: 500` | 普通文档流，`flex-shrink: 0` |
| 在页面中的位置 | 固定在视口底部，覆盖 Footer | 排在 Footer 之后，自然位于页面底部 |
| 与 Footer 的关系 | z-index 堆叠，iOS Safari 上被 body 的 `100vh` 误差推离视口 | 同一父组件（`#app`）内的兄弟元素，不存在覆盖关系 |

改为流式布局不产生新耦合——DebugPanel 和 Footer 在 `chat-entry.tsx` 中已是同一 `AppContent` 组件内的兄弟元素，无论 fixed 还是 flow 都是这个关系。

**代码**（`DebugPanel.module.css`）：
```css
.debugPanel {
  /* 删除了 position: fixed / bottom / left / right / z-index */
  background: rgba(0, 0, 0, 0.92);
  border-top: 1px solid #333;
  max-height: 35vh;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}
```

---

### 3.5 Token 进度条颜色代码

Token 进度条的颜色阈值在 `Header.tsx` 中计算，不通过 CSS 表达：

```typescript
function getColor(percent: number): string {
  if (percent >= 80) return "rgb(231, 76, 60)";   // 红色
  if (percent >= 50) return "rgb(243, 156, 18)";  // 橙色
  return "rgb(39, 174, 96)";                       // 绿色
}
```

---

### 3.6 base.css 的 #app 规则分离

**问题：** `base.css` 中的 `#app` 使用 ID 选择器（specificity 0-1-0-0），而 `ChatPage.module.css` 中的 `.app` 使用 CSS Module class（0-0-1-0）。在 CSS 层叠规则中，ID 选择器永远优先于 class 选择器——chat 页面无法通过 `.app` 覆盖 `#app` 中的 `height` 或 `position` 属性。使用 `!important` 不是可持续的方案。

**方案：** 将 `#app` 容器规则从共享的 `base.css` 中移除，各页面自行管理容器布局：

| 页面 | 容器规则位置 | 布局方式 |
|------|------------|---------|
| Chat 页面 | `ChatPage.module.css` 的 `.app` class（CSS Module） | `position: fixed; inset: 0`（见 §2.5） |
| Manage 页面 | `manage.css` 的 `#app` ID 选择器 | `height: calc(100vh - env(safe-area-inset-top))`（传统 flow 布局） |
| Login 页面 | 无独立容器规则（使用 `body` 的默认布局） | — |

**设计原则：** 容器级别的布局规则（`height`、`position`）属于页面级决策，不属于共享基础样式。`base.css` 只应包含：
- 全局重置（`* { box-sizing }`）
- 元素默认样式（`body`、`input`、`select`）
- 通用组件 class（`.btn`、`.modal`、`.chip`）
- 工具 class（`.hidden`）

---

## 四、组件架构

### 4.1 目录结构

```
src/main/frontend/src/
├── shared/             # 跨页面共享工具和组件（无子目录）
│   ├── types.ts        # 共享类型（ErrorType, Tag, Card, PageResponse 等）
│   ├── utils.ts        # formatDate, truncate, englishOnly
│   ├── tts.ts          # speakText
│   ├── debugLog.ts     # 调试日志报告器
│   ├── Modal.tsx
│   ├── Toast.tsx
│   ├── InlineChipInput.tsx   # 邮箱风格内联标签输入（Manage 弹窗 + FlashcardPanel 共用）
│   ├── InlineChipInput.module.css
│   └── Pagination.tsx
├── components/
│   ├── Header/         # 导航栏（nav links + token bar + sidebar menu）
│   ├── ChatInput/      # 消息输入框 + Send 按钮
│   ├── MessageList/    # 消息气泡列表（含纠错气泡插值）
│   ├── Footer/         # Mode 选择 + Start/End 按钮
│   ├── StatusBar/      # 状态指示条
│   ├── ReportModal/    # 会话报告弹窗
│   ├── CorrectionSidebar/  # 纠错侧边栏（绝对定位浮层）
│   ├── DebugPanel/     # WS 调试日志面板
│   ├── FlashcardPanel/ # 闪卡两阶段录入面板
│   └── manage/         # Manage 页面组件
│       ├── ManageApp.tsx       # Tab 切换容器
│       ├── CardsTab.tsx        # Cards 列表 + 筛选 + CRUD
│       ├── CardToolbar.tsx     # 搜索/排序/牌组筛选
│       ├── CardList.tsx        # 卡片分页列表
│       ├── CardBlock.tsx       # 单张卡片展示 + 操作
│       ├── TagsTab.tsx         # Tags 管理
│       ├── TagTable.tsx        # Tag 表格 + 内联编辑
│       └── TabBar.tsx          # Cards/Tags Tab 切换
├── state/
│   ├── chatState.ts     # ChatState / Message / Action / AppStatus
│   ├── chatReducer.ts   # 纯函数 reducer（11 种 Action）
│   └── ChatContext.tsx   # ChatProvider + useChatContext
├── entry/               # Vite Library Mode 入口（mount 到 window.ChatAgent）
│   ├── header-entry.tsx
│   ├── chat-entry.tsx
│   └── manage-entry.tsx
└── __tests__/           # 镜像 components/ 结构
    ├── chat/
    ├── manage/
    ├── shared/
    ├── state/
    ├── header/
    └── correction-sidebar/
```

### 4.2 命名约定

| 规则 | 示例 |
|------|------|
| 组件目录 = 组件名 | `Header/` → `Header.tsx` + `Header.module.css` |
| 共享组件无子目录 | `shared/Modal.tsx`（非 `shared/Modal/Modal.tsx`） |
| Hooks 以 `use` 前缀 | `useTagAutocomplete.ts` |
| 工具模块小写 | `utils.ts`, `tts.ts`, `debugLog.ts` |
| 测试文件镜像路径 | `__tests__/chat/ChatInput.test.tsx` |
| Props 接口随组件导出 | `export { Header }; export type { HeaderProps };` |

### 4.3 组件导出模式

```typescript
// 每个组件文件同时导出组件和 Props 接口
interface HeaderProps {
  tokenPercent?: number;
  activePanel?: string | null;
  onTogglePanel?: (panel: string) => void;
}

function Header({ tokenPercent, activePanel, onTogglePanel }: HeaderProps): JSX.Element {
  // ...
}

export { Header };
export type { HeaderProps };
```

---

## 五、状态管理

### 5.1 Chat 页面：ChatContext + useReducer

Chat 页面使用 Context + useReducer 集中管理所有 WebSocket 消息和 UI 状态。

### 5.2 Manage 页面：本地 useState

Manage 页面不使用 Context。每个 Tab 管理自己的状态：

### 5.3 Settings 页面（planned）：本地 useState

Settings 页面独立于其他页面，使用本地 useState 管理 9 个表单字段，通过 GET/PUT `/api/user/preferences` 读写。遵循 Manage 页面的本地状态模式。

```typescript
// CardsTab.tsx
const [cards, setCards] = useState<Card[]>([]);
const [deckId, setDeckId] = useState<string | null>(null);
const [page, setPage] = useState(0);
// ... 所有状态 + fetch 通过 useCallback/useEffect 管理

// TagsTab.tsx
const [tags, setTags] = useState<Tag[]>([]);
// ...
```

### 5.3 FlashcardPanel：本地状态 + InlineChipInput

```typescript
const [stage, setStage] = useState<1 | 2>(1);
const [front, setFront] = useState("");
const [back, setBack] = useState("");
const [chips, setChips] = useState<Tag[]>([]);
const [allTags, setAllTags] = useState<Tag[]>([]);
const [saving, setSaving] = useState(false);
```

标签输入通过共享组件 `InlineChipInput` 管理（`options={allTags} value={chips} onChange={setChips}`），FlashcardPanel 自身不再维护 `tagInput` 和 `showSuggestions` 状态。

Flashcard 和 Debug 面板通过 `Header` 组件的 `activePanel`/`onTogglePanel` props 实现互斥（`null | 'debug' | 'flashcard'`）。

---

## 六、构建配置

### 6.1 三入口 Vite Library Mode

| 配置文件 | 入口 | 产物 JS | 产物 CSS | 消费页面 |
|---------|------|---------|----------|---------|
| `vite.config.ts` | `header-entry.tsx` | `header-bundle.js` | `header-bundle.css` | Manage、Login |
| `vite.config.chat.ts` | `chat-entry.tsx` | `chat-bundle.js` | `chat-bundle.css` | Chat 页面 |
| `vite.config.manage.ts` | `manage-entry.tsx` | `manage-bundle.js` | `manage-bundle.css` | Manage 页面 |

**共同配置：**
- 格式：`formats: ["iife"]`（自执行函数，传统 `<script>` 加载）
- 全局命名空间：`name: "ChatAgent"`（所有 bundle 共享 `window.ChatAgent`）
- React 外部化：`external: ["react", "react-dom"]`（从 HTML 页面的 `<script src="react.production.min.js">` 加载）
- 输出目录：`outDir: ../resources/static/shared`
- CSS Modules：`localsConvention: "camelCaseOnly"`
- 构建命令：`vite build && vite build --config vite.config.chat.ts && vite build --config vite.config.manage.ts`

### 6.2 Maven 集成

`exec-maven-plugin` 在 `process-resources` 阶段自动触发 `npm run build`。开发者需要自行安装 Node.js。

### 6.3 重要配置细节

**`define` 条件替换（vite.config.ts）：**
```typescript
define:
  typeof process !== "undefined" && process.env?.NODE_ENV === "test"
    ? {}
    : { "process.env.NODE_ENV": JSON.stringify("production") },
```

仅在非 test 模式替换 `process.env.NODE_ENV`。如果 Vitest 加载了 React 生产版，`act()` 不可用，全部 test 会失败。

**`emptyOutDir: false`：** 防止并行构建时互相清空产物。

**Vitest CSS Modules 配置（`vitest.config.ts`）：**
```typescript
css: {
  modules: { classNameStrategy: "non-scoped" }
}
```

测试中使用 `"non-scoped"` 保留原始 class 名，组件测试中可以通过 class 名做断言（不依赖 `data-testid`，因为 Jest/Vitest 不运行 CSS Modules 哈希）。

---

## 七、vanilla bridge 与窗口命名空间（历史参考）

### 7.1 window.ChatAgent 命名空间

所有 entry 文件通过累加模式挂载到 `window.ChatAgent`，多个 bundle 可共存：

```typescript
const ns = (window as Record<string, unknown>).ChatAgent || {};
(window as Record<string, unknown>).ChatAgent = ns;
(ns as Record<string, unknown>).mountChatAgent = mountChatAgent;
```

### 7.2 HTML 页面消费 React bundle

```html
<!-- 1. 加载 React 运行时（全站共享，仅一次） -->
<script src="/shared/react.production.min.js"></script>
<script src="/shared/react-dom.production.min.js"></script>

<!-- 2. 加载业务 bundle -->
<link rel="stylesheet" href="/shared/chat-bundle.css">
<script src="/shared/chat-bundle.js"></script>

<!-- 3. 调用 mount 函数 -->
<script>
  window.addEventListener("DOMContentLoaded", function () {
    ChatAgent.mountChatAgent();
  });
</script>
```

### 7.3 Header 双模式

Header 组件支持两种集成模式：

1. **独立模式**（Manage/Login 页面）：不传 `activePanel`/`onTogglePanel`，使用内部 `useState` 管理 sidebar
2. **集成模式**（Chat 页面）：接收 `activePanel`/`onTogglePanel` props，由 ChatPage 统一协调各面板的互斥

---

## 八、测试约定

### 8.1 文件组织

```
src/__tests__/
├── chat/              # ChatInput, MessageList, Footer, StatusBar, ReportModal, DebugPanel, FlashcardPanel
├── manage/            # ManageApp, CardsTab, TagsTab, CardList, CardBlock, CardToolbar, TagTable, TabBar
├── shared/            # Modal, Toast, InlineChipInput, Pagination, utils, tts, useTagAutocomplete
├── state/             # chatReducer
├── header/            # Header
└── correction-sidebar/ # CorrectionSidebar
```

### 8.2 data-testid 约定

所有交互元素和显示元素必须添加 `data-testid` 属性，E2E 和单元测试均依赖此属性：

```
data-testid="message"     data-role="user|agent"
data-testid="text-input"
data-testid="send-btn"
data-testid="deck-chip"   data-active="true|false"
data-testid="modal"
data-testid="toast"
```

### 8.3 Context 依赖组件测试模式

需要 ChatContext 的组件通过 Provider wrapper 注入 mock 值：

```typescript
function createContextValue(overrides?: Partial<ChatContextValue>): ChatContextValue {
  return {
    state: { ...initialState, ...overrides?.state },
    dispatch: vi.fn(),
    send: vi.fn(),
    ...overrides,
  };
}

function renderWithContext(ui: JSX.Element, ctxValue: ChatContextValue) {
  return render(
    React.createElement(ChatContext.Provider, { value: ctxValue }, ui)
  );
}
```

### 8.4 Mock 模式

- `vi.fn()` — 用于 `dispatch` 和 `send` spy
- `vi.fn()` — 用于 `global.fetch` mock（Manage 页面 REST API 调用）
- `vi.useFakeTimers()` — 用于 Toast auto-dismiss 定时器测试
- 不使用 `userEvent`，统一使用 `fireEvent`

### 8.5 测试数量（当前：203 tests, 25 files）

---

## 九、错误处理

### 9.1 Toast 系统

```typescript
showToast("加载卡片失败");  // 默认 2100ms 自动消失
```

**实现：** 动态创建 DOM 节点 → `createRoot(div).render(<Toast ... />)` → 定时器自动 unmount + remove。不是 Portal，但效果等价。

### 9.2 WebSocket 错误恢复

- `ws.onerror`：仅 debugLog 记录，不改变 UI
- `ws.onclose`：dispatch `WS_CLOSED` → state 重置 + `appStatus: "Disconnected"`
- `ERROR` server message：特殊处理 "session not found" → 清除 localStorage + 恢复 Connected
- JSON 解析失败：静默 catch，不崩溃
- `AGENT_STREAM_END` 防御：streaming message 不存在时仍更新 tokenUsage + 清除 streamInProgress（防止卡在加载状态）

### 9.3 降级报告

会话结束时如果 ReportAgent 调用失败，后端生成降级报告（`fluencyScore=-1` 哨兵值），前端 `ReportModal` 检测 `fluencyScore === -1` 时隐藏评分行。

---

## 十、实现模式

### 10.1 DropdownMenu 通用下拉菜单按钮模式

**Props 约定**：`label`（按钮当前显示文字）、`items`（选项列表含 label/value/onClick）、`selectedValue`（当前选中值，选中项高亮）。

**交互**：点击按钮展开纵向绝对定位菜单 → 点击菜单项触发回调+关闭菜单 → 点击外部关闭（`document.addEventListener("mousedown", ...)`）。

**使用场景**：排序选择器（4 选项：Aa ↑/Aa ↓/T ↑/T ↓）、批量操作选择器（导出/导入 2 选项）。

**移动端注意**：菜单 position 需考虑视口边界，避免溢出。当前实现为 `top: 100%; left: 0`。

### 10.2 BatchOperationModal 三阶段状态机模式

**状态**：`select-tag` → `ready` → `loading` → `result`。

**导入模式**走全流程（4 个状态），**导出模式**走 select-tag → ready → 直接触发下载（跳过 loading/result）。

**选 tag**：使用 `<select>` 下拉框（`/api/tags?type=deck` 数据源），未选中时操作按钮 `disabled`。

**导入 result**：展示成功数 + 跳过行清单表格（行号、front、失败原因），`data-testid="batch-error-list"`。

### 10.3 文件上传/下载模式（代码库首次）

**上传**：`<input type="file" accept=".csv">` → `new FormData()` → `fetch(url, { method: "POST", body: formData, credentials: "same-origin" })` → 解析 JSON 响应展示结果。

**下载**：`fetch(url, { credentials: "same-origin" })` → `response.blob()` → `URL.createObjectURL(blob)` → 创建隐藏 `<a>` 元素 → `a.click()` → `URL.revokeObjectURL()`。

**CSRF**：已对 `/api/**` 禁用，仅需 JSESSIONID cookie（`credentials: "same-origin"`）。

---

## 十一、修订记录

| 日期 | 内容 |
|------|------|
| 2026-06-03 | 新增 §2.5（iOS Safari 100vh 陷阱）、§3.4（面板定位策略）、§3.6（base.css #app 规则分离）；更新 §2.2 CSS 示例代码；Chat 页面组件从扁平 `chat/` 目录迁移至独立子目录 |
| 2026-06-03 | 初始版本：浏览器兼容性（iOS sticky hover / safe-area / TTS）、CSS 约定、组件架构、状态管理、构建配置、vanilla bridge、测试约定、错误处理 |
