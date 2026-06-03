# PRD: 闪卡批量导入/导出（CSV）

**Status:** `ready-for-agent`

## Problem Statement

Learner 创建了大量闪卡用于间隔复习，目前这些卡片仅存储在本地 H2 数据库中。Learner 面临两个痛点：

1. **跨终端迁移**：当 Learner 更换设备（如在公司电脑和家里电脑之间切换）时，所有卡片和学习进度（FSRS 状态）需要手动逐张重新录入，无法批量迁移。
2. **备份需求**：Learner 希望将某个牌组（deck tag）下的全部卡片导出为文件进行备份，在需要时（如误删、重置数据后）完整还原，包括保留所有复习进度（stability、difficulty、due 等 FSRS 状态字段）。

Learner 需要一个简单的批量导入/导出功能，以 CSV 格式操作某个 deck tag 下的全部卡片，保留 FSRS 复习进度，支持跨终端继承学习数据。

## Solution

在 Manage 页面的 Cards tab 工具栏中新增批量导入/导出功能：

- **导出**：Learner 选择一个 deck tag，系统将该 tag 下全部卡片导出为 CSV 文件（含 front、back、FSRS 完整状态字段），cardState 使用可读的文本值（New/Learning/Review/Relearning）而非整数值，确保跨系统可读。
- **导入**：Learner 选择一个 deck tag、上传 CSV 文件，系统在内存中完成全部校验（逐行校验 + 内部去重 + 数据库查重），校验全部通过后事务性插入。每张导入的卡片只关联选中的那个 deck tag。校验失败时返回详细的错误清单（行号、front、失败原因），并记录到 batch_operation_log 表中。
- **审计**：所有导入/导出操作记录到 `batch_operation_log` 表，可通过 H2 console 查询历史。

工具栏交互重新设计：原有 `Aa` / `T` 两个独立排序按钮合并为一个下拉菜单按钮（4 个排序选项），新增一个文件图标下拉按钮（导出/导入），`+` 新建按钮保持不变。

## User Stories

1. 作为一名 Learner，我可以将某个牌组下的全部卡片导出为一个 CSV 文件，包含正面、背面和 FSRS 复习进度（稳定性、难度、状态、到期时间、复习次数、遗忘次数、上次复习时间），以便在其他设备上导入时继承学习进度。
2. 作为一名 Learner，导出的 cardState 字段显示为可读的文本（New/Learning/Review/Relearning）而非数字，让我用任何文本编辑器打开 CSV 时都能理解每张卡片的状态。
3. 作为一名 Learner，我可以将 CSV 文件导入到指定的牌组中，导入的卡片全部归属于该牌组，系统会自动跳过与已有卡片重复的行，并在结果中告诉我哪些行被跳过以及原因。
4. 作为一名 Learner，导入时如果 CSV 中包含 FSRS 状态列，系统会保留这些复习进度；如果 CSV 没有这些列，系统自动用默认初始值填充，我不会损失已有卡片数据。
5. 作为一名 Learner，导入或导出前我需要在弹出的模态框中先选择一个 deck 类型的标签，选中标签后导入/导出按钮才可用。
6. 作为一名 Learner，导入完成后我能在模态框中看到清晰的导入结果：成功导入 X 张、失败 Y 张，以及失败行的行号、front 内容和失败原因，方便我修正 CSV 后重新导入。
7. 作为一名 Learner，导出文件名为 `{牌组名}_{时间戳}.csv`，我可以多次下载同一个牌组而文件不会互相覆盖。
8. 作为一名 Learner，如果选中的牌组下没有卡片，导出的 CSV 文件仍有表头行（空数据），我知道这个牌组是空的。
9. 作为一名 Learner，工具栏上的排序按钮变为下拉菜单，我可以看到 4 种排序选项（Aa ↑、Aa ↓、T ↑、T ↓），选中后按钮标签更新为我的选择。
10. 作为一名 Learner，工具栏上的文件图标按钮展开菜单后有"导出"和"导入"两个选项，点击对应选项打开批量操作模态框。
11. 作为一名 Learner，导入过程中模态框显示加载指示器，操作完成后替换为结果展示，整个过程不阻塞页面其他操作。
12. 作为一名开发者，所有导入/导出操作自动记录到 `batch_operation_log` 表，可通过 H2 console 查询谁在什么时间对哪个牌组执行了什么操作、成功/失败的详情。
13. 作为一名开发者，CSV 导入兼容带 BOM 头的 UTF-8 文件（如 Windows 记事本生成的 CSV），系统自动跳过 BOM 头。
14. 作为一名开发者，CSV 解析按列名称匹配而非列序号，用户调整列顺序或添加额外列不影响正常导入。
15. 作为一名开发者，E2E 测试覆盖完整的导出→删卡→导入→验证 FSRS 状态还原的往返流程，确保批量操作的数据完整性。

