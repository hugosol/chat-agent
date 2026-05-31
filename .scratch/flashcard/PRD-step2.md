# PRD: 闪卡模块 Step 2 —— Deck 激活 + 卡片管理页面 + 导航统一

**Status:** `ready-for-agent`

## Problem Statement

Step 1 实现了闪卡录入 MVP——Learner 可以在聊天页底部两阶段面板快速创建卡片。但它存在三个痛点：

1. **Tag 组织混乱**：创建卡片时 tag 自动 upsert（输入什么就创建什么），没有 Tag 管理界面。Learner 无法查看、编辑或删除已创建的 Tag。Tag 之间没有层级——"牌组"概念虽已在数据模型中预留（`type` 字段），但从未激活。所有卡片平铺在一起，无法按牌组查看。

2. **没有卡片管理能力**：Learner 只能创建卡片，无法查看已有卡片列表、搜索卡片、编辑或删除。录错的卡片无法修改，多余的卡片无法清理。

3. **页面跳转缺失**：Learner 在聊天页录完卡片后，想进入管理页面整理卡片时没有入口——目前只有 `/index.html`（聊天页）和 `/login/main.html`（登录页）。两个页面之间没有统一的导航方式，header 布局也各不相同。

Learner 需要一个独立的卡片管理后台来组织和管理自己的闪卡库——包括 Tag/Deck 的 CRUD、卡片列表的浏览/搜索/编辑/删除、以及统一的页面导航系统。

## Solution

在三方面全面升级闪卡模块：

**① Deck 概念激活**：Tag 的 `type` 字段从"预留"变为活跃——`type="deck"` 的 Tag 就是 Deck（牌组）。每张卡片在创建时必须至少关联一个 Deck type 的 Tag。卡片创建参数从 tag 名称改为 tag ID（先在管理页创建 Tag，再在录入时使用）。

**② 独立管理页面 `/manage/`**：新建一个完整的卡片管理后台，含两个 Tab：
- **Cards Tab**（默认）：卡片 block 列表 + 搜索框 + A↔Z/时间排序 + Deck chip 单选筛选 + 服务端分页。点击卡片查看明细（Modal）、编辑（Modal）、删除（Modal 确认）。点击"新建卡片"打开创建 Modal。
- **Tags Tab**：Tag 表格列表，展示 name + Deck checkbox + Edit/Delete。Inline 编辑 name/type。创建 Tag 走 Modal。删除 Deck 时后端检查孤儿卡（orphan card——删除后会导致某卡片失去所有 Deck 归属），如有则阻止并提示。

**③ 统一页面导航**：提取 `shared/base.css` 和 `shared/nav.js`，所有认证页面共享统一的 header（Logout + 条件 Token 条 + ☰ 菜单按钮）和右侧 overlay 导航侧边栏（Chat / Cards 两个链接）。Corrections 按钮从 header 移入 Correction 侧边栏内部。

## User Stories

