# PRD: 闪卡遗忘功能 —— 重置卡片 FSRS 状态

**Status:** `ready-for-agent`

## Problem Statement

Learner 在使用闪卡复习的过程中，会遇到需要将某张或某批卡片"从头学起"的场景：导入了一个 Deck 但 FSRS 状态有误、长时间不复习已经完全遗忘、或单纯想重新开始一张卡片的记忆周期。当前系统没有提供重置卡片 FSRS 状态的功能——Learner 只能删除卡片再重新创建，但这会丢失卡片内容（front/back）和标签关联。

Learner 需要一个"遗忘"操作：将卡片的 FSRS 调度状态清空重置为全新卡片，同时清理掉与该卡片相关的所有复习历史，让卡片回到"从未被复习过"的状态。

## Solution

在管理页面新增"遗忘"功能，支持单张卡片遗忘和整个 Deck 批量遗忘。遗忘操作将卡片 FSRS 状态重置为 `createInitState` 的初始值，并物理删除该卡的所有 ReviewLog 记录。操作需要确认弹窗（显示将被删除的复习记录数量），且不可撤销。

## User Stories

1. 作为一名 Learner，我可以在管理页面对一张特定卡片执行"遗忘"，将其 FSRS 状态重置为全新卡片（state=New），复习历史全部清空。
2. 作为一名 Learner，我可以对整个 Deck 执行批量遗忘，一次操作重置该 Deck 下所有卡片的 FSRS 状态。
3. 作为一名 Learner，点击遗忘按钮后弹出确认对话框，明确显示将被删除的复习记录数量和受影响的卡片数量。
4. 作为一名 Learner，遗忘后卡片的内容（front/back）、标签、创建时间等非 FSRS 字段不受影响。
5. 作为一名 Learner，如果导入了带有错误 FSRS 状态的 Deck，我可以一键批量遗忘全部卡片，从头开始复习。
6. 作为一名 Learner，遗忘操作不可撤销，确认对话框明确告知这一点。
7. 作为一名开发者，遗忘操作是事务性的——ReviewLog 删除和 Card 状态重置要么全成功要么全失败。
8. 作为一名开发者，遗忘操作验证卡片归属权（userId），不同 Learner 的卡片无法被交叉遗忘。
9. 作为一名开发者，ReviewLog 表上的删除操作为物理删除（DELETE），不留下孤儿数据。

## Implementation Decisions

### 1. 卡片重置目标状态

遗忘后卡片状态与 `FsrsScheduler.createInitState(now)` 完全一致：

- `stability = 0.0`
- `difficulty = 0.0`
- `cardState = 0`（New）
- `step = -1`
- `due = Instant.now()`
- `reps = 0`
- `lapses = 0`
- `lastReview = null`
- `firstReviewDate = null`

卡片内容字段（front、back）、标签关联（tags）、创建时间（createTime）和 ID 保持不变。

### 2. ReviewLog 处理

物理删除（DELETE FROM review_logs WHERE card_id = ?）。不使用软删除或标记失效。理由：卡片已经是"全新"，其历史记录无参考价值，保留它们会在未来优化器中产生虚假的训练信号。

### 3. API 设计

**单卡遗忘：**
```
POST /api/cards/{cardId}/forget
Response: { "id": "...", "cardState": 0, "deletedReviewCount": 5 }
```

**Deck 批量遗忘：**
```
POST /api/cards/forget?deckId={tagId}
Response: { "cardCount": 50, "totalDeletedReviewCount": 320 }
```

- 两个端点均需 JSESSIONID cookie 认证（通过 `/api/**` 的 Spring Security 配置）
- 归属权验证：卡片/Deck 的 userId 必须与当前认证用户一致
- 卡片不存在 → 404；Deck 不存在或 Deck 下无卡片 → 200 + count=0

### 4. 事务性

`ReviewService.forgetCard()` 和 `ReviewService.forgetDeck()` 标注 `@Transactional`：
1. 验证所有权
2. 删除 ReviewLog（DELETE）
3. 重置 Card FSRS 字段（UPDATE）
4. Save Card

若任一步骤失败，全部回滚。

### 5. 前端交互

**管理页面单卡遗忘：**
- 每张卡片行新增"遗忘"按钮（`data-testid='btn-forget-card'`）
- 点击弹出确认对话框，显示："将删除 X 条复习记录，卡片恢复为全新状态。此操作不可撤销。"
- 确认后调用 API，操作成功后卡片列表刷新

