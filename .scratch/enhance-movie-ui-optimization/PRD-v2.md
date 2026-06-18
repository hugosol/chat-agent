# PRD v2: Enhance 与 Movie 优化（第二轮）

**Status:** `ready-for-agent`

## Problem Statement

第一轮优化完成后，Learner 在使用 Card Enhancement 和 Movie 管理时仍存在以下问题：

1. **字幕搜索存在子串误匹配 Bug**：搜索 `test` 会匹配到 `greatest`、`testing` 等包含 "test" 子串的台词行。原因是 `wordsLower` 字段使用 `LIKE '%word%'` 子串匹配，无法区分单词边界。

2. **增强台词过度集中于单一电影**：当某个闪卡单词在多部电影中都出现时，匹配结果按 `imdbId, lineIndex` 排序后取第一条——如果电影 A 字幕行数多，几乎所有单词的增强结果都来自电影 A，Learner 看不到其他电影中的用法。

3. **电影列表行内按钮占空间过大**：当前每行使用中文文字按钮（"下载字幕"/"重试"/"删除"），导致电影名称列被挤压，长电影名被 `text-overflow: ellipsis` 截断不可读。

4. **电影工具栏与卡片工具栏风格不一致**：排序使用原生 `<select>` 下拉框，添加和批量导入使用文字按钮。而卡片工具栏已使用 `DropdownMenu` 组件和图标按钮，两种风格并存造成体验割裂。

## Solution

四项独立优化，全部限定在现有模块边界内：

| # | 改动 | 模块 |
|---|------|------|
| 1 | `SrtParser` 构建 `wordsLower` 时首尾加空格，SQL 改用 `LIKE '% word %'` 精确单词边界匹配 | 后端 SrtParser + SubtitleLineRepository + CardEnhanceService |
| 2 | 匹配结果按 imdbId 去重后随机选择一部电影，避免集中在单一电影 | 后端 CardEnhanceService.searchSubtitle() |
| 3 | MovieBlock 下载/重试按钮改为 ⬇️/🔄 图标，删除按钮改为 🗑️ 图标，保留 tooltip | 前端 MovieBlock |
| 4 | MovieToolbar 参考 CardToolbar：排序改为 `DropdownMenu`，添加/批量导入改为 "+" / "📄" 图标按钮 | 前端 MovieToolbar |

## User Stories

1. 作为一名 Learner，搜索闪卡单词 "test" 时，增强结果不会错误地匹配到包含 "greatest" 或 "testing" 的台词。
2. 作为一名 Learner，搜索闪卡单词 "dream" 时，如果该词在 Inception、The Matrix、Shutter Island 三部电影中都出现，增强结果会在多部电影之间均匀分布，而不是总展示同一部电影的台词。
3. 作为一名 Learner，在电影列表页可以看到更长的电影名称，因为下载和删除按钮从文字改为紧凑图标，释放了更多宽度给电影名称列。
4. 作为一名 Learner，将鼠标悬停在 ⬇️ 图标上可以看到 "下载字幕" 的 tooltip 提示。
5. 作为一名 Learner，将鼠标悬停在 🔄 图标上可以看到 "重试" 的 tooltip 提示。
6. 作为一名 Learner，将鼠标悬停在 🗑️ 图标上可以看到 "删除" 的 tooltip 提示。
7. 作为一名 Learner，电影工具栏的排序控件与卡片工具栏的排序下拉风格一致，使用相同的 `DropdownMenu` 组件。
8. 作为一名 Learner，电影工具栏的 "添加电影" 按钮显示为 "+" 图标（与卡片工具栏一致），悬停可见 tooltip。
9. 作为一名 Learner，电影工具栏的 "批量导入" 按钮显示为 "📄" 图标，点击直接触发导入（无需下拉菜单）。
10. 作为一名 Learner，排序下拉按钮的文案会动态显示当前选中的排序方式（如 "名称 A→Z"）。

## Implementation Decisions

### 1. wordsLower 空格边界匹配

**数据层格式变更：**

