# 06 — E2E 测试

**Status:** `ready-for-agent`

## Parent

[PRD: 闪卡批量导入/导出](../PRD.md)

## What to build

两件事：(1) 新建 `FlashcardBatchIT` 覆盖完整导出→删卡→导入往返流程；(2) 修改 `ManagePageIT.manageSort()` 适配新的工具栏下拉菜单。

---

**新建 `FlashcardBatchIT`**（`src/test/java/com/hugosol/chatagent/e2e/FlashcardBatchIT.java`）：

测试用例：**导出 → 删卡 → 导入 → FSRS 状态还原验证**

1. 创建 deck tag（通过 UI 或直接 API 调用创建）
2. 通过 API 创建 2 张卡片（含 FSRS 字段：stability=3.0, difficulty=0.3, cardState=2, reps=5 等非默认值），关联该 tag
3. 打开 manage 页面 → 选 Cards tab
4. **导出**：点文件按钮 → 选"导出" → 在 modal 中选 tag → 点"导出" → 验证浏览器下载了 CSV 文件（检查 Playwright 的 download 事件）
5. 通过 API 删除其中 1 张卡片
6. **导入**：点文件按钮 → 选"导入" → 在 modal 中选 tag → 上传步骤 4 下载的 CSV 文件 → 点"导入" → 等待结果展示
7. **验证前端结果**：modal 显示"成功 X 张，跳过 Y 张"
8. **验证 H2 数据还原**：
   - 通过 `CardRepository` 直接查询数据库
   - 断言 2 张卡片都存在（被删的那张恢复了）
   - 断言 FSRS 字段精确还原：stability=3.0, difficulty=0.3, cardState=2, reps=5
9. **验证 batch_operation_log**：
   - 通过 `BatchOperationLogRepository` 查询
   - 断言有 2 条记录（1 条 EXPORT + 1 条 IMPORT），status=SUCCESS

**边界场景**（可作为额外测试或合并到主用例）：
- 导入含重复 front 的 CSV → 验证错误清单展示
- 导出空 tag → 验证 CSV 仅含表头

**E2E 基类**：继承 `E2ETestBase`，复用现有 Playwright + WireMock + H2 基础设施。不依赖 WireMock（闪卡模块不调 LLM，但 E2ETestBase 启动了 WireMock 无需修改）。自动注入 `CardRepository`、`TagRepository`、`BatchOperationLogRepository`。使用 `data-testid` 选择器定位元素。

**测试数据**：在测试方法内通过 `FlashcardController` 的 API 端点（POST /api/cards/add、POST /api/tags）创建前置数据，保证测试自包含。

---

**修改 `ManagePageIT.manageSort()`**（第 237–263 行）：

- 移除对 `[data-testid='sort-btn-name']` 和 `[data-testid='sort-btn-time']` 的直接点击
- 移除 `data-active='true'` 的等待和断言
- 替换为：
  1. 验证排序按钮初始标签为 `Aa ↑`（默认状态）
  2. 点击 `[data-testid='sort-dropdown-btn']` 打开排序菜单
  3. 等待菜单可见（通过菜单项出现判断）
  4. 点击菜单项（如 `[data-testid='sort-option']` 中包含 "Aa ↑" 文本的）
  5. 验证按钮标签已变为 "Aa ↑"
  6. 验证卡片列表按 front 升序排列（取第一张卡片的 `card-front` 文本与预期比较）
  7. 关闭菜单、重新打开、选择 "T ↓"
  8. 验证按钮标签 + 卡片排序变化

## Acceptance criteria

- [ ] `FlashcardBatchIT` 完整通过：导出 CSV → 删卡 → 导入 CSV → H2 数据还原（含 FSRS 字段精确验证）
- [ ] `batch_operation_log` 表验证通过（EXPORT + IMPORT 各一条，status=SUCCESS）
- [ ] `ManagePageIT.manageSort()` 通过：下拉菜单交互 + 排序行为正确
- [ ] 两个 E2E 测试不破坏其他现有 E2E（`mvn verify` 全绿）
- [ ] 所有选择器使用 `data-testid`，不依赖 CSS 类名或硬编码文本
- [ ] 测试使用 `@AfterEach` 自动保存截图到 `target/e2e-screenshots/`（继承自 E2ETestBase）

## Blocked by

- 05-frontend-batch-modal（完整前端功能可操作）
