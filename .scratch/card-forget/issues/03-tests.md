# 03: 测试补充

**Status:** `ready-for-agent`

## 范围

为 forget 功能补充单元测试、集成测试和 E2E 场景。

## 实现内容

### 单元测试（ReviewServiceTest 追加 6 个）

| 测试 | 验证内容 |
|------|---------|
| `forgetCard_resetsFsrsStateToNew` | 卡片 FSRS 字段全部重置为初始值（state=0, stability=0, ...） |
| `forgetCard_deletesAllReviewLogs` | mock 验证 `deleteByCardId(cardId)` 被调用一次 |
| `forgetCard_cardNotFound_throws404` | 不存在的卡片 ID → ResponseStatusException(NOT_FOUND) |
| `forgetCard_wrongUser_throws404` | 卡片属于其他用户 → ResponseStatusException(NOT_FOUND) |
| `forgetDeck_resetsAllCardsAndDeletesLogs` | mock 验证 N 张卡全部调用 save + deleteByCardIdIn |
| `forgetDeck_emptyDeck_returnsZeroCounts` | Deck 无卡片 → ForgetDeckResult(0, 0) |

### 集成测试（ReviewControllerTest 追加 2 个）

| 测试 | 验证内容 |
|------|---------|
| `forgetCard_validRequest_returns200` | 响应含正确 cardState=0 和 deletedReviewCount |
| `forgetDeck_validRequest_returns200` | 响应含正确 cardCount 和 totalDeletedReviewCount |

### E2E 测试（ManagePageIT 追加 2 个场景）

| 场景 | 步骤 |
|------|------|
| **单卡遗忘** | 1. 创建卡片 + 模拟复习（插入 ReviewLog）2. 点击 `[data-testid='btn-forget-card']` 3. 确认弹窗显示删除数量 4. 点击确认 5. 验证 DB 中卡片 state=0、ReviewLog 表无相关记录 |
| **Deck 批量遗忘** | 1. 创建 Deck + 多张卡片 + 模拟复习 2. 选择 Deck 3. 点击"重置全部卡片" 4. 确认弹窗显示正确计数 5. 确认执行 6. 验证 DB 中全部卡片 state=0、ReviewLog 清空 |

### 不受影响的现有测试（全部确认通过）
- `ReviewServiceTest`（23 个）：forget 方法是新增，不与现有测试交互
- `ReviewControllerTest`：新增端点不改变已有端点
- `ManagePageIT`（已有场景）：新增按钮的 `data-testid` 不与已有 `btn-delete-card` 等冲突
- `ReviewIT`（9 个）：遗忘不在复习流中
- `FlashcardIT`、`FlashcardBatchIT`：不涉及 forget

### Prior art
- `ManagePageIT` 已有卡片删除的确认弹窗流程（使用 Playwright dialog handler 或自定义 modal）— forget 复用相同模式
- `ReviewServiceTest` 的 `rateCard_wrongUser_throws404` 提供所有权验证的 mock 模式 — forget 复用

## 依赖
- Issue 01（后端 forget API 实现完毕）
- Issue 02（前端 forget UI 实现完毕）— E2E 测试需要

## 验证
- `mvn test` 全部通过（含新增的 8 个测试）
- `mvn verify` E2E 全部通过（含新增的 2 个 forget 场景）