## Implementation Decisions

### 1. CSV 格式：完整 FSRS 状态 + 文本 cardState

- 表头（英文，与 Card 实体字段名一致）：`front,back,stability,difficulty,cardState,due,reps,lapses,lastReview`
- `front` 和 `back`：卡片正反面文本，字段内含逗号或换行符时用双引号包裹（RFC 4180）。
- `cardState` 文本映射：`New`↔0, `Learning`↔1, `Review`↔2, `Relearning`↔3。导出写文本，导入解析文本映射回整数。
- `due` 和 `lastReview`：ISO-8601 格式（`Instant.parse()` 直接兼容）。
- `stability`、`difficulty`、`reps`、`lapses`：数值，与 FSRS-6 算法字段对应。
- CSV 编码：UTF-8 无 BOM（导出），导入时自动兼容 BOM（通过 BOMInputStream 跳过）。
- CSV 不含 tags 列——导入导出限定单个 deck tag，无多 tag 场景。

### 2. 导入导出限定单个 deck tag

- 导出范围：选中 deck tag 下的全部卡片（忽略页面搜索过滤状态）。
- 导入行为：所有卡片关联到选中的 deck tag（即使 CSV 来自其他 tag 的导出）。
- tag 类型校验：后端检查目标 tag 的 `type == "deck"` 且属于当前用户，不通过则直接拒绝。
- 选择器：前端使用 `<select>` 下拉框列出当前用户的所有 deck tag，单选。

### 3. 导入校验流程：前置全量校验 + 事务插入

校验在 `validateAll()` 一个方法中统一完成，分为三步：

1. **逐行校验**（纯内存）：front 为空、back 为空、FSRS 字段格式和范围（stability>0、difficulty∈[0,1]、cardState 文本映射、reps/lapses≥0、due/lastReview 有效 ISO-8601）。
2. **内存去重**（纯内存）：CSV 内部 front 重复（case-insensitive）。
3. **SQL 查重**（一次批量 IN 查询）：与数据库已有卡片 front 冲突（`WHERE LOWER(c.front) IN (:fronts) AND c.user_id = :userId`）。

- 校验进入点：tagId 校验失败则立即返回，不解析 CSV。
- 错误反馈：返回 `{ success: false, errors: [{row: N, front: "xxx", reason: "原因"}] }`，前端展示跳过行清单（行号、front、失败原因）。
- 整体事务：校验全部通过后才进入 `@Transactional` 批量插入——任何一行 DB 层面失败则全部回滚。
- Parser 只做解析不做校验：Apache Commons CSV 按表头名称匹配列（`get("front")`），缺失列留空、多余列忽略。空 front 等逻辑问题由校验层统一处理。
- 文件限制：`spring.servlet.multipart.max-file-size=5MB`。

### 4. batch_operation_log 审计表

一张表记录所有导入/导出操作（operationType=IMPORT/EXPORT）：

| 字段 | 说明 |
|------|------|
| `id` | UUID 主键 |
| `userId` | 操作人 |
| `operationType` | IMPORT / EXPORT |
| `tagId` | 目标 deck tag ID |
| `tagName` | 冗余字段，方便查询展示 |
| `fileName` | CSV 文件名 |
| `totalRows` | CSV 总行数（导出=卡片数，导入=CSV 有效行数） |
| `successCount` | 成功数量（导出留 null） |
| `skipCount` | 跳过/失败数量（导出留 null） |
| `errorDetails` | JSON 文本：`[{row, front, reason}]` — 仅导入失败时有值 |
| `status` | SUCCESS / PARTIAL / FAILED |
| `createTime` | 操作时间（BaseEntity） |

