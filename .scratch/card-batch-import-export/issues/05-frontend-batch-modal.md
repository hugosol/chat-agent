# 05 — 前端批量操作模态框

**Status:** `ready-for-agent`

## Parent

[PRD: 闪卡批量导入/导出](../PRD.md)

## What to build

实现 `BatchOperationModal` 组件及其在 `CardsTab` 中的集成，打通从前端选 tag → 上传/下载 → 查看结果的完整用户路径。

**新建 `BatchOperationModal` 组件**（`components/manage/BatchOperationModal.tsx` + `.module.css`）：

三阶段状态机（内部 state）：

```
select-tag → ready → loading → result
```

**Props**：`mode: "import" | "export"`、`onClose: () => void`、`onComplete: () => void`

**各阶段行为**：

1. **select-tag**：
   - `<select>` 下拉框，`fetch("/api/tags?type=deck")` 获取当前用户所有 deck tag
   - 未选中时操作按钮置灰（`disabled`）
   - `data-testid="batch-tag-select"`

2. **ready**：
   - **导入模式**：`<input type="file" accept=".csv" data-testid="batch-file-input">` + "导入"按钮（`data-testid="batch-import-btn"`）。选了文件后按钮可用。
   - **导出模式**："导出"按钮（`data-testid="batch-export-btn"`），始终可用。
   - 显示选中的 tag 名称

3. **loading**（仅导入）：
   - 显示 spinner + "导入中..."（`data-testid="batch-loading"`）
   - `fetch("/api/cards/import", { method: "POST", body: FormData })` 上传文件
   - 等待响应

4. **result**（仅导入）：
   - 成功时：`"成功导入 X 张卡片"`（绿色高亮）
   - 有跳过时：`"跳过 Y 张"` + 错误清单表格（行号、front、原因），`data-testid="batch-error-list"`
   - "关闭"按钮（`data-testid="batch-close-btn"`），点击调用 `onComplete` + `onClose`

**导出下载逻辑**（直接触发，不经过 loading/result 阶段）：
- `fetch(exportUrl)` → `response.blob()` → `URL.createObjectURL(blob)` → 创建隐藏 `<a>` 元素 → `a.click()` → `URL.revokeObjectURL()`
- 下载触发后 `onClose()`

**修改 `CardsTab` 组件**：
- `ModalState` 联合类型追加：`{ type: "batch"; mode: "import" | "export" }`
- `CardToolbar` 的 `onBatchOpen` 回调：`setModal({ type: "batch", mode })`
- 渲染 `<BatchOperationModal>`：`{modal?.type === "batch" && <BatchOperationModal mode={modal.mode} onClose={handleCloseModal} onComplete={() => { fetchCards(); fetchDecks(); }} />}`
- 导入成功/导出完成后调用 `fetchCards()` + `fetchDecks()` 刷新卡片列表

**CSS**：
- 新建 `BatchOperationModal.module.css`：select 下拉框样式、文件选择器样式、错误清单表格样式、spinner 动画
- 与现有 Modal 组件的暗色主题一致

## Acceptance criteria

- [ ] 工具栏点击"导入"→ 打开导入 modal → 显示 deck tag 下拉框 → 未选 tag 时"导入"按钮置灰
- [ ] 选 tag + 选 CSV 文件 → 点"导入"→ 显示 spinner → 完成后展示结果（成功 X 条 / 跳过 Y 条 + 错误清单）
- [ ] 错误清单包含：行号、front 内容、失败原因三列
- [ ] 点"关闭"后 modal 关闭、卡片列表自动刷新、新导入的卡片可见
- [ ] 工具栏点击"导出"→ 打开导出 modal → 选 tag → 点"导出"→ 自动下载 CSV 文件 → modal 关闭
- [ ] 下载的 CSV 文件名格式为 `{tagName}_{yyyyMMdd_HHmmss}.csv`
- [ ] 未选 tag 时导出/导入按钮不可用
- [ ] 移动端 390px 宽度 modal 布局正常
- [ ] 暗色主题与现有 Modal 风格一致
- [ ] 所有交互元素有 `data-testid` 属性供 E2E 定位

## Blocked by

- 02-import-backend（POST /api/cards/import 端点）
- 03-export-backend（GET /api/cards/export 端点）
- 04-frontend-toolbar（DropdownMenu 组件 + CardToolbar 的 onBatchOpen 回调）
