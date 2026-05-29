# PRD: 抽取会话结束管线 —— SessionComplete

**Status:** `ready-for-agent`

## Problem Statement

当前 `CoachMessageHandler.onEndSession()` 是一个 45 行的 God Method，注入 7 个依赖，时编排 6 步操作。它同时处理状态读取、LLM 报告生成、数据库持久化、异步记忆触发、内存态清理和 WebSocket 消息发送——跨了 4 个领域（状态管理、分析、持久化、传输）。

由此产生三个摩擦：
1. **浅薄的 Handler**：`CoachMessageHandler` 的接口认知负担过重——理解"结束会话"需要同时理解 reportAgent、SessionStore、MemoryService、MemoryCueService 的内部契约。
2. **异常语义模糊**：单一 `try-catch` 包裹全部 6 步，无法区分"报告 LLM 失败"（应降级展示）和"数据库不可用"（应清理后优雅退出），Learner 在前端看到模糊的 "Failed to end session"。
3. **缝泄漏**：`CoachWebSocketHandler.afterConnectionClosed()` 直接调用 `sessionService.unbind()`，跳过 `CoachMessageHandler`——会话断开的两条路径（WS 断连 vs 主动结束）走不同代码路径，让"会话离开"这一概念散落在两处。

## Solution

抽取 `SessionComplete` 深模块，将结束管线的核心逻辑（报告生成 → 持久化 → 异步记忆触发）集中在一个简单的接口后面。`CoachMessageHandler` 从 7 个依赖降至 4 个，`onEndSession()` 从 45 行缩至约 20 行。

### 职责划分

```
[CoachMessageHandler]       [SessionComplete]
┌─────────────────────┐     ┌──────────────────────────────┐
│ waitPendingCorrections│    │ 1. reportAgent.generate()    │
│ getMessages()        │────▶│    ├── 成功 → ReportResult   │
│ getCorrections()     │     │    └── 失败 → 降级报告       │
│ getUserId()          │     │ 2. sessionStore.complete()   │
│ getMode()            │     │    ├── 成功 → COMPLETED      │
│                      │     │    └── 失败 → catch+log+继续  │
│ ◀─ ReportResult ─────│     │ 3. memoryService.*Async()    │
│                      │     │ 4. memoryCueService.*Async() │
│ sessionService.remove│     └──────────────────────────────┘
│ protocol.send(report)│
└─────────────────────┘
```

### 接口

```java
@Component
public class SessionComplete {
    public ReportResult complete(
        String sessionId,
        List<MessageData> messages,
        List<CorrectionData> corrections,
        String userId,
        AgentMode mode
    );
    // 永不抛异常——所有降级路径内部 catch + log
}
```

### 异常处理矩阵

| 步骤 | 失败时行为 |
|------|-----------|
| `reportAgent.generate()` | catch → 打印日志，降级 report 含 `fluencyScore = -1` 哨兵值，继续执行后续 |
| `sessionStore.completeSession()` | null report → 设 `SessionStatus.FAILED`；DB 异常 → catch + 打印日志，继续执行 |
| `memoryService.generateMemoryAsync()` | fire-and-forget，内部自愈 |
| `memoryCueService.generateCuesAsync()` | fire-and-forget，内部自愈 |

**关键原则**：无论哪一步失败，返回的 `ReportResult` 总是可用的（非 null），异步记忆任务总是触发，`SessionComplete` 永不抛异常。

## User Stories

