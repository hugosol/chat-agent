Status: ready-for-agent

# 卡片删除时级联清理 CardEnhancement

## Parent

PRD: `.scratch/enhance-movie-ui-optimization/PRD.md` — 改动项 #4

## What to build

当 Learner 删除卡片时，同步清理 `card_enhancements` 表中的关联行，避免孤立数据残留。

### 后端改动

`CardEnhancementRepository` 新增 Spring Data JPA 派生查询方法：

```java
void deleteByCardId(String cardId);
```

`FlashcardService.deleteCard()` 在 `cardRepository.delete(card)` 之前调用：

```java
cardEnhancementRepository.deleteByCardId(cardId);
```

### 范围限定

- 仅覆盖 `FlashcardService.deleteCard()` 单卡删除路径。
- 批量 CSV 导入当前不删除已有卡片（仅检查重复并拒绝），因此不做批量覆盖删除的增强清理（PRD 明确 out of scope）。
- 无需修改 `FlashcardController`——controller 层只负责请求分发和响应，级联清理属于 service 层事务边界内。

## Acceptance criteria

- [ ] 删除卡片后，`card_enhancements` 表中该 cardId 的所有行被移除
- [ ] 删除不存在的卡片时抛出 404，不执行增强清理（事务一致）
- [ ] 删除他人卡片时抛出 404，不执行增强清理（权限隔离）
- [ ] 无增强记录的卡片删除正常（`deleteByCardId` 删除零行不报错）
- [ ] `FlashcardServiceTest`：`deleteCard_alsoDeletesEnhancements`、`deleteCard_cardNotFound_throws404`、`deleteCard_wrongUser_throws404`

## Blocked by

None — 可立即开始
