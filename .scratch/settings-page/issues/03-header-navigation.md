# 03: Header 导航 + 路由注册

**Status:** `ready-for-agent`

## 范围

在 Header 导航栏新增 ⚙ 设置图标/链接，并在前端路由中注册 `/settings` 页面。

## 实现内容

### Header 组件改造
- 导航栏右侧新增设置入口（⚙ 图标或文字"设置"）
- 使用 `data-testid='nav-settings'` 便于测试
- 点击跳转到 `/settings`
- 放置于现有导航项右侧（管理页链接旁或其他合适位置）

### 路由注册
- 前端 router 新增 `/settings` 路由 → 渲染 `SettingsPage` 组件
- 与现有的 `/review`、`/manage` 路由同级

### 页面入口
- `index.html` 或入口文件中确保 `/settings` 路径可访问
- 复用现有的认证机制（通过 JSESSIONID cookie）

## 依赖
- Issue 02：SettingsPage 组件已完成
- 现有 Header 组件和路由配置

## 验证
- Header 单元测试：验证 ⚙ 链接存在且 href 正确
- E2E：导航到 /settings 页面能正常渲染
