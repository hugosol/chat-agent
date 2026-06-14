# 01: 卡片 (front, tag) 级别防重 + 跨牌组确认

**Status:** `ready-for-agent`

## Parent

[PRD: 卡片 Tag 级别防重 + 牌组完成时间估算](../PRD.md)

## What to build

将卡片唯一性规则从全局 `front` 改为 `(front, tag)` pair。同一 Deck 内不允许两张 front 相同的卡片；不同 Deck 允许各自独立的同名卡片。编辑卡片时同样适用此规则。

**核心行为**:

1. **创建卡片 (POST /api/cards/add)**: 同 Deck 内已有同名 → 422 硬拒绝，消息包含冲突的 Deck 标签名称。跨 Deck 已有同名 → 正常创建（不拦截）。

2. **新增 check 端点 (POST /api/cards/check)**: 接受与 addCard 相同的 payload，返回跨牌组冲突信息 `{ "conflicts": [{ "tagId": "...", "tagName": "..." }] }`。前端在调用 addCard 之前先调 check，显示确认对话框让用户决定是否创建独立副本。

3. **编辑卡片 (PUT /api/cards/{id})**: 改 front 或改 tagIds 时执行 `(front, tag)` 级别检查（排除自身 ID），防止编辑后在同一 Deck 内产生同名冲突。

4. **前端确认对话框**: 创建卡片时若 check 返回跨牌组冲突，弹出对话框显示冲突牌组名称列表，用户确认后继续创建。

**不改的范围**: CSV 导入已有 `(front, tag)` 级别防重逻辑 (`findExistingFrontsByTag`)，无需改动。Tag 实体的 Many-to-Many 关系不变。

## Acceptance criteria

- [ ] `POST /api/cards/add` 同 Deck 有同名卡片 → 422，消息含牌组名
- [ ] `POST /api/cards/add` 跨 Deck 有同名卡片 → 201，正常创建
- [ ] `POST /api/cards/add` 无同名 → 201，正常创建
- [ ] `POST /api/cards/check` 返回跨牌组冲突的 tag 列表
- [ ] `PUT /api/cards/{id}` 改 front 到同 Deck 已有值 → 422，消息含牌组名
- [ ] `PUT /api/cards/{id}` 跨 Deck 已有同名但同 Deck 无冲突 → 200，正常更新
- [ ] 前端创建卡片时先调 check，有跨牌组冲突时弹出确认对话框
- [ ] 前端确认后正确调用 addCard 创建卡片
- [ ] 后端单元测试: `FlashcardServiceTest` 新增/改写 same-deck conflict、cross-deck allow、updateCard conflict、check endpoint 测试
- [ ] 后端 Controller 测试: `FlashcardControllerTest` 新增 check 端点测试
- [ ] 前端单元测试: DeckPicker/卡片创建组件适配新行为
- [ ] `mvn test` 全部通过

## Blocked by

None — can start immediately.
