# MemoryCueService + Handler 集成 + 线程池扩展

**Status:** `ready-for-agent`

## Parent

`.scratch/memory-system-upgrade/PRD.md` — 记忆系统升级

## What to build

将 #3（数据层）和 #4（Agent）串联为端到端行为。实现 `MemoryCueService` 在会话结束时异步生成结构化记忆，集成到 `CoachMessageHandler.onEndSession()` 中与 Report 和 Memory Merge 并行执行。扩展线程池以承载新增并发负载。

### MemoryCueService 流程

```
generateCuesAsync(sessionId, userId, mode, messages):
  1. 调用 agent.detectSwitches(messages, mode)
     失败 → 写 MemoryCue(status=FIRST_CALL_FAILED, segment_index=-1) + 日志，return
     成功 → segments = 按切换点切割

  2. segments.forEach((seg, i) → CompletableFuture.runAsync:
       agent.generateCue(seg, mode, i)
         成功 → 写 MemoryCue(status=COMPLETED)
         失败 → 写 MemoryCue(status=SEGMENT_FAILED)
     )

  3. CompletableFuture.allOf，不抛异常阻塞 onEndSession
```

### onEndSession 并行流

```
onEndSession():
  ├─ memoryExecutor: reportAgent.generate() → memoryService.generateMemoryAsync(userId, report, mode, sessionId)
  ├─ memoryExecutor: memoryCueService.generateCuesAsync(sessionId, userId, mode, messages)
  └─ 主线程（等 report 完成）:
       sessionStore.completeSession(...) → 发送 SESSION_REPORT → sessionService.remove(sessionId)
```

### 线程池扩展

`AsyncConfig.memoryExecutor`：core 2→4，max 4→8。承载 MemoryCue 第一步 + 第二步（最多 3 segment 并行）+ Report + Topic Memory Merge + Learning Profile Merge。

## Acceptance criteria

- [ ] `MemoryCueService.generateCuesAsync()` 按上述流程执行，第一步串行、第二步各 segment 并行
- [ ] 第一次 `detectSwitches` 失败 → 写入一条 `FIRST_CALL_FAILED` 记录（segment_index=-1），打印日志，不触发 `generateCue`
- [ ] 无切换 → 全部 messages 作为一个 segment 调用一次 `generateCue`，写入一条 `COMPLETED` 记录
- [ ] 有切换 → 按切换点切割为多段，每段异步调用 `generateCue`，成功的写入 `COMPLETED`，失败的写入 `SEGMENT_FAILED`
- [ ] 单个 segment 失败不影响其他 segment 成功入库
- [ ] 整个流程不抛异常阻塞 `onEndSession`（失败均以日志 + 失败状态行处理）
- [ ] `CoachMessageHandler.onEndSession()` 按上述并行流重构：MemoryCue 生成与 Report+Memory 并行
- [ ] `CoachMessageHandler` 主线程等待 report 完成后继续持久化和通知（MemoryCue 不阻塞主线程）
- [ ] `AsyncConfig.memoryExecutor`：coreSize=4，maxSize=8
- [ ] `MemoryCueServiceTest`：无切换→1 detect+1 generate+1 行 COMPLETED
- [ ] `MemoryCueServiceTest`：有切换（如 [3]）→1 detect+2 generate+2 行 COMPLETED
- [ ] `MemoryCueServiceTest`：第一步调用失败→1 行 FIRST_CALL_FAILED，不触发 generateCue
- [ ] `MemoryCueServiceTest`：某个 segment generateCue 失败→该行 SEGMENT_FAILED，其他 segment COMPLETED 不受影响

## Blocked by

- #3 — MemoryCue 数据层（需要 `MemoryCue` 实体、`MemoryCueRepository`、`MemoryCueStatus` 枚举）
- #4 — MemoryCueAgent + Prompt 模板（需要 `MemoryCueAgent.detectSwitches()` 和 `generateCue()`）
