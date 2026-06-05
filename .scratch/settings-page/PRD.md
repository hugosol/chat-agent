# PRD: FSRS 设置页面 —— Learner 自定义调度参数

**Status:** `ready-for-agent`

## Problem Statement

Learner 使用 FSRS-6 算法复习闪卡时，所有调度参数使用社区默认值：学习步 1 分钟→10 分钟、目标正确率 90%、最大间隔 100 年等。但每个 Learner 的学习习惯和记忆模式不同——有的人觉得 1 分钟的短步太烦，有的人希望复习更频繁以记得更牢。当前没有任何界面让 Learner 调整这些参数，FSRS 对 Learner 是一个完全黑盒。

同时，一些已有的 Learner 偏好（每日新卡上限、每日起始时间、时区）也缺乏统一的配置入口——每日起始时间和时区甚至没有 UI 暴露。

## Solution

新建独立的 `/settings` 设置页面，从 Header 导航栏 ⚙ 图标进入。页面集中展示 9 个 Learner 可配置的偏好字段：3 个已有字段（每日新卡上限、每日起始时间、时区）和 6 个新 FSRS 参数字段（学习步、重学步、目标正确率、最大间隔、随机微调、洗牌）。

学习步和重学步提供预设选项 + 自定义模式，每个预设附带一句话说明帮助 Learner 理解其含义。所有字段通过已有的 `PUT /api/user/preferences` 端点保存，保存后立即生效（Caffeine 缓存即时清除）。

## User Stories

1. 作为一名 Learner，我可以在 Header 导航栏点击 ⚙ 图标进入设置页面，统一管理我的复习偏好。
2. 作为一名 Learner，我可以选择一个学习步预设（如"Anki 默认"或"快速毕业"），看到预设的说明文字，理解这个选择对我的复习体验意味着什么。
3. 作为一名 Learner，我可以选择"自定义"学习步，手动输入任意间隔阶梯（如 `5m,1h`），系统会验证格式是否正确。
4. 作为一名 Learner，我可以选择重学步预设，决定遗忘后是否需要额外的短期重学步骤。
5. 作为一名 Learner，我可以设置目标正确率（如 0.90 = 期望记得 90% 的卡片），值越高复习越频繁。
6. 作为一名 Learner，我可以设置最大间隔上限（如 365 天），超过上限的卡片不会再推远。
7. 作为一名 Learner，我可以开关"随机微调"——开启后卡片到期日有小幅随机扰动，避免扎堆。
8. 作为一名 Learner，我可以开关"洗牌"——开启后同时到期的卡片随机出现，而非固定按时间顺序。
9. 作为一名 Learner，我可以调整每日新卡上限和每日起始时间——这些设置目前散落在 DeckPicker 中，现在统一在设置页管理。
10. 作为一名 Learner，我可以设置时区，确保"今天学了几张"等统计数据基于我本地时间而非服务器时间。
11. 作为一名 Learner，点击"保存设置"后所有参数立即生效，我当前的复习不受影响——已经在复习的卡片按上次配置继续。
12. 作为一名 Learner，如果输入有误（如目标正确率填了 1.5），保存时系统标红提示，不会写入错误数据。
13. 作为一名 Learner，DeckPicker 中仍然可以直接修改每日新卡上限——设置页和 DeckPicker 共享同一份数据，修改任意一处都会同步。
14. 作为一名 Learner，修改 learning_steps 或 desired_retention 后，已有卡片不会立即 reschedule（保持稳定），而是在下次复习时自然过渡到新参数。

## Implementation Decisions

### 1. 页面架构

- 独立路由 `/settings`，新建 React 页面组件 `SettingsPage`
- Header 导航栏新增 ⚙ 图标链接到 `/settings`（放在现有导航项右侧）
- 页面从 `GET /api/user/preferences` 加载全部 9 个字段，用 `PUT /api/user/preferences` 保存
- 前端使用 `fetch` + `credentials: "same-origin"`（与 DeckPicker 一致）

### 2. 字段清单（全部存入 UserPreferences）

**已有字段（3 个）：**

| 字段 | 类型 | 默认值 | UI 控件 |
|------|------|--------|---------|
| `newCardDailyLimit` | int | 20 | 数字输入框 + "张" 后缀 |
| `dayStartHour` | int | 6 | 数字输入框 + "时 (0-23)" 后缀 |
| `timezone` | String | null | 文本输入框，placeholder "Asia/Shanghai" |

