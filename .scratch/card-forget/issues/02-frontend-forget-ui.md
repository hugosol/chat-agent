# 02: 前端 forget 按钮与确认弹窗

**Status:** `ready-for-agent`

## 范围

在管理页面（CardsTab）新增单卡遗忘按钮和 Deck 批量遗忘入口，含确认对话框。

## 实现内容

### CardsTab 单卡遗忘
- 每张卡片行新增"遗忘"按钮，`data-testid='btn-forget-card'`
- 点击前通过 API 获取该卡片的复习记录数（或从已有卡片列表数据推算）
- 点击弹出确认对话框：
  - 标题："遗忘卡片"
  - 内容："将删除 X 条复习记录，卡片恢复为全新状态。此操作不可撤销。"
  - 按钮："确认遗忘"（危险样式）+ "取消"
- 确认后调用 `POST /api/cards/{cardId}/forget`
- 操作成功后刷新卡片列表

### Deck 批量遗忘入口
- 在卡片列表顶部的工具栏（CardToolbar）中，Deck 筛选器旁新增"重置全部卡片"按钮
- 仅在选中某个 Deck 时显示
- 点击弹出确认对话框：
  - 标题："重置 Deck 全部卡片"
  - 内容："将重置 Y 张卡片为全新状态，并删除共 X 条复习记录。此操作不可撤销。"
  - 按钮："确认重置"（危险样式）+ "取消"
- 操作成功后刷新卡片列表

### 确认对话框组件
- 复用或新建通用确认对话框组件（Modal），接受 props：
  - `title: string`
  - `message: string`
  - `confirmLabel: string`
  - `danger: boolean`（控制按钮样式）
  - `onConfirm: () => void`
  - `onCancel: () => void`

### 复习记录数获取
- 单卡：通过卡片已有的 FSRS 字段（reps, lapses）推算——不需要额外 API 调用。删除时将删除 `reps + lapses` 条记录（每个 review 产生一条 ReviewLog）
- Deck 批量：需要在确认弹窗前查询——新增或复用已有统计接口

### 按钮可见性
- 遗忘按钮在所有卡片行可见（包括 New 状态的卡片——虽然 New 卡遗忘是无操作，但保持 UI 一致性）
- 批量"重置全部卡片"按钮仅在选中 Deck（有卡片）时可见

## 依赖
- Issue 01（forget API 端点可用）
- 已有 CardToolbar、CardsTab 组件

## 验证
- 前端单元测试（Vitest）：确认对话框渲染、按钮点击触发 API 调用
- E2E：`ManagePageIT` 新增场景验证 forget 完整流程
