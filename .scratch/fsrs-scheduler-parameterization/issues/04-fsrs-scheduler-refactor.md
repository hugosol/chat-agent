# 04: FsrsScheduler 重构（静态→实例 + 公开方法）

**Status:** `ready-for-agent`

## 范围

将 `FsrsScheduler` 从纯静态工具类重构为可配置的实例类，同时公开两个 L1 方法。

## 实现内容

### 类结构改造
- 移除 `private` 构造函数 → 改为 `public FsrsScheduler(FsrsSchedulerConfig config)`
- `config` 字段替代所有 `static final` 硬编码常量（W[]、DESIRED_RETENTION、LEARNING_STEP_*、RELEARNING_STEP_*、MAXIMUM_INTERVAL、FUZZ_RANGES 等）
- 所有 `static` 方法改为实例方法，通过 `this.config` 读取参数

### 方法改造

| 方法 | 变化 |
|------|------|
| `createInitState(now)` | **保持 static**（不依赖配置） |
| `initNewCard(now)` | 改为实例方法 |
| `repeat(card, rating, now, fuzzSource)` | 改为实例方法 |
| `retrievability(card, now)` | **private→public** 实例方法 |
| `forgettingCurve(elapsedDays, stability, decay)` | **新增 public static** 纯函数 |
| `STATE_NEW = 0` | **新增 public static final** 常量 |

### 内部常量 → config 字段映射
- `W[]` → `config.weights()`
- `DESIRED_RETENTION` → `config.desiredRetention()`
- `LEARNING_STEPS_COUNT` → `config.learningSteps().length`
- `RELEARNING_STEPS_COUNT` → `config.relearningSteps().length`
- `LEARNING_STEP_1_SECONDS/LEARNING_STEP_2_SECONDS` → `config.learningSteps()[step].getSeconds()`
- `RELEARNING_STEP_SECONDS` → `config.relearningSteps()[step].getSeconds()`
- `MAXIMUM_INTERVAL` → `config.maximumInterval()`
- `enableFuzz` 判断 → `config.enableFuzz()`
- `sameDay` 短时稳定性 → `config.enableShortTerm()`
- `DECAY`/`FACTOR` → 从 `config.weights()[20]` 和 `config.desiredRetention()` 动态计算

### learningStepDuration / relearningStepDuration 重构
- 从硬编码 switch → 从 `config.learningSteps()` 数组按索引取值
- 越界兜底取最后一步

### 移除所有向后兼容层
- 不留任何 static 重载
- 所有调用方（ReviewService）同步改为实例调用

### 测试适配
- `FsrsSchedulerTest` 12 个现有测试：改为 `new FsrsScheduler(FsrsSchedulerConfig.defaults()).repeat(...)` 调用
- 新增测试：learningSteps=空 → 新卡直接毕业；relearningSteps=空 → Again 不进入 Relearning；不同 desiredRetention → 不同间隔

## 依赖
Issue 01（需要 FsrsSchedulerConfig 才能构造）

## 验证
- `mvn test` 通过（12+ 测试通过）
- 使用默认 config 的 Scheduler 产生的间隔与重构前完全一致（回归验证）
