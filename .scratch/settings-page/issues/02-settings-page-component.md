# 02: 前端 SettingsPage 组件

**Status:** `ready-for-agent`

## 范围

新建 `/settings` 路由的 SettingsPage React 组件，包含全部 9 个设置字段、预设切换、验证、保存。

## 实现内容

### 页面结构（从上到下三组）

**组 1：每日复习设置**
- 每日新卡上限：数字输入框（仅数字），"张" 后缀
- 每日起始时间：数字输入框，0~23，"时" 后缀
- 时区：文本输入框，placeholder "Asia/Shanghai"

**组 2：学习步 & 重学步**
- 学习步预设下拉 + 自定义文本框 + 说明文字区域
- 重学步预设下拉 + 自定义文本框 + 说明文字区域
- 预设选项和说明文字见 PRD 第 3-4 节

**组 3：FSRS 参数**
- 目标正确率：数字输入框 + "(0.01~0.99)"
- 最大间隔：数字输入框 + "天"
- 随机微调 (Fuzz)：switch/toggle 开关 + 说明文字
- 洗牌 (Shuffle)：switch/toggle 开关 + 说明文字

**底部按钮**
- [恢复默认值]（outline 样式）
- [保存设置]（primary 样式）

### 状态管理
- `useState` 管理 9 个字段值（初始从 GET API 加载）
- `useState` 管理错误信息 Map（字段名→错误文本）
- `useState` 管理预设选择（learningPreset, relearningPreset）
- `useState` 管理保存状态（loading, success toast）

### 预设逻辑
- 学习步预设 4 个选项；重学步预设 2 个选项
- 选非自定义预设 → 文本框自动填充，设为 readOnly + disabled 样式，说明文字切换
- 选"自定义" → 文本框 editable，说明文字切换为格式提示
- 文本框内容变化时，如果匹配某个预设的值则自动切换到该预设

### 验证（保存时）
- 逐字段检查（规则见 PRD 第 6 节）
- 失败字段标红 + 显示错误文本在字段下方
- 全部通过才调用 API

### API 交互
- `GET /api/user/preferences` on mount → 填充表单
- `PUT /api/user/preferences` on save → body 含全部 9 个字段
- Save 成功 → toast "设置已保存"，2 秒消失
- Save 失败 → 错误信息展示

### 恢复默认值
- 点击后调用 `FsrsSchedulerConfig.defaults()` 的值（或硬编码默认值）
- 前端状态重置，不自动保存

### CSS
- 与现有页面共享基础样式变量（颜色、圆角、间距）
- 使用 CSS Modules（与现有组件一致）

## 依赖
- Issue 01：API 端点已扩展
- 现有 Header 组件（Issue 03 添加导航入口）

## 验证
- SettingsPage Vita 单元测试（见 Issue 04）
