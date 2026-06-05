# 02: API 嵌入 preview 字段

**Status:** `ready-for-agent`

## 范围

在 `GET /api/review/start` 和 `POST /api/review/next` 的响应 JSON 中新增 `preview` 字段。

## 实现内容

### ReviewController 改造

**GET /api/review/start** (`startReview` 方法)：
- 获取卡片后调用 `reviewService.previewCard(card, Instant.now())`
- 构建 preview Map：遍历每个 Rating 的 CardState，用 `cardStateToMap()`（新建）转为 JSON
- 响应新增 `preview` 字段（仅 card 非 null 时）

**POST /api/review/next** (`processNextCard` 方法)：
- 先 `rateCard()`（不变）
- 再 `getNextCard()`（不变）
- 如果 nextCard 非 null → 调用 `previewCard(nextCard)` → 嵌入响应

### 新增 cardStateToMap() 私有方法
- 将 CardState record 转为 `Map<String, Object>`：
  - stability, difficulty, state, step, reps, lapses
  - due: ISO 字符串
  - lastReview: ISO 字符串或 null
  - elapsedDays

### 响应格式
```json
{
  "card": { "id": "...", "front": "...", ... },
  "stats": { "reviewedToday": 5, ... },
  "preview": {
    "AGAIN": { "stability": 2.5, "difficulty": 5.0, "state": 3, "step": 0, "due": "2026-06-05T10:01:00Z", ... },
    "HARD": { ... },
    "GOOD": { ... },
    "EASY": { ... }
  }
}
```

## 依赖
Issue 01（previewCard 方法）

## 验证
- ReviewControllerTest 扩展：start 和 next 端点响应中包含 `preview` 字段
- 验证 preview 有 4 个键（AGAIN/HARD/GOOD/EASY）