**管理页面 Deck 批量遗忘：**
- Deck 上下文菜单或工具栏新增"重置全部卡片"按钮
- 点击弹出确认对话框，显示："将重置 Deck 下 Y 张卡片为全新状态，并删除共 X 条复习记录。此操作不可撤销。"
- 确认后调用 API，操作成功后卡片列表刷新

### 6. ReviewLogRepository 新增方法

- `void deleteByCardId(String cardId)` — Spring Data JPA 派生方法
- `void deleteByCardIdIn(List<String> cardIds)` — 用于 Deck 批量遗忘
- `int countByCardId(String cardId)` — 用于确认弹窗显示复习记录数量

### 7. 归属权验证

- 单卡遗忘：通过 `CardRepository.findById(cardId)` 获取卡片，检查 `card.getUserId().equals(currentUserId)`
- Deck 批量遗忘：查询 Deck 下的卡片时添加 `userId` 过滤条件，确保只重置当前用户的卡片
- 不匹配 → 404（不暴露卡片存在信息）

## Testing Decisions

### What makes a good test
- 测试外部行为（卡片状态变为 New、ReviewLog 被删除），不测试内部字段赋值细节
- 使用 mock Repository 验证正确的删除和保存方法被调用
- 所有权验证：确保跨用户访问返回 404

### Test seams

**单元测试（ReviewServiceTest 追加 6 个）**
- `forgetCard_resetsFsrsStateToNew`：验证所有 FSRS 字段回到初始值
- `forgetCard_deletesAllReviewLogs`：mock 验证 `deleteByCardId(cardId)` 被调用
- `forgetCard_cardNotFound_throws404`
- `forgetCard_wrongUser_throws404`
- `forgetDeck_resetsAllCardsAndDeletesLogs`：验证 N 张卡片全部重置 + 全部 ReviewLog 删除
- `forgetDeck_wrongUser_throws404`

**集成测试（ReviewControllerTest 追加 2 个）**
- `forgetCard_validUser_returns200AndReset`：POST 端点返回正确响应
- `forgetDeck_validUser_returns200AndCounts`：批量端点返回正确计数

**E2E 测试（ManagePageIT 追加 2 个场景）**
- 单卡遗忘 + 验证 DB 中卡片 state=0、ReviewLog 清空
- Deck 批量遗忘 + 验证 DB 中全部卡片 state=0
- 使用 `data-testid='btn-forget-card'` 点击，不依赖按钮文本

**不受影响的现有测试**
- `ReviewServiceTest`（23 个）：forgetCard 和 forgetDeck 是新增方法，不影响现有测试
- `ReviewIT`（9 个）：遗忘在管理页执行，不在复习流中
- `FlashcardIT`、`FlashcardBatchIT`：遗忘不涉及创建或导入导出
- `ManagePageIT` 已有场景：遗忘按钮的 selector 是新增的，不替代已有按钮

### Prior art
- `ManagePageIT` 已有卡片删除场景（`btn-delete-card`），forget 流程类似——管理页按钮 + 确认弹窗 + API 调用 + DB 验证
- `ReviewServiceTest` 已有 rateCard 的所有权验证模式（`wrongUser_throws404`），forget 复用相同模式

## Out of Scope

- **rollback 撤销遗忘**：遗忘不可逆，不提供撤销功能。后续 L2 的 rollback 功能可能覆盖此场景
- **部分重置**（只重置 FSRS 状态但不删除 ReviewLog）：Learner 需求明确要求"完全遗忘"，删除 ReviewLog 是核心诉求
- **forget 在复习页面**：仅管理页面提供遗忘入口，复习流中不显示
- **ReviewLog 归档/备份**：删除前不做数据保留，直接物理删除
- **标签/内容级联影响**：遗忘只影响被选中的卡片，不影响同一 Tag 下未被选中的卡片

## Further Notes

### 依赖关系
- 独立功能，不依赖 P0（Scheduler 参数化）或其他 PRDs
- `createInitState()` 为静态方法，可直接调用，无需 Scheduler 实例
- ReviewLogRepository 已有基本 CRUD，只需新增派生 delete 方法

### 数据一致性
- Deck 批量遗忘时，先收集 Deck 下所有卡片 ID，再批量 delete ReviewLog + 批量 update Card，而非逐张处理
- 若 Deck 下有 ReviewLog 的 cardId 与 Card 表不匹配（孤立日志），静默跳过