- 每条导入/导出操作写入一条日志。
- 日志通过 H2 console 查询，无前端展示页面。

### 5. 后端架构：CardBatchService + CardCsvParser

- **CardCsvParser**：纯解析，接收 InputStream → 返回 `List<ParsedCardRow>`。按列名称匹配（`CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)`），处理 BOM、引号转义、换行。
- **CardBatchService**：导入/导出业务编排。注入 `CardRepository`、`TagRepository`、`BatchOperationLogRepository`、`CardCsvParser`。
  - `importCards(MultipartFile, tagId, userId) → ImportResult`
  - `exportCards(tagId, userId) → byte[] csvBytes + String fileName`
- 注册为独立 Service（不扩展 FlashcardService），职责清晰分离。
- 导入 FSRS 字段可缺失：缺失列→对应字段 null → Service 层判断 null 则调用 `FsrsScheduler.createInitState()` 填充默认值。

### 6. REST API 端点

**POST /api/cards/import**（multipart/form-data）
```
Request:  file (CSV), tagId (string)
Response: { totalRows: 10, successCount: 7, errors: [{row: 3, front: "apple", reason: "已存在"}, ...] }
```
- 校验失败时 `successCount=0`，`errors` 列出全部问题行。
- 全部成功时 `errors=[]`。

**GET /api/cards/export?tagId=xxx**
```
Response: Content-Type: text/csv; charset=UTF-8
          Content-Disposition: attachment; filename="{tagName}_{yyyyMMdd_HHmmss}.csv"
Body: CSV 文件（UTF-8 无 BOM）
```
- 空 tag（无卡片）：返回仅含表头的 CSV。
- 认证：两个端点均需 JSESSIONID cookie（`/api/**` 不在 permit-all-paths）。

### 7. 工具栏重新设计

工具栏行从 `[搜索框] [Aa↑] [T↓] [+]` 改为 `[搜索框] [▾排序] [📄批量] [+]`：

- **排序下拉按钮**：显示当前排序标签（如 `Aa ↑`）。点击展开纵向菜单（4 个选项）：`Aa ↑`（front 升序）、`Aa ↓`（front 降序）、`T ↑`（createTime 升序）、`T ↓`（createTime 降序）。选中后按钮标签更新、菜单关闭。
- **文件图标按钮**：显示文件图标。点击展开纵向菜单（2 个选项）：`导出`、`导入`。点击对应选项打开 `BatchOperationModal`。
- **`+` 按钮**：最右侧，独立，行为不变。
- 两个下拉菜单均为通用 `DropdownMenu` 组件。

### 8. BatchOperationModal 三阶段状态机

```
[select-tag]  →  [ready]  →  [loading]  →  [result]
```

- **select-tag**：`<select>` 展示所有 deck tag，未选中时操作按钮置灰。
- **ready**：tag 已选中。导入模式显示 `<input type="file" accept=".csv">` + "导入"按钮；导出模式显示"导出"按钮。
- **loading**：显示 spinner + "导入中..."。导出模式跳过此状态（直接触发下载后关闭）。
- **result**（仅导入）：显示"成功 X 条，跳过 Y 条"，以及跳过行表格（行号、front、原因）。"关闭"按钮关闭 modal + 调用 `fetchCards()` 刷新卡片列表。
- 导出下载：`fetch(url)` → `Blob` → 创建隐藏 `<a>` 元素 → `click()` 触发下载。
- 导入/导出共用同一个 modal 组件，通过 `mode` prop 区分。

### 9. 前端下载触发方式

导出使用隐藏 `<a>` 标签 + `click()` 触发浏览器下载（GET 请求），不通过 `window.location` 导航，不干扰页面状态。前端不解析下载响应。

## Testing Decisions

### 测试 seam

