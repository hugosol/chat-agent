# 03: CoachMessageHandler 集成 + 测试更新

**Status:** `ready-for-agent`

## 目标

将 `CoachMessageHandler.onEndSession()` 中的报告/持久化/记忆逻辑委托给 `SessionComplete`，简化 Handler 的依赖项。更新 Handler 单元测试。

## 变更

### 修改 `CoachMessageHandler.java`

**构造函数变更**：
- 移除 4 个注入：`ReportAgent`、`SessionStore`、`MemoryService`、`MemoryCueService`
- 新增 1 个注入：`SessionComplete`

**`onEndSession()` 重构**：

```java
public void onEndSession(WebSocketSession ws) throws IOException {
    long startTime = System.currentTimeMillis();

    String sessionId = sessionService.getSessionId(ws.getId());
    if (sessionId == null) {
        protocol.send(ws, new ServerMessage.ErrorMessage("No active session to end."));
        return;
    }

    protocol.send(ws, new ServerMessage.StateUpdate("PROCESSING",
            sessionService.getUsageRatio(sessionId)));

    // 1. 等待纠错 + 收集状态数据（Handler 职责）
    sessionService.waitForPendingCorrections(sessionId, 10_000);
    List<MessageData> messages = sessionService.getMessages(sessionId);
    List<CorrectionData> corrections = sessionService.getCorrections(sessionId);
    String userId = sessionService.getUserId(sessionId);
    AgentMode mode = AgentMode.valueOf(sessionService.getMode(sessionId));

    // 2. 总结（SessionComplete 职责——永不抛异常）
    ReportResult report = sessionComplete.complete(sessionId, messages, corrections, userId, mode);

    // 3. 清理 + 响应（Handler 职责）
    sessionService.remove(sessionId);

    var reportData = new ServerMessage.ReportData(
            report != null ? report.overallAssessment() : "",
            report != null ? report.topicSummary() : "",
            report != null ? report.fluencyScore() : 0,
            report != null ? report.errorSummary() : "",
            report != null ? report.keyTakeaway() : ""
    );
    protocol.send(ws, new ServerMessage.SessionReportMessage(reportData));

    long elapsed = System.currentTimeMillis() - startTime;
    log.info("Conversation close latency: {}s", String.format("%.1f", elapsed / 1000.0));
}
```

注意：移除外层 try-catch——`SessionComplete` 永不抛异常，无需包裹。

### 修改 `CoachMessageHandlerTest.java`

**mock 变更**：
- 移除 4 个 `@Mock`：`ReportAgent`、`SessionStore`、`MemoryService`、`MemoryCueService`
- 新增 1 个 `@Mock`：`SessionComplete`

**`setUp()` 更新**：
```java
handler = new CoachMessageHandler(sessionService, turnProcessor,
        sessionComplete, protocol);
```

**修改 `onEndSessionCompletesAndSendsReport`**：
- Mock `sessionComplete.complete(s1, any(), any(), eq("user1"), eq(WORKPLACE_STANDUP))` → 返回正常 `ReportResult`
- Verify `sessionComplete.complete()` 被调用且参数匹配
- Verify `sessionService.remove("s1")` 在 `complete()` 之后（用 InOrder）
- Verify 发送 `SessionReportMessage` 含正确的 `topicSummary`

**删除 `onEndSessionErrorDuringReportSendsError`**：
- 报告失败不再抛异常到 Handler，此测试场景不再适用

**新增 `onEndSessionReportFailedStillSendsReport`**：
- Mock `sessionComplete.complete()` → 返回降级 `ReportResult`（fluencyScore = -1）
- Verify 发送的 `SessionReportMessage` 不是 `ErrorMessage`
- Verify `sessionService.remove("s1")` 被调用
- 可选：verify `reportMsg.report().fluencyScore() == -1`

**保持 `onEndSessionNoSessionSendsError`** 不变。

## 阻塞于

Issue 01（SessionComplete 模块）、Issue 02（SessionStore）

## Comments
