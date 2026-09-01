# Conventions — 设计理念与编码规则

> 面向人类开发者的编码手册。AI 代理操作指南见 [AGENTS.md](../AGENTS.md)，领域术语见 [CONTEXT.md](../CONTEXT.md)，架构决策见 [architecture.md](architecture.md)。

---

## 一、核心设计理念

### 1.1 双核架构：StateGraph + Service

系统采用 **LangGraph StateGraph + Service 层** 双层架构：

```
┌─────────────────────────────────────────────┐
│  Service 层 (TurnProcessor, SessionComplete) │  ← 编排、流式、并行
│  管理会话生命周期、流式输出、异步任务编排       │
├─────────────────────────────────────────────┤
│  LangGraph 层 (ChatState, CorrectionNode)    │  ← 状态容器、纠错
│  仅处理单轮纠错（1 个节点），利用 Checkpoint   │
│  实现状态持久化与恢复                           │
└─────────────────────────────────────────────┘
```

**为什么不是纯图架构？** 对话流式生成（token-by-token 推送到 WebSocket）不适合 StateGraph 的同步节点模型。将流式对话提取到 Service 层后，图只负责状态管理和纠错，两者各司其职。

### 1.2 深模块哲学

遵循 John Ousterhout 的"深模块"原则：**接口简单，实现复杂**。

| 深模块 | 接口 | 封装内容 |
|--------|------|---------|
| `SessionComplete` | `complete(sessionId, messages, corrections, userId, mode)` → `ReportResult` | 报告生成、持久化、三条异步管线（LearningProfile + Assertion + MemoryCue）、降级处理 |
| `LlmReqConstructor` | `register(name, definition)` + `execute(name, params, ctx)` → 结果 | 模板拆分、消息组装、模型选择、LLM 调用、日志写入、错误策略 |
| `TurnProcessor` | `processTurn(sessionId, input, msgId, callback)` | 记忆注入、并行流式对话+纠错、回调通知 |

**原则**：调用方不应该知道"报告失败时返回降级报告"、"detectSwitches 在两个 Service 之间共享"——这些是模块内部的事。

### 1.3 双管线记忆系统

写入端并行运行 **MemoryCue + MemoryAssertion**：

- **MemoryCue**：维持 RAG 检索端（ConversationAgent 在 Round 2+ 注入历史记忆）
- **MemoryAssertion**：提供结构化去重与演化追踪（Extractor → Manager → lineage DAG）

共享一次 `detectSwitches` LLM 调用（`SessionComplete` 内部），消除重复成本。两者互不依赖——一个崩溃不影响另一个。

### 1.4 LLM 调用统一入口

所有非流式 LLM 调用**必须**通过 `LlmReqConstructor`，不得直接调用 `ChatLanguageModel`。

**设计原因**：
- Prompt 模板采用子目录结构：每个 Task 目录下 `system.txt`（系统提示词）+ 可选的 `examples.txt`（few-shot 示例），`userTemplate` 内联为 Java 字面量
- 对话数据统一使用 XML 格式（`<turn role="user">...</turn>`），由 `ExampleMsgFormatter.toXml()` 生成
- 日志自动记录完整字段（systemPrompt / chatHistory / inputTokens / outputTokens）
- 统一错误策略和模型路由

---

## 二、分层规范

```
┌──────────────────────────────────────────┐
│  WebSocket / REST Controller             │  ← 协议解析、认证
├──────────────────────────────────────────┤
│  Service                                 │  ← 业务逻辑、编排
├──────────────────────────────────────────┤
│  Repository (Spring Data JPA)            │  ← 数据访问
├──────────────────────────────────────────┤
│  Model (JPA Entity)                      │  ← 数据定义
└──────────────────────────────────────────┘
```

### 2.1 调用方向

- **单向依赖**：上层可调用下层，下层**禁止**引用上层
- Controller 不直接调用 Repository（必须经过 Service）
- Service 之间可相互调用（`SessionComplete` 编排 `AssertionService`、`MemoryCueService`）
- Agent 类（`agent/` 包）属于 Service 层——通过 `LlmReqConstructor` 调用 LLM，不直接访问数据库