- **最高 seam**：`FlashcardController` 的 `/api/cards/import` 和 `/api/cards/export` 端点——HTTP 请求/响应。E2E 测试在此层验证完整链路。
- **中间 seam**：`CardBatchService.importCards()` 和 `exportCards()`——注入 mock Repository 后验证导入/导出业务逻辑和校验流程。
- **最低 seam**：`CardCsvParser.parse()`——纯函数，输入 CSV 字节流，输出 `List<ParsedCardRow>`。通过单元测试穷举边界情况（换行符、引号转义、BOM、缺失列、FSRS 非法值等）。

### 后端子单测

| 测试类 | 覆盖内容 |
|--------|---------|
| `CardCsvParserTest` | 正常 CSV 解析、字段内含逗号/引号转义/换行符、BOM 跳过、cardState 文本映射、缺失列容错、多余列忽略、FSRS 数值解析容错、空 front、空 back 不在此处校验（由 Service 层负责） |
| `CardBatchServiceTest` | importCards：全部成功、全部失败（各种失败原因）、部分失败、tagId 非法、CSV 空文件；exportCards：正常导出、空 tag 导出；batch_operation_log 写入验证（@Mock CardRepository + TagRepository + CardCsvParser + BatchOperationLogRepository） |

### E2E 测试

| 测试类 | 覆盖内容 |
|--------|---------|
| `FlashcardBatchIT`（新建） | 完整往返：创建 deck tag + 2 张卡片 → 导出 CSV → 校验 CSV 文件内容（含 FSRS 字段） → 删除其中 1 张卡片 → 导入刚导出的 CSV → 验证数据库还原（包括 FSRS 状态字段精确还原） |
| `ManagePageIT.manageSort()`（修改） | 将原有对 `sort-btn-name` / `sort-btn-time` 的直接点击 + `data-active` 断言，替换为：打开排序下拉菜单 → 选择选项 → 验证按钮标签变化 + 卡片排序效果 |

### E2E 不改动的测试

`ChatAgentSessionIT`、`ChatAgentResumeIT`、`ChatAgentMemoryIT`、`DailyTalkIT`、`ChatAgentMemoryCueIT`、`FlashcardIT` 均不涉及 manage 页面工具栏，无需改动。

### 测试参考

- 后端子单测参考 `FsrsSchedulerTest` 的 Mock 和断言风格。
- E2E 测试参考 `ManagePageIT` 和 `FlashcardIT` 的 Playwright + WireMock + H2 断言模式。
- 所有 E2E 使用 `data-testid` 选择器，非 CSS 类名或文本内容。

## Out of Scope

- Excel (.xlsx) 格式导入/导出 — 仅支持 CSV。
- 多 tag 导入/导出 — 限定单个 deck tag。
- 导入时覆盖已有卡片 — 仅跳过，不做任何覆盖/合并。
- 导入导出的前端页面操作日志展示 — 仅通过 H2 console 查看 `batch_operation_log` 表。
- 前端 CSV 预览功能 — 导入前不做数据预览。
- 导入进度条 — 仅使用简单 spinner 加载指示器。
- 批量删除卡片。
- Excel 兼容性（BOM 已处理但不做 Excel 专用优化）。
- 卡片导入后的 tag 关联保留 — 导入卡片全部归属选中的 deck tag，不保留原始 tag 信息。

## Further Notes

- Apache Commons CSV 依赖需添加到 `pom.xml`（`org.apache.commons:commons-csv:1.11.0`）。
- `spring.servlet.multipart.max-file-size` 和 `max-request-size` 需在 `application.yml` 和 `application-e2e.yml` 中配置为 5MB。
- `CardRepository` 新增 `@Query` 方法：`SELECT LOWER(c.front) FROM Card c WHERE LOWER(c.front) IN (:fronts) AND c.userId = :userId`（大小写不敏感批量去重）。
- CSV 字段内含换行符时，Apache Commons CSV 默认支持 RFC 4180 带引号多行字段，无需额外配置。
- 文档更新范围：`CONTEXT.md`（术语表）、`AGENTS.md`（Flashcard 模块补充）、`README.md`（使用说明 + 项目结构）、`docs/architecture.md`（决策日志 + 项目结构）、`docs/frontend-notes.md`（实现模式）。
