# 文档更新、数据迁移与 ADR

**Status:** `ready-for-agent`

## Parent

`.scratch/rag-memory-retrieval/PRD.md` — RAG-based MemoryCue Retrieval

## What to build

在所有代码变更完成后，更新项目文档以反映 RAG 架构、移除标签引用、添加新术语。同时执行数据库标签列的数据迁移。编写新的 RAG 架构决策记录（ADR）。

端到端行为：文档准确反映当前系统架构；数据库 `memory_cues` 表的 `tags` 列已移除；新 ADR 记录了标签→RAG 的决策过程以供未来参考。

## Acceptance criteria

### architecture.md
- [ ] 新增 RAG 架构章节：包含 EmbeddingService + InMemoryEmbeddingStore 流程图
- [ ] 新增完整数据隔离表（`userId × mode` 跨 H2、运行时和 RAG 层）
- [ ] 修正已有报告提示词描述差异：第 五 节描述的 4 项列表 → 实际提示词生成的 5 字段 JSON，补充缺失的 `topicSummary`，修正 `fluencyScore` 的描述

### CONTEXT.md
- [ ] 新增术语表条目：`MemoryCue Retrieval`（向量语义检索）、`EmbeddingService`（RAG 向量化）、`CueMatch`（搜索结果记录）、`MemoryContent`（System Prompt 注入负载）
- [ ] 移除 `Tag Consolidation` 术语

### README.md
- [ ] 更新项目结构以反映新模块：`service/EmbeddingService.java`、`dto/CueMatch.java`、`dto/MemoryContent.java`
- [ ] 记录 `app.memory.*` 配置键
- [ ] 将 ONNX 堆内存（约 200MB）列为已知关注事项

### AGENTS.md
- [ ] 更新项目结构图以包含 `service/EmbeddingService.java`、`dto/CueMatch.java`、`dto/MemoryContent.java`
- [ ] 移除标签和合并相关引用
- [ ] 更新 "Dual memory system" 条目以反映 RAG 检索

### docs/adr/dual-memory-system.md
- [ ] 将 MemoryCue 描述从标签式 `(topic, summary, tags)` 更新为 RAG 式 `(topic, summary)`
- [ ] 将 "tags for JSON_CONTAINS" 未来路径替换为 "RAG vector retrieval" 未来路径

### docs/adr/rag-memory-retrieval.md（新建）
- [ ] 记录从关键词标签切换到向量嵌入的决策理由
- [ ] 记录嵌入模型选择过程（ONNX all-MiniLM-L6-v2 vs 考虑的备选方案）
- [ ] 记录向量存储选择过程（InMemoryEmbeddingStore + JSON vs 外部向量数据库）
- [ ] 记录磁盘序列化策略及损坏恢复方案

### 数据迁移
- [ ] `DataInitializer` 或 `@PostConstruct` 方法中执行 `ALTER TABLE memory_cues DROP COLUMN IF EXISTS tags`（原生 SQL）
- [ ] Hibernate `ddl-auto=update` 继续正常工作（移除 `@Column` 和 `@Convert` 注解后，JPA 不再管理该列）

### 构建验证
- [ ] `mvn compile` 通过（文档变更不破坏构建）
- [ ] 启动应用，H2 控制台确认 `memory_cues` 表无 `tags` 列

## Blocked by

- `.scratch/rag-memory-retrieval/issues/01-remove-tags-subsystem.md`（需待标签子系统移除后才可更新文档和迁移列）
- `.scratch/rag-memory-retrieval/issues/04-rag-retrieval-injection.md`（需待 RAG 完整流程落定后才可编写最终架构文档和 ADR）
