# 03: 文件日志 + 日志级别清理

**Status:** `ready-for-agent`

## Parent

`.scratch/llm-logging/PRD.md` — LLM 调用日志 + 文件日志

## What to build

为 local 开发环境添加文件日志输出，并清理控制台刷屏的 INFO 日志。

新建 `src/main/resources/logback-spring.xml` 配置文件，定义两个 appender：

- **Console appender**（全局生效）：INFO 级别，`%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n`
- **File appender**（`<springProfile name="local">` 包裹）：DEBUG 级别，路径 `./logs/english-coach.%d{yyyy-MM-dd}.log`，使用 `TimeBasedRollingPolicy` 按天滚动，`maxHistory=3`，pattern `%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n`

同时在 Java 代码中将 `ReportAgent` 和 `MemoryAgent` 中完整的 prompt/response 打印从 `log.info` 降级为 `log.debug`（各 4 处，共 8 处）。生产环境和 E2E 环境不会激活 file appender（`springProfile` 限定），不影响现有行为。

## Acceptance criteria

- [ ] `src/main/resources/logback-spring.xml` 存在，Console appender 全局生效（INFO），File appender 仅 local profile 激活（DEBUG）
- [ ] File appender 路径为 `./logs/english-coach.%d{yyyy-MM-dd}.log`，`maxHistory=3`
- [ ] `ReportAgent` 中所有 `log.info`（含 prompt/response 内容）改为 `log.debug`（4 处）
- [ ] `MemoryAgent` 中所有 `log.info`（含 prompt/response 内容）改为 `log.debug`（4 处）
- [ ] `mvn spring-boot:run -Dspring-boot.run.profiles=local` 启动后，控制台仅显示 INFO 及以上，`./logs/` 目录下生成按天命名的日志文件且包含 DEBUG 级别内容
- [ ] `mvn spring-boot:run`（默认 profile 无 local）启动后，不生成 `./logs/` 目录和文件日志
- [ ] `mvn test` 全部通过
- [ ] `mvn verify` 全部通过（E2E 使用 e2e profile，不激活 local file appender）

## Blocked by

None — 可立即开始（与 01/02 完全解耦）
