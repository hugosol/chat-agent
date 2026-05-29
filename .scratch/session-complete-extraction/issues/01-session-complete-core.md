# 01: SessionComplete 核心模块 + 单元测试

**Status:** `ready-for-agent`

## 目标

新建 `SessionComplete` 深模块及其 5 个单元测试用例。

## 变更

### 新建 `SessionComplete.java`

位置：`src/main/java/com/hugosol/webagent/service/SessionComplete.java`

```java
@Component
public class SessionComplete {
    // 注入: SessionStore, ReportAgent, MemoryService, MemoryCueService

    /**
     * 总结 Practice 会话：生成报告、持久化、触发异步记忆。
     * 永不抛异常——所有降级内部 catch + log。
     * @return ReportResult——报告成功时含真实数据，失败时含降级占位符（fluencyScore = -1）
     */
    public ReportResult complete(
        String sessionId,
        List<MessageData> messages,
        List<CorrectionData> corrections,
        String userId,
        AgentMode mode
    );
}
```

内部管线：

1. `generateReport(messages, corrections, userId, mode)`
   - 构造 `TaskContext(sessionId, userId, mode.name())`
   - 调用 `reportAgent.generate(messages, corrections, ctx)`
   - 成功 → 返回 `ReportResult`
   - 失败 → catch + log.error → 返回降级 `ReportResult`（overallAssessment=友好提示, topicSummary="N/A", fluencyScore=-1, errorSummary="N/A", keyTakeaway="N/A"）

2. `persistSession(sessionId, messages, corrections, report)`
   - 调用 `sessionStore.completeSession(sessionId, messages, corrections, report)`
   - 成功 → 正常
   - 失败 → catch + log.error + 继续

3. `fireAsyncMemory(userId, report, mode, sessionId, messages)`
   - 仅当 `userId != null` 时执行
   - `memoryService.generateMemoryAsync(userId, report, mode, sessionId)`
   - `memoryCueService.generateCuesAsync(sessionId, userId, mode, List.copyOf(messages))`

4. `return report`（报告失败时是降级报告，持久化失败时是真实报告）

降级报告措辞：

> Sorry, the session report generation failed. Your conversation and corrections have been saved.

### 新建 `SessionCompleteTest.java`

位置：`src/test/java/com/hugosol/webagent/service/SessionCompleteTest.java`

参考 `SessionStoreTest.java` 的 Mockito 风格。5 个测试用例：

| # | 测试名 | Mock 配置 | 断言 |
|---|--------|----------|------|
| 1 | `complete_AllSuccess_ReturnsValidReport` | 4 个依赖全部正常 | 返回的 ReportResult 与 reportAgent 产出一致；verify generateMemoryAsync + generateCuesAsync 被调用；verify completeSession 收到非 null report |
| 2 | `complete_ReportFails_ReturnsFallback_StillPersistsAndFiresMemory` | reportAgent.generate() 抛 RuntimeException | 返回降级报告（fluencyScore == -1）；仍调用 completeSession(..., null)；仍触发两个 async memory |
| 3 | `complete_PersistFails_ReturnsReport_StillFiresMemory` | sessionStore.completeSession() 抛 RuntimeException | 不抛异常；返回完整 ReportResult（reportAgent 生成的）；仍触发两个 async memory |
| 4 | `complete_DoubleFailure_ReturnsFallback_StillFiresMemory` | reportAgent + sessionStore 都抛异常 | 返回降级报告；仍触发两个 async memory；不抛异常 |
| 5 | `complete_NullUserId_SkipsMemory` | userId = null，其余正常 | generateMemoryAsync + generateCuesAsync 不被调用；completeSession 正常调用 |

## 阻塞于

无（独立模块，先于 Issue 02-04 完成）

## Comments