`SrtParser` 构建 `wordsLower` 时，在清洗后（去标点、小写化、空格归一化、trim）的文本首尾各加一个空格：

```
原文: "You mustn't be afraid to dream a little bigger, darling!"
清洗: "you mustn't be afraid to dream a little bigger darling"
结果: " you mustn't be afraid to dream a little bigger darling "
```

**SQL 查询模式变更：**

`SubtitleLineRepository` 的 JPQL 从 `LIKE '%word%'` 改为 `LIKE '% word %'`。

**Java 侧标记逻辑同步：**

`CardEnhanceService.generateSceneSummary()` 中的目标词标记从 `.contains(targetWord)` 改为 `.contains(" " + targetWord + " ")`。

**数据迁移：**

现有 `subtitle_lines` 表中的 `words_lower` 列保留旧格式（无首尾空格）。在应用启动时执行一次性 UPDATE：
```sql
UPDATE subtitle_lines SET words_lower = ' ' || words_lower || ' '
```
纳入 `DataInitializer` 或等价启动逻辑。用户也可通过重新下载字幕获得新格式数据。

### 2. 电影分散匹配

**去重 + 随机选择逻辑：**

`CardEnhanceService.searchSubtitle()` 中，拿到 `LIKE` 查询结果后：

1. 按 `imdbId` 分组（`Collectors.groupingBy`）
2. 每组取 `lineIndex` 最小的那条（最早出现的台词）
3. 如果只有 1 组（词仅在一部电影中出现），直接用该组结果
4. 如果多组，`Collections.shuffle(groups)` 随机选一组
5. 用选中组的那条 `SubtitleLine` 作为最终匹配

### 3. MovieBlock 按钮图标化

**按钮映射：**

| 状态 | 旧按钮文案 | 新图标 | tooltip |
|------|-----------|--------|---------|
| PENDING | 下载字幕 | ⬇️ | 下载字幕 |
| FAILED | 重试 | 🔄 | 重试 |
| DONE | （无下载按钮） | — | — |
| 任何状态 | 删除 | 🗑️ | 删除 |

**图标选择：**

- 下载：⬇️（U+2B07 U+FE0F）
- 重试：🔄（U+1F504）
- 删除：🗑️（U+1F5D1 U+FE0F）

**交互行为不变：**

- 点击下载/重试仍弹出 `MovieRetryModal` 确认弹窗
- 点击删除仍弹出 `MovieDeleteModal` 确认弹窗
- 按钮仍使用现有 `data-testid`（`movie-download-btn`、`movie-delete-btn`）

**CSS 变更：**

- 移除 `.actionBtn` 的文字按钮样式（`padding: 4px 12px; font-size: 0.8em`）
- 新增图标按钮样式：更大的 touch target（建议 32×32px 以上），透明背景，hover 时变色
- `.actions` 的 `gap` 从 `6px` 适当增大以容纳图标

### 4. MovieToolbar 参考 CardToolbar

**排序控件：原生 `<select>` → `<DropdownMenu>`**

- 复用已有的 `DropdownMenu` 组件（来自 `src/main/frontend/src/components/manage/DropdownMenu.tsx`）
- 6 个排序选项不变：名称 A→Z / Z→A / 年份 ↑ / 年份 ↓ / 添加时间 ↑ / 添加时间 ↓
- `label` 动态显示当前选中项（如 "名称 A→Z"），在 `MoviesApp` 中跟踪当前 sort 值
- `testId`: `"movies-sort-btn"`，option 的 `testId`: `"movies-sort-option"`

**添加按钮：文字 → "+" 图标**

- 按钮图标："+"，与 `CardToolbar` 的 `createBtn` 一致
- tooltip: "添加电影"
- `testId`: `"movies-add-btn"` 保留

**批量导入按钮：文字 → "📄" 图标**

- 按钮图标："📄"
- 直接触发 `onImportMovies`（无需下拉菜单——电影只有导入，没有导出）
- tooltip: "批量导入"
- `testId`: `"movies-import-btn"` 保留

**CSS 布局参考 CardToolbar：**

