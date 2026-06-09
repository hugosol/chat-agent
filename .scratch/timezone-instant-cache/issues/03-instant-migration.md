Status: `ready-for-agent`

## What to build

将存储层所有时间戳字段从 `LocalDateTime` 统一迁移为 `Instant`，消除类型层面的时区歧义。基础改动在 `BaseEntity`（影响 17 个实体子类）和 `Session` 独立字段，然后级联适配所有下游引用点。

**端到端流程**：卡片创建 → `BaseEntity.createTime` 存储为 `Instant.now()`（UTC 绝对时刻）→ API 返回 `"2026-06-07T02:00:00Z"`（带 Z 后缀）→ 前端 `new Date(dateStr)` 按 UTC 解析后转为浏览器本地时区正确显示。

**关键实现点**：

1. **`BaseEntity.createTime/updateTime`**：字段类型 `LocalDateTime` → `Instant`。`@CreatedDate`/`@LastModifiedDate` 自动适配，无需改动注解。17 个子类自动继承。

2. **`Session.startTime/endTime`**：独立字段（不继承 BaseEntity），类型 `LocalDateTime` → `Instant`。所有构造/设置点中 `LocalDateTime.now()` → `Instant.now()`：构造函数、`complete()`、`SessionDbStore`（成功和失败路径）。

3. **`CueMatch.createdAt`**：record 字段 `LocalDateTime` → `Instant`。`Instant` 本身实现 `Comparable`，`MemoryCueQueue` 的 `Comparator.comparing(CueMatch::createdAt)` 自然兼容，无需改动。

4. **`EmbeddingService`**：`indexSync()` / `indexAsync()` 参数 `LocalDateTime createdAt` → `Instant createdAt`。写入端 `createdAt.toEpochMilli()` 直接替代 `atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()`；读取端 `Instant.ofEpochMilli(...)` 直接替代 `LocalDateTime.ofInstant(Instant.ofEpochMilli(...), ZoneId.systemDefault())`。

5. **下游类型适配**：
   - `UserListItem.createTime`：`LocalDateTime` → `Instant`
   - `LlmCallLogRepository.deleteByCreateTimeBefore()`：参数 `LocalDateTime cutoff` → `Instant cutoff`
   - `LlmCallLogService.cleanupOnStartup()`：`LocalDateTime.now().minusDays(3)` → `Instant.now().minus(3, ChronoUnit.DAYS)`
   - `FlashcardController` / `ReviewController`：`card.getCreateTime().toString()` 输出格式从 `"2026-06-07T02:00:00"` 变为 `"2026-06-07T02:00:00Z"`（带 Z 后缀）
   - `MemoryCue.getTimeLabel()`：内部做 Instant → LocalDateTime 适配桥接，保持旧 `TimeLabel` 接口可用（等待 Issue 4 正式改签名）

6. **H2 DDL 迁移**：`spring.jpa.hibernate.ddl-auto: update` 下，Hibernate 自动将 TIMESTAMP 列改为 TIMESTAMP WITH TIME ZONE。历史 `LocalDateTime.now()`（UTC 墙面时间）与 `Instant.now()` 表示相同绝对时刻，读写映射一致。

## Acceptance criteria

- [ ] `BaseEntity.createTime/updateTime` 类型为 `Instant`，17 个子类正常编译
- [ ] `Session.startTime/endTime` 类型为 `Instant`，所有构造/设置点使用 `Instant.now()`
- [ ] `CueMatch.createdAt` 类型为 `Instant`，`MemoryCueQueue` 排序行为不变
- [ ] `EmbeddingService.indexSync/indexAsync` 接受 `Instant createdAt`，epoch-milli 读写简化
- [ ] `LlmCallLogRepository.deleteByCreateTimeBefore` 参数为 `Instant`
- [ ] `LlmCallLogService.cleanupOnStartup` 使用 `Instant.now().minus(3, ChronoUnit.DAYS)`
- [ ] `UserListItem.createTime` 类型为 `Instant`
- [ ] 卡片 API 返回的 createTime 字符串带 Z 后缀（如 `"2026-06-07T02:00:00Z"`）
- [ ] H2 数据库启动后 `createTime` 列类型为 `TIMESTAMP WITH TIME ZONE`（Hibernate ddl-auto:update 自动迁移）
- [ ] 以下测试全部通过：TimeLabelTest、ConversationAgentTest、TurnProcessorTest、LlmCallLogServiceTest、LlmCallLogRepositoryTest、EmbeddingServiceTest、MemoryCueQueueTest、MemoryCueServiceTest、SessionAuditingTest、SessionDbStoreTest、ChatMessageHandlerTest
- [ ] 类型兼容的测试文件（ErrorRecordAuditingTest、MessageAuditingTest、SessionReportAuditingTest、UserProgressAuditingTest、UserAuditingTest、ChatAgentSessionIT、DailyTalkIT 等）不被破坏
- [ ] `mvn test` 全绿

## Blocked by

None - can start immediately