1. 作为 Learner，我希望有一个独立的卡片管理后台页面，可以离开聊天环境专心整理我的闪卡库，不依赖 WebSocket 连接。
2. 作为 Learner，我可以在管理页看到我所有的卡片，每条卡片以卡片 block 形式展示：正面（front）为主标题、背面（back）截断为副标题、所属标签以 chip 展示、以及 Edit/Delete 操作按钮。
3. 作为 Learner，点击一张卡片后，可以弹出 Modal 查看完整信息：front、back、全部关联 tags、牌组（Deck）、卡片状态（New/Learning/Review/Relearning）、下次复习时间（due）、创建时间。
4. 作为 Learner，我可以在管理页创建新卡片——弹出一个表单 Modal，填入 front、back，从已有 Tag 下拉选择标签（而不是自由输入），保存时后端自动初始化 FSRS-6 调度状态。
5. 作为 Learner，我可以编辑已有卡片——修改 front、back 或重新选择关联的标签，保存时系统检查是否有至少一个 Deck 牌组标签。
6. 作为 Learner，我可以删除不需要的卡片，删除前弹出确认 Modal 防止误操作。
7. 作为 Learner，我可以通过顶部搜索框按正面单词（front）模糊搜索卡片，快速定位目标。
8. 作为 Learner，我可以按牌组筛选卡片——已创建的 Deck 以 chip 形式列在搜索框下方，点击某个 chip 即只显示该牌组下的卡片，再次点击取消筛选显示全部。
9. 作为 Learner，我可以切换卡片排序方式——按名称 A→Z / Z→A，或按创建时间正序/倒序，两种排序互相独立。
10. 作为 Learner，当卡片数量较多时，列表底部显示分页控件（上一页/页码/下一页），点击切换页面。任何筛选条件变化自动回到第一页。
11. 作为 Learner，管理页有标签管理 Tab，展示我所有的 Tag 列表，每条显示标签名称和"是否为 Deck"状态。
12. 作为 Learner，我可以创建新 Tag——Modal 弹窗填入名称和可选勾选"作为 Deck"，保存时系统检查名称唯一性（大小写不敏感）。
13. 作为 Learner，我可以直接 Inline 编辑 Tag 的名称或切换 Deck 状态，无需弹出 Modal。
14. 作为 Learner，我可以删除不再需要的 Tag。但如果删除会导致任何卡片失去所有 Deck 归属（成为孤儿卡），系统拒绝删除并提示影响的卡片数量。
15. 作为 Learner，我在聊天页底部录入面板创建卡片时，标签只能从下拉列表中选择已存在的 Tag（不能自由输入随意创建），确保标签体系干净。
16. 作为 Learner，我在聊天页录入面板创建卡片时，必须至少选择一个 Deck 类型标签，否则保存失败并提示"至少需要一个牌组标签"。
17. 作为 Learner，我在所有认证后的页面（聊天页、管理页）都能看到统一的 header——左侧 Logout 按钮、中间聊天页专属的 LLM Token 用量条、右侧 ☰ 菜单按钮。
18. 作为 Learner，点击 ☰ 菜单按钮后，右侧滑出一个导航侧边栏，包含两个链接：Chat（跳回聊天页）和 Cards（跳转管理页）。当前所在页面高亮显示。
19. 作为 Learner，在聊天页打开导航侧边栏时，如果 Correction 纠错侧边栏恰好打开，导航侧边栏会自动关闭它——两个侧边栏不会同时出现侵占屏幕空间。
20. 作为 Learner，管理页没有卡片或没有标签时，页面显示空状态引导——"暂无卡片，创建第一张卡片"或"暂无标签，创建标签"或"暂无牌组，创建牌组"按钮。

## Implementation Decisions

### 1. Deck 概念正式激活

- Tag 的 `type` 字段从 nullable 预留字段变为活跃字段。`type=null` 表示普通标签，`type="deck"` 表示牌组。
- 同一用户下 Tag name 唯一（大小写不敏感），`TagRepository` 新增 `findByNameIgnoreCaseAndUserId` 查询方法。
- "每张卡片必须至少有一个 Deck type 的 Tag"——此约束在 `FlashcardService` 中校验：创建/编辑卡片时遍历传入的 tagIds，查出的 Tag 中必须至少一个 `getType().equals("deck")`，否则返回 422。
- 管理页编辑时如果修改 tags 集合导致失去所有 Deck 归属，同样返回 422。

### 2. 卡片创建改为传入 tagIds

- 从 Step 1 的 tag 名称列表改为 tag ID 列表。
- `AddCardRequest` 字段从 `List<String> tags` 改为 `List<String> tagIds`。
- `FlashcardService.createCard()` 按 ID 查 Tag，不存在则直接 422（不再自动创建 tag）。
- Tag 必须由 Learner **先在管理页创建**，然后才能在录入面板或管理页创建卡片时选择。
- 录入面板 (`flashcard.js`) 的自动完成输入框改为纯选择题——只能从已有 Tag 下拉列表中选取，不能自由输入不存在的名称。

### 3. 服务端分页实现

- `CardRepository` 扩展 `JpaSpecificationExecutor<Card>`，支持动态 Specification 组合。
- `FlashcardService` 新增 `listCards(userId, search, deckId, pageable)` 方法，内部构建 Specification：
  - `userId = :userId`（必须，数据隔离）
  - `LOWER(front) LIKE %:search%`（可选，文本搜索）
  - `JOIN tags WHERE tag.id = :deckId`（可选，Deck 筛选）
