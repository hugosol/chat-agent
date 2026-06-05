# PRD: FSRS Scheduler 参数化改造（P0 — 底层重构）

**Status:** `ready-for-agent`

## Problem Statement

当前 `FsrsScheduler` 是一个纯静态工具类，所有 FSRS-6 参数硬编码为 `static final` 常量：W[21] 权重数组、desired retention (0.9)、learning steps (60s→600s)、relearning steps (600s)、maximum interval (36500天)、fuzz 开关、short-term stability 行为。每个 Learner 被迫使用完全相同的调度参数。

Learner 无法根据自身学习偏好调整复习节奏——有的人想间隔短一点记得更牢，有的人想间隔长一点减少复习量。同时，后续规划中的 FSRS 优化器（根据复习历史自动计算最优 W[21] 参数）也无法工作，因为没有 per-Learner 的参数存储和运行时配置注入点。

## Solution

将 `FsrsScheduler` 从静态工具类重构为可配置的实例类，引入双层配置架构：系统管理参数（`FsrsParameters` JPA 实体，存储 W[21]）和用户偏好参数（`UserPreferences` 扩展字段，存储学习步/目标正确率/洗牌开关等），通过 `FsrsSchedulerConfig` record 在运行时合并为单一配置对象。Scheduler 实例由 Caffeine 缓存管理（24h TTL），避免高频 `rateCard` 请求反复读库。

同时新增到期卡片随机排序（洗牌）功能，让复习体验不那么机械重复。

## User Stories

1. 作为一名 Learner，我希望复习新卡片时的学习步间隔可以自定义（如"1分钟→10分钟"或"只一步30分钟"），以适应我自己的学习节奏。
2. 作为一名 Learner，我希望遗忘卡片后的重学步间隔可以自定义（如"10分钟"或"5分钟→30分钟"），以控制我重新掌握卡片的节奏。
3. 作为一名 Learner，我希望设置目标正确回忆率（desired retention），比如 95% 表示我希望记得更牢（复习更多），85% 表示我接受偶尔遗忘（复习更少）。
4. 作为一名 Learner，我可以设置卡片复习的最大间隔上限（如不超过 365 天），超过这个天数的卡片不会推得更远。
5. 作为一名 Learner，我可以关闭间隔 fuzz（小随机扰动），获得精确的到期时间而不是"大约 X 天"。
6. 作为一名 Learner，当多张卡片同时到期时，我希望可以打乱它们的出现顺序（洗牌），而不是总是按最早到期的顺序机械呈现。
7. 作为一名 Learner，即使我从未配置过上述任何偏好，系统也会使用社区标准的 FSRS-6 默认参数，我的复习体验不受影响。
8. 作为一名开发者，W[21] 参数存储在独立的 `FsrsParameters` 表中，不与用户偏好混在一起，后续优化器可以直接写入最优参数。
9. 作为一名开发者，`FsrsScheduler` 现在是可配置的实例类，接受 `FsrsSchedulerConfig` 作为构造参数，测试时可以传入任意参数验证调度逻辑的正确性。
10. 作为一名开发者，`ReviewService.rateCard()` 每次调用时自动读取 FsrsParameters + UserPreferences，合并为 `FsrsSchedulerConfig` 后通过 Caffeine 缓存管理，避免高频请求反复查库。
11. 作为一名开发者，已有用户的数据库在服务启动时自动补充默认 FsrsParameters 行（DataInitializer），升级过程无需手动迁移脚本。
12. 作为一名开发者，`createInitState()` 保持为静态方法（不依赖任何配置参数），`FlashcardService` 和 `CardBatchService` 的闪卡创建流程不受影响。
13. 作为一名开发者，洗牌查询使用数据库原生的 `ORDER BY RAND()`（H2），当多张卡片同时到期时只返回一张随机卡片，不将全量卡片加载到内存。

## Implementation Decisions

### 1. 双层配置架构（FsrsParameters + UserPreferences）