### 2.2 DTO 边界

- `dto/` 包使用 Java `record`，仅用于跨层数据传输
- Controller 接收/返回 DTO，Service 内部使用 Entity
- `EntityMapper` 负责 Entity ↔ DTO 转换
- **禁止** Entity 泄露到 Controller 层、**禁止** DTO 出现在 Repository 层

### 2.3 状态隔离

- `ChatState` 是 `SessionService` 的**内部实现细节**
- `ChatMessageHandler`、`ReportAgent` 等**永不**直接导入 `ChatState`
- 跨线程获取 userId：通过 `SessionService.getUserId(sessionId)` 从 ChatState 读取（非 ThreadLocal）

---

## 三、数据层规范

### 3.1 主键策略

**统一使用 UUID 字符串**（`@GeneratedValue(strategy = GenerationType.UUID)`）。不使用自增 ID。

**原因**：UUID 天然支持分布式扩展（未来可能迁移到多实例），避免自增 ID 的合并冲突。H2 的 UUID 生成效率足够（单机场景）。

### 3.2 数据隔离

- 所有用户数据表包含 `user_id` 列（NOT NULL）
- 跨 Session 查询**必须**加 `userId` 过滤：`findByUserIdAndXxx(userId, ...)`
- 单 Session 查询通过 `sessionId`（UUID）天然隔离，无需额外过滤
- Repository 方法命名遵循 Spring Data 规范：`findByUserIdAndMode`、`findBySessionId`

### 3.3 软删除

需要保留历史记录的表使用 `enabled` 字段（BOOLEAN, DEFAULT true）：

| 表 | 删除方式 | 原因 |
|----|---------|------|
| `memory_assertions` | `enabled = false` | 保留演化链（AssertionLineage 引用） |
| `users` | `enabled = false` | 保留学习数据引用完整性 |

**原则**：有关联引用 → 软删除；纯审计/日志表 → 硬删除或定期清理。

### 3.4 审计字段

需要追踪创建/更新时间的实体继承 `BaseEntity`（提供 `createdAt`、`updatedAt`），通过 JPA `@EntityListeners(AuditingEntityListener.class)` 自动填充。

### 3.5 唯一性约束

- 用户级唯一性：`(userId, field)` 组合唯一（如 `card (userId, front, tag)` 组合）
- 全局唯一性：单列 `@Column(unique = true)`（如 `session_id`、`username`）

---

## 四、错误处理

### 4.1 ErrorStrategy 矩阵

| 策略 | 含义 | 使用场景 |
|------|------|---------|
| `SWALLOW` | 静默失败，返回 null，不阻断流程 | 非关键 Agent（Correction、Report、Learning、MemoryCue）——一个 Agent 失败不应中断整个会话 |
| `THROW` | 抛异常，中断管线 | 关键流程（AssertionService 的全部 4 个 Task）——部分失败导致的数据库不一致比完全跳过更危险 |

### 4.2 降级哨兵值

| 哨兵值 | 含义 | 处理 |
|--------|------|------|
| `fluencyScore = -1` | Report LLM 调用失败 | 前端条件渲染，隐藏评分行 |
| `null`（SWALLOW 返回） | Agent 调用失败 | 调用方检查 null，跳过后续处理 |

### 4.3 Null 防护

- LangChain4j 在网络错误时可能回调 `onCompleteResponse(null)`——**必须**在使用 `response` 前检查 null
- `TurnProcessor.onCompleteResponse` 已做防护，新增类似回调点需遵循相同模式
- Repository 查询可能返回 `Optional.empty()`——使用 `.orElseThrow()` 或显式处理

---

## 五、异步与线程

### 5.1 线程池清单

