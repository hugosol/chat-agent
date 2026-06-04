# 02: FsrsOptimizeService 编排层

**Status:** `ready-for-agent`

## 范围

实现 `FsrsOptimizeService` —— Spring Service 负责数据读写、调用优化器、写回结果、清除缓存、reschedule 卡片。

## 实现内容

### FsrsOptimizeService 类
- `@Service`，注入 ReviewLogRepository、FsrsParametersRepository、CardRepository、UserPreferencesService、CacheManager、FsrsSchedulerConfig
- 核心方法：`@Async("optimizerExecutor") CompletableFuture<OptimizeResult> optimize(String userId)`

### optimize(userId) 流程
1. `List<ReviewLog> logs = reviewLogRepository.findByUserId(userId)`
2. 若 `logs.size() < 512` → `return CompletableFuture.completedFuture(null)`（跳过）
3. `FsrsParameters params = paramsRepo.findByUserId(userId).orElse(FsrsParameters.defaults(userId))`
4. `UserPreferences prefs = prefsService.get(userId)`
5. `FsrsSchedulerConfig config = FsrsSchedulerConfig.merge(params, prefs)`
6. `FsrsOptimizer optimizer = new FsrsOptimizer(logs, config)`
7. `OptimizeResult result = optimizer.optimize(progressCallback)`
8. 验证：对比 `result.finalLoss()` 与默认参数的 BCELoss。若优化后 loss 未降低 → 保留旧参数，返回 result（带 warning）
9. 写入：`params.setWeights(result.weights())` → `paramsRepo.save(params)`
10. 清除缓存：`cacheManager.getCache("fsrsConfig").evict(userId)`
11. 自动 reschedule：调用 `rescheduleAllCards(userId, result.weights(), config)` — 对所有有 ReviewLog 的卡片逐卡重放
12. 返回 `CompletableFuture.completedFuture(result)`

### rescheduleAllCards(userId, weights, config) 方法
- 查询该用户所有有 ReviewLog 的卡片
- 对每张卡片：读取其 ReviewLog（按时间排序），用新参数从头模拟 → 更新 Card 的 FSRS 状态
- 未复习过的卡片跳过（新参数在首次 Review 时自然生效）
- 批量 save

### 并发控制
- Progress 存储在 `ConcurrentHashMap<String, OptimizeProgress>`（userId → progress）
- POST /optimize 时检查是否已有运行中任务
- 运行中 → 返回已有 taskId；空闲 → 启动新任务

### Progress 状态管理
- `OptimizeProgress` record：`(String status, int epoch, int batch, int totalBatches, double loss, OptimizeResult result, String reason)`
- status 枚举：PENDING → RUNNING → COMPLETED / FAILED / SKIPPED

## 依赖
- Issue 01（FsrsOptimizer 核心算法）
- P0 完成后的 FsrsParametersRepository、Caffeine CacheManager

## 验证
- 集成测试：mock Repository，验证 optimize() 完整流程
- 边界测试：<512 条 ReviewLog → 不入库；loss 上升 → 保留旧参数
