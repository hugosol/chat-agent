# PRD：Chat 页面 React 渐进迁移 —— Phase 1: CorrectionSidebar

**Status**: `ready-for-agent`

## Problem Statement

当前 Chat Agent 的聊天页面（`index.html`）前端全部使用原生 Vanilla JS（`app.js` 551 行），包含 WebSocket 连接管理、消息流式渲染、纠错侧边栏、报告弹窗、调试面板、输入处理、TTS 播放等 6 个以上的逻辑子域，全部耦合在一个 IIFE 中。随着功能持续增长（闪卡模块、多标签协调、会话恢复、记忆注入），DOM 操作散布在单个文件中，改动一个子域极易影响其他子域。已迁移的 Header 组件证明了 React 模块化对可维护性和可测试性的提升，但聊天页面的 551 行仍然是一个不可测试的单体。

Learner 需要更可靠的纠错展示——目前纠错侧边栏通过手动 DOM 拼接（`innerHTML` + `createElement` + `appendChild`）渲染，纠错数量、显示/隐藏状态、数据流与 `app.js` 的 WebSocket 处理器紧密耦合，任何改动都可能导致 Badge 计数错误或侧边栏状态丢失。

## Solution

将纠错侧边栏（CorrectionSidebar）从 `app.js` 中抽取为独立的 React + TypeScript 模块——这是 Chat 页面 React 迁移的第一阶段。采用与 Header 组件相同的渐进模式：

- **独立入口 + 独立 IIFE bundle**：通过 Vite Library Mode 构建，挂载到 `window.ChatAgent.mountCorrectionSidebar()`。
- **命令式桥接**：`mount()` 返回 `{ addCorrection, clear, getCount }` 对象，供剩余 vanilla JS 的 `app.js` 调用。后续阶段（WebSocket 服务层抽取）可通过同一接口或 context 驱动，不破坏组件内部。
- **React 完全接管 DOM**：`index.html` 中纠错侧边栏的静态 HTML 骨架简化为容器 div，所有子元素由 React 渲染。
- **CSS Modules**：样式从 `style.css` 迁出，与页面其他样式隔离。
- **data-testid 选择器**：E2E 测试改用 `data-testid` 属性定位，不再依赖 CSS class 名。

整个 Chat 页面的最终架构目标（后续阶段）是以 `useReducer + context` 为中心的状态管理——一个 reducer 统一处理所有 WebSocket 消息，同时更新多个关联 UI（如 CORRECTION_RESULT 既更新 sidebar 纠错列表，又插入消息气泡）。本期 Phase 1 通过纯组件 + props 设计为该目标做前向兼容准备。

## User Stories

1. 作为一名 Learner，在聊天过程中收到纠错时，纠错侧边栏应正常显示纠错条目（类型、原文 → 修正、解释），与迁移前完全一致。
2. 作为一名 Learner，纠错数量应正确显示在浮动 Badge（⚠️ N ◂）和侧边栏标题中，每新增一条纠错，计数自动 +1。
3. 作为一名 Learner，我可以点击浮动 Badge 展开纠错侧边栏（260px 宽），点击侧边栏头部的 ▸ 按钮收起。
4. 作为一名 Learner，侧边栏初始状态为收起，当第一次收到纠错后，浮动 Badge 从隐藏变为可见。
5. 作为一名 Learner，结束会话后点 "New Session" 时，纠错侧边栏应清空所有纠错条目并重置计数为 0。
6. 作为一名 Learner，通过页面刷新恢复会话（RESUME_SESSION）后，纠错侧边栏应正确恢复所有历史纠错条目和计数。
7. 作为一名 Learner，纠错 summary bubble（编号列表，如 "1. I go → I went"）应继续正常显示在聊天流中（此功能不受本次迁移影响，由 `app.js` 消息渲染管道处理）。
8. 作为一名 Developer，CorrectionSidebar 组件应使用 TypeScript 编写，props 接口类型安全，所有渲染行为可通过 React Testing Library 单元测试验证。
9. 作为一名 Developer，CorrectionSidebar 的 CSS 样式应与项目中其他模块隔离（CSS Modules），不会产生全局样式冲突。
10. 作为一名 Developer，前端构建应通过 `mvn compile` 的 `exec-maven-plugin` 自动触发，Vite 多入口构建同时产出 `header-bundle.js` 和 `correction-sidebar-bundle.js`。
11. 作为一名 Developer，`app.js` 中移除的 sidebar 逻辑约 80 行，不应影响剩余的 WebSocket 连接、消息渲染、报告弹窗等功能。
12. 作为一名 QA，E2E 测试的选择器应从 `#correctionSidebar`、`.correction-item` 等 CSS 选择器迁移为 `data-testid` 属性选择器，确保 CSS Modules 哈希化不影响测试。

