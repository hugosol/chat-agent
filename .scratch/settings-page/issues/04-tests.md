# 04: 测试补充

**Status:** `ready-for-agent`

## 范围

为设置页功能补充前端单元测试、后端集成测试和 E2E 场景。

## 实现内容

### 前端单元测试（SettingsPage.test.tsx — 新建）

| 测试 | 验证 |
|------|------|
| `renders_all_9_fields_on_load` | GET /api/user/preferences mock 返回数据，页面渲染 9 个字段 |
| `preset_learning_anki_default` | 选"Anki 默认"→ 文本框填充 `1m,10m`(disabled) + 说明显示"两步验证..." |
| `preset_learning_fast_graduation` | 选"快速毕业"→ 文本框填充 `30m` + 说明切换 |
| `preset_learning_custom_enables_editing` | 选"自定义"→ 文本框可编辑 + 说明变为格式提示 |
| `preset_learning_auto_detect` | 手动输入 `30m` → 自动切换预设为"快速毕业" |
| `preset_relearning_anki_default` | 选"Anki 默认"→ 文本框 `10m` + 说明显示 |
| `preset_relearning_no_steps` | 选"不留重学步"→ 文本框 `""` + 说明切换 |
| `validation_desired_retention_out_of_range` | 输入 1.5 + 保存 → 显示错误 "请输入 0.01 到 0.99 之间的数值" |
| `validation_learning_steps_bad_format` | 输入 "abc" + 保存 → 显示格式错误 |
| `save_calls_put_api_with_all_fields` | 修改 3 个字段 + 保存 → fetch PUT body 含全部 9 个字段 |
| `reset_defaults_button` | 点击"恢复默认值"→ 全部值回到默认 + 未调用 PUT |
| `toast_after_save_success` | PUT 返回 200 → toast 出现"设置已保存" |

### 后端集成测试（ReviewControllerTest 扩展）

| 测试 | 验证 |
|------|------|
| `getPreferences_includesFsrsFields` | GET 响应包含 6 个新字段（值可能为 null） |
| `putPreferences_saveFsrsFields` | PUT body 含 desiredRetention=0.85 → 200 + 响应含 0.85 |
| `putPreferences_partialUpdate_keepsOldValues` | PUT body 仅含 3 个旧字段 → 新字段值不变 |
| `putPreferences_invalidLearningSteps_returns400` | PUT body 含 learningSteps="abc" → 400 |

### E2E 测试（新建 SettingsPageIT 或追加到 ManagePageIT）

| 场景 | 步骤 |
|------|------|
| 设置页面完整流程 | 1. 导航到 /settings 2. 改变学习步预设 + 验证说明切换 3. 修改 desired_retention 4. 关闭 Fuzz 开关 5. 点保存 6. 验证 API 被调用 7. 验证 toast 出现 8. 刷新页面 9. 验证设置持久化 |

### 现有测试兼容性

| 测试类 | 影响 | 确认项 |
|--------|------|--------|
| `ReviewIT`（9 个） | 零影响 | DeckPicker dailyLimit 输入框不变 |
| `ManagePageIT` | 零影响 | 设置页不在管理页路径 |
| `ReviewControllerTest`（现有） | 零破坏 | GET/PUT 端点响应新增字段，原有 key 不变 |
| DeckPicker Vitest | 零影响 | 组件 props 和 API 调用不变 |
| Header Vitest | 新增断言 | 验证 ⚙ 链接存在 |

## 依赖
- Issue 01：后端 API 扩展完成
- Issue 02：SettingsPage 组件完成
- Issue 03：Header 导航 + 路由完成

## 验证
- `mvn test` 全部通过（含新增前端单元测试）
- `mvn verify` E2E 全部通过（含新增设置页场景）
