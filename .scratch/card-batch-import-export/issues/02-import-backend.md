# 02 — 导入后端全链路

**Status:** `ready-for-agent`

## Parent

[PRD: 闪卡批量导入/导出](../PRD.md)

## What to build

实现从 HTTP 请求接收 CSV 文件到卡片持久化的完整导入链路。包括以下层：

**数据层**：
- `BatchOperationLog` JPA 实体（id, userId, operationType, tagId, tagName, fileName, totalRows, successCount, skipCount, errorDetails, status, createTime）
- `BatchOperationType` 枚举：IMPORT, EXPORT
- `BatchOperationStatus` 枚举：SUCCESS, PARTIAL, FAILED
- `BatchOperationLogRepository`（`JpaRepository<BatchOperationLog, String>`）
- `CardRepository` 新增 `@Query` 方法：`SELECT LOWER(c.front) FROM Card c WHERE LOWER(c.front) IN (:fronts) AND c.userId = :userId`，返回 `List<String>`——用于大小写不敏感批量查重

**配置层**：
- `application.yml` 新增：`spring.servlet.multipart.max-file-size=5MB`、`spring.servlet.multipart.max-request-size=5MB`
- `application-e2e.yml` 同新增

**服务层**：新建 `CardBatchService`（独立 Service，不扩展 FlashcardService）：
- `importCards(MultipartFile file, String tagId, String userId) → ImportResult`
- 完整流程（在一个方法内编排）：
  1. 校验 tagId（TagRepository.findById → userId 匹配 → type=="deck"，失败抛异常并记录 FAILED 日志）
  2. `CardCsvParser.parse(file.getInputStream())` → `List<ParsedCardRow>`
  3. `validateAll(rows, userId)` → 统一校验方法，三步顺序执行：
     - **逐行校验**：front.isBlank()、back.isBlank()、FSRS 字段格式和范围（stability>0、difficulty∈[0,1]、cardState 文本映射值、reps≥0、lapses≥0、due/lastReview 有效 ISO-8601）
     - **内存去重**：`Set<String>` 按 front.toLowerCase() 检查 CSV 内部重复
     - **SQL 查重**：调用 CardRepository 批量 IN 查询 → `Set.contains()` 检查冲突
     - 收集所有 `ImportError(row, front, reason)` 到 `List`
  4. 有错误 → `BatchOperationLog` 记录 status=FAILED + `errorDetails` 为 JSON `[{row,front,reason}]` → 返回 `ImportResult`（totalRows, successCount=0, errors）
  5. 无错误 → `@Transactional` 批量创建 Card 实体（front/back + FSRS 字段有值则设值、无值调 `FsrsScheduler.createInitState()`）→ `card.setTags(Set.of(tag))` → `cardRepository.saveAll()` → 记录 status=SUCCESS 日志
- `exportCards()` 方法在本 issue 中仅声明接口（留空或抛 UnsupportedOperationException），由 Issue 03 实现

**Controller 层**：`FlashcardController` 新增：
- `POST /api/cards/import` — `@RequestParam("file") MultipartFile file, @RequestParam("tagId") String tagId` → `ImportResult`

**DTO**：
- `ImportResult` record：`int totalRows, int successCount, List<ImportError> errors`
- `ImportError` record：`int row, String front, String reason`

**单元测试**：`CardBatchServiceTest`（@Mock CardRepository + TagRepository + CardCsvParser + BatchOperationLogRepository）覆盖：
- 全部成功导入
- 全部校验失败（各种原因：front 空、back 空、FSRS 非法、CSV 内重复、DB 重复）
- 部分成功（有错误行 + 有效行，整体事务回滚——因为校验全量前置）
- tagId 不存在 / 不属于用户 / type != "deck"
- CSV 无有效行
- batch_operation_log 正确写入（status + errorDetails）

## Acceptance criteria

- [ ] `POST /api/cards/import` 接受 multipart file + tagId，返回 ImportResult JSON
- [ ] 全部校验通过时：卡片入库、每张卡归属选中的 deck tag、FSRS 字段有值则保留/无值则默认
- [ ] 校验失败时：不插入任何卡片（整体事务）、返回 `{totalRows: N, successCount: 0, errors: [...]}`
- [ ] tagId 非法时：直接返回错误、不解析 CSV
- [ ] `batch_operation_log` 表正确记录每次操作（SUCCESS / FAILED，含 errorDetails JSON）
- [ ] file 超过 5MB 被 Spring 拒绝（HTTP 413 或 MultipartException）
- [ ] `CardBatchServiceTest` 全部通过（`mvn test -Dtest=CardBatchServiceTest`）
- [ ] 手动 curl 验证：上传合法 CSV → 卡片入库；上传含重复 front 的 CSV → 返回错误清单、卡片未入库

## Blocked by

- 01-card-csv-parser（需要 CardCsvParser 解析 CSV）