FSRS 调度参数分为系统管理（非用户可见）和用户可配两类：

| 参数 | 存储位置 | 类型 | 默认值 | 用户可配 |
|------|---------|------|--------|---------|
| W[0]–W[20] | FsrsParameters.w0–w20 | DOUBLE | FSRS-6 社区默认 | 否（优化器写入） |
| enable_short_term | FsrsParameters.enableShortTerm | BOOLEAN | true | 否（算法内部） |
| learning_steps | UserPreferences.learningSteps | VARCHAR nullable | `"1m,10m"` | 是 |
| relearning_steps | UserPreferences.relearningSteps | VARCHAR nullable | `"10m"` | 是 |
| desired_retention | UserPreferences.desiredRetention | DOUBLE nullable | 0.9 | 是 |
| maximum_interval | UserPreferences.maximumInterval | INTEGER nullable | 36500 | 是 |
| enable_fuzz | UserPreferences.enableFuzz | BOOLEAN nullable | true | 是 |
| shuffle_due_cards | UserPreferences.shuffleDueCards | BOOLEAN nullable | false | 是 |

UserPreferences 新字段均为 `nullable`。null 值在合并时回退到 `FsrsSchedulerConfig.defaults()` 中的默认值。这避免了对已有用户的强制数据迁移——没有设置过偏好的用户自动获得默认行为。

### 2. FsrsSchedulerConfig — 运行时合并 Record

引入不可变 record 作为唯一的运行时配置载体：

```
FsrsSchedulerConfig(
    double[] weights,           // W[0]..W[20]
    double desiredRetention,    // 0.01~0.99
    Duration[] learningSteps,   // 如 [PT1M, PT10M]
    Duration[] relearningSteps, // 如 [PT10M]
    int maximumInterval,        // 天, ≥1
    boolean enableFuzz,
    boolean enableShortTerm
)
```

- `defaults()` — 静态工厂返回 FSRS-6 标准默认值
- `merge(FsrsParameters, UserPreferences)` — 静态合并方法：对每个参数，UserPreferences 非 null 则用用户值，否则用系统默认。FsrsParameters 为 null 时（新用户未建行）权重数组使用默认值。merge 执行分层防御：保存时 API 层校验，merge 时对非法值静默降级 + LOG.warn
- `parseSteps(String)` — 解析 `"1m,10m"` 格式简写字符串为 Duration[]。支持单位 s/m/h/d（秒/分/时/天）。空串返回空数组，表示跳过 Learning/Relearning 状态直接进入 Review

### 3. FsrsScheduler 重构：静态 → 实例

改造前后对比：

| 方法 | 改造前 | 改造后 |
|------|--------|--------|
| `createInitState(now)` | static | **保持 static**（不依赖任何配置） |
| `initNewCard(now)` | static | **实例方法**（依赖 learningSteps.length） |
| `repeat(card, rating, now, fuzz)` | static | **实例方法**（依赖全部配置） |
| `retrievability(card, now)` | private static | **public 实例方法**（依赖 W[20]） |
| `forgettingCurve(elapsed, stability, decay)` | 不存在 | **public static**（纯数学函数） |
| `STATE_NEW=0` | 不存在 | **新增常量** |

不带向后兼容静态重载。所有调用方（ReviewService）同步改造。

### 4. FsrsParameters JPA 实体

新建 `fsrs_parameters` 表，与 Learner 通过 `userId` 软关联（无外键，与项目现有 Card、ReviewLog、UserPreferences 模式一致）：

- `id` VARCHAR UUID (PK, BaseEntity)
- `userId` VARCHAR NOT NULL UNIQUE
- `w0`—`w20` DOUBLE NOT NULL（21 列，FSRS-6 权重）
- `enable_short_term` BOOLEAN NOT NULL DEFAULT true
- `create_time` / `update_time` TIMESTAMP (BaseEntity)

