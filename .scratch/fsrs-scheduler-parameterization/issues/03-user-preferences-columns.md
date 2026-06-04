# 03: UserPreferences 新增 6 个可空字段

**Status:** `ready-for-agent`

## 范围

在现有 `UserPreferences` JPA 实体上新增 6 个 nullable 列，用于存储用户可配置的 FSRS 参数。

## 实现内容

### 新增字段（全部 nullable，包装类型）
- `learningSteps` VARCHAR nullable — 格式 `"1m,10m"`，null=默认值
- `relearningSteps` VARCHAR nullable — 格式 `"10m"`，null=默认值
- `desiredRetention` Double nullable — 0.01~0.99，null=默认 0.9
- `maximumInterval` Integer nullable — ≥1，null=默认 36500
- `enableFuzz` Boolean nullable — null=默认 true
- `shuffleDueCards` Boolean nullable — null=默认 false

### UserPreferencesService 适配
- 无需改动核心逻辑（字段为 nullable，getter 返回 null 时 merge 层处理降级）
- 保存/更新时接受新字段

### ReviewController 适配
- `PUT /api/user/preferences` 扩展接受新字段
- `GET /api/user/preferences` 返回新字段（null 时返回 null，前端自行处理默认值显示）

### 数据库迁移
- 依赖 `ddl-auto: update` 自动添加列
- 已有用户列值为 NULL → merge 层回退默认值，无需手动 UPDATE

## 依赖
无（独立 migration，不依赖其他 issue）。需要 Issue 01 的 `FsrsSchedulerConfig.merge()` 来实际使用这些字段。

## 验证
- `mvn test` 通过
- H2 Console 查看 `user_preferences` 表已包含新列
- `ReviewControllerTest` 验证 GET/PUT 端点包含新字段
