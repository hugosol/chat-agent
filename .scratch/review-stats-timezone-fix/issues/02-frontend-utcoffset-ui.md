# 02: 前端 — SettingsPage 时区输入 + Header 自动检测适配

**Status:** `ready-for-agent`

## Parent

[PRD: Review 统计时区统一 + 凌晨边界修复 + 重复代码清理](../PRD.md)

## What to build

前端适配后端 `utcOffset`（Integer）字段替换旧的 `timezone`（String）字段，涉及两个入口点：

### SettingsPage 时区输入 UI

将自由文本 `<input type="text" placeholder="Asia/Shanghai">` 改为带 `+`/`-` 切换按钮的数字输入框：

- 左侧 `-` 按钮递减、右侧 `+` 按钮递增
- 数字输入框允许直接输入，`inputMode="numeric"`
- 范围限制：-12 到 +14
- 显示 "UTC" 标签，正数显示 `+` 符号
- 字段名从 `timezone` 改为 `utcOffset`（SettingsFields 类型、JSON key、data-testid 全部统一）
- 加载时：`prefs.utcOffset ?? 8` → 转换为字符串存储
- 保存时：`parseInt(fields.utcOffset, 10)` 发送整数，空值时发送 `null`
- 前端校验：非整数或超出 -12~+14 范围显示错误提示

### Header 自动检测

`Header.tsx` 中的时区自动检测逻辑适配新字段：

- `Intl.DateTimeFormat().resolvedOptions().timeZone` → `-(new Date().getTimezoneOffset() / 60)`
- 检查条件：`prefs.timezone` → `prefs.utcOffset != null`
- PUT body key：`{ timezone: tz }` → `{ utcOffset: offset }`

### CSS

`SettingsPage.module.css` 新增 `offsetRow`、`offsetBtn`、`offsetLabel`、`offsetSign` 样式。

### 测试

- `SettingsPage.test.tsx`：`mockPrefs.timezone: "Asia/Shanghai"` → `utcOffset: 8`；`settings-timezone` testid 更新；新增测试：
  - `+` 按钮点击 → 数值 +1
  - `-` 按钮点击 → 数值 -1
  - 达到 14 上限时 `+` 不再递增
  - 达到 -12 下限时 `-` 不再递减
  - 非法输入显示错误提示
- `Header.test.tsx`：`timezone` → `utcOffset`；mock 返回 JSON key 和断言的 body key 同步更新

## Acceptance criteria

- [ ] `npm test` 全部前端单元测试通过
- [ ] E2E 测试 `ReviewIT` 和 `SettingsPageIT` 无回归
- [ ] SettingsPage 中 `+`/`-` 按钮可正确增减 UTC 偏移值，范围限制在 -12~+14
- [ ] 首次访问时 Header 自动检测浏览器 UTC 偏移并 PUT 保存（sessionStorage 防抖）
- [ ] 已设置 `utcOffset` 时不会重复 PUT
- [ ] 保存到后端的 `utcOffset` 为 Integer 类型
- [ ] 加载时正确显示已保存的 `utcOffset` 值

## Blocked by

None — API 契约已在 PRD 中约定（`utcOffset` Integer key），可独立开发验证。但完全集成验证需 #01 完成。
