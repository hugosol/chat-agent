# 02: FsrsParameters JPA 实体 + Repository + DataInitializer

**Status:** `ready-for-agent`

## 范围

新建 `FsrsParameters` JPA 实体、对应的 Spring Data JPA Repository、以及在 `DataInitializer` 中为新/已有用户创建默认行的逻辑。

## 实现内容

### FsrsParameters 实体
- 表名 `fsrs_parameters`，继承 BaseEntity（id UUID + createTime + updateTime）
- `userId` VARCHAR NOT NULL UNIQUE（软关联，无外键）
- `w0`—`w20` DOUBLE NOT NULL（21 列）
- `enableShortTerm` BOOLEAN NOT NULL DEFAULT true
- `getWeights()` 工具方法返回 `double[]`
- `static FsrsParameters defaults(String userId)` 工厂方法创建默认参数行

### FsrsParametersRepository
- `Optional<FsrsParameters> findByUserId(String userId)`
- 继承 JpaRepository<FsrsParameters, String>

### DataInitializer 改造
- 新增 `@Autowired FsrsParametersRepository`
- 新增 `initFsrsParameters()` 方法：遍历所有 User，对没有 FsrsParameters 的用户创建默认行
- 在 `run()` 末尾调用

### 集成测试
- 新建 DataInitializer 相关测试：验证新用户自动创建默认 FsrsParameters；已有用户补充默认行（不重复创建）

## 依赖
Issue 01（FsrsSchedulerConfig.defaults() 提供默认权重值）

## 验证
- `mvn test` 通过
- H2 Console 查看 `fsrs_parameters` 表已创建并包含默认行
