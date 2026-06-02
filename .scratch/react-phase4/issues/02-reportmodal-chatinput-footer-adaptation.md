# 02: ReportModal + ChatInput/Footer Adaptation

**Status**: `ready-for-agent`

## Parent

[PRD：React 全量迁移：聊天页面纯 React 化](../PRD.md)

## What to build

实现 ReportModal React 组件，同时将 ChatInput 和 Footer 从旧 `sessionStatus` 切换到新的 `appStatus`。

### ReportModal 组件 + CSS Module

- 零 props，从 `useChatContext()` 读取 `state.report`
- `report === null` 时不渲染（返回 null）
- `report !== null` 时渲染 modal overlay，显示以下字段：
  - `summary`：会话概要
  - `fluencyScore`：流利度评分（仅 `fluencyScore >= 0` 时显示评分行；`fluencyScore < 0` 时隐藏）
  - `errorSummary`：错误统计摘要
  - `keyTakeaway`：关键收获
- Close 按钮：dispatch 一个清空 report 的 action（或通过 reducer action 将 `report` 设为 null）
- 必须渲染 `data-testid="report-modal"`（替代 `#reportModal`）并支持 `aria-hidden` 属性（可见时 `aria-hidden="false"`，隐藏时不渲染元素或 `aria-hidden="true"`）
- 必须渲染 `data-testid="report-content"`（替代 `#reportContent`）
- 必须渲染 `data-testid="report-close-btn"`（替代 `#closeReportBtn`）

### SESSION_REPORT Reducer 更新

`SESSION_REPORT` action 的 reducer 逻辑：
1. 重置 `messages`、`corrections`、`streamInProgress`、`statusPayload` 为初始值
2. 设置 `appStatus: "Connected"`
3. 存储 `report: action.report`

如果当前没有对应的 action 字段传递 report 对象，需要在 Action 类型中补充 report payload。

新增 `DISMISS_REPORT` action（或复用现有机制）：Close 按钮 dispatch 后将 `report` 设为 null。

### ChatInput Adaptation

- `disabled` 逻辑：`!isSessionActive(appStatus) || streamInProgress`
- 添加 `data-testid="text-input"`（替代 `#textInput`）
- 添加 `data-testid="send-btn"`（替代 `#sendTextBtn`）
- 去掉 Portal，直接返回 JSX DOM 结构

### Footer Adaptation

- `disabled` 逻辑：使用 `isSessionActive(appStatus)` 替代 `sessionStatus === "active"`
- 添加 `data-testid="mode-select"`（替代 `#modeSelect`）
- 添加 `data-testid="start-btn"`（替代 `#startBtn`）
- 添加 `data-testid="end-btn"`（替代 `#endBtn`）
- 去掉 Portal，直接返回 JSX DOM 结构

### 单元测试

**`ReportModal.test.tsx`（新建）**：
- report 为 null 时不渲染
- report 不为 null 时渲染所有字段
- fluencyScore >= 0 时显示评分行
- fluencyScore < 0 时隐藏评分行
- Close 按钮 dispatch 清空 report

**`ChatInput.test.tsx`（重写）**：
- `isSessionActive(appStatus)` 逻辑替代 `sessionStatus`
- Enter/Send 正确触发 send + dispatch
- data-testid 正确渲染

**`Footer.test.tsx`（重写）**：
- `isSessionActive(appStatus)` 逻辑替代 `sessionStatus`
- Start/End 和 Mode Select 的 disabled 逻辑
- data-testid 正确渲染

## Acceptance criteria

- [ ] ReportModal 组件：report 为空不渲染，为非空渲染完整内容
- [ ] fluencyScore >= 0 显示评分，< 0 隐藏评分
- [ ] Close 按钮 dispatch 清空 report
- [ ] `report-modal`、`report-content`、`report-close-btn` 三个 `data-testid` 渲染正确
- [ ] `report-modal` 支持 `aria-hidden` 属性
- [ ] `SESSION_REPORT` reducer：重置 + `appStatus: "Connected"` + 存储 report
- [ ] ChatInput：`disabled = !isSessionActive(appStatus) || streamInProgress`
- [ ] ChatInput：`text-input`、`send-btn` 两个 `data-testid` 渲染正确
- [ ] Footer：Mode Select、Start、End 的 disabled 逻辑使用 `isSessionActive(appStatus)`
- [ ] Footer：`mode-select`、`start-btn`、`end-btn` 三个 `data-testid` 渲染正确
- [ ] ChatInput 和 Footer 去掉 Portal，直接返回 JSX
- [ ] `ReportModal.test.tsx` 新建通过（至少 4 个 test case）
- [ ] `ChatInput.test.tsx` 重写通过
- [ ] `Footer.test.tsx` 重写通过
- [ ] `npm test` 全绿
- [ ] `mvn compile` 成功

## Blocked by

- [01: AppStatus State + ChatProvider Rewire + StatusBar](./01-appstatus-state-reducer-chatprovider-statusbar.md)
