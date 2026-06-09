Status: `ready-for-agent`

## What to build

将"合并缓存 `fsrsConfig`"拆解为 `userPreferences` + `fsrsParameters` 两个独立缓存，并新建 `FsrsParametersService`（与 `UserPreferencesService` 设计完全对称），消除 Review 流程中每次请求重复查库的问题。

**端到端流程**：Review 评分 → `rateCard`/`getNextCard`/`computeStats` 读取用户偏好 → 从 `userPreferences` 缓存命中（不再每次查 DB）→ 用户修改设置页时区 → `save()` 仅驱逐 `userPreferences` 缓存 → 下一个 Review 请求即时生效新时区。

**关键实现点**：

1. **新建 `FsrsParametersService`**：`get(userId)` 加 `@Cacheable(value = "userPreferences")` — 不对，应该是 `@Cacheable(value = "fsrsParameters")`。`save(params)` 加 `@CacheEvict(value = "fsrsParameters")`。
   - `FsrsOptimizeService.saveParameters()` 和 `DataInitializer` 中 `paramsRepository.save()` 改为调 `FsrsParametersService.save()`

2. **`UserPreferencesService` 加缓存**：`get(userId)` 添加 `@Cacheable(value = "userPreferences", key = "#userId")`；`save()` 的 `@CacheEvict` 从 `"fsrsConfig"` 改为 `"userPreferences"`

3. **`CacheConfig`**：缓存名列表从 `"fsrsConfig"` 改为 `"userPreferences", "fsrsParameters"`。TTL 保持 24h `expireAfterAccess`

4. **`FsrsConfigService.getConfig()`**：移除 `@Cacheable`，改为调 `fsrsParametersService.get(userId)` + `preferencesService.get(userId)` → 现场 merge → 返回 `FsrsSchedulerConfig`。merge 是纯字段复制 + 字符串解析，开销可忽略

5. **移除手动 CacheManager**：
   - `FsrsOptimizeService`：移除 `CacheManager` 注入，缓存通过 `FsrsParametersService.save()` 的 `@CacheEvict` 自动失效
   - `ReviewService`：移除 `CacheManager` 注入，`rescheduleAllCards()` 中手动 evict 删除

## Acceptance criteria

- [ ] `FsrsParametersService` 创建完成，get/save 方法与 `UserPreferencesService` 设计对称
- [ ] `CacheConfig` 注册 `"userPreferences"` 和 `"fsrsParameters"` 两个缓存
- [ ] `UserPreferencesService.get()` 带 `@Cacheable`，`save()` 的 `@CacheEvict` 指向 `"userPreferences"`
- [ ] `FsrsConfigService.getConfig()` 无 `@Cacheable`，改为调两个 Service 后现场 merge
- [ ] `FsrsOptimizeService` 和 `ReviewService` 不再注入 `CacheManager`，无手动 evict 调用
- [ ] `FsrsOptimizeService` 和 `DataInitializer` 中的参数存储路径经过 `FsrsParametersService.save()`
- [ ] `FsrsOptimizeServiceTest` 使用 `@Mock FsrsParametersService` 替代 `@Mock FsrsConfigService` + `CacheManager`
- [ ] `ReviewServiceTest` 移除 `@Mock CacheManager` + `Cache`，移除构造函数中 CacheManager 参数
- [ ] 全部测试通过（`mvn test`）

## Blocked by

None - can start immediately
