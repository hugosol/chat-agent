# PRD: FSRS 优化器 —— W[21] 参数自动优化

**Status:** `ready-for-agent`

## Problem Statement

Learner 使用 FSRS-6 算法复习闪卡时，系统目前使用社区默认的 21 个权重参数（W[0]–W[20]）。这些默认参数来自大规模用户数据的统计平均，但每个 Learner 的记忆曲线是不同的——有的人遗忘快，有的人记忆持久。使用与自身记忆模式不匹配的参数，会导致卡片复习间隔要么太短（浪费 Learner 时间），要么太长（遗忘后再复习更费力）。

Learner 已经积累了一定数量的复习历史（ReviewLog），这些数据包含了 Learner 真实记忆模式的信号。但系统没有利用这些数据来优化调度参数——每次复习仍然使用一刀切的默认值。

## Solution

实现一个纯 Java 的 FSRS 参数优化器，从 Learner 的 ReviewLog 历史中自动计算出最适合该 Learner 的 W[21] 参数。优化器采用与 py-fsrs（官方 Python 实现）相同的算法：BCELoss（二元交叉熵损失）+ Adam 梯度下降 + 有限差分数值梯度。

优化器作为异步后台任务运行（手动触发或每周定时），优化完成后自动将新参数写入 FsrsParameters 表、清除缓存、并将所有有复习历史的卡片用新参数重新调度（reschedule）。

## User Stories

1. 作为一名 Learner，我可以在积累了一定数量的复习记录后，点击"优化参数"按钮，系统根据我的复习历史自动调整 FSRS 参数，让未来的卡片间隔更贴合我的记忆曲线。
2. 作为一名 Learner，优化过程中我可以打开状态页面查看当前进度（第几轮、已处理多少条 review、当前损失值），不用盲等。
3. 作为一名 Learner，如果我的复习数据不足（少于 512 条 ReviewLog），系统会提示数据不足无法优化，避免产出不可靠的参数。
4. 作为一名 Learner，优化完成后系统自动将我所有复习过的卡片用新参数重新计算到期时间，不用我手动操作。
5. 作为一名 Learner，如果优化结果不如默认参数（损失值反而上升），系统自动保留旧参数，不会让我的卡片调度变差。
6. 作为一名 Learner，即使我从不手动触发优化，系统也会每周自动在后台运行一次优化，持续改进我的卡片调度。
7. 作为一名 Learner，优化参数后，我新创建或新复习的卡片会立即使用优化后的参数，不需要重启或刷新。
8. 作为一名开发者，优化器是纯 Java 类，不依赖 PyTorch、Rust WASM、或其他外部计算框架，与项目现有的单 JAR 部署模型一致。
9. 作为一名开发者，优化器可以通过 py-fsrs 的公开测试数据（12,580 条真实 Anki 复习记录）进行交叉验证，确保 Java 实现输出的 W[21] 与 Python 实现在容差范围内一致。
10. 作为一名开发者，优化器是确定性的——相同输入产生相同输出，不会因为随机因素导致不同结果。
11. 作为一名开发者，优化器在数据不足（<512 条 ReviewLog）时直接返回默认参数，不浪费计算资源。
12. 作为一名开发者，优化器与数据持久化层解耦——算法部分不依赖 Spring 或 JPA，可以独立进行单元测试。

## Implementation Decisions

### 1. 优化算法：手动 Adam + 数值梯度

采用与 py-fsrs 完全相同的算法框架：

- **损失函数**：BCELoss（Binary Cross Entropy Loss）。每条 ReviewLog 计算当前参数的预测可回忆率 R，与真实回忆结果（Again=0 忘记，Hard/Good/Easy=1 记住）对比。只统计非同天 review 的损失（同天内多次复习不产生 loss，但卡片状态仍更新）
- **优化器**：Adam（lr=4e-2, beta1=0.9, beta2=0.999, epsilon=1e-8），5 个 epoch，mini_batch_size=512
- **梯度计算**：有限差分法，步长 h=1e-4。对每个 W[i] 计算 (loss(W+h) - loss(W-h)) / 2h 作为梯度近似。每次梯度更新需要 42 次 loss 求值（21 参数 × 2 扰动）
- **学习率调度**：CosineAnnealingLR，从 lr_max=4e-2 余弦衰减到 0，周期 T_max = 总批次数 × 5 epochs
- **参数约束**：每次梯度更新后将 W[i] 夹紧到 py-fsrs 的 LOWER_BOUNDS/UPPER_BOUNDS 范围内

### 2. 数据预处理

优化器构造函数接收 `List<ReviewLog>`，内部完成以下预处理：
- 按 cardId 分组，组内按 reviewedAt 升序排序
- 每条 review 计算二元 recall 标签：Again=0，Hard/Good/Easy=1
- 标记同日 review（不贡献 loss，但仍用于更新卡片模拟状态）
- 每张卡片最多取前 64 条 review（max_seq_len=64）
- 输入顺序不影响结果（构造时排序）

