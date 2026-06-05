# 06: Caffeine 缓存集成

**Status:** `ready-for-agent`

## 范围

引入 Caffeine 缓存库，为 `FsrsSchedulerConfig` 提供 per-user 缓存，避免 `rateCard()` 每次请求反复读取 FsrsParameters + UserPreferences。

## 实现内容

### Maven 依赖
- 在 `pom.xml` 添加 `com.github.ben-manes.caffeine:caffeine`（最新稳定版，需确认与 Spring Boot 3.4.7 兼容）
- 添加 `spring-boot-starter-cache`（如果尚未引入）

### 缓存配置类
- 新建 `CacheConfig`（`@Configuration` + `@EnableCaching`）
- 定义 `CacheManager` Bean：`CaffeineCacheManager`，设置 `expireAfterAccess(24, TimeUnit.HOURS)`
- Cache name：`"fsrsConfig"`

### FsrsConfigService（新建或合并到 ReviewService）
- 方法 `@Cacheable(value = "fsrsConfig", key = "#userId")` 标注的 `getConfig(String userId)`
- 方法逻辑：读取 FsrsParametersRepository + UserPreferencesService → 调用 `FsrsSchedulerConfig.merge()` → 返回合并后的 config
- 缓存未命中：执行方法逻辑并写缓存；命中：直接返回缓存值

### ReviewService 集成
- `rateCard()` 中调用 `getConfig(userId)` 替代直接读 DB（通过 Cache 自动管理）
- 缓存对 ReviewService 透明——ReviewService 只需调用 `getConfig()`，不感知缓存细节

### 缓存失效
- UserPreferences 保存时：在 `savePreferences()` 方法上加 `@CacheEvict(value = "fsrsConfig", key = "#userId")`
- 优化器完成时（P4，此次预留）：程序化 `cacheManager.getCache("fsrsConfig").evict(userId)`，写在优化器完成回调中

### 注意
- 单用户场景下缓存最大仅 1 条目，无内存压力
- 缓存只存 `FsrsSchedulerConfig`（不可变 record），无线程安全问题

## 依赖
- Issue 01（FsrsSchedulerConfig）
- Issue 02（FsrsParameters 可读取）
- Issue 03（UserPreferences 可读取）

## 验证
- `mvn test` 通过
- 验证 Caffeine 依赖成功解析
- 验证 ApplicationContext 启动成功（CacheConfig 加载）
