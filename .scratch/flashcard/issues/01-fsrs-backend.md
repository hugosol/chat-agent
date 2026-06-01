# 01 — 后端基础：FSRS-6 调度器 + 数据模型 + REST API + 安全配置

**Status:** `ready-for-agent`

## Parent

[PRD: 闪卡模块 —— 卡片录入 MVP](../PRD.md)

## What to build

实现闪卡模块的完整后端：FSRS-6 间隔重复调度算法、Card/Tag JPA 实体与仓库、FlashcardService 业务逻辑、REST API（`POST /api/cards/add` 和 `GET /api/tags`），以及 SecurityConfig 的 CSRF 豁免配置。整个流程通过单元测试验证，可通过 `curl` 手动测试。

**FSRS-6 调度器（~150 行，纯 Java，无 Spring 依赖）：**
- 21 个默认参数常量（w[0]...w[20]），值与 py-fsrs v6.3.1 完全一致
- 无状态纯函数：输入卡片当前状态 + 评分（Again/Hard/Good/Easy）→ 输出新状态（stability, difficulty, state, due, reps, lapses, lastReview）
- 提供 `initNewCard()` 工厂方法：stability=2.5, difficulty=0.0, state=New, due=now, reps=0, lapses=0, lastReview=null
- `repeat()` 方法实现完整调度但不暴露 REST 端点（为后续复习预留）

**数据模型：**
- `Card` 实体：继承 `BaseEntity`，`@Id` UUID 字符串，字段：userId, front, back, stability, difficulty, state, due, reps, lapses, lastReview
- `Tag` 实体：继承 `BaseEntity`，`@Id` UUID 字符串，字段：userId, name, type（nullable String，MVP 为 null）
- `Card` ↔ `Tag` 通过 `@ManyToMany` 关联（join table `card_tags`），Card 侧为 owning side
- 与现有聊天实体（Session、Message 等）无 FK 关联

**仓库：**
- `CardRepository extends JpaRepository<Card, String>` — 无额外方法（MVP 只有创建）
- `TagRepository extends JpaRepository<Tag, String>` — `findByNameAndUserId(String name, String userId)` 用于 upsert 时的去重查找；`findByUserId(String userId)` 用于 autocomplete

**服务层：**
- `FlashcardService.createCard(front, back, tagNames, userId)`：
  1. 调用 `FsrsScheduler.initNewCard()` 初始化 FSRS 状态
  2. 遍历 tagNames，按 name+userId 查找已有 Tag，不存在的创建（type=null），收集到 Set
  3. 创建 Card 并关联 Tag Set
  4. `cardRepository.save()` 持久化
- `FlashcardService.getTags(userId)`：返回该用户所有 Tag（name + type）

**REST API：**

*`POST /api/cards/add`*
```
Request:  { "front": "yesterday", "back": "昨天", "tags": ["daily", "time"] }
Response: { "id": "uuid", "front": "yesterday", "back": "昨天", "tags": [{"name":"daily","type":null},{"name":"time","type":null}], "due": "2026-05-30T...Z" }
```

*`GET /api/tags`*
```
Response: [{"name":"daily","type":null},{"name":"time","type":null}]
```

- userId 从 `SecurityContextHolder.getContext().getAuthentication()` 获取；e2e profile 下 Principal 为 null → fallback `"anonymous"`
- 请求验证：front 不能为空

**控制器：**
- `FlashcardController`（`@RestController`，`@RequestMapping("/api")`）——代码库中首个 `@RestController`
- 分别映射 `POST /cards/add` 和 `GET /tags`

**安全配置：**
- `SecurityConfig` 中 CSRF 豁免列表增加 `"/api/**"` ——不影响现有规则
- `/api/**` 不加入 `permit-all-paths`（需要认证，通过 JSESSIONID cookie）

## Acceptance criteria

- [ ] `mvn test` 通过：8 个 FSRS-6 单元测试（预期值来自 py-fsrs，tolerance 1e-4）覆盖全部 21 个参数和 4 种评分分支
- [ ] `mvn test` 通过：FlashcardService 单元测试验证卡片创建 + FSRS 状态初始化 + Tag upsert
- [ ] H2 DDL auto-update 自动创建 `card`、`tag`、`card_tags` 三张表
- [ ] `curl -X POST http://localhost:8080/api/cards/add -H "Content-Type: application/json" -d '{"front":"hello","back":"你好","tags":["greeting"]}'`（带有效 JSESSIONID）返回 200 + 完整 JSON
- [ ] `curl http://localhost:8080/api/tags` 返回已创建的 tag 列表
- [ ] 同一用户重复使用已存在的 tag 名，Tag 记录不重复创建（upsert）
- [ ] `/api/**` 请求在无 JSESSIONID 时返回 302 重定向到登录页（非 e2e profile）

## Blocked by

None — 可立即开始
