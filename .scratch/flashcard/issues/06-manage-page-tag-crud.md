# 06 — Manage Page Shell + Tag CRUD

**Status:** `ready-for-agent`

## Parent

[PRD: 闪卡模块 Step 2 —— Deck 激活 + 卡片管理页面 + 导航统一](../PRD-step2.md)

## What to build

创建管理页 `/manage/index.html` 的外壳（暗色主题页面 + 右侧竖排 Tab 栏 + Tab 切换逻辑），实现 Tags Tab 的完整 CRUD 功能。同时构建 `manage/modal.js` 共享 Modal 模块（后续 Slice 4 的 Cards Tab 复用）。Tag CRUD API 在本切片中全部就位。

Learner 通过 ☰ 导航栏的 Cards 链接进入管理页后，可以看到两个 Tab（Cards / Tags），切换到 Tags Tab 后可以创建、编辑、删除 Tag。删除 Deck 类型 Tag 时如果有卡片会失去所有 Deck 归属（孤儿卡），后端阻止并提示。

### `manage/modal.js` — 共享 Modal 模块

通用 Modal 工具模块，`window.manageModal` 全局对象：

- `open({ title, bodyHtml, onSave, onCancel })` — 创建 Modal overlay，渲染标题和 body HTML。确认按钮触发 `onSave(modalEl)` 回调（由调用方收集表单数据并提交），取消按钮或点击遮罩触发 `onCancel`。
- `close()` — 关闭并移除 Modal DOM。
- Modal 透出模式与现有聊天页 `.modal` 视觉一致（`base.css` 中已提取通用 Modal 样式）。管理页 CSS 中做微调（如宽度）。
- 所有按钮使用 `alert()` 提示错误，不引入 toast/notification 新机制。

### `manage/index.html` — 页面外壳

独立的 HTML 页面，路径 `/manage/index.html`：

- `<head>` 引入 `shared/base.css` + `manage/manage.css` + `shared/nav.js`。
- `<header data-show-token="false"></header>` — nav.js 注入 header（Token 条隐藏）。
- 页面主体包含：
  - **右侧竖排 Tab 栏**（40-50px 宽）："Cards" 和 "Tags" 两个按钮，垂直排列。当前选中按钮高亮（如 `#e94560` 强调色）。默认选中 "Cards"（即使 Cards Tab 暂为空占位）。
  - **内容区**：根据选中的 Tab 显示/隐藏对应内容。初始 Cards Tab 内容区为空状态占位 `"暂无卡片，创建第一张卡片"`（无按钮——卡片创建在 Slice 4 实现）。Tags Tab 内容区由 `tag.js` 渲染。
- 内联 `<script>` 块（~15-20 行）实现 Tab 切换：
  - 监听 Tab 按钮 click → 高亮切换 + 显示/隐藏内容区 + 调用 `window.manageCards?.destroy()` / `window.manageCards?.init()` 或 `window.manageTags?.destroy()` / `window.manageTags?.init()`。
- 页面加载时默认初始化 Cards Tab → 空状态（即使 card.js 尚未加载亦可——优雅降级）。

### `manage/manage.css` — 管理页专属样式

新建 CSS 文件，管理页专属样式（不污染 base.css 和 style.css）：

- 页面整体布局：左侧主内容区 + 右侧 Tab 栏
- Tab 栏样式：固定宽度、竖排按钮、高亮状态
- Tags 表格样式：暗色行、列布局、inline 编辑态
- 管理页 Modal 微调
- 搜索框、排序按钮、Deck chip 筛选行样式
- 卡片 block 列表样式（为 Slice 4 预留——本切片定义 CSS class 即可）
- 空状态样式
- 分页控件样式
- 聊天气泡/WebSocket 相关样式绝不引入此文件

### `manage/tag.js` — Tags Tab 前端模块

IIFE 模式，`'use strict'`。导出 `window.manageTags = { init, destroy }`。

**`init()`**：
1. 调用 `GET /api/tags` 获取所有 Tag（含 id、name、type）。
2. 渲染 Table 列表，每行包含：
   - Name 列：显示 tag.name
   - Deck 列：checkbox（checked = `type === "deck"`），只读态下 disabled
   - Edit 按钮：进入 inline 编辑态
   - Delete 按钮：触发删除确认
3. 如果 Tag 列表为空，显示空状态 `"暂无标签"` + `"创建标签"` 按钮。

**Inline 编辑**：
- 点 Edit → 该行变为可编辑态：
  - Name 变为 `<input>` 预填当前值
  - Deck checkbox 变为可交互
  - Edit/Delete 按钮替换为 Save/Cancel 按钮
- Save → PUT `/api/tags/{id}` 携带 `{name, type}`。成功 → 退出编辑态刷新该行。失败（如 name 重复 422）→ alert 错误信息。
- Cancel → 退出编辑态恢复原始显示。
- name 为空 → alert 不提交。