| Bean 名称 | 用途 | core/max | 饱和策略 |
|-----------|------|----------|---------|
| `llmLogExecutor` | LLM 调用日志写入 | 2/4 | CallerRunsPolicy |
| `llmRequestExecutor` | 会话结束管线（Report + Profile + Assertion + MemoryCue） | 4/8 | CallerRunsPolicy |
| `embeddingExecutor` | ONNX 向量化（CPU 密集型） | 2/2 | CallerRunsPolicy |
| `optimizerExecutor` | FSRS 参数优化（CPU 密集型） | 2/4 | CallerRunsPolicy |

所有线程池：
- Daemon 线程（不阻止 JVM 退出）
- `allowCoreThreadTimeOut(true)`（空闲时回收核心线程）
- E2E/Test 模式下通过 `asyncEnabled=false` 切换为 `DirectExecutorService`（同步执行）

### 5.2 Fire-and-Forget 语义

会话结束时的三条异步管线（LearningProfile、Assertion、MemoryCue）使用 fire-and-forget 模式：
- 通过 `CompletableFuture.runAsync(..., llmRequestExecutor)` 提交
- **不等待**结果（会话结束时立即返回 SESSION_REPORT 给前端）
- 失败仅记录日志，不影响已完成的报告和持久化

### 5.3 WebSocket 线程安全

从异步线程向 WebSocket 发送消息时，**必须**使用 `synchronized(wsSession)`：
```java
synchronized (wsSession) {
    sendSynced(wsSession, message);
}
```
`sendSynced()` 封装 IOException，已内置同步保护。

### 5.4 共享调用模式

当一个操作被多个下游模块需要时，在编排层执行一次，结果共享：
```java
// SessionComplete.complete()
var segments = detectAndSplit(messages);  // 一次 LLM 调用
assertionService.generateAsync(segments); // 共享结果
memoryCueService.generateAsync(segments); // 共享结果
```
**禁止**各 Service 内部各自调用（会产生重复 LLM 成本）。

---

## 六、命名规范

### 6.1 包结构

```
com.hugosol.chatagent/
├── agent/           # Agent 实现类
│   └── common/      # 横切关注点（LlmReqConstructor 等）
├── config/          # Spring @Configuration
├── controller/      # @RestController
├── dto/             # Data Transfer Objects (record)
├── flashcard/       # FSRS 算法模块
├── graph/           # LangGraph 定义
│   └── nodes/       # 图节点实现
├── model/           # JPA Entity + Enum
├── protocol/        # WebSocket 协议定义
├── repository/      # Spring Data JPA Repository
├── service/         # @Service 业务逻辑
│   └── card/        # 闪卡专用 Service
├── speech/          # V2: STT/TTS
└── websocket/       # WebSocket Handler
```

### 6.2 类名后缀

| 后缀 | 含义 | 示例 |
|------|------|------|
| `*Service` | 业务逻辑服务 | `SessionService`, `AssertionService` |
| `*Controller` | REST 端点 | `FlashcardController`, `ReviewController` |
| `*Repository` | 数据访问 | `MemoryAssertionRepository` |
| `*Agent` | LLM Agent（属于 Service 层） | `CorrectionAgent`, `MemoryCueAgent` |
| `*Config` | Spring 配置 | `SecurityConfig`, `AsyncConfig` |
| `*Handler` | WebSocket 处理器 | `ChatWebSocketHandler`, `ChatMessageHandler` |
| `*Runner` | 命令行启动器 | —（`TaskRunner` 已删除，由 `LlmReqConstructor` 替代） |

### 6.3 测试命名

- 单元测试：`{ClassName}Test.java`，位于镜像源码路径的 `src/test/java/`
- E2E 集成测试：`{Feature}IT.java`，位于 `src/test/java/.../e2e/`
- 测试方法命名：英文描述，表达"给定-当-则"语义（如 `emptyConversation_producesNoAssertions`）

---

## 七、代码风格

### 7.1 不可变配置