## Implementation Decisions

### 构建：多入口 Library Mode

Vite 从单入口（`header-entry.tsx`）扩展为多入口，每个入口输出独立 IIFE bundle + CSS 文件。React 和 ReactDOM 作为 external 共享加载。

多入口配置核心逻辑（源自原型）：
```
入口:
  header              → header-bundle.js + header-bundle.css
  correction-sidebar  → correction-sidebar-bundle.js + correction-sidebar-bundle.css
```

单个 HTML 页面按需加载对应 bundle。聊天页需同时加载 `header-bundle.js`、`correction-sidebar-bundle.js` 及其对应的 CSS 文件。

### 状态管理策略：三期路线图

本期 Phase 1 不做集中状态管理，Sidebar 由 entry wrapper 通过本地 `useState` 管理状态，对外暴露命令式 API。

完整三期目标：

- **Phase 1**（本期）：CorrectionSidebar 独立模块，命令式 API 桥接。vanilla JS 的 `app.js` 调用 `window.ChatAgent.correctionSidebar.addCorrection(c)` 推送数据。
- **Phase 2**：抽取 WebSocket 服务层 + `useReducer + context` 集中状态管理。一个 reducer 统一处理所有 WS 消息，组件通过 context 读取 state。命令式 API 保留为兼容路径。
- **Phase 3**：MessageList、ReportModal、DebugPanel、InputBar 全部迁移为 React。统一 React 树，WS context → 各组件。命令式 API 移除。

### 组件接口

**共享类型**（`shared/types.ts`）：
```typescript
type ErrorType = 'GRAMMAR' | 'WORD_CHOICE' | 'CHINGLISH' | 'PRONUNCIATION' | 'FLUENCY';

interface CorrectionData {
  type: ErrorType;
  original: string;
  corrected: string;
  explanation: string;
  messageId: number;
}
```

**纯展示组件 props**：
```typescript
interface CorrectionSidebarProps {
  corrections: CorrectionData[];
  collapsed: boolean;
  onToggle: () => void;
}
```

**命令式 API**（mount 返回值）：
```typescript
interface CorrectionSidebarAPI {
  addCorrection(c: CorrectionData): void;
  clear(): void;
  getCount(): number;
}
```

### app.js 适配

`app.js` 中移除的函数和逻辑：
- `addCorrectionSidebarItem()`（完整移除）
- `updateCorrectionBadge()`（完整移除）
- `toggleCorrectionSidebar()`（完整移除）
- `correctionCount` 变量（移除）
- `els.correctionSidebar`、`els.correctionSidebarContent`、`els.correctionSidebarToggle`、`els.correctionSidebarClose`、`els.correctionBadge`、`els.correctionBadgeHeader` 引用（移除）

保留不变：
- `handleCorrectionResult()` 中的消息气泡渲染逻辑（`.correction-bubble` 展示）——改为增加 `sidebar.addCorrection(c)` 调用。
- `handleSessionResumed()` 中的消息重建逻辑——改为增加 `sidebar.clear()` + 逐条 `addCorrection()` 调用。
- `SESSION_STARTED` handler 中的清空逻辑——改为调用 `sidebar.clear()`。
- `newSessionBtn` click handler 中的清空逻辑——改为调用 `sidebar.clear()`。

`escapeHtml()` 和 `speakText()` 重复定义同步移除，改用 `shared/utils.ts` 和 `shared/tts.ts` 中已有版本——通过 Vite 构建时内联到 bundle 或通过全局对象暴露。

### DOM 结构变更

迁移前（`index.html` 中静态定义）：
```html
<div id="correctionSidebar" class="correction-sidebar collapsed">
  <div class="correction-sidebar-header">
    <span>Corrections (<span id="correctionBadgeHeader">0</span>)</span>
    <button id="correctionSidebarClose">▸</button>
  </div>
  <div id="correctionSidebarContent" class="correction-sidebar-content">
    <div class="correction-sidebar-empty">No corrections yet.</div>
  </div>
</div>
<button id="correctionSidebarToggle" class="correction-sidebar-toggle hidden">
  ⚠️ <span id="correctionBadge">0</span> ◂
</button>
```

迁移后（React 完全接管）：
```html
<div id="correction-sidebar-root"></div>
```

