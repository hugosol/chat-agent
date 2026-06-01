# 05 — Deck Activation + Tag API + Flashcard Panel Rework

**Status:** `ready-for-agent`

## Parent

[PRD: 闪卡模块 Step 2 —— Deck 激活 + 卡片管理页面 + 导航统一](../PRD-step2.md)

## What to build

激活 Tag.type 字段的 Deck 概念，将卡片创建从"传 tag 名称"改为"传 tagIds"，强制要求每张卡片至少关联一个 Deck 类型标签。同时扩展 `GET /api/tags` 端点（加 `id` 字段和 `?type=deck` 过滤器），为后续管理页提供基础 Tag 查询能力。聊天页录入面板（`flashcard.js`）从自由输入 tag 改为从下拉列表选择已存在的 Tag。

### 后端变更

**Tag 模型激活**：
- `Tag.type` 字段从预留变为活跃：`type=null` = 普通标签，`type="deck"` = 牌组（Deck）。数据库列已存在，无需改 DDL。
- `TagRepository` 新增方法：`Optional<Tag> findByNameIgnoreCaseAndUserId(String name, String userId)` — 同一用户下 Tag name 唯一（大小写不敏感）。

**`AddCardRequest` 字段变更**：
- `tags: List<String>`（tag 名称） → `tagIds: List<String>`（tag UUID）。向后不兼容——前端需同步更新。

**`TagResponse` 扩展**：
- 新增 `id: String` 字段（Tag 的 UUID）。现有 `name` 和 `type` 字段保持不变。
- `GET /api/tags` 新增可选查询参数 `?type=deck`：传入时只返回 `type="deck"` 的 Tag；不传时返回所有 Tag。

**`FlashcardService.createCard()` 逻辑修改**：
1. 校验 `tagIds` 非空（为空 → 422 `"标签不能为空"`）
2. 根据 `tagIds` 逐个查 `TagRepository.findById()` + 校验 `userId` 归属（别人的 Tag → 422）
3. 查出的 Tag 中至少一个 `getType().equals("deck")`（否则 → 422 `"至少需要一个牌组标签"`）
4. 不再自动 upsert Tag——tagId 不存在直接 422 `"标签不存在"`（Tag 必须先由 Learner 在管理页创建）
5. front 重复检查（大小写不敏感）逻辑保持不变

**`FlashcardController` 修改**：
- `createCard()` 的 `AddCardRequest` 反序列化自动适配新字段名
- `getTags()` 新增 `@RequestParam(required = false) String type` 参数，传给 Service 做过滤

**数据迁移**：
- 清空现有卡片和标签数据（因为新约束下旧数据无效）：
  ```sql
  DELETE FROM card_tags;
  DELETE FROM cards;
  DELETE FROM tags;
  ```
- 在 `DataInitializer` 或独立的 `@PostConstruct` 迁移脚本中执行。确保只在表存在且有数据时执行。

### 前端变更：`flashcard.js` 录入面板

- Tag 输入区从自由文本输入 + autocomplete 创建改为**纯下拉多选选择器**：
  - 输入框触发时调用 `GET /api/tags` 获取全部用户 Tag（含 id、name、type字段）
  - 下拉列表展示 Tag name，已选中的 tags 在下拉中置灰/标记
  - 点击 Tag 名添加到 chips（存储 `{id, name, type}`），不创建新 Tag
  - 不再允许输入不存在的名称来创建新标签
- Save 按钮回调：
  - POST body 改为 `{front, back, tagIds: [...]}`（不再传 `tags`）
  - Save 前检查 chips 中是否至少有一个 `type === "deck"` 的 Tag → 否则 alert "至少需要一个牌组标签"
- 下拉列表的每条 Tag 区分显示：Deck 类型 Tag 可加标识（如 icon 或括号标注），方便 Learner 识别

### 单元测试

参照现有 `FlashcardServiceTest`（Mockito）和 `FlashcardControllerTest`（`@WebMvcTest`）风格，覆盖：

| 端点 | 测试场景 |
|------|---------|
| `POST /api/cards/add` | tagIds 为空 → 422；tagId 不存在 → 422；缺少 Deck tag → 422；front 大小写不敏感重复 → 422；tag 不属于当前用户 → 422；正常创建成功 → 200 |
| `GET /api/tags` | 无参数返回全部 Tag；`?type=deck` 只返回 Deck Tag；返回体包含 `id` 字段 |

## Acceptance criteria

- [ ] `Tag.type` 列的值在创建 Tag 时可以被设置为 `"deck"`（通过直接 INSERT 验证即可——管理页创建接口在 Slice 3 实现）
- [ ] `POST /api/cards/add` 改为接收 `tagIds`，按 ID 查找 Tag，拒绝不存在的 tagId（422）
- [ ] `POST /api/cards/add` 拒绝没有 Deck 类型 Tag 的请求（422 `"至少需要一个牌组标签"`）
- [ ] `POST /api/cards/add` 拒绝其他用户的 tagId（422）
- [ ] `GET /api/tags` 返回的每个 Tag 包含 `id` 字段
- [ ] `GET /api/tags?type=deck` 只返回 `type="deck"` 的 Tag
- [ ] 聊天页录入面板的 tag 输入区变成下拉多选选择器（不再自由输入创建）
- [ ] 录入面板保存时提示"至少需要一个牌组标签"如果未选择 Deck
- [ ] 现有 `FlashcardIT` E2E 测试更新为使用新的 tagIds 协议并通过
- [ ] `mvn test` 全部通过，包括新单元测试
- [ ] `mvn compile` 通过

## Blocked by

None — can start immediately（不依赖 Slice 1）