- Deck 筛选为**单选**：前端 chip 单选（点一个选中，再点另一个切换，再点同一个取消），后端收单个 `deckId` 参数。
- 分页参数 `page`、`size`、`sort` 由 Spring Data 从 Controller 自动绑定 `Pageable`。
- 返回体包含完整 `content` 数组 + `page`、`totalPages`、`totalElements`、`size`。
- 列表 API 一次返回卡片全部字段（id / front / back / tags / due / cardState / createTime），不需要额外的详情端点。FSRS 调度参数（stability / difficulty / reps / lapses / lastReview）不暴露在前端——管理页不是复习页。

### 4. 管理页布局与交互

- **布局**：独立页面 `/manage/`，访问路径 `/manage/index.html`。
- **Header**：由 `shared/nav.js` 统一注入，`data-show-token="false"` 隐藏 Token 条。
- **右侧纵向 Tab 栏**：40-50px 宽，竖排 "Cards"（默认选中）/ "Tags" 两个按钮。当前选中按钮高亮。Tab 切换用 `manage/index.html` 内联脚本控制：销毁旧模块的 DOM 事件监听，初始化新模块。
- **Cards Tab 内容**：
  - 搜索框 + 两个互斥排序按钮（名称 A→Z 默认 / 时间）在一行
  - Deck chip 单选筛选在第二行（调用 `GET /api/tags?type=deck` 获取 Deck 列表，空状态提示"暂无牌组，创建牌组"）
  - 卡片 block 列表：每条一个暗色 block，主标题 front + 副标题 back（截断 50 字符）+ tag chips + Edit/Delete 按钮
  - 点击卡片 → Modal 展示完整 front/back/tags/due/cardState/createTime（cardState 映射为可读标签：0=New, 1=Learning, 2=Review, 3=Relearning）
  - 底部分页：上一页 / 页码按钮 / 下一页
  - 任何筛选条件变化 → 重置 page=0，重新 fetch
  - 空状态："暂无卡片" + "创建第一张卡片" 按钮
- **Tags Tab 内容**：
  - 表格列表：name 列 + Deck checkbox 列 + Edit/Delete 操作列
  - Inline 编辑：点 Edit → 该行变为可编辑态（name input + checkbox + Save/Cancel 按钮）
  - 创建 Tag：Modal（name + Deck checkbox），后端检查 name 唯一性
  - 删除 Tag：后端先 count 查询——如果删除该 Tag 会导致任何卡片失去所有 Deck 归属（orphan card），返回 422 + `{"orphanCount": N}`，前端 alert 提示 "N 张卡片将失去所有牌组，无法删除"
  - 空状态："暂无标签" + "创建标签" 按钮
- **CardState 映射**：只读显示，前端枚举映射：0→New, 1→Learning, 2→Review, 3→Relearning
- **所有错误提示**：统一使用 `alert()`，和现有 `flashcard.js` 风格一致。

### 5. 导航侧边栏与 Header 统一

- **`shared/nav.js`**：独立的 JS 模块，所有认证页面引入。负责：
  1. 根据 `<header data-show-token="true/false">` 占位元素注入 header DOM：左侧 Logout 按钮 + 中间 Token 条（条件渲染）+ 右侧 ☰ 按钮
  2. 创建右侧 overlay 导航侧边栏（200px 宽，暗色主题，与 Correction 侧边栏视觉一致）
  3. ☰ 点击 → 展开/收起侧边栏
  4. 侧边栏内容：Chat（跳转 `/`）和 Cards（跳转 `/manage/`），通过 `window.location.pathname` 判断当前页并高亮
  5. `×` 按钮关闭侧边栏
  6. 聊天页打开 nav 侧边栏时自动关闭 Correction 侧边栏（`document.getElementById('correctionSidebar')?.classList.add('collapsed')` 优雅降级）
  7. 登录页不引入 `nav.js`
- **`shared/base.css`**：从 `style.css` 提取通用样式——`*` reset、`body`、`#app`、`header`（含 logout + token 条）、`.modal`、`.btn`、暗色表单控件、`.chip`、toast 动画。管理页引入 `base.css` + `manage.css`；聊天页引入 `base.css` + `style.css`。
- **Corrections 按钮**：从 header 移到 Correction 侧边栏内部 header——`app.js` 的 `updateCorrectionBadge()` 改为更新侧边栏内部的 badge 元素。

### 6. 卡片编辑去重