- `.toolbar` 内使用 `.toolbarRow` 布局（`display: flex; gap: 8px; align-items: center`）
- `.searchInput` 保持 `flex: 1`
- `.actions` 使用 `display: flex; gap: 8px; align-items: center; flex-shrink: 0`
- 图标按钮样式参考 `CardToolbar` 的 `createBtn`

## Testing Decisions

### 测试原则

- 行为导向，断言外部可观察行为（API 响应字段、前端 DOM 渲染、数据库行状态、SQL 查询结果）。
- 使用已有测试基类和 mock 模式，不引入新测试框架。
- 优先复用现有测试文件，仅在新功能无覆盖时新增测试用例。
- 所有前端测试使用 `data-testid` 选择器，不依赖 CSS Modules 哈希类名。
- E2E 测试断言从确定性改为存在性（如 "匹配到某部电影" 而非 "匹配到 Inception"），以适配随机分散逻辑。

### 后端 Unit Test 改动

| 测试文件 | 改动说明 |
|---------|---------|
| `service/SrtParserTest.java` | 3 个测试的 `wordsLower` 断言加首尾空格：`parsesBasicSrt`、`stripsHtmlTags`、`generatesWordsLowerWithoutPunctuation`。其余测试不对 `wordsLower` 做精确断言，无需改动。 |
| `repository/SubtitleLineRepositoryTest.java` | 2 个测试的 LIKE 参数和 test data 加空格：`findByImdbIdInAndWordsLowerLike_matchesWord`、`findByImdbIdInAndWordsLowerLike_ordersByImdbIdAndLineIndex`。`saveAndFindById_persistsCorrectly` 使用 `contains()` 不受影响。 |
| `service/CardEnhanceServiceTest.java` | `fullSuccess` 的 pattern mock 从 `eq("%dream%")` 改为 `eq("% dream %")`。新增测试：多电影场景验证 `shuffle` 后的随机分散行为（mock 多条结果跨多个 imdbId，验证最终结果来自其中某部电影）。 |

### 前端 Unit Test 改动

| 测试文件 | 改动说明 |
|---------|---------|
| `movies/MovieToolbar.test.tsx` | 排序相关 2 个测试改为 DropdownMenu 交互模式：点击 trigger → 断言菜单选项 → 点击选项 → 验证 `onSortChange`。添加/导入按钮测试保留，`data-testid` 不变。 |
| `movies/MoviesPage.test.tsx` | `sorts movies on dropdown change` 测试：原 `fireEvent.change(select)` 改为 DropdownMenu 点击交互。 |
| `movies/MovieBlock.test.tsx` | **无需改动** — 所有断言使用 `data-testid`，图标化不影响 selector。 |
| Modal 测试 ×3 | **无需改动** — 弹窗逻辑未变。 |

### E2E 测试改动

| 测试文件 | 改动说明 |
|---------|---------|
| `e2e/MoviesPageIT.java` | `sortChangesOrdering` 测试：`page.locator("[data-testid='movies-sort-select']").selectOption(...)` 改为 DropdownMenu 点击交互。其余测试使用 `data-testid`，不受影响。 |
| `e2e/CardEnhanceIT.java` | 放宽断言：不再断言特定电影名称，改为验证 "匹配到某部电影"（`movieQuote` 字段非 null 即可）。 |

## Out of Scope

- 字幕下载后端异步化
- 增强重试逻辑
- MovieBlock 状态图标（⏳/⏬/✓/✗）的样式变化——这些保留不变
- 电影列表的牌组 Tab（电影没有 deck 概念，CardToolbar 的 `deckTabs` 不迁移）
- 批量操作的导出功能（电影只有导入）

## Further Notes

- `DropdownMenu` 组件从 `manage/` 目录共享——无需复制，直接 import。
- `wordsLower` 格式变更是一个数据迁移，现有字幕数据需要通过启动 SQL 或重新下载字幕来适配新格式。推荐启动 SQL，用户无感。
- 电影分散匹配使用 `Collections.shuffle`，在同一台机器上每次调用的随机结果不同——这对于单次复习场景是期望行为（每次看到不同电影的台词），而非 bug。