1. 作为一名 Learner，当我的会话报告生成失败（LLM 超时或不可用），我仍然能看到一个完整的"会话结束"界面，第一行显示友好的降级提示文字，评分行自动隐藏，不会看到误导的 "0/10"。
2. 作为一名 Learner，当数据库写入失败时，我仍能在前端看到完整的报告，报告内容不会丢失——系统会优雅降级而不是报错。
3. 作为一名开发者，当需要修改会话结束流程时，我只需要关心 `SessionComplete` 一个类——不需要在多处追踪 45 行的 `onEndSession`。
4. 作为一名开发者，当要新增一个结束流程步骤时，我只需在 `SessionComplete.complete()` 中追加一步，无需触碰 `CoachMessageHandler`。
5. 作为一名开发者，当排查"为什么某个会话没有报告"时，我能在后端日志中看到精确的失败位置——"report generation failed" 或 "persistence failed"，而不是通用的 "Failed to end session"。
6. 作为一名测试者，我可以独立测试 `SessionComplete` 管线的 5 种失败组合（全成功、报告失败、持久化失败、双失败、null userId），每个测试只 mock 4 个依赖，不依赖 Handler 的 WebSocket 层。
7. 作为一名维护者，`CoachMessageHandler` 的构造函数从 7 个参数减为 4 个，依赖图更易理解。

## Implementation Decisions

### 1. SessionComplete —— 深模块

- 注入 4 个依赖：`SessionStore`、`ReportAgent`、`MemoryService`、`MemoryCueService`
- 不注入 `SessionService`（状态读取和清理留给 Handler）
- 不注入 `ProtocolDispatcher`（WebSocket 发送留给 Handler）
- `complete()` 返回 `ReportResult`，永不抛异常——所有降级内部 catch + log
- 报告失败时返回降级报告：`overallAssessment` 含友好提示、`topicSummary = "N/A"`、`fluencyScore = -1`、`errorSummary = "N/A"`、`keyTakeaway = "N/A"`
- 持久化失败时仍然返回已生成的完整 `ReportResult`（因为报告已生成）
- 异步记忆任务总是触发——Memory Cue 分析原始消息文本，不依赖报告；即使报告为空 Topic Memory 也会安全写入空值

### 2. SessionStore.completeSession() 支持 null report

修改 `completeSession(sessionId, messages, corrections, report)` 使其接受 `null` 的 report 参数：

- `report != null` → 调用 `session.complete()` 设 `COMPLETED`，正常保存报告
- `report == null` → 设 `SessionStatus.FAILED` + `endTime`，跳过 `saveReport()`，仍保存消息和纠错，仍更新 `UserProgress`

`SessionReport` 返回值在 report 为 null 时为 null（调用方 SessionComplete 不消费此返回值）。

### 3. SessionStatus 新增 FAILED

`SessionStatus` 枚举新增 `FAILED` 值，表示会话正常关闭但报告 LLM 调用失败。与 `ACTIVE`（进行中）和 `COMPLETED`（正常结束）平级。

### 4. ReportResult 哨兵值

`ReportResult.fluencyScore` 使用 `-1` 作为"报告生成失败"的哨兵值。有效评分范围为 0-10，`-1` 永不冲突。

### 5. 前端 app.js showReport() 条件渲染

`showReport()` 方法中增加条件判断：

```
if (report.fluencyScore >= 0) {
    渲染 "Fluency Score: X/10" 行
}
// fluencyScore < 0 时整行隐藏，避免显示 "-1/10"
```

降级报告的前端展示效果：
```
Overall Assessment:  Sorry, the session report generation failed. Your conversation and corrections have been saved.
Topic Summary:       N/A
                     ← Fluency Score 行消失
Error Summary:       N/A
Key Takeaway:        N/A
```

### 6. 降级报告的措辞

报告 LLM 失败时 `overallAssessment` 的值（英文）：

> Sorry, the session report generation failed. Your conversation and corrections have been saved.

不暴露 LLM 内部错误细节给 Learner，避免不必要的困惑。

### 7. CoachMessageHandler 简化

`onEndSession()` 重构为三阶段：

1. **等待 + 收集**：`waitForPendingCorrections` → `getMessages` / `getCorrections` / `getUserId` / `getMode`（Handler 职责，使用 `SessionService`）
2. **总结**：`sessionComplete.complete(sessionId, messages, corrections, userId, mode)` → 返回 `ReportResult`（`SessionComplete` 职责）
3. **清理 + 响应**：`sessionService.remove(sessionId)` → `protocol.send(sessionReportMessage)`（Handler 职责）

