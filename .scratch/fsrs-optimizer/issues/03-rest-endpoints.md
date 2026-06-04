# 03: REST API 端点

**Status:** `ready-for-agent`

## 范围

在现有 `ReviewController` 或新建 Controller 中添加优化器的 REST 端点。

## 实现内容

### POST /api/fsrs/optimize
- 无需请求体（userId 从 SecurityContext 获取）
- 调用 `FsrsOptimizeService.optimize(userId)`
- 响应 `{taskId: uuid, status: "running"}`
- 幂等保护：若该 userId 已有 RUNNING 任务 → 返回已有 taskId 和 status
- 若 ReviewLog < 512 → 返回 `{taskId: uuid, status: "skipped", reason: "insufficient_data"}`

### GET /api/fsrs/optimize/status?taskId=xxx
- 从 ConcurrentHashMap 读取进度
- 响应：
```json
{
  "taskId": "uuid",
  "status": "running" | "completed" | "failed" | "skipped",
  "progress": {
    "epoch": 3,
    "batch": 45,
    "totalBatches": 200,
    "currentLoss": 0.32
  },
  "result": null | {
    "weights": [0.12, 1.29, ...],
    "finalLoss": 0.31,
    "iterations": 1000,
    "durationMs": 15234
  },
  "reason": null | "insufficient_data"
}
```
- taskId 不存在 → 404

### 认证
- 两个端点均走 `/api/**`，需 JSESSIONID cookie 认证（与现有 FlashcardController 一致）
- CSRF 已对 `/api/**` 禁用

### 线程安全
- Progress 存储在 `ConcurrentHashMap<String, OptimizeProgress>`，单例 Service 中
- 读（GET /status）和写（callback.onProgress）通过 ConcurrentHashMap 天然线程安全

## 依赖
- Issue 02（FsrsOptimizeService）
- 现有 SecurityConfig（/api/** 认证）

## 验证
- ReviewControllerTest 或新建 FsrsOptimizeControllerTest：验证 POST/GET 端点响应格式
- E2E 测试：POST → GET /status 轮询 → 验证 completed 状态（需要 WireMock 模拟？不需要——优化器不调 LLM）
