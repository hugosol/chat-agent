# PRD: 全局时区修复 + Instant 类型迁移 + 实体缓存解耦

**Status:** `ready-for-agent`

## Problem Statement

Learner 在 Docker 部署环境中遇到多个由 Docker 容器 UTC 时区导致的 bug：

1. **Review 当天统计错误**（已确认）：`reviewedToday` 始终为 0，`learnedToday` 始终为 0（新卡无限出）。根因：`computeTodayStart()` 在 `UserPreferences.timezone` 为 null 时回退到 `ZoneId.systemDefault()` → UTC。结合 `dayStartHour=6`，"今天"边界 = UTC 6:00 AM = 北京时间 14:00 PM。14:00 前复习的卡片 `lastReview` < 边界，不计入当天。Learner 测试验证：14:00 之后学习，`reviewedToday` 正常 +1。

2. **MemoryCue 时间标签错误**：AI 系统提示词中引用的记忆时间标签（"this morning / yesterday / last night"）反映的是服务器 UTC 墙面时间，而非 Learner 本地时间。例如上午 10 点创建的 MemoryCue 被标为 "last night"（因为 UTC 时间是凌晨 2 点，hour=2 < 6）。

3. **数据库查询效率低**：Review 流程中 `UserPreferences` 每次请求被查 3 次（rateCard → getNextCard → computeStats），`FsrsParameters` 被查 1 次。现有 `"fsrsConfig"` 缓存耦合两个数据源，`UserPreferences` 无独立缓存。

## Solution

一次性修复四条战线：

1. **前端静默初始化时区**：Header 入口检测浏览器时区 → 若 `UserPreferences.timezone` 为空则自动保存。SessionStorage 防抖避免重复。
2. **存储层全面使用 Instant**：`BaseEntity.createTime/updateTime` 和 `Session.startTime/endTime` 从 `LocalDateTime` → `Instant`。消除存储层时区歧义。
3. **展示层按用户时区显示**：`TimeLabel.computeLabel()` 接受 `Instant + ZoneId`，`ConversationAgent` 和 `TurnProcessor` 注入 `UserPreferencesService` 获取时区后转换。
4. **实体缓存解耦**：`"userPreferences"` + `"fsrsParameters"` 两个独立缓存替代旧的 `"fsrsConfig"` 合并缓存。

| 问题 | 修复依赖 | 关键改动 |
|------|---------|---------|
| Review 统计 | 前端时区初始化 + UserPreferences 缓存 | Header entry + CacheConfig + UserPreferencesService |
| MemoryCue 标签 | 存储 Instant + TimeLabel 新签名 + Agent 注入时区 | BaseEntity + TimeLabel + ConversationAgent + TurnProcessor |
| Card 创建时间 | Instant 存储（意外收获） | BaseEntity（前端 `new Date()` 自动正确） |
| Session 时间 | Instant 存储 | Session 字段类型变更 |

## User Stories

1. 作为一名 Learner，无论是否手动配置过时区，首次登录后系统自动检测我的浏览器时区并保存，所有时区相关功能正确运作。
2. 作为一名 Learner，在 Docker 部署环境下上午 10 点复习的卡片也能正确计入"今日已复习"，不再等到下午 2 点才生效。
3. 作为一名 Learner，新卡每日上限正确生效——学到上限后不再出新卡，而非无限出新卡。
4. 作为一名 Learner，AI 对话中引用的记忆提示显示正确的时间标签（"今天早上"而非错误的"last night"）。
5. 作为一名 Learner，管理页面卡片创建时间以我的本地时区正确显示。
6. 作为一名 Learner，在设置页面手动修改时区后，Review 统计和 MemoryCue 标签都立即使用新时区（缓存即时失效）。
7. 作为一名 Learner，Review 评分过程中偏好数据从内存缓存读取，不需要每次都查数据库。
8. 作为一名 Developer，所有时间戳统一存储为 Instant，消除 LocalDateTime 的时区歧义。
9. 作为一名 Developer，UserPreferences 和 FsrsParameters 缓存互相独立——修改偏好只失效偏好缓存，修改 FSRS 参数只失效参数缓存。
10. 作为一名 Developer，新模块 FsrsParametersService 与 UserPreferencesService 设计完全对称。

## Implementation Decisions

### 1. 时区初始化：Header 入口 + sessionStorage 防抖

- 放置在 Header 构建入口组件的 useEffect 中。Header 在每个认证页面都会被挂载，天然全覆盖。
- `sessionStorage.getItem('tz_checked')` 防抖：同一 tab 会话内只执行一次。
- 流程：GET /api/user/preferences → 若 timezone 为空 → `Intl.DateTimeFormat().resolvedOptions().timeZone` → PUT /api/user/preferences 静默保存。
- 异步不阻塞渲染。第一张卡片可能仍用旧时区，第二张起生效。

