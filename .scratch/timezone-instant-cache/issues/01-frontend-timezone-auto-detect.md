Status: `ready-for-agent`

## What to build

首次登录或任何认证页面加载时，Header 组件静默检测浏览器本地时区。若后端 `UserPreferences.timezone` 为空，则自动保存。通过 sessionStorage 防抖避免同一 tab 内重复请求。

**端到端流程**：用户打开任意认证页面 → Header 挂载 → 发起 GET `/api/user/preferences` → 发现 timezone 为空 → 取 `Intl.DateTimeFormat().resolvedOptions().timeZone` → PUT `/api/user/preferences` 静默保存 → 后续 Review 中 `computeTodayStart()` 读取到正确时区 → `reviewedToday`/`learnedToday` 统计正常。

**关键实现点**：
- 放置在 Header 组件的 `useEffect` 中（空依赖数组，mount 时执行一次）。Header 在所有认证页面均被渲染，天然全覆盖。
- `sessionStorage.getItem('tz_checked')` 防抖——同一 tab 会话只执行一次。
- 异步不阻塞渲染。第一页可能仍用旧时区（`ZoneId.systemDefault()` 即 UTC），第二页起生效。
- 此改动不涉及任何后端代码——`computeTodayStart()` 已正确读取 `UserPreferences.timezone`，只需让 timezone 不再为空。

## Acceptance criteria

- [ ] 清除浏览器 sessionStorage 后登录，用户偏好中的 timezone 字段被自动填充为浏览器时区
- [ ] 再次登录同一 tab，不发起重复的时区检测请求（sessionStorage 防抖生效）
- [ ] Docker 部署环境（容器 UTC 时区）下，上午 10 点（北京时间）复习的卡片正确计入"今日已复习"
- [ ] 新卡每日上限正确生效——达到上限后不再出新卡
- [ ] 时区检测异步不阻塞页面渲染
- [ ] 现有前端测试不被破坏（header-entry.tsx 为 Vite 构建入口，不被组件测试 import）

## Blocked by

None - can start immediately
