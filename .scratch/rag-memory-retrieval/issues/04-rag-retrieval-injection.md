# 逐轮 RAG 检索与 System Prompt 注入（读取路径）

**Status:** `ready-for-agent`

## Parent

`.scratch/rag-memory-retrieval/PRD.md` — RAG-based MemoryCue Retrieval

## What to build

实现 RAG 管道的读取路径：从第 4 轮开始，每条用户消息触发对历史 MemoryCue 的语义搜索，将匹配结果注入 System Prompt。具体包括：
1. `ConversationAgent` 重构为接受 `MemoryContent` 单参数，实现 3 分支注入逻辑
2. `TurnProcessor` 新增 `EmbeddingService.search()` 编排，构建 `MemoryContent`
3. 更新 `conversation-system.txt` 提示词模板，新增 `{memoryCues}` 占位符
4. 编写单元测试和端到端验证

端到端行为：用户在会话的 Round 4+ 发送消息时，系统自动对其输入做语义搜索，检索历史 MemoryCue 中相似度 ≥ 0.6 的前 2 条匹配，将摘要以 `", as well as, "` 连接后注入 System Prompt。若未匹配到相关历史，则不注入，activeEngagement 也不触发。检索日志输出匹配的 cue、相似度分数及模式/用户过滤信息。

## Acceptance criteria

### ConversationAgent 重构
- [ ] `generateStream()` 签名变更为 `(List<MessageData> history, AgentMode mode, MemoryContent memoryContent, int messageId, StreamingChatResponseHandler handler)` — 参数数从 7 个降为 5 个
- [ ] `buildSystemContent()` 实现 3 分支注入逻辑：
  - Round 1-3（`memoryContent.topicSummary() != null`）：注入 `{topicSummary}` + `{learningProfile}` + `{activeEngagement}`，`{memoryCues}` 置空
  - Round 4+ 有 RAG 结果（`memoryContent.memoryCuesText() != null`）：注入 `{memoryCues}` + `{activeEngagement}`，`{topicSummary}`/`{learningProfile}` 置空
  - Round 4+ 无结果：所有占位符置空，不触发 activeEngagement
- [ ] `buildPromptJson()` 同步更新以匹配新签名（用于 LLM 调用日志记录）

### TurnProcessor 编排
- [ ] 注入 `EmbeddingService` 和 `AppProperties`
- [ ] `processTurn()` 中新增 `MemoryContent` 构建逻辑：
  - `messageId <= app.memory.user-memory-rounds`（默认 3）：`new MemoryContent(topicSummary, learningProfile, null)`
  - `messageId > app.memory.user-memory-rounds`：调用 `embeddingService.search(userInput, mode, userId, topK, threshold)`，结果以 `", as well as, "` 拼接 → `new MemoryContent(null, null, memoryCuesText)`
- [ ] RAG 检索结果同步记录日志：匹配的 cueId、topic、相似度分数、模式、userId

### 提示词模板
- [ ] `src/main/resources/prompts/conversation-system.txt`：在 `{topicSummary}` 和 `{learningProfile}` 之间新增 `{memoryCues}` 占位符
- [ ] `{activeEngagement}` 独立控制 — 当 topicSummary 或 memoryCuesText 任一非空时触发

### 测试
- [ ] `ConversationAgentTest`：适配 `MemoryContent` 参数；测试所有 3 分支：User Memory 分支、RAG 分支、无记忆分支；验证 activeEngagement 仅在分支 1 和 2 中出现，分支 3 不出现
- [ ] `TurnProcessorTest`：
  - `messageId=1` → 不调用 `search()`，注入 topicSummary
  - `messageId=5` → 调用 `search()`，注入 memoryCuesText
  - `messageId=5` 且 `search()` 返回空 → 不注入任何内容

### E2E 测试
- [ ] 新建 `EnglishCoachRagRetrievalIT`：使用 Playwright + WireMock 验证完整 RAG 流程
  - 会话 1：3 轮对话 + 结束会话 → MemoryCue 已生成并向量化（H2 断言）
  - 会话 2：前 3 轮注入 User Memory，第 4 轮触发 RAG 检索 → 验证 System Prompt 中包含检索到的 MemoryCue 摘要
- [ ] `EnglishCoachMemoryCueIT`：更新 `memoryCueGeneratedAtSessionEndWithTopicSwitch()` 移除 `getTags()` 断言；保留 E2E 标记匹配（WireMock）和 topic/summary 的 H2 断言

### WireMock
- [ ] 更新 `memory-cue-entry-success.json` 和 `memory-cue-entry-seg2.json`：移除 `tags` 字段
- [ ] 删除 `tag-consolidation-response.json`
- [ ] 删除 `WireMockStubs.java` 中标签合并相关的场景桩方法及注册调用
- [ ] 更新 `src/test/resources/prompts/memory-cue-entry.txt` 测试提示词以匹配 2 字段结构
- [ ] 新增 RAG 检索场景所需的 WireMock 桩（如需要新增 LLM 调用的 mock 响应）

### 构建验证
- [ ] `mvn compile` 通过
- [ ] `mvn test` 通过
- [ ] `mvn verify`（E2E）通过

## Blocked by

- `.scratch/rag-memory-retrieval/issues/02-rag-embedding-infrastructure.md`（需要 `EmbeddingService`、`MemoryContent`、`CueMatch`、配置）
- `.scratch/rag-memory-retrieval/issues/03-session-end-vectorization.md`（E2E 测试需要 MemoryCue 已向量化入库才能被检索到）
