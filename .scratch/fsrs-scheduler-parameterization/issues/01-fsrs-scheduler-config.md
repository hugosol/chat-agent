# 01: FsrsSchedulerConfig Record + parseSteps

**Status:** `ready-for-agent`

## 范围

新建 `FsrsSchedulerConfig` record，作为 FSRS 所有调度参数的不可变运行时载体。

## 实现内容

### 新建 FsrsSchedulerConfig record
- 字段：`weights(double[])`, `desiredRetention(double)`, `learningSteps(Duration[])`, `relearningSteps(Duration[])`, `maximumInterval(int)`, `enableFuzz(boolean)`, `enableShortTerm(boolean)`
- 静态工厂 `defaults()` — 返回 FSRS-6 标准默认值（当前硬编码在 FsrsScheduler 中的值）
- 静态方法 `merge(FsrsParameters params, UserPreferences prefs)` — 合并规则见 PRD Implementation Decisions 第 1 条。每个参数：UserPreferences 非 null→用用户值，null→用 defaults() 值。FsrsParameters 为 null→权重用 defaults()

### 新建 parseSteps(String) 静态方法
- 解析 `"1m,10m"` 格式为 `Duration[]`
- 支持单位：s(秒), m(分), h(时), d(天)
- 空串或 null → 空 Duration[]
- 非法格式（如 "abc"）→ LOG.warn + 返回 defaults() 值（分层防御）

### 单元测试（FsrsSchedulerConfigTest 新建）
- merge：UserPreferences 全部 null → 返回全默认值
- merge：UserPreferences 部分设置 → 设置项覆盖，未设置项默认
- merge：FsrsParameters 为 null → 权重数组用默认值
- merge：desiredRetention 越界 → clamp 或降级
- parseSteps："1m,10m" → 2 Duration
- parseSteps："30s" → 1 Duration
- parseSteps："" → 空数组
- parseSteps："abc" → 降级到默认值 + LOG.warn

## 依赖
无。独立新建文件。

## 验证
`mvn test` 通过新加的 FsrsSchedulerConfigTest。
