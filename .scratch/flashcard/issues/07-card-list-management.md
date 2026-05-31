# 07 — Card List & Management

**Status:** `ready-for-agent`

## Parent

[PRD: 闪卡模块 Step 2 —— Deck 激活 + 卡片管理页面 + 导航统一](../PRD-step2.md)

## What to build

实现管理页 Cards Tab 的完整功能：卡片 block 列表、全文搜索、A↔Z/时间排序、Deck chip 单选筛选、服务端分页。每条卡片支持查看详情 Modal、编辑 Modal、删除确认 Modal。卡片创建 Modal 可供从管理页直接录入新卡片。

本切片完成后，Learner 可以完全脱离聊天页在管理页独立管理闪卡库——浏览、搜索、筛选、排序、分页、创建、编辑、删除，形成完整的 CRUD 闭环。

### 后端 Card CRUD API

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET /api/cards` | 分页列表。参数：`search`（可选，front 模糊搜索）、`deckId`（可选，Deck 单选筛选）、`page`、`size`、`sort`（如 `front,asc` 或 `createTime,desc`）。后端用 Spring Data Pageable 自动绑定。 |
| `PUT /api/cards/{id}` | 编辑卡片。body: `{front, back, tagIds}`。卡片不存在 → 404。userId 不匹配 → 404。front 重复排除自身（大小写不敏感）→ 422。缺少 Deck tag → 422。tagId 不存在 → 422。tag 不属于当前用户 → 422。 |
| `DELETE /api/cards/{id}` | 删除卡片。卡片不存在或 userId 不匹配 → 404。删除中间表 `card_tags` 关联记录 + 删除 Card 本身。 |

**`CardRepository` 扩展**：
- 继承 `JpaSpecificationExecutor<Card>`，支持动态 Specification 组合查询。
- 新增方法：`Optional<Card> findByFrontIgnoreCaseAndUserIdAndIdNot(String front, String userId, String id)` — 编辑去重排除自身。

**`FlashcardService` 新增方法**：
- `listCards(String userId, String search, String deckId, Pageable pageable)` — 构建 Specification：
  - `(root, query, cb) -> cb.equal(root.get("userId"), userId)`（必须，数据隔离）
  - search 非空时：`cb.like(cb.lower(root.get("front")), "%" + search.toLowerCase() + "%")`
  - deckId 非空时：`cb.equal(join(root.join("tags"), "id"), deckId)`
  - 由 `cardRepository.findAll(spec, pageable)` 执行分页查询
  - 返回 `Page<Card>`（content 迭代时需要保证 tags 已加载，使用 `@EntityGraph` 或 JOIN FETCH 避免 N+1）
- `updateCard(String userId, String cardId, String front, String back, List<String> tagIds)` — 与 createCard 类似的校验逻辑（Deck 检查、tagId 有效性、tag 归属、front 去重排除自身）
- `deleteCard(String userId, String cardId)` — 归属检查 + 删除

**列表 API 返回体**（`Page<Card>` → Spring 自动序列化为）：
```json
{
  "content": [
    {
      "id": "uuid",
      "front": "...",
      "back": "...",
      "tags": [{ "id": "uuid", "name": "...", "type": "deck" }],
      "due": "2025-01-01T00:00:00Z",
      "cardState": 0,
      "createTime": "2025-01-01T00:00:00"
    }
  ],
  "page": 0,
  "totalPages": 1,
  "totalElements": 1,
  "size": 20
}
```
- `cardState` 为 FSRS 状态整数（0=New, 1=Learning, 2=Review, 3=Relearning），仅作为只读标签显示。
- FSRS 调度参数（stability、difficulty、reps、lapses、lastReview）不暴露在列表响应中（使用 `@JsonIgnore` 或 DTO 映射排除）。

### 前端 `manage/card.js` — Cards Tab 模块

IIFE 模式，`'use strict'`。导出 `window.manageCards = { init, destroy }`。

**`init()`**：
1. 初始调用 `GET /api/cards?page=0&size=20&sort=front,asc` 加载第一页卡片列表。
2. 渲染控件行（搜索框 + 排序按钮 + Deck chip 筛选行）。
3. 渲染卡片 block 列表 + 分页控件。
4. 绑定所有事件监听。

**搜索框**：
- `<input type="text" placeholder="搜索卡片...">` + 输入防抖 300ms → 重置 `page=0` → 重新 fetch（追加 `search` 参数）。

**排序按钮**：
- 两个互斥按钮：`A→Z`（默认激活）和 `时间`。只能单选一个。
- `A→Z`：`sort=front,asc`（名称正序）或 `sort=front,desc`（点击切换正/倒序，在按钮上显示当前方向）。
- `时间`：`sort=createTime,desc`（时间倒序）或 `sort=createTime,asc`（点击切换）。
- 切换排序方式 → 重置 `page=0` → 重新 fetch。

**Deck chip 单选筛选**：
- 初始化时调用 `GET /api/tags?type=deck` 获取所有 Deck Tag。
- 以 chip 列表渲染在搜索/排序行下方。每条 chip 显示 tag.name。
- 点击 chip → 选中（高亮，附加 `deckId` 到 API 请求）→ 重置 `page=0` → 重新 fetch。
- 再次点击已选中的 chip → 取消选中（`deckId` 从请求中移除）→ 重新 fetch。
- Deck 列表为空 → 该行显示 `"暂无牌组，创建牌组"` 提示文字。

**卡片 block 列表**：
- 每条卡片渲染为一个暗色 block（与聊天页消息气泡视觉一致）：
  - 主标题：`front`（加粗或较大字号）
  - 副标题：`back` 截断 50 字符（超长加 `...`）
  - tag chips：显示关联的所有 Tag（用 `.chip` 样式，与录入面板风格一致）
  - 操作按钮：`Edit` + `Delete`（右侧对齐）
- 点击整个 block（非按钮区域）→ 弹出详情 Modal。
- 列表为空 → 显示空状态 `"暂无卡片"` + `"创建第一张卡片"` 按钮。

**分页控件**：
- 位于列表底部：`< 上一页` | 页码按钮（如 `1 2 3 ... 10`）| `下一页 >`。
- 当前页按钮高亮不可点击。第一页时"上一页"禁用；末页时"下一页"禁用。
- 总页数 ≤ 1 时隐藏分页控件。
- 点击任何页码 → 重新 fetch 对应 page。
- page size 固定为 20（`size=20`），不做可调配置。

**详情 Modal**：
- 点击卡片 block → `manageModal.open()` 弹出，展示（只读）：
  - Front
  - Back
  - Tags（chip 展示，Deck 类型标记）
  - 卡片状态（cardState → 可读标签：0=New, 1=Learning, 2=Review, 3=Relearning）
  - 下次复习时间（due，格式化为可读日期时间）
  - 创建时间（createTime）
- Modal 只有关闭按钮，无可编辑表单。

**创建卡片 Modal**：
- `"创建第一张卡片"` 按钮（空状态时）或顶部 `"新建卡片"` 按钮 → 弹出 Modal：
  - Front `<input>`
  - Back `<textarea>`
  - Tag 多选下拉（调用 `GET /api/tags`，渲染为 checkbox 列表，至少选一个 Deck Tag）
  - 保存按钮
- 确认 → POST `/api/cards/add`（body: `{front, back, tagIds}`）。成功 → 关闭 Modal 刷新列表（回到第一页）。失败（422）→ alert 错误信息。

**编辑卡片 Modal**：
- Edit 按钮 → 弹出 Modal，预填当前 front、back、tagIds：
  - Front `<input>` 预填当前值
  - Back `<textarea>` 预填当前值
  - Tag 多选下拉（预选当前 tags）
  - 保存按钮
- 确认 → PUT `/api/cards/{id}`。成功 → 关闭 Modal 刷新列表。失败 → alert 错误信息。
- Front 重复检查允许与自身相同（大小写变化如 "yesterday" → "Yesterday" 不算重复）。

**删除卡片**：
- Delete 按钮 → `window.confirm("确定要删除这张卡片吗？")` → DELETE `/api/cards/{id}` → 成功 → 刷新列表。

**`destroy()`**：解绑所有事件监听（搜索 input、排序按钮、Deck chip、卡片 block click、Edit/Delete 按钮、分页按钮），清空内容区 DOM。

### 单元测试

| 端点 | 测试场景 |
|------|---------|
| `GET /api/cards` | 正常分页（page=0, size=20）；搜索含特殊字符；空结果；Deck 筛选有/无结果；sort=front,asc / sort=createTime,desc；搜索 + 分页联动（搜索后 page 重置为 0）；无效排序字段 → 默认排序 |
| `PUT /api/cards/{id}` | 卡片不存在 → 404；userId 不匹配 → 404；front 重复（不同卡片） → 422；front 相同（自身，如大小写变化） → 200；缺少 Deck tag → 422；tagId 不存在 → 422；正常编辑 → 200 |
| `DELETE /api/cards/{id}` | 卡片不存在 → 404；userId 不匹配 → 404；正常删除 → 200（连带中间表记录清除） |

## Acceptance criteria

- [ ] 管理页切换到 Cards Tab → 可见搜索框 + A→Z/时间排序按钮 + Deck chip 筛选行
- [ ] 搜索框输入文字后 300ms 自动请求 API 并刷新列表（防抖）
- [ ] 点击 A→Z 排序 → 卡片按 front 正序排列；再次点击 → 倒序排列
- [ ] 点击时间排序 → 卡片按创建时间倒序排列；再次点击 → 正序排列
- [ ] A→Z 和时间排序互斥——选中一个时另一个取消
- [ ] Deck chip 列表显示所有 Deck Tag；点击 chip → 只显示该 Deck 下的卡片；再次点击 → 取消筛选显示全部
- [ ] 如果没有 Deck Tag，Deck chip 行显示 "暂无牌组，创建牌组"
- [ ] 卡片 block 显示 front（主标题）、back 截断 50 字符（副标题）、tag chips、Edit/Delete 按钮
- [ ] 点击卡片 block → 详情 Modal 展示 front/back/tags/cardState/due/createTime
- [ ] 创建卡片：Modal 输入 front/back + 选择 tags → 保存 → 列表刷新出现新卡片
- [ ] 创建卡片：只选普通 tag 不选 Deck → alert "至少需要一个牌组标签"
- [ ] 编辑卡片：Modal 预填原值 → 修改 front/back/tags → 保存 → 刷新
- [ ] 编辑卡片：修改 front 为与另一卡片相同的值 → alert "卡片已存在"
- [ ] 编辑卡片：修改 front 为仅大小写变化 → 保存成功（排除自身去重）
- [ ] 删除卡片：确认 Modal → 确认 → 卡片从列表消失；H2 中已删除
- [ ] 列表为空时显示 "暂无卡片" + "创建第一张卡片" 按钮
- [ ] 分页控件：多页时显示上一页/页码/下一页，点击可翻页
- [ ] 筛选/搜索/排序变化时自动重置到 page=0
- [ ] `destroy()` 正确清理所有事件监听
- [ ] `mvn test` 全部通过，包括新单元测试
- [ ] `mvn compile` 通过

## Blocked by

- [#05 — Deck Activation + Tag API + Flashcard Panel Rework](./05-deck-activation-tag-api.md)（需要 `AddCardRequest.tagIds` 和 Deck 验证逻辑；需要 `GET /api/tags` 含 id 和 type 过滤）
- [#06 — Manage Page Shell + Tag CRUD](./06-manage-page-tag-crud.md)（需要管理页外壳 + Tab 切换框架 + `manage/modal.js` 共享 Modal 模块）