移除 4 个注入依赖：`ReportAgent`、`SessionStore`、`MemoryService`、`MemoryCueService`。新增 1 个注入依赖：`SessionComplete`。

Handler 从 7 个依赖缩减为 4 个。

### 8. 所有异常场景的前端表现

| 场景 | 前端模态框 | 数据库状态 |
|------|----------|-----------|
| 全部成功 | 完整报告，5 字段有内容，评分正常 | `COMPLETED`，全量入库 |
| 报告 LLM 失败 | 首行友好降级提示，Fluency Score 行隐藏，其余 N/A | `FAILED`，消息+纠错入库，无报告 |
| 持久化失败 | 完整报告（与场景 1 相同） | 无变化（事务回滚），下次查历史少此条 |
| 无活跃会话 | 红色错误提示 | 无影响 |

### 9. 命名：SessionComplete 而非 SessionClosure

`SessionClosure` 暗示"关闭、收尾"，但模块的核心工作是"生成报告 + 持久化 + 触发记忆"——这是对会话内容的**总结**，不是单纯的关闭操作。`SessionComplete` 更准确地反映其职责。

### 10. 一次改变一个模块

本次重构**仅改变**会话结束管线。报告渲染到前端的时机保持不变（报告同步生成→同步展示到前端模态框）。不改变 WebSocket 协议、不改变异步记忆的触发逻辑、不改变会话恢复流程。

## Testing Decisions

### 测试原则

- 只测试外部可观测行为：给定输入 → 期望输出，不验证内部中间状态
- `SessionComplete` 依赖全部 mock（`@ExtendWith(MockitoExtension.class)`），参考 `SessionStoreTest` 的测试模式
- Handler 层测试仅验证 `SessionComplete.complete()` 被调用的参数和顺序，不深入验证管线内部

### 新建测试

**SessionCompleteTest**（5 个测试用例）：

1. **全部成功**：mock 4 个依赖均正常 → 返回 `ReportResult` 与 `reportAgent` 产出一致；验证 `generateMemoryAsync` + `generateCuesAsync` 被调用
2. **报告 LLM 失败**：`reportAgent.generate()` 抛 `RuntimeException` → 返回降级报告（`fluencyScore == -1`，`overallAssessment` 含 "failed"）；仍调用 `completeSession(null report)` 和两个 async memory
3. **持久化失败**：`sessionStore.completeSession()` 抛 `RuntimeException` → 不抛异常；返回完整 `ReportResult`（报告已生成）；仍触发两个 async memory
4. **双失败**：报告 + 持久化都抛异常 → 返回降级报告；仍触发两个 async memory；不抛异常
5. **userId 为 null**：跳过所有记忆触发；`generateMemoryAsync` 和 `generateCuesAsync` 不被调用

### 修改测试

**CoachMessageHandlerTest**：

- 新增 mock `SessionComplete`，移除 `reportAgent`、`sessionStore`、`memoryService`、`memoryCueService` mock
- 修改 `onEndSessionCompletesAndSendsReport`：验证 `sessionComplete.complete()` 被调用（参数匹配）；验证 `sessionService.remove()` 在其之后
- 删除 `onEndSessionErrorDuringReportSendsError`：报告失败不再抛异常到 Handler 层
- 新增 `onEndSessionReportFailedStillSendsReport`：验证降级报告仍以 `SessionReportMessage` 送达（非 `ErrorMessage`），`fluencyScore == -1`
- 保持 `onEndSessionNoSessionSendsError` 不变

**SessionStoreTest**：

- 新增 `completeSession_NullReport_MarksSessionFailed`：`completeSession(..., null)` → `session.getStatus() == FAILED`；`sessionReportRepository.save()` 不被调用；消息和纠错仍然入库

### E2E 测试