所有子元素、Badge、Toggle 按钮、collapsed 状态由 React 组件内部控制。`index.html` 中移除静态 toggle 按钮（原本独立于 sidebar 的浮动元素），由 React 组件统一渲染。

### 文件结构

```
src/main/frontend/src/
├── shared/
│   ├── types.ts                          ← 新增：共享 TS 类型
│   ├── utils.ts                          ← 已有
│   ├── tts.ts                            ← 已有
│   ├── Modal.tsx                         ← 已有
│   ├── Toast.tsx                         ← 已有
│   ├── ChipInput.tsx                     ← 已有
│   └── useTagAutocomplete.ts             ← 已有
├── components/
│   ├── Header/                           ← 已有
│   │   ├── Header.tsx
│   │   └── Header.module.css
│   └── CorrectionSidebar/                ← 新增
│       ├── CorrectionSidebar.tsx
│       └── CorrectionSidebar.module.css
├── entry/
│   ├── header-entry.tsx                  ← 已有，构建配置扩展为多入口
│   └── correction-sidebar-entry.tsx      ← 新增
├── __tests__/
│   ├── header/
│   │   └── Header.test.tsx               ← 已有
│   ├── shared/
│   │   └── ...                           ← 已有（6 个测试文件）
│   └── correction-sidebar/               ← 新增
│       └── CorrectionSidebar.test.tsx
├── test-setup.ts
└── vite-env.d.ts
```

### CSS Modules 迁移

`CorrectionSidebar.module.css` 包含从 `style.css` 迁出的样式选择器：
- 侧边栏容器（position, width, height, transform, transition）
- 侧边栏折叠状态（.collapsed）
- 浮动 Badge（position, z-index, 动画）
- 纠错条目（字体、间距、箭头样式）
- 纠错类型标签
- 纠错解释文字

`style.css` 中移除约 80 行对应的全局选择器。

### E2E 测试适配

React 组件渲染时使用 `data-testid` 属性替代 CSS class/ID 作为 E2E 选择器：

| 迁移前选择器 | 迁移后选择器 |
|-------------|-------------|
| `#correctionSidebar` | `[data-testid="correction-sidebar"]` |
| `.correction-item` | `[data-testid="correction-item"]` |
| `#correctionSidebarToggle` | `[data-testid="correction-toggle"]` |
| `#correctionSidebarClose` | `[data-testid="correction-sidebar-close"]` |
| `.correction-sidebar.collapsed` | `[data-testid="correction-sidebar"][aria-expanded="false"]` |
| `.correction-sidebar:not(.collapsed)` | `[data-testid="correction-sidebar"][aria-expanded="true"]` |
| `#correctionBadge` / `#correctionBadgeHeader` | `[data-testid="correction-badge"]` |

受影响的 E2E 测试文件：
- `E2ETestBase.java`：`countCorrectionBubbles()`、`countCorrectionSidebarItems()`、`hasCorrectionBubbleWith()` 中的选择器。
- `ChatAgentSessionIT.java`：sidebar toggle、sidebar items 计数断言。
- `ChatAgentResumeIT.java`：sidebar items 恢复断言。
- `DailyTalkIT.java`：sidebar items 计数断言。

注意：`.correction-bubble` 选择器不受影响——纠错 summary bubble 属于消息渲染管道（非 sidebar 范围），由 `app.js` 保留处理。

### 文档更新

- **新增 ADR**：`docs/adr/centralized-chat-state.md`——记录 useReducer+context 集中状态管理选型及 trade-off（why not Zustand/Redux/各自订阅）。
- **更新 ADR**：`docs/adr/frontend-react-migration.md`——追加多入口构建、纯组件+连接器模式、Phase 1 CorrectionSidebar 作为第二个迁移案例。
- **更新 README.md**：前端迁移说明从"Phase 1: Header + shared utilities"更新为"Phase 2: Chat 页面模块化迁移进行中"；Tech Stack 表更新 frontend 行；Project Structure 更新 `static/shared/` 下新产物。
- **更新 AGENTS.md**：前端迁移段追加多入口构建、CorrectionSidebar 模块、useReducer+context 状态管理计划、纯组件+连接器模式；Gotchas 新增多入口 CSS 产物说明；Project Structure 更新 `src/main/frontend/src/` 下新目录。
- **更新 docs/architecture.md**：新增决策 #49（Chat 页面 React 集中状态管理）；修订决策 #48（多入口构建发现）。
- **CONTEXT.md**：无需变更——纠错侧边栏已在领域词汇表中定义，纯技术迁移不引入新领域概念。

## Testing Decisions

### 单元测试

