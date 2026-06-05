# 02: 测试补充

**Status:** `ready-for-agent`

## 范围

为 reschedule 功能补充单元测试和集成测试，同步处理 initNewCard→enchantCard 命名变更的影响。

## 实现内容

### FsrsSchedulerTest 追加（5 个新测试）

| 测试 | 数据 | 断言 |
|------|------|------|
| `reschedule_emptyLogs_returnsCreateInitState` | 空 List | state=0, stability=2.5, difficulty=0 |
| `reschedule_singleReview_updatesState` | 1 条 rating=Good | state≠New, stability>0 |
| `reschedule_multipleReviews_accumulatesState` | 10 条混合评分（Again/Good） | 稳定性 > 单条 review 的稳定性 |
| `reschedule_deterministic_noFuzz` | 固定 5 条日志 | 调用两次 → `Arrays.equals(run1.due(), run2.due())` |
| `reschedule_differentConfig_producesDifferentDue` | 相同日志，不同 desiredRetention config | 最终 due 不同（高 retention → 短间隔） |

### ReviewServiceTest 追加（2 个新测试）

| 测试 | mock | 断言 |
|------|------|------|
| `rescheduleAllCards_processesCardsWithReviewLogs` | mock cardRepository: 3 卡有日志；mock reviewLogRepository: 每卡 5 条日志 | 验证 `cardRepository.saveAll` 被调用，参数含 3 张更新后的卡片 |
| `rescheduleAllCards_skipsCardsWithoutReviewLogs` | mock cardRepository: 2 卡无日志 | 验证 saveAll 参数不含这 2 张卡（或参数为空 list） |

### 命名变更适配（无新增，纯改名）

| 文件 | 改动 |
|------|------|
| `FsrsSchedulerTest` | `initNewCard` → `enchantCard`（12 个测试中的调用） |
| `ReviewServiceTest` | rateCard 相关测试中不变（rateCard 通过 service 调用 Scheduler，不直接调 initNewCard） |
| 其他测试 | 任何直接调用 `FsrsScheduler.initNewCard()` 的地方改为 `enchantCard()` |

### 不受影响的测试（确认通过）

| 测试类 | 影响 |
|--------|------|
| `FsrsSchedulerTest`（12 个） | 纯重命名 `initNewCard→enchantCard`，断言不变 |
| `ReviewServiceTest`（23 个） | 零影响——reschedule 是新增方法 |
| `ReviewIT`（9 个） | 零影响——后台行为 |
| `ManagePageIT`、`FlashcardIT` 等 | 零影响 |

### Prior art
- `FsrsSchedulerTest` 已有 repeat() 系列测试——reschedule 测试复用相同的 CardState 构造和断言模式

## 依赖
- Issue 01（reschedule 方法已实现）
- P0 全部完成（Scheduler 实例化）

## 验证
- `mvn test` 全部通过（含新增 7 个测试 + 命名变更）
