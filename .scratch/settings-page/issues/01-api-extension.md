# 01: 后端 API 扩展 —— UserPreferences 端点接受新字段

**Status:** `ready-for-agent`

## 范围

扩展 `GET /api/user/preferences` 和 `PUT /api/user/preferences` 端点，支持 6 个新 FSRS 字段的读写。确保部分更新兼容（DeckPicker 不传新字段时保持原值）。

## 实现内容

### GET /api/user/preferences 扩展
- 响应新增 6 个字段：`learningSteps`, `relearningSteps`, `desiredRetention`, `maximumInterval`, `enableFuzz`, `shuffleDueCards`
- null 值字段原样返回 null（前端自行处理默认值显示）
- 保持现有字段不变

### PUT /api/user/preferences 扩展
- 请求体新增接受 6 个字段（全部可选）
- 部分更新逻辑：
  - 若 body 包含某字段 → 更新对应 UserPreferences 字段
  - 若 body 不包含某字段 → 保持现有值（不重置为 null）
- 所有字段的类型校验 + 格式校验
- 保存成功后清除 Caffeine 缓存：`cacheManager.getCache("fsrsConfig").evict(userId)`
- 响应返回更新后的全部字段

### 后端验证规则
- `learningSteps`：匹配 `^(\d+[smhd],)*\d+[smhd]$` 或 null/空  → 不合法返回 400
- `relearningSteps`：同上
- `desiredRetention`：null 或 0.01~0.99（Double）
- `maximumInterval`：null 或 ≥ 1（Integer）
- `enableFuzz`：null 或 boolean
- `shuffleDueCards`：null 或 boolean

### 部分更新兼容（关键）
DeckPicker 发送 `{ lastDeckId, lastMode, newCardDailyLimit }` → 后端只更新这 3 个字段，FSRS 字段保持不变。实现方式：逐字段检查 body.containsKey()。

## 依赖
- P0 Issue 03：UserPreferences 实体已有 6 个新字段
- P0 Issue 06：Caffeine CacheManager 可用

## 验证
- ReviewControllerTest 扩展：4 个新测试用例