DataInitializer 在启动时扫描所有 User，为没有 FsrsParameters 行的用户创建默认行。创建时机：启动时一次性批量补充，不做懒加载。

### 5. Caffeine 缓存

`rateCard()` 是最高频请求。为避免每次请求反复读 FsrsParameters + UserPreferences，使用 Caffeine Cache 缓存合并后的 `FsrsSchedulerConfig`：

- 缓存 key：`userId`
- 过期策略：`expireAfterAccess(24, TimeUnit.HOURS)` — 复习期间常驻，闲置 24 小时后过期
- 失效策略：UserPreferences 保存时 `@CacheEvict`；优化器完成时程序化 `cache.evict(userId)`
- 缓存未命中时：从 DB 读取 FsrsParameters + UserPreferences，调用 `FsrsSchedulerConfig.merge()`，写入缓存

### 6. 洗牌功能

当 `UserPreferences.shuffleDueCards = true` 时，所有 Review Mode 的到期卡片以随机顺序呈现：

| Mode | 改造前 | 改造后（洗牌启用） |
|------|--------|-------------------|
| STANDARD | due 按 ascending due 取第一张 | due 随机取一张；new 随机取一张 |
| REVIEW_ONLY | due 按 ascending due 取第一张 | due 随机取一张 |
| NEW_ONLY | new 按任意顺序取第一张 | new 随机取一张 |
| CRAM | 已随机 | 不变 |

实现方式：CardRepository 新增原生查询 `@Query(... ORDER BY RAND() LIMIT 1, nativeQuery=true)`，直接由数据库随机返回一张卡片，不加载全量到内存。未来迁移到 PostgreSQL 时仅需将 `RAND()` 改为 `RANDOM()`。

### 7. ReviewService 适配

`rateCard()` 改造后的流程：
```
1. 从 CardRepository 读取 Card
2. 从 Caffeine 缓存获取 FsrsSchedulerConfig (userId key)
   → 未命中: 读 FsrsParameters + UserPreferences → merge → 写缓存
3. 判断首次复习（firstReviewDate == null）
4. 构建 CardState：新卡(cardState==0) 用 initNewCard()，已有卡用 CardState 字段构造
5. new FsrsScheduler(config).repeat(cardState, rating, now, AleaPrng::next)
6. 更新 Card 字段(stability/difficulty/cardState/due/step/reps/lapses/lastReview)
7. 如果是首次复习 → 设置 firstReviewDate
8. 保存 Card
9. 创建 ReviewLog（含 before/after 快照）
10. 保存 ReviewLog
```

### 8. 学习步格式与解析

- 存储格式：`"1m,10m"` — 逗号分隔的简写字符串，单位 s=秒, m=分钟, h=小时, d=天
- 空字符串或 null → 空 Duration[] → Scheduler 行为：Learning 卡片直接毕业进入 Review，Review 卡片评 Again 不进入 Relearning
- 解析失败（非法格式）→ LOG.warn + 回退默认值（分层防御：API 保存时校验拦截，merge 时兜底降级）
- `FsrsScheduler` 内部的 `learningStepDuration(int step)` 改为从 `config.learningSteps()` 数组按索引取值（越界兜底取最后一步）

### 9. 测试策略

测试接缝和优先级：

- **单元测试（FsrsSchedulerConfigTest）**：merge 逻辑（null 降级、自定义覆盖）、parseSteps（正常格式、空串、非法格式降级）、defaults 验证
- **单元测试（FsrsSchedulerTest 改造）**：现有 12 个测试适配实例调用；新增不同 config 下的间隔差异验证（learningSteps=0步、relearningSteps=空）
- **集成测试（ReviewServiceTest 改造）**：mock FsrsParametersRepository + UserPreferencesService；验证 rateCard 使用正确合并的 config；验证洗牌时调用正确的 Repository 方法
- **集成测试（DataInitializerTest 新增）**：验证新用户自动创建默认 FsrsParameters；已有用户补充默认行
- **集成测试（ReviewControllerTest）**：验证 REST 端点响应不变（向后兼容）
- **E2E 测试**：`FlashcardBatchIT` 和 `FlashcardIT` 无影响（`createInitState()` 保持静态）

