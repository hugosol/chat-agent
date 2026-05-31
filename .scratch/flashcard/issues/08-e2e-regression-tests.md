# 08 — E2E Regression Tests (ManagePageIT)

**Status:** `ready-for-agent`

## Parent

[PRD: 闪卡模块 Step 2 —— Deck 激活 + 卡片管理页面 + 导航统一](../PRD-step2.md)

## What to build

新增 `ManagePageIT` E2E 测试类，覆盖本次 PRD 的 20 个用户故事的端到端验收。参照现有 `FlashcardIT` 模式：继承 `E2ETestBase`，使用 Playwright 操作 DOM，验证 H2 数据。使用 e2e profile，不需要 WireMock（管理页和闪卡 API 都不调 LLM）。

### 测试类结构

- 文件路径：`src/test/java/com/hugosol/chatagent/e2e/ManagePageIT.java`
- 继承 `E2ETestBase`（获得 Playwright `page`、`@Autowired` repos、screenshot 自动保存）
- 使用 `@ActiveProfiles("e2e")`（通过 `E2ETestBase` 已声明）
- userId fallback `"anonymous"`（e2e profile 下 `permit-all-paths: [/**]`）
- 不需要 `@BeforeAll` 启动 WireMock（不调 LLM）
- 每个 `@Test` 方法可独立运行，数据通过 `@Transactional` 自动回滚（或使用 `@BeforeEach` 清理）

### 测试流程覆盖

测试可以组织为 1-2 个 `@Test` 方法（一个流程串起多个步骤）或多个独立 `@Test` 方法。优先参照 `FlashcardIT` 的 style——一个大流程 test，因为步骤间有状态依赖。

#### 步骤 1：☰ 导航侧边栏 — 在聊天页验证

1. 打开聊天页 `page.navigate("/")`
2. 等待页面加载完成（`page.waitForSelector("header")`）
3. 点击 ☰ 菜单按钮 → 验证导航侧边栏出现（`page.waitForSelector(".nav-sidebar")` 或等效选择器）
4. 验证导航栏包含 Chat 和 Cards 两个链接
5. Chat 链接高亮（当前页为 `/`）
6. 点击 Chat → 验证仍在聊天页（URL 不变）
7. 点击 Cards → 验证跳转到 `/manage/`
8. 回到步骤便于截图验证

#### 步骤 2：Tag 创建 — 管理页 Tags Tab

1. 跳转到 `/manage/`
2. 等待管理页加载（header 出现 + Tab 栏可见）
3. 切换到 Tags Tab（点击 "Tags" 按钮）→ 等待 Tags 内容区渲染
4. 验证空状态 "暂无标签" + "创建标签" 按钮
5. 点击 "创建标签" → modal 出现
6. 填入 name = "Daily English"，勾选 Deck checkbox
7. 点击保存 → modal 关闭，表格出现新行（name="Daily English", Deck=checked）
8. 再次创建普通 tag：name = "verb"，不勾选 Deck → 验证新行出现（Deck=unchecked）
9. 验证 H2：`TagRepository` 中存在两条记录，type 分别为 "deck" 和 null

#### 步骤 3：卡片创建 — 管理页 Cards Tab

1. 切换到 Cards Tab
2. 验证空状态 "暂无卡片" + "创建第一张卡片"
3. 点击 "创建第一张卡片" → modal 出现
4. 填入 front="yesterday", back="昨天"
5. 在 tag 多选下拉中选择 "Daily English" 和 "verb"（验证下拉列表中有这两条 tag）
6. 保存 → modal 关闭，卡片列表出现新卡片 block
7. 验证卡片 block 显示：front="yesterday", back="昨天"（截断 50 内完整显示）
8. 验证 tag chips 显示 "Daily English" 和 "verb"
9. 验证 H2：`CardRepository` 中存在一条卡片，关联两个 Tag

#### 步骤 4：卡片编辑 — 大小写不敏感去重

1. 点击卡片的 Edit 按钮 → modal 出现
2. 验证 modal 预填 front="yesterday", back="昨天", tags 预选 "Daily English" 和 "verb"
3. 修改 front 为 "Yesterday"（大写 Y）
4. 保存 → 成功（与自身 front 仅大小写不同，不触发重复检查）
5. 验证前端列表刷新显示 "Yesterday"

#### 步骤 5：搜索

1. 在搜索框输入 "yesterday"（小写）
2. 等待 300ms 防抖后自动请求 → 验证列表仍显示 "Yesterday" 卡片（大小写不敏感匹配）
3. 清空搜索框 → 验证列表恢复

#### 步骤 6：排序

1. 点击 A→Z 排序 → 验证 url query 中 `sort=front,asc`（或通过 API 响应验证排序结果）
2. 再次点击 A→Z → 验证 `sort=front,desc`
3. 点击时间排序 → 验证 `sort=createTime,desc`
4. 再次点击时间排序 → 验证 `sort=createTime,asc`

