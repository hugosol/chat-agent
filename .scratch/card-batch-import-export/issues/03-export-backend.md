# 03 — 导出后端

**Status:** `ready-for-agent`

## Parent

[PRD: 闪卡批量导入/导出](../PRD.md)

## What to build

在 `CardBatchService`（Issue 02 已创建）中实现 `exportCards()` 方法，并在 `FlashcardController` 中新增 GET 导出端点。

**服务层**：`CardBatchService.exportCards(String tagId, String userId) → ExportData`（内部 record，含 `byte[] csvBytes` + `String fileName`）：
1. 校验 tagId：TagRepository.findById → userId 匹配 → type=="deck"，失败抛 ResponseStatusException
2. 查询该 tag 下全部卡片：通过 Specification 或直接查询 JPA 关联（确保按 userId 隔离）
3. 生成 CSV（Apache Commons CSV，`CSVFormat.DEFAULT.builder().setHeader(...)`）：
   - 表头：`front,back,stability,difficulty,cardState,due,reps,lapses,lastReview`
   - 每行写 front、back（RFC 4180 转义）、stability、difficulty、cardState（整数→文本映射：0→New/1→Learning/2→Review/3→Relearning）、due（ISO-8601）、reps、lapses、lastReview（ISO-8601 或留空）
   - UTF-8 无 BOM
4. 记录 `BatchOperationLog`：operationType=EXPORT，totalRows=卡片数，successCount=null，skipCount=null，status=SUCCESS
5. 文件名格式：`{tagName}_{yyyyMMdd_HHmmss}.csv`（如 `基础语法_20240604_103000.csv`）

**Controller 层**：`FlashcardController` 新增：
- `GET /api/cards/export?tagId=xxx` — 设置 `Content-Type: text/csv; charset=UTF-8`，`Content-Disposition: attachment; filename="..."`，写入 CSV 字节流到 `HttpServletResponse.getOutputStream()`

**边界情况**：
- tag 下无卡片：返回仅含表头的 CSV（空数据行）
- due 或 lastReview 为 null：CSV 该单元格留空

## Acceptance criteria

- [ ] `GET /api/cards/export?tagId=xxx` 返回 CSV 文件，浏览器自动触发下载
- [ ] CSV 文件名为 `{tagName}_{yyyyMMdd_HHmmss}.csv`
- [ ] CSV 内容包含 tag 下全部卡片的 front、back、FSRS 状态字段
- [ ] cardState 显示为 `New`/`Learning`/`Review`/`Relearning`（非数字）
- [ ] due 和 lastReview 为 ISO-8601 格式
- [ ] 字段内含逗号或换行符的 front/back 正确用双引号包裹
- [ ] tag 下无卡片时返回仅含表头的空 CSV
- [ ] `batch_operation_log` 正确记录导出操作（operationType=EXPORT）
- [ ] 手动浏览器验证：访问 URL 下载 CSV，用文本编辑器打开检查内容
- [ ] 导出再导入（Issue 02 的 curl 验证）能正确往返

## Blocked by

- 02-import-backend（共享 CardBatchService 和 BatchOperationLog 实体）
