# 04: FlashcardPanel React Migration

**Status**: `ready-for-agent`

## Parent

[PRD：React 全量迁移：聊天页面纯 React 化](../PRD.md)

## What to build

将 `flashcard.js`（272 行 vanilla JS）全量重写为 React FlashcardPanel 组件。复用已有的 `ChipInput` 和 `useTagAutocomplete` 共享组件，零 ChatContext 依赖。

### FlashcardPanel 组件 + CSS Module

- Props: `isOpen: boolean` + `onToggle: () => void`
- 零 ChatContext 依赖
- 两阶段 UI（与当前 vanilla 版本行为一致）：

**Stage 1（最小面板）**：
- 输入框：`data-testid="flashcard-front"`（替代 `#flashcardFront`）
- "继续"按钮：`data-testid="flashcard-continue"`（替代 `#flashcardContinue`）
- Enter 键或点击 → 进入 Stage 2

**Stage 2（展开面板）**：
- Back textarea：`data-testid="flashcard-back"`（替代 `#flashcardBack`）
- Tag chip 输入：复用已有 `ChipInput` 组件 + `useTagAutocomplete` hook
  - 输入框：`data-testid="flashcard-tag-input"`（替代 `#flashcardTagInput`）
  - 建议列表：`data-testid="flashcard-tag-suggestions"`（替代 `#flashcardTagSuggestions`），需支持 `aria-hidden`
  - 建议项：`data-testid="tag-suggestion-item"`（替代 `.tag-suggestion-item`）
  - Chip 标签：`data-testid="flashcard-chip"`（替代 `.flashcard-chip`）
- "保存"按钮：`data-testid="flashcard-save"`（替代 `#flashcardSave`）

**Toast**：
- `data-testid="flashcard-toast"`（替代 `#flashcardToast`），需支持 `aria-hidden`
- 保存成功后显示 "已保存"，2-3 秒后自动消失

**面板容器**：
- `data-testid="flashcard-panel"`（替代 `#flashcardPanel`），需支持 `aria-expanded`
- 打开时 `aria-expanded="true"`，关闭时 `aria-expanded="false"`

**Toggle 按钮**（Debug 等外部面板引用）：
- `data-testid="flashcard-toggle"`（替代 `#flashcardToggle`）

### API 调用

POST `/api/cards/add` 保存：
- Request body: `{ front, back, tagIds: [...] }`
- 成功：显示 toast，关闭面板
- 422 验证错误：在 UI 上显示错误信息
- 400/500 错误：显示通用错误提示

### 实现要点

- 内部 state 管理：`stage: 1 | 2`、`front: string`、`back: string`、`selectedTags: Tag[]`
- Tag 自动完成：调用 `GET /api/tags`（通过 `useTagAutocomplete` hook）
- 面板关闭时重置内部 state（回到 Stage 1）
- 从旧 `style.css` 提取闪卡相关样式规则到新 CSS Module
- 不使用 FlashcardPanel 组件中的全局 CSS 类名（`.hidden`、`.collapsed`），改用 `aria-hidden` / `aria-expanded`

### 单元测试

**`FlashcardPanel.test.tsx`（新建）**：
- 初始状态 Stage 1：front 输入框可见，back 不可见
- 点击 Continue → Stage 2：back textarea 可见
- Tag 自动完成：输入触发 suggestions 显示
- 点击 suggestion 添加 chip
- 移除 chip
- Save 调用 POST `/api/cards/add`，成功后显示 toast
- 422 错误处理（显示验证错误）
- Unmount 清理（无 DOM 残留）

## Acceptance criteria

- [ ] FlashcardPanel 组件实现两阶段流程（Stage 1 → Stage 2）
- [ ] 复用已有 `ChipInput` 和 `useTagAutocomplete` 组件
- [ ] POST `/api/cards/add` 保存功能正常
- [ ] 保存成功显示 toast，自动关闭面板
- [ ] 422 验证错误正确显示
- [ ] 所有 11 个 `data-testid` 渲染正确（见 What to build 中列出的所有 testid）
- [ ] 面板使用 `aria-expanded`（不用 `.collapsed` class）；Stage 2 使用 `aria-hidden`（不用 `.hidden` class）
- [ ] 面板关闭时重置内部 state
- [ ] `FlashcardPanel.test.tsx` 新建通过（至少 7 个 test case）
- [ ] `npm test` 全绿
- [ ] `mvn compile` 成功

## Blocked by

None - can start immediately（零 ChatContext 依赖，完全独立）