#### 步骤 7：Deck 筛选

1. 验证 Deck chip 行显示 "Daily English" chip（因为存在 Deck tag）
2. 点击 "Daily English" chip → chip 高亮，验证列表只显示该 Deck 下的卡片
3. 再次点击同一 chip → 取消高亮，验证列表恢复全部

#### 步骤 8：分页

1. 单张卡片时验证分页不显示（或只有 1 页）
2. 如果能快速创建多张卡片（如 25 张），验证 page 2 出现
3. （可选，非必须：Pagination 至少有基本 DOM 结构）

#### 步骤 9：卡片详情 Modal

1. 点击卡片 block 主体 → detail modal 出现
2. 验证 modal 展示字段：front="Yesterday", back="昨天", tags（有两个 chip）, cardState="New"（0→New）, due（ISO 日期格式）, createTime
3. 关闭 modal

#### 步骤 10：卡片删除

1. 点击 Delete 按钮 → 确认 dialog 出现（`page.on("dialog", ...)` 监听）
2. 确认 → 卡片从列表消失
3. 验证 H2：卡片记录已删除（同时 `card_tags` 中间表记录也已删除）

#### 步骤 11：Tag inline 编辑

1. 切换到 Tags Tab
2. 点击 "verb" 行的 Edit 按钮 → 该行变为编辑态（input + checkbox + Save/Cancel）
3. 修改 name 为 "verbs"
4. 点击 Save → 退出编辑态，表格显示 "verbs"
5. 验证 H2：Tag name 已更新

#### 步骤 12：Tag 删除 — 孤儿卡拦截

1. 尝试删除 "Daily English" Deck tag
2. 确认 dialog → 验证后端返回 422 orphanCount > 0
3. 验证前端 alert 提示 "N 张卡片将失去所有牌组，无法删除"
4. 验证 H2：Tag 未删除（虽然前面卡片已被删除——如果在步骤 10 已经删了卡片，则应在删除卡片之前先测试孤儿拦截，或额外创建一张卡片来触发）

注意：调整步骤顺序——孤儿拦截测试应在卡片删除之前执行（此时卡片仍关联着 Deck tag）。在步骤 10（卡片删除）之前先做步骤 12。

#### 步骤 13：Tag 删除 — 成功

1. 在步骤 10 卡片已删除后
2. 切换到 Tags Tab
3. 删除 "verbs" 普通 tag → 确认 → 删除成功
4. 再次删除 "Daily English" Deck tag → 因为没有关联卡片了，应成功删除
5. 验证 H2：两条 Tag 都已删除
6. 验证空状态重新出现

#### 步骤 14：☰ 菜单在管理页

1. 在管理页点 ☰ → 导航侧边栏出现
2. 验证 Chat 链接可用，Cards 链接高亮（当前页为 `/manage/`）
3. 点击 Chat → 跳回聊天页

### 其他验证点

| 场景 | 验证方式 |
|------|---------|
| 录入面板 tagIds 迁移后 `FlashcardIT` 仍然通过 | 运行现有 `FlashcardIT`，验证通过（或同步更新现有测试） |
| 录入面板 Deck 强制 | 在聊天页面板创建卡片时只选普通 tag → alert；选 Deck → 成功（此场景可在现有 `FlashcardIT` 中补充） |
| 所有错误提示走 `alert()` | 验证 `page.on("dialog")` 捕获 alert 消息文本 |

### 注意事项

- 参照 `FlashcardIT` 的 waiter 模式（`page.waitForFunction()`、`page.waitForSelector()`）
- DOM 选择器避免过度具体的 CSS class，使用语义化选择器（如 `[data-testid]` 或 button text 匹配）
- Screenshot 自动保存（`E2ETestBase` 的 `@AfterEach` 中已实现）
- 测试数据隔离：每个 `@Test` 独立，`@Transactional` 自动回滚
- H2 数据验证使用 `@Autowired` 的 `CardRepository` 和 `TagRepository`

## Acceptance criteria

- [ ] `ManagePageIT` 文件存在，extends `E2ETestBase`
- [ ] 测试覆盖上述 14 个步骤的全部验收点
- [ ] `mvn verify` 中 `ManagePageIT` 全部通过
- [ ] 测试失败时自动截图保存到 `target/e2e-screenshots/`
- [ ] 现有 E2E 测试（`ChatAgentSessionIT`、`FlashcardIT` 等）不受影响，全部通过
- [ ] 测试不使用 WireMock（不调用 LLM）

## Blocked by

- [#04 — Shared Navigation Foundation](./04-shared-navigation.md)
- [#05 — Deck Activation + Tag API + Flashcard Panel Rework](./05-deck-activation-tag-api.md)
- [#06 — Manage Page Shell + Tag CRUD](./06-manage-page-tag-crud.md)
- [#07 — Card List & Management](./07-card-list-management.md)