**新增 FSRS 字段（6 个）：**

| 字段 | 类型 | 默认值 | UI 控件 |
|------|------|--------|---------|
| `learningSteps` | String | `"1m,10m"` | 预设下拉 + 自定义文本框 |
| `relearningSteps` | String | `"10m"` | 预设下拉 + 自定义文本框 |
| `desiredRetention` | Double | 0.9 | 数字输入框 + "(0.01~0.99)" |
| `maximumInterval` | Integer | 36500 | 数字输入框 + "天" |
| `enableFuzz` | Boolean | true | 开关 toggle |
| `shuffleDueCards` | Boolean | false | 开关 toggle |

### 3. 学习步预设

下拉选择后，说明文字和文本框同步更新。选中"自定义"时文本框变为可编辑。

| 预设 | 值 | 说明 |
|------|-----|------|
| Anki 默认 | `1m,10m` | 两步验证：1分钟后快速确认，10分钟后再检查一次，确保真正记住后才进入长期复习。最保守的选项，Anki 默认设置。 |
| 快速毕业 | `30m` | 一步验证：30分钟后检查一次即可毕业。适合对自己记忆力有信心、不想被频繁打断的 Learner。 |
| 只一步 | `10m` | 一步验证：10分钟后检查一次即可毕业。比快速毕业更短的第一步检查，适合想快但又不放心跳过的 Learner。 |
| 无步骤 | `""` | 跳过短期测试，新卡评 Good 后直接进入长期复习。相信 FSRS 的首次间隔计算，不需要反复确认。 |

### 4. 重学步预设

| 预设 | 值 | 说明 |
|------|-----|------|
| Anki 默认 | `10m` | 遗忘后进入重学流程：10分钟后重新出现。给你一次快速重学机会，防止刚忘就被推到几天后。 |
| 不留重学步 | `""` | 遗忘后不进入单独的重学流程。FSRS 自动计算一个更短的间隔，卡片留在 Review 状态直接进入下一次复习。 |

### 5. 预设切换行为

- 选非自定义预设 → 文本框自动填充对应值，设为只读（灰色/disabled），说明文字切换为对应预设说明
- 选"自定义" → 文本框变为可编辑，说明文字切换为格式提示："逗号分隔，单位 s/m/h/d，留空=跳过短期测试"
- 选预设后文本框内容变化，但只有点"保存设置"才提交

### 6. 验证规则（保存时一次性校验）

| 字段 | 规则 | 错误提示 |
|------|------|---------|
| `newCardDailyLimit` | ≥ 0 的整数 | "请输入大于等于 0 的整数" |
| `dayStartHour` | 0~23 的整数 | "请输入 0 到 23 之间的整数" |
| `learningSteps` | 匹配 `^(\d+[smhd],)*\d+[smhd]$` 或空 | "格式错误。如: 1m,10m 或 30s 或留空" |
| `relearningSteps` | 同上 | 同上 |
| `desiredRetention` | 0.01~0.99 | "请输入 0.01 到 0.99 之间的数值" |
| `maximumInterval` | ≥ 1 的整数 | "请输入大于等于 1 的整数" |

### 7. API 协议

**GET /api/user/preferences**（已有端点，扩展响应）
```json
{
  "newCardDailyLimit": 20,
  "dayStartHour": 6,
  "timezone": "Asia/Shanghai",
  "lastDeckId": "...",
  "lastMode": "STANDARD",
  "learningSteps": "1m,10m",
  "relearningSteps": "10m",
  "desiredRetention": 0.9,
  "maximumInterval": 36500,
  "enableFuzz": true,
  "shuffleDueCards": false
}
```

**PUT /api/user/preferences**（已有端点，扩展请求体接受新字段）
```json
{
  "newCardDailyLimit": 20,
  "learningSteps": "1m,10m",
  "desiredRetention": 0.9,
  ...
}
```
- 支持部分更新：未传字段保持原值（不重置为 null）。DeckPicker 只传 3 个字段时不会影响 FSRS 设置
- 保存成功后清除 Caffeine 缓存（通过 `@CacheEvict` 或程序化 evict）
- Response 返回更新后的全部字段（用于确认）

### 8. 保存后行为