- **测试范围**：`CorrectionSidebar.tsx` 纯展示组件。
- **测试工具**：Vitest + React Testing Library + jsdom。
- **测试原则**：只测外部行为（渲染输出、用户交互结果），不测内部 state 结构或实现细节。
- **测试用例**（约 8 个）：
  - 空纠错列表时渲染 "No corrections yet." 占位文字
  - 单条纠错时渲染类型、原文、箭头、修正文字
  - 多条纠错时按顺序渲染
  - 纠错包含 explanation 字段时渲染解释文字
  - collapsed=true 时 sidebar 不可见（`aria-expanded="false"`）
  - collapsed=false 时 sidebar 可见（`aria-expanded="true"`）
  - Badge 显示正确的纠错数量
  - 纠错数量为 0 时 Badge 隐藏
  - 点击 Toggle 按钮时 `onToggle` 回调被调用
  - 点击 Close 按钮时 `onToggle` 回调被调用
- **不测试**：`correction-sidebar-entry.tsx`（纯编排层，无业务逻辑）、`shared/types.ts`（类型定义，无运行时行为）。
- **参考先例**：`Header.test.tsx`（97 行，10 个测试）——相同模式：render + fireEvent + data-testid 断言。

### 不测试

- `correction-sidebar-entry.tsx`：薄编排层，仅组合 React 组件 + 状态管理，无独立业务逻辑。
- `shared/types.ts`：纯类型定义，编译时检查已足够。
- `vite.config.ts`：构建配置，通过 `mvn compile` 的构建成功/失败间接验证。
- `index.html` / `style.css` 的标记性改动。

### E2E 测试

- 修改 3 个 IT 类 + 1 个 Base 类的选择器（如上表）。
- 不新增 E2E 测试文件——现有测试覆盖已充分，本次仅做选择器适配。
- E2E 由 `mvn verify` 触发，前端构建产物已在 `mvn compile` 阶段生成。

## Out of Scope

- **不迁移 WebSocket 服务层**：WebSocket 连接管理、协议解析保留在 `app.js` 中（Phase 2 实现）。
- **不引入集中状态管理**：`useReducer + context` 为 Phase 2 目标，本期不做。
- **不迁移消息渲染管道**：`app.js` 中的消息流式渲染（stream delta/end）、correction bubble、TTS 播放保持不变。
- **不迁移报告弹窗**：`showReport()` 功能保持不变。
- **不迁移调试面板**：`debugLog()` 功能保持不变。
- **不迁移输入处理**：`sendTextInput()`、visibility resume 逻辑保持不变。
- **不迁移 flashcard 模块**：`flashcard.js` 保持不变——尽管已有 React `ChipInput.tsx` + `useTagAutocomplete.ts` 可作为其迁移依赖，但不在本期范围。
- **不迁移 manage 页面**：`manage/card.js`、`manage/tag.js`、`manage/modal.js` 保持不变——manage 页面独立迁移，不与 chat 页面耦合。
- **不迁移 login 页面**：`login/main.js` 保持不变。
- **不改变 WebSocket 协议**：前后端通信完全不变。
- **不修改 Spring Boot 后端代码**：Java 层零改动。

## Further Notes

- 此 PRD 基于 12 轮 grilling 决策，涵盖模块定义、迁移优先级、桥接接口设计、构建策略、DOM 所有权、CSS 策略、前向兼容性、状态管理选型、测试策略、文档更新等全部决策分支。
- 纠错 summary bubble（`app.js` 中的 `createCorrectionBubble()` 和 `.correction-bubble` 样式）不属于 CorrectionSidebar 范围——它位于聊天消息流中（每条用户消息下方），不是侧边栏的一部分。此功能由 `app.js` 消息渲染管道处理，在 Phase 3（MessageList 迁移）时再处理。
- `escapeHtml()` 在 `app.js`（line 440-445）中的重复定义应在本次迁移中移除——`shared/utils.ts` 已有 TypeScript 版本且经过测试。建议在 `correction-sidebar-entry.tsx` 或通过 Vite 构建将共享版本暴露给 `app.js` 使用。
- `speakText()` 同理——`shared/tts.ts` 已有 TypeScript 版本。
- 后续 Phase 2（WebSocket 服务层）是整个 Chat 页面迁移的架构转折点——一旦 `useReducer + context` 就位，剩余的 MessageList、ReportModal、DebugPanel、InputBar 可在统一数据流上并行迁移。
- CSS Modules 的侧边栏 `.collapsed` 状态通过 `aria-expanded` 属性控制渲染（`aria-expanded="false"` 时 `display: none`），替代旧的 CSS class toggle 模式。
