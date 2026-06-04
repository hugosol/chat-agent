# 07: 测试适配与新增

**Status:** `ready-for-agent`

## 范围

适配所有受改造影响的现有测试，新增覆盖新功能的测试。

## 实现内容

### 现有测试适配

| 测试文件 | 改动 |
|---------|------|
| `FsrsSchedulerTest` (12 tests) | 添加 `FsrsScheduler scheduler = new FsrsScheduler(FsrsSchedulerConfig.defaults())` 初始化；所有 `FsrsScheduler.repeat()` → `scheduler.repeat()`；`FsrsScheduler.initNewCard()` → `scheduler.initNewCard()` |
| `ReviewServiceTest` | mock `FsrsParametersRepository`；mock `UserPreferencesService` 返回含新字段的 UserPreferences；适配 `rateCard` 新调用方式 |
| `FlashcardServiceTest` | 验证 `createInitState()` 静态调用不变；无其他改动 |
| `CardBatchServiceTest` | 同上 |
| `ReviewControllerTest` | 验证 `PUT /api/user/preferences` 接受新字段；`GET /api/user/preferences` 返回新字段 |
| `CardBatchServiceTest` / `FlashcardServiceTest` | 确认 `createInitState()` 静态调用仍然编译通过 |

### 新增测试

| 测试文件（新建） | 覆盖内容 |
|-----------------|---------|
| `FsrsSchedulerConfigTest` | merge 全部 null→默认 / 部分覆盖 / FsrsParameters null→权重默认 / desiredRetention 越界 / parseSteps 正常+空串+非法 |
| `FsrsParametersRepositoryTest` | `findByUserId` 查询 / save 后读取 |
| DataInitializer 测试（可追加到现有测试类） | 验证新用户创建 FsrsParameters 默认行 / 已有用户补充默认行不重复 |

### 回归验证
- 用 `FsrsSchedulerConfig.defaults()` 构造的 Scheduler，`repeat()` 输出与重构前逐 bit 一致
- `mvn test` 全部通过
- `mvn verify` E2E 全部通过（FlashcardBatchIT / FlashcardIT）

## 依赖
所有前置 Issue（01-06）完成后才执行测试适配。

## 验证
- `mvn test` 全部通过
- `mvn verify` E2E 全部通过