运行时配置对象使用 Java `record`：
```java
public record FsrsSchedulerConfig(double[] w, double desiredRetention, ...) {
    public static FsrsSchedulerConfig defaults() { ... }
    public static FsrsSchedulerConfig merge(FsrsParameters p, UserPreferences u) { ... }
}
```
- 构造后不可变，线程安全
- 提供静态工厂方法（`defaults()`、`merge()`）而非多个构造器重载

### 7.2 枚举序列化

- REST API 使用枚举的 `name()` 字符串（如 `"GRAMMAR"`）
- 反序列化使用 `@JsonCreator` 做大小写不敏感匹配
- JPA 存储使用 `@Enumerated(EnumType.STRING)`

### 7.3 依赖注入

- **字段注入** (`@Autowired private XxxService service;`)：仅在 `@SpringBootTest` 测试基类中使用
- **构造器注入**（推荐）：所有生产代码使用 `public ClassName(XxxService s1, YyyService s2) { ... }`
- 不使用 `@Autowired` 在构造器上（Spring 自动识别单构造器）

### 7.4 日志

- SLF4J + Logback，通过 `LoggerFactory.getLogger(Class.class)` 获取
- LLM 调用日志通过 `LlmReqConstructor` 自动记录（不手动调用 `LlmCallLogService`）
- 管线步骤打印耗时日志（如 `"AssertionService: detectSwitches done in {}ms"`）
- 禁止在日志中打印完整 prompt（包含在 `llm_call_logs` 表中供调试）

---

## 八、测试规范

> 完整测试清单见 [tests.md](tests.md)。此处仅列核心原则。

### 8.1 Mock Seam

- 单元测试的最高 seam 是 `LlmReqConstructor`——Mock 它即可隔离所有 LLM 调用
- 集成测试的最高 seam 是 `SessionComplete`——Mock 其依赖即可验证编排逻辑
- E2E 测试使用 WireMock 模拟 DeepSeek API HTTP 响应

### 8.2 测试原则

| 测试什么 | 不测试什么 |
|---------|-----------|
| 外部可观测行为（断言是否生成、lineage 是否正确、enabled flag 是否切换） | LLM 输出内容（由 LLM 自身保证，不写死期望文本） |
| 边界条件（空输入、null、异常路径） | 实现细节（内部线程调度、串行化顺序） |
| 数据隔离（userId 过滤正确性） | 性能指标（日志观察阶段，暂不定量） |

### 8.3 数据层测试

- `@DataJpaTest` 用于 Repository 测试（自动回滚）
- 验证 SQL 查询逻辑：`enabled` 过滤、userId 隔离、递归 CTE
- 断言库：**AssertJ** (`assertThat`) 是项目标准

---

## 九、文档维护

> 完成功能开发后，按 [AGENTS.md](../AGENTS.md) 中的 **Documentation Update Checklist**（逆向查找表）逐行检查每份文档是否需要更新。该表以文档为索引，列出每份文档的触发条件和跳过场景。以下为文档职责概要——详细更新规则以 AGENTS.md 为准。

| 文档 | 面向 | 核心职责 |
|------|------|---------|
| `README.md` | 新用户 | 项目概览、快速上手 |
| `CONTEXT.md` | 开发者 | 领域术语表 |
| `docs/architecture.md` | 架构师 | 架构决策日志 |
| `docs/design-rationale.md` | 设计者 | 深层设计机制与权衡 |
| `docs/conventions.md`（本文档） | 开发者 | 编码规则与模式 |
| `docs/data-model.md` | 开发者 | 实体关系图与枚举 |
| `docs/fsrs.md` | 算法开发者 | FSRS 算法参考 |
| `docs/frontend-notes.md` | 前端开发者 | 前端实现规范 |
| `docs/tests.md` | QA/开发者 | 测试清单 |
| `AGENTS.md` | AI Agent | 操作手册 + 文档更新规则 |

### ADR 与代码的关系

- `docs/adr/` 为历史决策记录，**以代码和上述持续更新文档为准**
- 仅当 ADR 明确过期时追加过期标识
- 新增重大决策（难逆转、无上下文会困惑、涉及真实权衡）时写入 ADR
