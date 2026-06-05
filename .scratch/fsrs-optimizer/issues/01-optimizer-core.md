# 01: FsrsOptimizer 核心算法（纯 Java，零依赖）

**Status:** `ready-for-agent`

## 范围

实现 `FsrsOptimizer` 类 —— 纯 Java、零 Spring 依赖的参数优化器。

## 实现内容

### FsrsOptimizer 类
- 构造函数：`FsrsOptimizer(List<ReviewLog> reviewLogs, FsrsSchedulerConfig config)` —— 接收原始 ReviewLog 和 runtime config
- 暴露方法：`OptimizeResult optimize(ProgressCallback callback)` —— 执行优化，回调报告进度
- 返回类型：`OptimizeResult(double[] weights, double finalLoss, int iterations, long durationMs)` record

### 数据预处理（构造函数内完成）
- 按 `cardId` 分组，组内按 `reviewedAt` 升序排序
- 计算二元 recall 标签：Again=0, Hard/Good/Easy=1
- 标记同日 review（`elapsedDays < 1`）：不贡献 loss 但更新卡片模拟状态
- 每张卡片最多取前 64 条 review（max_seq_len=64）
- 生成有序的 cardId 列表供 epoch 间 shuffle

### BCELoss 计算
- `computeBatchLoss(double[] weights)` — 对所有 ReviewLog 计算平均 BCELoss
- 每个 loss 求值新建 FsrsScheduler（参数可能不同）
- 预测可回忆率 R 夹紧到 [1e-10, 1-1e-10]
- 只统计非同天 review

### 数值梯度（中心差分）
- `computeGradient(double[] weights)` — 对每个 W[i] 计算 (loss(W+h) - loss(W-h)) / (2h)
- h = 1e-4
- 扰动后夹紧到 LOWER_BOUNDS/UPPER_BOUNDS

### Adam 优化器
- 初始化 m=0, v=0（一阶和二阶动量），t=0
- 每步更新：m = beta1*m + (1-beta1)*g, v = beta2*v + (1-beta2)*g²
- 偏差修正：m_hat = m/(1-beta1^t), v_hat = v/(1-beta2^t)
- 参数更新：W -= alpha * m_hat / (sqrt(v_hat) + epsilon)
- 更新后夹紧到边界

### CosineAnnealingLR
- 私有方法，公式：`lr(t) = 0.5 * 4e-2 * (1 + cos(π * t / T_max))`
- T_max = ceil(num_non_same_day_reviews / 512) * 5

### ProgressCallback 函数式接口
```java
@FunctionalInterface
interface ProgressCallback {
    void onProgress(int epoch, int batch, int totalBatches, double currentLoss);
}
```

### 超参数常量（全部 private static final）
- 与 py-fsrs 完全一致（见 PRD 第 8 节）

### 参数边界常量
- LOWER_BOUNDS 和 UPPER_BOUNDS 数组（见 PRD 第 9 节）

## 依赖
P0 完成后的 FsrsScheduler、FsrsSchedulerConfig CardState 类。无 Spring 依赖。

## 验证
- 单元测试：空列表 → 默认参数；<512 条 → 默认参数
