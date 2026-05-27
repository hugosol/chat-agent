# 添加 RAG 嵌入基础设施

**Status:** `ready-for-agent`

## Parent

`.scratch/rag-memory-retrieval/PRD.md` — RAG-based MemoryCue Retrieval

## What to build

引入本地 ONNX 嵌入模型 `all-MiniLM-L6-v2`（384 维）作为 RAG 检索的向量化引擎。创建 `EmbeddingService` 深模块，封装所有 ONNX 向量操作和 `InMemoryEmbeddingStore` 管理。同时新建 `MemoryContent` 和 `CueMatch` 两个 DTO 记录，添加 `app.memory.retrieval.*` 配置项和专用 `embeddingExecutor` 线程池。

端到端行为：应用启动时加载或重建向量存储；`EmbeddingService` 提供 `search()`/`indexAsync()`/`saveToDisk()` 供后续 Issue 接入。当前阶段尚未与 MemoryCue 或 ConversationAgent 连线。

## Acceptance criteria

### Maven 依赖
- [ ] `pom.xml` 新增 `langchain4j-embeddings-all-minilm-l6-v2` 依赖

### 配置
- [ ] `AppProperties` 新增 `MemoryProperties` 内部类，包含 `userMemoryRounds`(3)、`RetrievalProperties` 内部类（`topK`=2、`similarityThreshold`=0.6）
- [ ] `application.yml` 新增 `app.memory.user-memory-rounds: 3` 及 `app.memory.retrieval.*` 配置段
- [ ] 配置可绑定（`@ConfigurationProperties`）

### EmbeddingService
- [ ] 新建 `EmbeddingService.java`，`@Service` 注解，注入 `EmbeddingModel`（ONNX all-MiniLM-L6-v2）、`InMemoryEmbeddingStore`、`MemoryCueRepository`、`AppProperties`
- [ ] `init()` — `@EventListener(ApplicationReadyEvent.class)`：若 `./data/embedding-store.json` 存在则从中加载，否则从 H2 `memory_cues` 表全量重建。增量 diff：查询 H2 中 `COMPLETED` 且 `createTime` 晚于文件时间戳的记录，仅嵌入新增项；清理孤立条目（存储中有但 H2 中已删除的）
- [ ] `indexAsync(String cueId, String topic, String summary, AgentMode mode, String userId)` — 对 `topic + " " + summary` 做 ONNX 嵌入，加入 `InMemoryEmbeddingStore`，附带元数据 `{cueId, topic, mode, userId}`，异步序列化到磁盘
- [ ] `search(String userInput, AgentMode mode, String userId, int topK, double threshold)` → `List<CueMatch>` — 嵌入用户输入，按 `mode AND userId` 过滤搜索，返回高于阈值的匹配项
- [ ] `saveToDisk()` — `@PreDestroy`：同步将 `InMemoryEmbeddingStore` 序列化为 `./data/embedding-store.json` 的 JSON 文件

### 错误处理
- [ ] ONNX 模型在启动时加载失败 → 应用启动失败（fail fast），附带明确的错误信息
- [ ] 磁盘 JSON 文件损坏 → 日志警告，回退到从 H2 全量重建
- [ ] `indexAsync()` 中 ONNX 失败 → 日志警告，跳过（下次启动时通过 H2 diff 重试）
- [ ] `saveToDisk()` 中序列化失败 → 记录完整堆栈，向上传播异常

### DTO
- [ ] 新建 `dto/MemoryContent.java` — record：`String topicSummary, String learningProfile, String memoryCuesText, boolean isEmpty()`
- [ ] 新建 `dto/CueMatch.java` — record：`String cueId, String topic, String summary, double score`

### 线程池
- [ ] `AsyncConfig` 新增 `embeddingExecutor` bean（core=2, max=2），专门用于 ONNX CPU 密集嵌入操作
- [ ] 支持 `app.memory.async=false` 的 `DirectExecutorService` 回退（用于 E2E 测试配置）

### 仓库
- [ ] `MemoryCueRepository` 新增 `findAllByStatus(MemoryCueStatus status)` 方法

### 测试
- [ ] 新建 `EmbeddingServiceTest`
  - `init()` 磁盘为空 → 从 H2 构建
  - `init()` 已有磁盘文件 → 加载但不重新嵌入
  - `init()` 磁盘文件损坏 → 从 H2 重建
  - `indexAsync()` → 存储包含正确元数据和文本的条目
  - `search()` 匹配查询 → 返回高于阈值的 top 结果
  - `search()` 不匹配查询 → 返回空列表
  - `search()` 模式错误 → 返回空列表（模式隔离）
  - `search()` userId 错误 → 返回空列表（用户隔离）

### 构建验证
- [ ] `mvn compile` 通过
- [ ] `mvn test` 通过（`EmbeddingServiceTest` 包含 ONNX 模型加载）

## Blocked by

None — 可立即开始（可与 Issue 1 并行）
