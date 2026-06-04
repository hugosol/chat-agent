# 04: 定时自动优化 + 交叉验证测试

**Status:** `ready-for-agent`

## 范围

实现 `@Scheduled` 定时自动优化任务，并编写基于 py-fsrs 测试数据的交叉验证测试。

## 实现内容

### Scheduled 定时任务
- 在 `FsrsOptimizeService` 中添加 `@Scheduled(cron = "0 0 3 * * SUN")` 方法
- 每周日凌晨 3:00 执行
- 遍历所有 User（通过 UserRepository），对每个用户调用 `optimize(userId)`
- 对跳过的情况（ReviewLog < 512）静默处理
- 异常不中断其他用户的优化

### 测试数据文件
- 将 `review_logs_josh_1711744352250_to_1728234780857.csv` 复制到 `src/test/resources/fsrs/`
- 文件格式：`card_id,review_rating,review_time,review_duration`
- review_rating：1=Again, 2=Hard, 3=Good, 4=Easy
- review_time：ISO 8601 含时区
- review_duration：毫秒（测试中忽略该列）

### 交叉验证测试（FsrsOptimizerTest）
- 解析 CSV → List<ReviewLog>（cardId, rating, reviewedAt）
- 构造 FsrsSchedulerConfig(withDefaults()) 
- `new FsrsOptimizer(logs, config).optimize(null)` → OptimizeResult
- 断言 1：每个 W[i] 与 py-fsrs `test_optimal_parameters` 的绝对差 ≤ 0.05
- 断言 2：优化后 BCELoss < 默认参数 BCELoss
- 断言 3：同一数据跑两次 → Arrays.equals(run1.weights(), run2.weights())
- 断言 4：打乱 logs 顺序后优化 → 结果与有序一致

### py-fsrs 期望输出（硬编码在测试中）
```java
static final double[] PYFSRS_OPTIMAL = {
    0.12340357383516173, 1.2931, 2.397673571899466, 8.2956,
    6.686820427099132, 0.45021679958387956, 3.077875127553957, 0.053520395733247045,
    1.6539992229052127, 0.1466206769107436, 0.6300772488850335, 1.611965002575047,
    0.012840136810798864, 0.34853762746216305, 1.8878958285806287, 0.8546376191171063,
    1.8729, 0.6748536823468675, 0.20451266082721842, 0.22622814695113844,
    0.46030603398979064
};
```

### 边界测试
- 空列表：`new FsrsOptimizer(emptyList, config).optimize(null)` → 返回默认 W[21]
- 前 500 条：<512 条 → 返回默认 W[21]
- 全是 Again（rating=1）：不崩溃
- 全是 Easy（rating=4）：不崩溃，难度降到 1.0 后优化继续

### 合成数据恢复测试
- 用默认 W[21] + FsrsScheduler 模拟 100 张卡各 10 条 review
- 优化器恢复 W[21] — 每个参数应在默认值 ±0.1 以内

### 需要的新增依赖
- `opencsv` 或手动解析（CSV 格式简单，4 列逗号分隔，可手写解析器避免新增依赖）

## 依赖
- Issue 01（FsrsOptimizer 核心算法）
- 测试数据文件已下载

## 验证
- `mvn test` 全部通过
- 交叉验证测试是核心质量门禁
