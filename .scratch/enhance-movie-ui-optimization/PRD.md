# PRD: Enhance 与 Movie UI 体验优化

**Status:** `ready-for-agent`

## Problem Statement

Learner 在使用闪卡增强（Card Enhance）和电影管理（Movies）功能时遇到一系列 UX 缺陷，影响操作流畅度和信息可读性：

1. **Movie 页面缺少导航栏**：其他所有页面（Chat、Review、Manage、Tune、Settings、Profile）都有 Header 导航栏，唯独 Movies 页面没有。Learner 在 Movies 页面无法导航到其他页面，只能通过浏览器后退。

2. **增强数据展示不符合预期**：已增强过的卡片翻牌后不自动展示电影台词和词源——后端已返回 `enhancement` 数据，但前端没有传递给 `CardDisplay`。此外，当电影台词或词源查不到时，对应的区域直接消失，Learner 无从知晓是"没查"还是"查了但没有"。

3. **增强数据各区块缺少视觉分隔**：释义区与增强区之间、电影台词区与词源区之间没有清晰的分割线，信息层次模糊。

4. **Enhance 按钮交互不够明确**：当前按钮是文字按钮 "Card Enhance"，点击后直接触发 API 调用，Learner 可能误触。缺乏确认步骤和下载中的明确反馈。

5. **删除卡片时增强数据残留**：Learner 删除卡片后，与之关联的 `card_enhancements` 行仍留在数据库，造成孤立数据。

6. **电影字幕下载缺少 loading 反馈**：Learner 点击"下载字幕" → 确认 → Modal 关闭后，没有任何 loading 指示，Learner 不知道下载是否在进行中。实际上后端是同步下载（可能需要 10-30 秒），前端在等待 HTTP 响应期间 UI 完全静默。

## Solution

六项独立优化，全部限定在现有模块边界内：

| # | 改动 | 模块 |
|---|------|------|
| 1 | Movies 页面复用 Header 组件 | 前端 MoviesApp |
| 2 | 增强数据自动展示 + 占位符 + 分割线 | 后端 ReviewService + 前端 ReviewPage + CardDisplay |
| 3 | Enhance 按钮改为放大镜 + 确认弹窗 | 前端 CardDisplay |
| 4 | 卡片删除时级联清理 CardEnhancement | 后端 FlashcardService |
| 5 | 字幕下载 Modal 内显示 loading | 前端 MovieRetryModal |

## User Stories

1. 作为一名 Learner，我可以在 Movies 页面看到与其他页面一致的 Header 导航栏，随时跳转到 Chat、Review、Manage 等其他页面。
2. 作为一名 Learner，翻到已增强过的卡片背面时，电影台词和词源内容自动展示，无需再次点击按钮。
3. 作为一名 Learner，当电影台词查不到时，我看到 `【暂无电影台词数据】` 占位符，知道系统已尝试搜索但无结果或失败。
4. 作为一名 Learner，当词源查不到时，我看到 `【暂无词源数据】` 占位符，知道系统已尝试查询但无结果或失败。
5. 作为一名 Learner，卡片背面的释义区与增强区之间有分割线，增强区内部的电影台词区与词源区之间也有分割线，信息层次清晰。
6. 作为一名 Learner，从未增强过的卡片背面显示一个放大镜图标，而不是文字按钮。
7. 作为一名 Learner，点击放大镜图标后会弹出确认提示"是否获取更多信息？"，确认后才开始获取增强数据。
8. 作为一名 Learner，点击确认后看到 loading spinner 覆盖卡片背面，等待增强数据加载完毕后自动展示。
9. 作为一名 Learner，删除卡片时，关联的所有增强数据同时被清理，不留孤立数据。
10. 作为一名 Learner，批量 CSV 导入覆盖删除卡片时，增强数据也同步清理。
11. 作为一名 Learner，点击电影的字幕下载按钮并确认后，Modal 内显示 loading spinner 和"下载中..."文字提示，确认按钮被禁用，直到下载完成 Modal 才自动关闭。
12. 作为一名 Learner，下载完成后 Modal 关闭、电影列表自动刷新显示最新字幕状态。

## Implementation Decisions

### 1. Movie 页面复用小尺寸 Header

- `MoviesApp` 组件内插入 `<Header />`，不传任何 props。
- Header 已内置 `isMoviesPage` 判断逻辑，自动隐藏 token 进度条和面板切换按钮，仅显示左侧导航按钮和用户名。
- 无需创建新的 Header 变体或修改 Header 组件本身。

### 2. 增强数据自动展示逻辑

**后端 `ReviewService.buildEnhancementMap()` 行为变更：**

- 改为检查 `card_enhancements` 表中是否有**任何**记录（不再要求 `status=SUCCESS`）。
- 无任何记录 → 返回 `null`（前端不展示增强区域，显示放大镜按钮）。
- 有记录 → 返回 map，`movieQuote` 和 `etymology` 字段仅在 `status=SUCCESS` 时填充实际数据，否则为 `null`。

`ReviewController.cardToMap()` 不变——它继续将 `buildEnhancementMap()` 的返回值塞入 `enhancement` 字段。

**前端数据流修复：**

- `ReviewPage` 传递 `card.enhancement` 给 `CardDisplay` 的 `enhancement` prop（当前缺失）。
- `CardDisplay` 已有的 `activeEnhancement = enhancement || localEnhancement` 逻辑不变。

**CardDisplay 渲染逻辑：**