## Testing Decisions

### What makes a good test
- 测试外部行为而非实现细节。例如：验证传入不同 desiredRetention 的 Scheduler 产生不同间隔，而非验证某个内部变量被赋值
- 纯数学逻辑（FsrsScheduler.repeat、FsrsSchedulerConfig.merge）用单元测试，模拟依赖
- 涉及 DB 读写的逻辑（ReviewService.rateCard、DataInitializer）用集成测试，mock Repository
- 端到端体验用 Playwright E2E 测试

### Prior art
- `FsrsSchedulerTest`（12 个单元测试）— 改造为实例调用，保持同样的测试向量
- `ReviewServiceTest` — mock 驱动的集成测试，新增 mock FsrsParametersRepository
- `CardBatchServiceTest` / `FlashcardServiceTest` — 验证 `createInitState()` 保持为快速路径（静态调用可以工作）

### New tests
- `FsrsSchedulerConfigTest` — merge() 和 parseSteps() 的完整边界覆盖
- DataInitializer 集成测试 — 验证 FsrsParameters 默认行自动创建

## Out of Scope

以下内容不在本次 P0 范围内，属于后续批次：

- **P1 — 前端学习步设置 UI**：Learner 在管理页面或设置面板中修改 learning_steps/relearning_steps/desired_retention 的界面交互。P0 仅打通后端到数据库的全链路（UserPreferences 字段存在、API 可读写）
- **P2 — repeat() 预览功能**：在用户评分前展示四种评分对应的卡片状态。P0 仅公开单次 repeat()
- **P2 — next_state/next_interval 公开**：独立的状态机步骤方法
- **P2 — JSON 序列化**：Card/ReviewLog/FsrsParameters 的 to_json/from_json
- **P3 — rollback/forget 历史操作**：撤销复习和重置卡片
- **P3 — reschedule 基础版**：从复习历史重建卡片状态
- **P4 — FSRS 优化器**：基于 ReviewLog 的 W[21] 参数优化（Nelder-Mead + Commons Math3）、最优 retention 计算、学习步推荐、优化后自动 reschedule
- **P4 — Caffeine + Spring Cache 的完整生产配置**：当前仅引入 Cache 管理缓存，不做分布式缓存或持久化缓存

## Further Notes

### ADRs 关联
- `docs/adr/fsrs-optimizer-pure-java.md` — 优化器技术选型（P4 依据）
- `docs/adr/scheduler-config-two-layer.md` — 双层配置架构决策（P0 核心）
- `docs/adr/scheduler-static-to-instance.md` — 静态到实例改造决策（P0 核心）

### CONTEXT.md 更新
- 新增术语：FsrsSchedulerConfig、FsrsParameters、Learning step、Relearning step、Desired retention、Maximum interval、Fuzz、Short-term stability、Shuffle due cards、FSRS Optimizer、Reschedule
- 修正术语：FSRS-6（去掉"未通过 REST 暴露"）、Review（补充个性化调度）、Review Mode（补充洗牌说明）

### 依赖关系
P0 是所有后续 FSRS 工作的基础。没有 P0，P1（前端 UI）无法保存用户配置，P4（优化器）无处写入最优参数。

### 风险
- Caffeine 缓存引入新依赖，需确认 `pom.xml` 版本兼容性
- FsrsScheduler 全改实例方法会波及 3 个 Service + 12 个测试文件，改动面较大
- 洗牌查询 `ORDER BY RAND()` 绑定 H2 方言，未来换数据库需改 SQL
- UserPreferences 新增 6 列 + FsrsParameters 新增 22 列表，`ddl-auto: update` 对 H2 行为需验证