**创建 Tag**：
- `"创建标签"` 按钮 → `manageModal.open()` 弹出 Modal，包含：
  - Name `<input>`
  - Deck `<input type="checkbox">` + label "作为牌组"
  - 确认按钮
- 确认 → POST `/api/tags` 携带 `{name, type}`（type 值 `"deck"` 或 null）。成功 → 关闭 Modal 刷新列表。失败（name 重复 422）→ alert 错误信息。

**删除 Tag**：
- Delete 按钮 → `window.confirm("确定要删除这个标签吗？")`（或 modal 确认）
- 确认 → DELETE `/api/tags/{id}`
- 后端可能返回：
  - 200 → 刷新列表
  - 422 + `{"orphanCount": N}` → alert `"N 张卡片将失去所有牌组，无法删除"`

**`destroy()`**：解绑事件监听、清空内容区 DOM。确保内存不泄漏。

### 后端 Tag CRUD API

以下端点全部新增在 `FlashcardController` 中（不新建 Controller 类）：

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST /api/tags` | 创建 Tag。body: `{name, type}`。name 为空 → 422 `"标签名不能为空"`。name 大小写不敏感重复 → 422 `"标签'xxx'已存在"`。type 允许值：`null` 或 `"deck"`。 |
| `PUT /api/tags/{id}` | 编辑 Tag。body: `{name, type}`。Tag 不存在 → 404。name 被其他 Tag 占用（排除自身） → 422。userId 归属检查 → 404。 |
| `DELETE /api/tags/{id}` | 删除 Tag。**孤儿卡检查**：执行 COUNT 查询——统计关联了该 Tag 的所有卡片中，有多少卡片在移除该 Tag 后将没有任何 Deck type Tag。若 orphanCount > 0 → 422 + `{"orphanCount": N}`。若 orphanCount = 0 → 删除中间表记录 + 删除 Tag。Tag 不存在或 userId 不匹配 → 404。 |

**`FlashcardService` 新增方法**：
- `createTag(String userId, String name, String type)` — 唯一性检查后 save
- `updateTag(String userId, String tagId, String name, String type)` — 归属检查 + 唯一性检查（排除自身）后 save
- `deleteTag(String userId, String tagId)` — 孤儿卡检查 + 中间表清理 + 删除
- `getTags(String userId, String typeFilter)` — 原有方法，加 type 过滤参数

**`TagRepository` 新增/修改**：
- `findByNameIgnoreCaseAndUserId(String name, String userId)` — Slice 2 中已添加（如果是先做 Slice 2），否则本切片添加
- `existsByNameIgnoreCaseAndUserIdAndIdNot(String name, String userId, String id)` — 编辑时去重（排除自身）

### 单元测试

| 端点 | 测试场景 |
|------|---------|
| `POST /api/tags` | name 为空 → 422；name 大小写不敏感重复 → 422；正常创建（type=null） → 200；正常创建 Deck（type="deck"） → 200 |
| `PUT /api/tags/{id}` | tag 不存在 → 404；userId 不匹配 → 404；name 改为已存在的值 → 422；name 改为相同值（自身） → 200；正常修改 → 200 |
| `DELETE /api/tags/{id}` | tag 不存在 → 404；userId 不匹配 → 404；孤儿计数 > 0 → 422 + orphanCount；孤儿计数 = 0 → 200 删除成功 |

## Acceptance criteria

- [ ] `/manage/index.html` 可访问，页面显示右侧竖排 Cards / Tags Tab 栏
- [ ] 默认选中 Cards Tab（空状态占位），点击 Tags 切换到 Tags Tab
- [ ] `☰` 导航栏中 Cards 链接正确跳转到 `/manage/`
- [ ] Tags Tab 初始为空状态："暂无标签" + "创建标签" 按钮
- [ ] 创建 Tag：Modal 输入 name + 勾选 Deck → 保存 → 表格新增一行
- [ ] 创建 Tag 时 name 重复 → alert 错误提示
- [ ] Inline 编辑：点 Edit → 行变为 name input + checkbox + Save/Cancel → Save → 更新成功
- [ ] Inline 编辑时 name 改为已存在的其他 Tag → alert 错误提示
- [ ] 删除非 Deck 的普通 Tag → 确认后删除，表格移除该行
- [ ] 删除 Deck Tag 且存在孤儿卡 → alert "N 张卡片将失去所有牌组，无法删除"，Tag 未被删除
- [ ] 删除 Deck Tag 且无孤儿卡 → 确认后删除，表格移除该行
- [ ] Tags Tab 的 `destroy()` 正确清理 DOM 和事件监听（切换到 Cards Tab 时调用）
- [ ] `mvn test` 全部通过，包括新单元测试
- [ ] `mvn compile` 通过

## Blocked by

- [#04 — Shared Navigation Foundation](./04-shared-navigation.md)（需要 nav.js 提供 header 和 ☰ 导航，需要 base.css 提供 Modal 样式）