### 3. 架构分层

两层架构，关注点分离：

**算法层 — FsrsOptimizer（纯 Java，零 Spring 依赖）**
```
构造函数：FsrsOptimizer(List<ReviewLog>, FsrsSchedulerConfig)
方法：OptimizeResult optimize(ProgressCallback)
```
- 每次 loss 求值时新建 FsrsScheduler（权重可能不同）
- ProgressCallback 是函数式接口：`onProgress(int epoch, int batch, int totalBatches, double loss)`
- OptimizeResult record：`weights(double[21]), finalLoss(double), iterations(int), durationMs(long)`

**编排层 — FsrsOptimizeService（Spring @Service）**
- 注入 ReviewLogRepository、FsrsParametersRepository、CardRepository、CacheManager
- `@Async("optimizerExecutor")` 异步执行
- 流程：读 ReviewLog → 不足 512 条则跳过 → 读 FsrsParameters + UserPreferences → merge config → new FsrsOptimizer → optimize → 写入结果 → 清除 Caffeine 缓存 → reschedule 所有有 ReviewLog 的卡片
- 边界处理：ReviewLog < 512 → 返回 "insufficient_data"；优化后 loss > 默认 loss → 保留旧参数

### 4. REST API

**POST /api/fsrs/optimize**
- 立即启动异步优化任务，返回 `{taskId, status: "running"}`
- 幂等保护：若该用户已有运行中任务，返回已有 taskId
- 异步执行，不阻塞 HTTP 请求

**GET /api/fsrs/optimize/status?taskId=xxx**
- 返回 `{taskId, status: "running"|"completed"|"failed"|"skipped", progress: {epoch, batch, totalBatches, currentLoss}, result: {weights} | null, reason: "insufficient_data" | null}`

### 5. 定时自动优化

- `@Scheduled(cron="0 0 3 * * SUN")` 每周日凌晨 3:00 执行
- 遍历所有 User，对每个用户调用 `optimize(userId)`
- 与手动触发走同一代码路径（通过 FsrsOptimizeService）

### 6. 优化后 reschedule

只对有 ReviewLog 历史的卡片执行逐卡重放：
- 对每张有 ReviewLog 的卡片，读取其所有 ReviewLog
- 用优化后的新 W[21] 从头模拟所有复习状态转移
- 更新卡片的 stability、difficulty、cardState、due 等 FSRS 状态字段
- 未复习过的卡片（无 ReviewLog）不动——新参数会在它们的首次 Review 时自然生效
- CardRepository 已有按 cardId 和 userId 查询 ReviewLog 的方法

### 7. 缓存失效

优化完成后自动清除该用户的 Caffeine 缓存（`cacheManager.getCache("fsrsConfig").evict(userId)`），确保下次 `rateCard()` 使用新的 FsrsSchedulerConfig（含优化后的 W[21]）。

### 8. Adam 超参数

所有超参数硬编码，与 py-fsrs 一致：

| 参数 | 值 | 含义 |
|------|-----|------|
| learning_rate (alpha) | 4e-2 | Adam 学习率 |
| beta1 | 0.9 | 一阶动量衰减 |
| beta2 | 0.999 | 二阶动量衰减 |
| epsilon | 1e-8 | 数值稳定常数 |
| num_epochs | 5 | 全数据遍历次数 |
| mini_batch_size | 512 | 每次梯度更新的 review 数 |
| max_seq_len | 64 | 每张卡片最多使用的 review 数 |
| grad_step_h | 1e-4 | 数值梯度扰动步长 |
| random_seed | 42 | 随机种子（epoch 间 shuffle 卡片顺序） |
| min_reviews | 512 | 最低 ReviewLog 数量要求 |

### 9. W[21] 参数边界

每次梯度更新后夹紧到以下范围（与 py-fsrs 完全一致）：

| W 下标 | 含义 | 下界 | 上界 |
|--------|------|------|------|
| w[0-3] | 每评分初始稳定性 | 0.001 | 100.0 |
| w[4] | 初始难度计算 | 1.0 | 10.0 |
| w[5] | 难度指数 | 0.001 | 4.0 |
| w[6] | 难度 delta | 0.001 | 4.0 |
| w[7] | 均值回归 | 0.001 | 0.75 |
| w[8] | 稳定性增长 | 0.0 | 4.5 |
| w[9] | 稳定性指数 | 0.0 | 0.8 |
| w[10] | retrievability 影响 | 0.001 | 3.5 |
| w[11] | 遗忘后稳定性 | 0.001 | 5.0 |
| w[12] | 难度影响 | 0.001 | 0.25 |
| w[13] | 稳定性影响 | 0.001 | 0.9 |
| w[14] | retrievability 权重 | 0.0 | 4.0 |
| w[15] | Hard 惩罚系数 | 0.0 | 1.0 |
| w[16] | Easy 加成系数 | 1.0 | 6.0 |
| w[17] | 短时记忆 | 0.0 | 2.0 |
| w[18] | 短时记忆参数 | 0.0 | 2.0 |
| w[19] | 短时记忆指数 | 0.0 | 0.8 |
| w[20] | 遗忘曲线衰减 | 0.1 | 0.8 |