- 编辑卡片时 front 重复检查排除自身：`PUT /api/cards/{id}` 在检查 front 是否已存在时，排除 `id` 等于当前卡片的记录。
- 允许 Learner 修改 front 的字母大小写（如 "yesterday" → "Yesterday"），不算重复。

### 7. Tag 删除孤儿卡检查

- 删除 Tag 时后端执行 COUNT 查询：统计关联了该 Tag 的所有卡片中，有多少卡片在移除该 Tag 后将没有任何 Deck type 的 Tag。
- 若 orphanCount > 0，返回 422 + `{"orphanCount": N}`，前端 alert 提示数量并终止。
- 若 orphanCount = 0，正常删除 Tag 及 `card_tags` 中间表关联记录，卡片本身不受影响。

### 8. 现有数据迁移策略

- 不做数据迁移。Step 1 创建的卡片和标签数据直接清空：`DELETE FROM card_tags; DELETE FROM cards; DELETE FROM tags;`
- 新约束（强制 Deck、tagIds 传参）下的数据从空白状态开始。

### 9. 全量 API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST /api/cards/add` | 🔧 改用 `tagIds` 传参 + Deck 校验 |
| `GET /api/cards` | ✨ 分页列表（search + deckId + sort + pageable） |
| `PUT /api/cards/{id}` | ✨ 编辑 front/back/tagIds，排除自身去重 |
| `DELETE /api/cards/{id}` | ✨ 删除卡片 |
| `GET /api/tags` | 🔧 TagResponse 加 `id` 字段；支持 `?type=deck` 过滤器 |
| `POST /api/tags` | ✨ 创建 Tag（name 大小写不敏感唯一性检查） |
| `PUT /api/tags/{id}` | ✨ 编辑 name + type |
| `DELETE /api/tags/{id}` | ✨ 删除前 orphan 检查 → 422 |

### 10. 前端文件结构变更

```
static/
├── shared/                          (全新目录)
│   ├── base.css                     (从 style.css 提取)
│   └── nav.js                       (header + ☰ 导航侧边栏)
├── manage/                          (全新目录)
│   ├── index.html
│   ├── manage.css
│   ├── modal.js                     (通用 modal 模块)
│   ├── card.js                      (卡片列表 CRUD)
│   └── tag.js                       (Tag 列表 CRUD)
├── index.html                       (修改：header 改用占位 + nav.js)
├── style.css                        (修改：提取通用样式到 base.css)
├── app.js                           (修改：correction badge 移至侧边栏)
├── flashcard.js                     (修改：tag 名→ID，纯选择器)
└── login/
    └── main.html                    (不变，不引入 nav.js)
```

### 11. 模块职责划分

- **`shared/nav.js`**：header 注入 + 导航侧边栏 DOM + ☰ 交互 + 当前页高亮 + Correction 侧边栏互斥。导出全局 `window.openNav()` / `window.closeNav()` 供 header 内联调用。
- **`manage/modal.js`**：通用 Modal 打开/关闭/表单模板收集/提交。`window.manageModal` 供 card 和 tag 模块调用。
- **`manage/card.js`**：卡片列表（分页+搜索+排序+Deck筛选） + 创建 Modal 触发 + 编辑 Modal 触发 + 删除确认 + 明细 Modal。`window.manageCards.init()` / `.destroy()` 由 Tab 切换管理器控制。
- **`manage/tag.js`**：Tag 列表 + inline 编辑 + 创建 Modal 触发 + 删除确认（孤儿检查错误处理）。`window.manageTags.init()` / `.destroy()` 由 Tab 切换管理器控制。
- **Tab 切换**：内联在 `manage/index.html` 的 `<script>` 块中（~15 行），控制显示/隐藏内容区 + 高亮 tab 按钮 + 调 `init()`/`destroy()`。

## Testing Decisions

### 测试原则

- 只测试外部行为（给定输入 → 预期输出或 DOM 状态），不测试实现细节。
- 单元测试覆盖所有 API 端点的异常阻断路径（422 等）。
- E2E 测试以 Learner 视角验证管理页完整流程。

### 单元测试：FlashcardServiceTest + FlashcardControllerTest

覆盖所有 API 异常路径：