- 成功后展示短暂 toast 提示"设置已保存"，2 秒后自动消失
- Caffeine 缓存即时清除——下一次 rateCard 自动使用新参数
- **不触发 reschedule**——已有卡片保持原状态，新参数在下次复习时自然生效
- 如果 DeckPicker 页面也打开了，下一次加载时会读到新的 dailyLimit

### 9. DeckPicker 兼容

- DeckPicker 保留现有 dailyLimit 输入框不变
- 两端独立调用同一 API，自然保持数据一致
- DeckPicker 不需要引入新的依赖或逻辑

### 10. 恢复默认值

- "恢复默认值"按钮点击后将全部字段重置为默认值（9 个字段回到各自的 default）
- 重置仅修改前端表单状态，不自动保存——Learner 仍需点击"保存设置"确认
- 每个字段的默认值与 `FsrsSchedulerConfig.defaults()` 或当前 UserPreferences 实体默认值一致

## Testing Decisions

### What makes a good test
- 测试外部行为：预设切换更新说明文字、保存按钮调用 API、验证失败标红
- 不测试内部 React state 细节
- API 测试验证请求/响应格式和后端部分更新兼容性

### Test seams

**前端单元测试（SettingsPage.test.tsx — 新建）**

| 测试 | 验证 |
|------|------|
| `renders_all_9_fields` | 页面加载后 9 个字段全部渲染 |
| `preset_changes_update_text_and_explanation` | 选"快速毕业"→文本框填充 `30m`+说明文字切换 |
| `custom_enables_text_input` | 选"自定义"→文本框可编辑 + 格式提示出现 |
| `validation_shows_error_on_bad_format` | 输入非法值 + 点保存 → 错误提示显示 |
| `save_calls_api_with_all_fields` | 点保存 → fetch 被调用，body 含全部 9 个字段 |
| `reset_defaults_button` | 点恢复默认 → 全部字段回到默认值（不自动保存） |

**后端集成测试（ReviewControllerTest 扩展）**

| 测试 | 验证 |
|------|------|
| `getPreferences_includesNewFields` | 响应含 6 个新 FSRS 字段 |
| `putPreferences_withFsrsFields` | PUT 含新字段 → 200 + 响应确认值 |
| `putPreferences_partialUpdate` | PUT 仅传 3 个旧字段 → 新字段保持原值不变 |
| `putPreferences_invalidLearningSteps` | 传非法格式 → 400 |

**E2E 测试（新建 SettingsPageIT 或追加到现有）**

| 场景 | 步骤 |
|------|------|
| 设置页面完整流程 | 1. 导航到 /settings 2. 改学习步预设 3. 改 desired_retention 4. 开关 toggle 5. 保存 6. 刷新页面 7. 验证值持久化 |

**现有测试影响**

| 测试类 | 影响 |
|--------|------|
| `ReviewIT`（9 个） | **零影响**——DeckPicker 保留 dailyLimit 输入框不变 |
| `ManagePageIT` | **零影响**——设置页是独立页面 |
| `FlashcardIT` 等 | **零影响** |
| Header 组件测试 | 新增断言验证 ⚙ 链接存在 |

## Out of Scope

- **设置页触发 reschedule**：改学习步/desired_retention 后不自动重算已有卡片。P4 优化器完成后才触发 reschedule
- **学习步/重学步的实时预览**（如"30m 意味着新卡半小时后重现"）——当前用预设说明文字覆盖
- **移动端独立布局**——设置页使用与现有页面相同的响应式 CSS 模式
- **设置导出/导入**：延后迭代
- **多用户共享设置模板**：单用户应用，不需要

## Further Notes

### 依赖关系
- **依赖 P0 部分完成**：UserPreferences 实体需已扩展 6 个新字段 + Controller 需已添加对应的 getter/setter（P0 Issue 03）
- **依赖 P0 Issue 06 完成**：Caffeine 缓存配置就绪，`@CacheEvict` 在 PUT 端点生效
- **不依赖 P4**——设置页独立于优化器

### 与 P0 的边界
- P0 负责 UserPreferences 字段定义、API 扩展、缓存清除
- P1 负责前端 UI + 验证逻辑 + 预设说明
- 如果 P0 的 API 后续调整字段名或类型，P1 需同步

### 页面性能
- 单次 GET + 单次 PUT，9 个字段均为轻量文本/bool/int
- 预设切换纯前端计算，不触发 API 调用
- 保存时仅一次 HTTP 请求，响应时间 <100ms