现有 `EnglishCoachSessionIT` 覆盖完整会话结束流程。重构后需验证不变：
- 正常会话结束后的报告模态框正常展示
- WireMock 层不受影响（mock HTTP 层，不感知 `SessionComplete`）

## Out of Scope

- 报告生成改为异步（保持现有同步时序，不新增前端协议消息）
- 统一 WS 断连与主动结束的代码路径（Candidate #5，独立处理）
- `SessionService` 的状态读写职责拆分（`SessionService` 保持现有接口不变）
- `TurnProcessor` 的记忆注入逻辑迁移（Candidate #3，独立处理）
- 会话恢复流程修改
- 前端报告模态框样式变更（仅增加条件渲染逻辑）
- 数据库迁移脚本（JPA `ddl-auto: update` 自动处理枚举新增）

## Further Notes

- `SessionComplete` 不依赖 Spring 事务——事务由 `SessionStore.completeSession()` 的 `@Transactional` 管理
- `generateMemoryAsync` 和 `generateCuesAsync` 保持独立 fire-and-forget，不互相等待——两者无数据依赖，各自内部有 try-catch 自愈
- `close latency` 日志（会话结束耗时）保留在 Handler 层计时——因为 Handler 负责 `waitForPendingCorrections`（可能在 `SessionComplete.complete()` 之前阻塞），完整耗时应从 Handler 入口开始计
- `TaskContext` 构造（`sessionId + userId + mode`）在 `SessionComplete` 内部完成——调用方只传入原始字段，避免 `TaskContext` 作为接口参数导致调用方依赖 `TaskContext` 类型
- `ReportAgent` 无需修改——其 `generate()` 签名已有 `TaskContext`（从 Candidate #1 的模板合并而来），`SessionComplete` 自然兼容

## Implementation Plan

```text
01-session-complete-core.md  →  新建 SessionComplete.java + SessionCompleteTest.java（5 用例）
02-session-store-failed.md   →  SessionStore.completeSession(null report) + SessionStatus.FAILED + SessionStoreTest 新增
03-handler-integration.md    →  CoachMessageHandler 重构 + CoachMessageHandlerTest 修改（改 1、删 1、增 1）
04-frontend-sentinel.md      →  app.js showReport() 条件渲染 fluencyScore
05-e2e-verification.md       →  mvn verify + 文档更新
```

### 文档更新清单（Issue 05 内含）

| 文件 | 位置 | 改动 |
|------|------|------|
| `docs/architecture.md` | §二 决策日志 #41 | 新增：会话结束管线抽取 |
| `docs/architecture.md` | §四 [用户 End Session] | 数据流更新为 Handler → SessionComplete → Handler 三阶段 |
| `docs/architecture.md` | §六 数据模型 Enum | `SessionStatus { ACTIVE, COMPLETED }` → `{ ACTIVE, COMPLETED, FAILED }` |
| `docs/architecture.md` | §八 写入时机 | 更新为 `SessionComplete.complete()` 内部串联 |
| `docs/architecture.md` | §八 会话生命周期图 | 用 `SessionComplete.complete()` 调用替代展开的 4 步 |
| `docs/architecture.md` | §十 `service/` 文件结构 | 新增 `SessionComplete.java` |
| `docs/architecture.md` | §十 `model/SessionStatus` | 枚举值新增 `FAILED` |
| `docs/architecture.md` | §十 test 文件结构 | 新增 `SessionCompleteTest.java` |
| `docs/architecture.md` | §十二 实现阶段 | 新增阶段 16：会话结束管线抽取 |
| `AGENTS.md` | §Project Structure `service/` | 新增 `SessionComplete (session-ending pipeline)` |
| `AGENTS.md` | §Conventions 线程池 | 补充说明报告生成通过 `SessionComplete` 调用 |

`CONTEXT.md` 无需修改——`SessionComplete` 是类名，非领域概念。"Practice session 结束"已定义在 CONTEXT.md。
