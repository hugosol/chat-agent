# 01: 后端 forget API

**Status:** `ready-for-agent`

## 范围

实现 forget 功能的后端逻辑：Service 方法、Repository 扩展、REST 端点。

## 实现内容

### ReviewLogRepository 扩展
- `void deleteByCardId(String cardId)` — 删除单张卡片的所有 ReviewLog
- `void deleteByCardIdIn(List<String> cardIds)` — 批量删除
- `int countByCardId(String cardId)` — 统计复习记录数（确认弹窗用）
- Spring Data JPA 派生方法，自动实现

### ReviewService.forgetCard()
- 签名：`void forgetCard(String cardId, String userId)`
- `@Transactional`
- 流程：
  1. `cardRepository.findById(cardId)` → 不存在或 userId 不匹配 → 404
  2. `reviewLogRepository.deleteByCardId(cardId)` → 删除复习历史
  3. `FsrsScheduler.createInitState(Instant.now())` → 获取初始状态
  4. 将 Card 的 FSRS 字段重置为初始值（stability/difficulty/cardState/step/due/reps/lapses/lastReview/firstReviewDate）
  5. `cardRepository.save(card)`

### ReviewService.forgetDeck()
- 签名：`forgetDeckResult forgetDeck(String deckId, String userId)`
- 返回 record：`ForgetDeckResult(int cardCount, int deletedReviewCount)`
- `@Transactional`
- 流程：
  1. 查询 Deck 下属于当前 userId 的卡片 ID 列表
  2. 若列表为空 → 返回 `(0, 0)`
  3. `reviewLogRepository.deleteByCardIdIn(cardIds)` → 批量删除
  4. 逐卡重置 FSRS 字段为初始值
  5. `cardRepository.saveAll(cards)` → 批量保存

### ReviewController 端点

**POST /api/cards/{cardId}/forget**
- userId 从 SecurityContext 获取
- 调用 `reviewService.forgetCard(cardId, userId)`
- 响应 `{ "id": cardId, "cardState": 0, "deletedReviewCount": N }`
- 卡片不存在或权限不匹配 → 404

**POST /api/cards/forget?deckId={tagId}**
- 调用 `reviewService.forgetDeck(deckId, userId)`
- 响应 `{ "cardCount": N, "totalDeletedReviewCount": M }`
- Deck 不存在或无卡片 → 200 + count=0

### DTO
- `ForgetDeckResult(int cardCount, int deletedReviewCount)` — 新的 record，放在 dto 包

## 依赖
无（独立功能，不依赖 P0 或其他 PRD）

## 验证
- `mvn test` 通过
- ReviewControllerTest 验证端点 200/404 响应