```
enhancement == null（无任何记录）
  → 显示放大镜按钮

enhancement != null（有记录，无论成功或失败）
  → 释义区（背面文本）
  → <hr> 分割线
  → 电影台词区：
      有数据 → 片名 + 时间戳 + 台词 + 场景摘要
      无数据 → 【暂无电影台词数据】
  → <hr> 分割线
  → 词源区：
      有数据 → 词源文本
      无数据 → 【暂无词源数据】
  → 不显示放大镜按钮
```

### 3. 放大镜图标 + 确认弹窗

- 按钮图标：🔍（U+1F50D，`"\uD83D\uDD0D"`），替代文字 "Card Enhance"。
- 点击放大镜 → 弹出确认弹窗，使用简单的 `window.confirm("是否获取更多信息？")` 或内联确认状态——优先使用内联 UI 以保持与现有 Modal 模式一致。
- 确认 → 执行 `handleEnhance()`（loading spinner + API 调用）。
- 取消 → 不调 API。
- 按钮显示条件：`enhancement == null`（无任何 `CardEnhancement` 记录）。
- **暂不支持重试**：有记录但某类型失败的情况，显示占位符而非重试按钮。重试逻辑属于后续迭代范围。

### 4. 删除卡片时级联清理

**`FlashcardService.deleteCard()` 改动：**

在 `cardRepository.delete(card)` 之前增加：
```java
cardEnhancementRepository.deleteByCardId(cardId);
```

**新增 Repository 方法：**

`CardEnhancementRepository` 新增：
```java
void deleteByCardId(String cardId);
```

**`CardBatchService` 同步清理：**

当前批量导入不删除已有卡片（仅检查重复并拒绝），未来若加入覆盖删除逻辑需同步调用 `cardEnhancementRepository.deleteByCardId()`。本次不做改动。

### 5. 字幕下载 Modal Loading

**`MovieRetryModal` 改动：**

- 在 Modal body 中增加 loading 状态渲染：当 `retrying === true` 时，显示 spinner + "下载中..." 文字，隐藏或禁用确认按钮。
- 确认按钮在 `retrying === true` 时 `disabled`。
- 错误提示（已有）在 `retrying === false` 且有错误时显示。
- 请求成功后 `onRetried()` 回调自动关闭 Modal。

**后端不变**：`SubtitleService.downloadSubtitles()` 保持同步阻塞，请求期间 Modal 等待。

## Testing Decisions

### 测试原则

- 行为导向，断言外部可观察行为（API 响应字段、前端 DOM 渲染、数据库行状态）。
- 使用已有测试基类和 mock 模式，不引入新测试框架。
- 优先复用现有测试文件，仅在功能无覆盖时新增。

### 后端 Unit Test 改动

| 测试文件 | 状态 | 改动说明 |
|---------|------|---------|
| `service/FlashcardServiceTest.java` | **修改** | 新增：`deleteCard_alsoDeletesEnhancements`、`deleteCard_cardNotFound_throws404`、`deleteCard_wrongUser_throws404` |
| `service/ReviewServiceTest.java` | **修改** | 新增：`buildEnhancementMap_returnsNullWhenNoRecords`、`buildEnhancementMap_returnsPartialWhenOnlySubtitle`、`buildEnhancementMap_returnsPartialWhenOnlyEtymology`、`buildEnhancementMap_handlesFailedStatus`、`buildEnhancementMap_returnsNullWhenAllFailed` |
| `controller/FlashcardControllerTest.java` | 不修改 | 已有 delete endpoint 测试，mock 层验证增强清理 |

### 前端 Unit Test 改动

| 测试文件 | 状态 | 改动说明 |
|---------|------|---------|
| `review/CardDisplay.test.tsx` | **大幅修改** | 现有测试调整：放大镜按钮渲染、占位符逻辑。新增：确认弹窗交互（确认/取消）、enhancement prop 传递后的各种渲染分支、分割线存在性、loading 期间确认按钮禁用 |
| `movies/MovieRetryModal.test.tsx` | **新建** | 确认后 loading spinner 显示、按钮禁用、成功自动关闭、失败显示错误 |
| `movies/MoviesPage.test.tsx` | **可能微调** | MoviesApp 现在渲染 Header，可能需验证 Header 存在 |
| `movies/MovieBlock.test.tsx` | 不修改 | 按钮显隐逻辑不变 |

### E2E 测试

| 测试文件 | 状态 | 改动说明 |
|---------|------|---------|
| `e2e/MoviesPageIT.java` | **可能微调** | 页面现在包含 Header，现有断言（基于 data-testid）不受影响 |
| `e2e/ReviewIT.java` | 不修改 | 当前不涉及 enhance 场景 |

## Out of Scope

- Enhance 重试逻辑（某类型失败后的重新获取）
- 字幕下载后端异步化
- 电影列表行级 loading（非 Modal 内）
- Cardinality 批量导入覆盖删除的增强清理（当前导入不删已有卡）
- Enhance 确认弹窗的 UI 风格统一（用 `window.confirm` 或内联，不做 Modal 组件改造）

## Further Notes

- 本次改动全部限定在现有模块内，不新增文件（除 `MovieRetryModal.test.tsx`）。
- `ReviewPage` 传递 `enhancement` prop 的遗漏是一个 bug fix，非功能新增。
- Header 已在五个页面使用，Movies 页面加入后覆盖全部主要页面。
- `CardEnhancementRepository.deleteByCardId()` 是 Spring Data JPA 派生查询，无需手写 SQL。