### 10. Loss 函数定义（BCELoss）

```
对每条非同天 review：
  predicted_retrievability = Scheduler.get_card_retrievability(card, review_time)
  recall_binary = (rating == Again) ? 0.0 : 1.0
  loss += -[recall * ln(R) + (1-recall) * ln(1-R)]
  // R 夹紧到 [1e-10, 1-1e-10] 避免 ln(0)

平均 loss = total_loss / 非同天 review 数量
```

### 11. CosineAnnealingLR 公式

```
lr(t) = 0.5 * lr_max * (1 + cos(π * t / T_max))
其中 t = 当前批次序号, T_max = 总批次数 * num_epochs
```

## Testing Decisions

### What makes a good test
- 测试优化器的外部行为（输入 ReviewLog，输出 W[21]），不测试内部梯度计算细节
- 使用 py-fsrs 的公开测试数据做交叉验证，而非手工构造
- 确定性验证（同一数据多次运行产出相同结果）
- 边界情况：空数据、少量数据、极端参数

### Test seams and prior art

**交叉验证测试（FsrsOptimizerTest — 新建单元测试类）**
- 加载 `review_logs_josh_1711744352250_to_1728234780857.csv`（12,580 条真实 Anki 复习记录）
- 解析为 ReviewLog 列表（cardId, rating, reviewedAt，忽略 review_duration）
- 运行优化器
- 断言：每个 W[i] 与 py-fsrs `test_optimal_parameters` 的绝对差 ≤ 0.05
- 断言：优化后 BCELoss < 默认参数 BCELoss

**确定性测试**
- 同一数据集运行两次 → 输出完全一致（`Arrays.equals(run1, run2)`）

**无序数据测试**
- 打乱 ReviewLog 顺序后优化 → 输出与有序输入一致

**边界测试**
- 空列表 → 返回默认 W[21]
- <512 条 → 返回默认 W[21]
- 全是 Again 评分 → 优化不崩溃
- 全是 Easy 评分（难度降到 1.0）→ 优化不崩溃

**模拟数据恢复测试**
- 用默认 W[21] + FsrsScheduler 模拟生成 ReviewLog → 优化器应恢复出接近默认值的 W[21]

### Prior art
- py-fsrs `tests/test_optimizer.py`（8 个测试用例）— 直接对应
- 项目已有 `FsrsSchedulerTest`（12 个单元测试）— 验证优化后的 W[21] 在 Scheduler 中产生合理间隔

## Out of Scope

以下内容不在此 PRD 范围内：

- **compute_optimal_retention**（最优目标正确率计算）：依赖 `review_duration` 字段——当前 Java ReviewLog 实体无此字段。延后到 ReviewLog 实体添加 `reviewDuration` 列后实现
- **学习步推荐**（compute_optimal_steps）：同样延后
- **优化历史记录**：暂不保存每次优化的中间结果或历史版本
- **多用户并发优化**：当前单用户场景，不做优化任务队列
- **优化器参数的手动调节 UI**：Adam 超参数硬编码，不暴露给 Learner

## Further Notes

### 依赖关系
- **强依赖 P0**（FsrsScheduler 参数化改造）：优化器需要 FsrsParameters（写入结果）、FsrsSchedulerConfig（合并参数）、实例化 FsrsScheduler（模拟卡片状态转移）。P0 必须先完成
- **依赖 ReviewLog 数据**：需要 Learner 积累 ≥512 条 ReviewLog 才能运行。ReviewLog 实体已存在，无需额外改造

### 性能预估
| ReviewLog 总量 | 预估耗时 | 说明 |
|---------------|---------|------|
| 500 条 | <1s（跳过） | 不足 512，直接返回默认参数 |
| 1,000 条 | ~5s | 2 batches × 5 epochs × 42 loss 求值 |
| 10,000 条 | ~20s | 20 batches × 5 epochs × 42 loss 求值 |
| 100,000 条 | ~3 min | 200 batches × 5 epochs × 42 loss 求值 |

### ADRs 关联
- `docs/adr/fsrs-optimizer-pure-java.md` — 优化器技术选型（手动 Adam + 数值梯度 vs Rust fsrs-rs vs Commons Math3）
- `docs/adr/scheduler-config-two-layer.md` — 写入 FsrsParameters 的基础架构
- `docs/adr/scheduler-static-to-instance.md` — 优化器内部反复新建 Scheduler 的基础

### CONTEXT.md 关联
- 术语 "FSRS Optimizer"、"Reschedule" 已在 CONTEXT.md 中定义
- 测试数据文件放置在 `src/test/resources/fsrs/` 下
