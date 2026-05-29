# 02: SessionStore 支持 null report + SessionStatus.FAILED

**Status:** `ready-for-agent`

## 目标

修改 `SessionStore.completeSession()` 使其接受 null report，新增 `SessionStatus.FAILED` 枚举值，新增对应单元测试。

## 变更

### 修改 `SessionStatus.java`

新增枚举值：

```java
ACTIVE, COMPLETED, FAILED
```

### 修改 `SessionStore.completeSession()`

方法签名不变，内部逻辑分支：

```java
@Transactional
public SessionReport completeSession(String sessionId, List<MessageData> messages,
                                       List<CorrectionData> corrections, ReportResult report) {
    Session session = find(sessionId);

    if (report != null) {
        session.complete();  // status = COMPLETED, endTime = now
        SessionReport sr = mapper.buildReport(sessionId, report);
        sessionReportRepository.save(sr);
    } else {
        session.setStatus(SessionStatus.FAILED);
        session.setEndTime(LocalDateTime.now());
    }
    sessionRepository.save(session);

    // 无论 report 是否 null，都保存消息和纠错
    List<Message> savedMessages = mapper.buildMessages(sessionId, messages);
    messageRepository.saveAll(savedMessages);
    List<ErrorRecord> errorRecords = mapper.buildErrorRecords(sessionId, corrections, savedMessages);
    errorRecordRepository.saveAll(errorRecords);
    updateUserProgress(session);

    return report != null ? sessionReport : null;
}
```

### 新增 `SessionStoreTest` 测试

新增 `completeSession_NullReport_MarksSessionFailed`：

- mock sessionRepository.findById → 返回一个正常 Session
- 调用 `completeSession("s1", messages, corrections, null)`
- 断言：`session.getStatus() == SessionStatus.FAILED`
- 断言：`sessionReportRepository.save()` 从不被调用
- 断言：`messageRepository.saveAll()` 被调用
- 断言：`errorRecordRepository.saveAll()` 被调用
- 断言：`userProgressRepository.save()` 被调用（progress 仍然更新）

现有 `completeSessionSavesAllEntitiesInSequence` 增加断言：`session.getStatus() == COMPLETED`（当前未验证）。

## 阻塞于

Issue 01（SessionComplete 调用需要此修改）

## Comments