### 2. 缓存架构：两个独立实体缓存

```
"userPreferences" 缓存 ← @Cacheable on UserPreferencesService.get()
"fsrsParameters"  缓存 ← @Cacheable on FsrsParametersService.get()
```

- FsrsConfigService.getConfig() 移除 @Cacheable，改为从两个服务各取实体 → 现场 merge → 返回 FsrsSchedulerConfig。
- merge 是纯字段复制 + 字符串解析，计算开销可忽略，无需缓存结果。
- CacheConfig 缓存名列表从 "fsrsConfig" 改为 "userPreferences", "fsrsParameters"。TTL 保持 24h expireAfterAccess。
- UserPreferencesService.get() 添加 @Cacheable。
- UserPreferencesService.save() 的 @CacheEvict 从 "fsrsConfig" 改为 "userPreferences"。

### 3. FsrsParametersService 新建

- 对称于 UserPreferencesService：get(userId) + @Cacheable；save(params) + @CacheEvict。
- FsrsOptimizeService.saveParameters() 和 DataInitializer 的 paramsRepository.save() → FsrsParametersService.save()。
- FsrsConfigService.getConfig() → 改为调 fsrsParametersService.get() + preferencesService.get() + merge。

### 4. 移除手动 CacheManager 操作

- FsrsOptimizeService：移除 CacheManager 注入。缓存通过 FsrsParametersService.save() 的 @CacheEvict 自动失效。
- ReviewService：移除 CacheManager 注入。rescheduleAllCards() 中的手动 evict 删除（该方法无生产调用方且不改参数）。

### 5. BaseEntity.createTime / updateTime → Instant

- 字段从 LocalDateTime 改为 Instant。@CreatedDate / @LastModifiedDate 自动适配 Instant.now()。
- 17 个实体子类自动继承新类型。getUpdateTime() 无生产调用方。

### 6. Session.startTime / endTime → Instant

- Session 自己的独立字段（不继承 BaseEntity），从 LocalDateTime 改为 Instant。
- 构造函数、complete()、SessionDbStore 中的 LocalDateTime.now() → Instant.now()。
- ChatMessageHandler 序列化用 toString()，自动输出带 Z 后缀。前端不消费 SESSION_HISTORY。

### 7. TimeLabel.computeLabel() 签名变更

```java
// 旧
public static String computeLabel(LocalDateTime eventTime, LocalDateTime referenceTime)

// 新
public static String computeLabel(Instant eventTime, Instant referenceTime, ZoneId zoneId)
// 内部：eventTime.atZone(zoneId).toLocalDateTime() → 其余逻辑不变
```

- MemoryCue.getTimeLabel() 和 UserLearningProfile.getTimeLabel()（@Transient，无生产调用方）同步更新签名。

### 8. ConversationAgent 注入 UserPreferencesService

- 构造函数新加 UserPreferencesService 参数（Spring 自动注入）。
- formatMemoryCuesForPrompt() 从 static 改为实例方法，内部通过 UserPreferencesService 获取 ZoneId 传入 TimeLabel。
- 需要从上方调用链传入 userId（buildSystemContent 已有上下文可扩展）。

### 9. TurnProcessor 注入 UserPreferencesService

- 构造函数新加 UserPreferencesService 参数（Spring 自动注入）。
- resolveMemoryContext() 中获取 ZoneId，传入 TimeLabel.computeLabel()。

### 10. CueMatch createdAt → Instant

- Record 字段从 LocalDateTime 改为 Instant。
- Instant 实现 Comparable，MemoryCueQueue 的 Comparator.comparing 自然兼容。

### 11. EmbeddingService 简化

- indexSync() 和 indexAsync() 参数 LocalDateTime createdAt → Instant createdAt。
- 写入：createdAt.toEpochMilli() 直接替代 atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()。
- 读取：Instant.ofEpochMilli(...) 直接替代 LocalDateTime.ofInstant(Instant.ofEpochMilli(...), ZoneId.systemDefault())。

### 12. 下游类型适配

- UserListItem 记录字段 LocalDateTime createTime → Instant createTime。
- LlmCallLogRepository.deleteByCreateTimeBefore() 参数 LocalDateTime cutoff → Instant cutoff。
- LlmCallLogService.cleanupOnStartup() 中 LocalDateTime.now().minusDays(3) → Instant.now().minus(3, ChronoUnit.DAYS)。
- FlashcardController 和 ReviewController 中 card.getCreateTime().toString() 输出格式从 `2026-06-07T02:00:00` → `2026-06-07T02:00:00Z`（带 Z 后缀）。

### 13. 前端影响

