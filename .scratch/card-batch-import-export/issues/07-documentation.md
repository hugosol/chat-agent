# 07 — 文档更新

**Status:** `ready-for-agent`

## Parent

[PRD: 闪卡批量导入/导出](../PRD.md)

## What to build

更新 5 份项目文档，反映批量导入/导出功能的实现和新增术语。

---

**1. `CONTEXT.md` — 术语表追加**

在"Flashcard Module"节追加三个新术语：
- **BatchOperationLog**（批量操作日志）：记录每次导入/导出操作的审计信息的数据表，包含操作类型、目标牌组、文件名、成功/跳过计数、失败详情（JSON 格式）、操作时间。通过 H2 console 查询，无前端展示页面。
- **CSV Import/Export**（CSV 批量导入导出）：按 deck tag 单体批量导入/导出闪卡的功能。CSV 格式含 front、back 及完整 FSRS 状态字段（stability/difficulty/cardState/due/reps/lapses/lastReview），cardState 使用文本映射（New/Learning/Review/Relearning）保证跨系统可读性。导入限定单个 deck tag，校验全部通过后事务性插入，失败返回详细错误清单。
- **BatchOperationModal**（批量操作模态框）：管理页面导入/导出的共享模态框组件，含三阶段状态机：select-tag（选择 deck tag）→ ready（选文件或确认导出）→ loading（上传中）→ result（展示成功/跳过结果）。

---

**2. `AGENTS.md` — Flashcard 模块 + 项目结构补充**

Flashcard Module 节追加：
- **批量导入导出**：`CardBatchService`（批量导入/导出 Service，含校验编排和事务管理）、`CardCsvParser`（CSV 解析器，按名称匹配列，BOM 兼容，cardState 文本映射）、`BatchOperationLog`（操作日志实体 + Repository）。`POST /api/cards/import`（multipart/form-data）和 `GET /api/cards/export?tagId=` 两个新端点。Apache Commons CSV 依赖。`spring.servlet.multipart.max-file-size=5MB` 配置。

项目结构图追加新文件路径：
- `model/BatchOperationLog.java`
- `model/BatchOperationType.java` / `model/BatchOperationStatus.java`
- `repository/BatchOperationLogRepository.java`
- `dto/ImportResult.java` / `dto/ImportError.java`
- `service/card/CardCsvParser.java`
- `service/card/CardBatchService.java`
- 前端的 `BatchOperationModal.tsx`、`DropdownMenu.tsx` 及对应 CSS Modules

---

**3. `README.md` — 使用说明 + 项目结构 + Roadmap**

"How to Use"表格追加一行：
| Step | Action |
|------|--------|
| — | 在 Manage 页面的 Cards tab，点击工具栏 📄 按钮 → 选择"导出"下载某个牌组的 CSV 备份，或选择"导入"上传 CSV 批量录入卡片（含 FSRS 复习进度） |

"Project Structure"追加新文件（与 AGENTS.md 一致的新增后端和前端文件）。

"V2 Roadmap"中 `闪卡批量导入导出（CSV）` 标记 `[x]`（本次实现后即可勾选）。

---

**4. `docs/architecture.md` — 决策日志 + 项目结构**

决策日志追加新条目（ID 约 47）：
> **47. CSV 批量导入导出设计决策**
> - 选择 Apache Commons CSV（RFC 4180 兼容、流式解析、轻量依赖）作为 CSV 解析库
> - 限定单 deck tag 导入/导出（简化数据模型，CSV 不含 tags 列避免多对多序列化复杂度）
> - 导入采用"前置全量校验 + 整体事务"策略：tagId 校验 → 逐行校验 → 内存去重 → SQL 查重全部在内存完成，全部通过后才 @Transactional 批量插入
> - cardState 使用文本映射（New/Learning/Review/Relearning）保证 CSV 跨系统可读性
> - parser 按名称匹配列（非列序号），缺失列自动留空、多余列忽略——兼容各种编辑器生成或手动调整的 CSV
> - 新增 BatchOperationLog 表记录所有批量操作历史（审计追溯）
> - 前端工具栏重构：排序和批量操作合并为两个 DropdownMenu 按钮，BatchOperationModal 共享导入/导出两种模式

项目结构图追加新增文件路径。

---

**5. `docs/frontend-notes.md` — 实现模式**

在"Component Architecture"节后追加三个新实现模式：

**DropdownMenu 通用下拉菜单按钮模式**：
- Props 约定：`label`（按钮当前显示文字）、`items`（选项列表含 label/value/onClick）、`selectedValue`（当前选中值，选中项高亮）
- 交互：点击按钮展开纵向绝对定位菜单 → 点击菜单项触发回调+关闭菜单 → 点击外部关闭
- 使用场景：排序选择器（4 选项）、批量操作选择器（导出/导入 2 选项）
- 移动端注意：菜单 position 需考虑视口边界，避免溢出

**BatchOperationModal 三阶段状态机模式**：
- 状态：select-tag → ready → loading → result
- 导入模式走全流程（4 个状态），导出模式走 select-tag → ready → 直接触发下载（跳过 loading/result）
- 选 tag 使用 `<select>` 下拉框（`/api/tags?type=deck` 数据源），未选中时操作按钮置灰
- 导入 result 展示成功数 + 跳过行清单表格（行号、front、失败原因）

**文件上传/下载模式**（代码库首次）：
- **上传**：`<input type="file" accept=".csv">` → `new FormData()` → `fetch(url, { method: "POST", body: formData, credentials: "same-origin" })` → 解析 JSON 响应展示结果
- **下载**：`fetch(url, { credentials: "same-origin" })` → `response.blob()` → `URL.createObjectURL(blob)` → 创建隐藏 `<a>` 元素 → `a.click()` → `URL.revokeObjectURL()`
- CSRF 已对 `/api/**` 禁用，仅需 JSESSIONID cookie（credentials: "same-origin"）

## Acceptance criteria

- [ ] `CONTEXT.md` 追加三个术语定义（BatchOperationLog、CSV Import/Export、BatchOperationModal），格式与现有术语一致
- [ ] `AGENTS.md` Flashcard Module 节追加批量导入导出说明 + 项目结构图追加所有新文件路径
- [ ] `README.md` "How to Use"表追加导入/导出操作行、"Project Structure"追加新文件、"V2 Roadmap"勾选已完成项
- [ ] `docs/architecture.md` 决策日志（ID 47）完整描述所有设计决策 + 项目结构图追加新文件
- [ ] `docs/frontend-notes.md` 追加三个实现模式：DropdownMenu、BatchOperationModal 状态机、文件上传/下载
- [ ] 所有文档不包含待定信息（TODO、待实现）——反映实际完成状态

## Blocked by

- 06-e2e-tests（文档应反映最终实现状态，在功能完成并通过 E2E 后更新）