| 端点 | 需覆盖的测试场景 |
|------|-----------------|
| `POST /api/cards/add` | tagIds 为空、tagId 不存在、缺少 Deck tag、front 重复（大小写不敏感）、tag 不属于当前用户 |
| `PUT /api/cards/{id}` | 卡片不存在、userId 不匹配、front 重复（排除自身）、缺少 Deck tag、tagId 不存在 |
| `DELETE /api/cards/{id}` | 卡片不存在、userId 不匹配 |
| `POST /api/tags` | name 重复（大小写不敏感）、name 为空 |
| `PUT /api/tags/{id}` | tag 不存在、name 重复、userId 不匹配 |
| `DELETE /api/tags/{id}` | tag 不存在、孤儿卡阻止（返回 422 + orphanCount） |
| `GET /api/cards` | 正常分页、搜索含特殊字符、空结果、无效排序字段、Deck 筛选、搜索+分页联动 |

参照现有 `FlashcardServiceTest`（Mockito）和 `FlashcardControllerTest`（`@WebMvcTest`）的测试风格。

### E2E 测试：ManagePageIT

参照现有 `FlashcardIT` 模式：`extends E2ETestBase`，Playwright 操作 DOM，验证 H2 数据。

- 不需要 WireMock（管理页和闪卡 API 都不调 LLM）。
- 使用 e2e profile（`permit-all-paths: [/**]`），userId fallback "anonymous"。
- 测试流程覆盖：
  1. ☰ 导航侧边栏——在聊天页打开、点 Chat 留在原地、点 Cards 跳转管理页
  2. Tag 创建——切换到 Tags tab → 创建 Deck tag（"Daily English" + 勾选 Deck）+ 普通 tag（"verb"）
  3. 卡片创建——切换到 Cards tab → 创建卡片（front: "yesterday", back: "昨天", tags: Deck+普通）
  4. 卡片编辑——编辑刚创建的卡片，修改 front 为 "Yesterday"，再次验证大小写不敏感重复检查
  5. 搜索——搜索 "yesterday" 匹配卡片
  6. 排序——切换 A→Z / Z→A 和按时间排序
  7. Deck 筛选——点 Deck chip 筛选、取消筛选
  8. 分页——验证页码显示（单张卡片时只有 1 页）
  9. 卡片明细——点击卡片弹出 Modal，验证展示字段
  10. 卡片删除——删除卡片，Modal 确认，验证 H2 中已删除
  11. Tag inline 编辑——编辑 tag 名称
  12. Tag 删除（孤儿拦截）——尝试删除 Deck tag，验证 422 阻止和 alert 提示
  13. Tag 删除（成功）——先将关联卡片删除，再删除 tag，验证成功
  14. ☰ 菜单在管理页——点击 Chat 跳回聊天页

## Out of Scope

1. 闪卡复习功能——每日复习队列、评分按钮（Again/Hard/Good/Easy）、复习进度统计。（仍属于 V2）
2. FSRS 参数优化（Optimizer）——使用默认参数，不对 Learner 的复习日志进行参数学习。
3. 从 Practice session 的 Correction 自动生成闪卡——纯手动录入，不关联会话数据。
4. 富文本/Markdown 卡片内容——卡片内容为纯文本。
5. 卡片跨设备同步——数据仅存本地 H2。
6. 批量操作（多选删除、批量编辑）——单条操作足矣。
7. Tag 的第三种 type 值——目前只有 `null`（普通标签）和 `"deck"`（牌组）两种，不考虑更多 type。
8. 卡片导出/导入——纯 H2 存储，无外部交换格式。

## Further Notes

- 历史数据直接清空：`DELETE FROM card_tags; DELETE FROM cards; DELETE FROM tags;`——新约束下的数据从零开始。
- 管理页不会调用 LLM，纯粹是 CRUD 操作，E2E 测试不需要 WireMock stubs。
- 导航侧边栏与 Correction 侧边栏的互斥逻辑放在 `shared/nav.js` 中，而非 `app.js`，这样导航侧边栏成为唯一的互斥协调者。
- CardState 在管理页仅作为只读标签显示，不暴露 FSRS 调度参数——管理页是组织工具，不是复习工具。
- 本次实施后，闪卡模块将拥有完整的 CRUD 闭环：创建（录入面板 + 管理页）、浏览（管理页列表）、编辑（管理页）、删除（管理页）。