- 卡片创建时间：formatDate() 用 new Date(dateStr) 接收带 Z 后缀的 ISO 字符串，JavaScript 按 UTC 解析后转为浏览器本地时区——比现在 LocalDateTime.toString()（无时区被当成本地时间解析）更准确，属于额外修复。
- 用户管理页：substring(0, 10) 截取 ISO Instant 字符串的日期部分，仍有效。
- 前端测试：header-entry.tsx 是 Vite 构建入口，不被任何组件测试 import，零影响。

### 14. H2 数据库 DDL 迁移

- spring.jpa.hibernate.ddl-auto: update 下，Hibernate 自动将 TIMESTAMP 列改为 TIMESTAMP WITH TIME ZONE。
- 历史数据兼容：原 LocalDateTime.now()（UTC 墙面时间）和 Instant.now() 表示相同绝对时刻，Hibernate 读写映射一致。
- 如自动迁移失败，需备份 .mv.db 文件后重建。建议实施前验证。

## Testing Decisions

### 测试原则

- 只测试外部行为，不测试缓存实现细节（@Cacheable 是否存在、CacheManager 是否被调）。
- 前端测试不 mock sessionStorage（jsdom 原生支持），不因 entry 层改动破坏。
- 后端测试验证业务流程，不验证缓存命中。

### 需要适配的测试文件（13 个）

| 测试文件 | 改动要点 |
|---------|---------|
| TimeLabelTest | 最重：52 处 computeLabel(LocalDateTime, LocalDateTime) → (Instant, Instant, ZoneId)；所有 LocalDateTime.of() → Instant 等价构造 |
| ConversationAgentTest | 构造函数加 UserPreferencesService 参数（2 处）；5 处 CueMatch 类型变更；formatMemoryCuesForPrompt 测试中 LocalDateTime.now().withHour(9) → ZonedDateTime 转换 |
| TurnProcessorTest | 构造函数加 UserPreferencesService；9 处 CueMatch 创建 LocalDateTime.of() → Instant.parse() |
| LlmCallLogServiceTest | ArgumentCaptor<LocalDateTime> → <Instant>；minusMinutes → Duration 模式 |
| LlmCallLogRepositoryTest | 4 处 LocalDateTime.now() → Instant.now()；minusDays() → Duration 模式；native SQL 列类型需验证 |
| EmbeddingServiceTest | LocalDateTime.of() → Instant.parse() |
| MemoryCueQueueTest | 4 处 CueMatch 创建变更 |
| MemoryCueServiceTest | indexAsync() 参数位置如变化需调整 mock matching |
| ReviewServiceTest | 移除 @Mock CacheManager + Cache；删除手动 evict 断言；移除构造函数中的 CacheManager 参数 |
| FsrsOptimizeServiceTest | 移除 Cache mock + CacheManager 注入；@Mock FsrsConfigService → @Mock FsrsParametersService |
| SessionAuditingTest | getCreateTime/StartTime/EndTime 返回类型变更 — 断言 isNotNull() 兼容 |
| SessionDbStoreTest | getEndTime() 返回类型变更 — 断言兼容 |
| ChatMessageHandlerTest | 若 ChatMessageHandler 构造函数也加 UserPreferencesService 则需适配 |

### 类型变化但断言兼容的测试文件（10 个）

ErrorRecordAuditingTest、MessageAuditingTest、SessionReportAuditingTest、UserProgressAuditingTest、UserAuditingTest、ChatAgentSessionIT、DailyTalkIT 等——assertNotNull() 对 Instant 同样有效，无需修改。

### 零影响测试文件

FlashcardControllerTest、CardBatchServiceTest、ChatAgentMemoryIT、EntityMapperTest、ReviewControllerTest、E2ETestBase、UserPreferencesServiceTest、FsrsSchedulerTest、FsrsOptimizerTest、FsrsRescheduleLogRepositoryTest 等。

## Out of Scope

- CSV 导出文件名时间戳 — 纯展示，低优先级。
- LLM 日志清理窗口 — 窗口本就不精确（3 天），时区偏移无感知。
- FSRS Optimizer 行为 — same-day 检测使用原始 elapsedSeconds < 86400，时区无关。
- UserLearningProfile.getTimeLabel() — 当前无生产调用方，仅做编译适配。
- Docker TZ 环境变量 — 不依赖。本方案通过 UserPreferences.timezone 支持每用户独立时区。

## Further Notes

- 若多个时区用户使用同一部署实例，Docker TZ 环境变量只能服务一个时区。本方案通过每用户 UserPreferences.timezone 支持多时区场景。
- Card.createTime 的 toString() 格式变更（增加 Z 后缀）对前端是额外修复——new Date() 行为更正确。
- 排查 H2 DDL update 行为应在实施第一步完成。如失败则备份数据文件重建。
- 不改动 computeTodayStart() 逻辑本身——它已正确读取 UserPreferences.timezone，只需外部让它拿到正确值。
- 不改动 FsrsSchedulerConfig record——保持纯算法 record，不引入 timezone 或 dayStartHour。
