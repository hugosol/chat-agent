# PRD: Memory Assertion Extraction — 结构化断言存储

> Status: `ready-for-agent`
> Created: 2026-06-26

---

## Problem Statement

当前记忆系统以 MemoryCue 作为最小存储单元，每条 MemoryCue 是一个 (topic, summary) 对，其中 summary 是一个自由文本 CLOB，覆盖一个对话话题段内的**所有被讨论事实**。

这导致三个问题：

1. **无法合并类似记忆。** Learner 在两个会话中讨论同一件事（如 "卡在 OAuth 配置" → "OAuth 已用 Keycloak 解决"），系统产生两条独立 MemoryCue。检索时两条可能同时注入 system prompt，导致 Agent 看到矛盾信息。

2. **无法更新记忆。** 一个事实发生变化时，无法字段级 UPDATE——只能追加新行。旧信息和新信息在检索端共存，LLM 需要自行判断哪个是"当前状态"。

3. **粒度不匹配检索需求。** 一个 summary blob 覆盖多个不相关事实。检索到一条 MemoryCue 意味着注入整个 blob，Agent 需要自行筛选相关信息。

**根因：** 记忆存储粒度过粗（blob），缺少结构化字段支撑去重、更新、演化追踪。

---

## Solution

引入 **MemoryAssertion**（断言）作为新的记忆存储单元，替代 MemoryCue 在会话结束时的写入管道。断言将每条独立事实作为独立行存储，配合 embedding 语义检索和 LLM 驱动的合并/更新管线，实现记忆的结构化收束。

**核心流水线：**

```
会话结束
  → detectSwitches（复用 MemoryCueAgent） → 分割话题段
  → per segment × per assertion_group:
      Step1 LLM: 抽取 topic 列表（"反复出现的概念"）
      per topic:
        Step2 LLM: 生成 state（一句自然语言断言）
        → INSERT memory_assertion (enabled=true)
        → indexAsync(state) 到独立 embedding store
  → Manager（串行，逐条处理新断言）:
      Search(top-3) from assertion_embedding_store
      per candidate (并行): Judge LLM → 是/否
      对每个 YES（串行）:
        Merge LLM → 合并后的 state
        → old assertion: enabled=false, indexRemove
        → new assertion: INSERT (enabled=true, topic=较新那条)
        → INSERT assertion_lineage (parent_id, child_id, operation='MERGE')
```

**V1 范围：** 仅 group=`error-pattern`，仅写入端（检索端保持使用 MemoryCue 管道）。

---

## User Stories

1. As a Learner, I want the system to merge duplicate memories about my English mistakes, so that the coach doesn't tell me "you're still stuck on OAuth" when I've already solved it.
2. As a Learner, I want the system to update my learning progress records, so that when my skill improves the coach's advice reflects the current state.
3. As a Developer, I want each assertion to be independently vectorized, so that semantic search returns only the most relevant facts rather than whole topic summaries.
4. As a Developer, I want to trace the evolution of an assertion (which old assertions were merged to produce it), so that I can audit the memory system's behavior.
5. As a Developer, I want assertion groups to be extensible, so that future modes (learning-goal, personal-fact) can reuse the same pipeline without code changes.
6. As a Developer, I want the assertion pipeline to run asynchronously at session end (like MemoryCue), so that report generation is not blocked.
7. As a Developer, I want the assertion pipeline to be observable with per-step timing logs, so that I can evaluate LLM call cost before expanding to multiple groups.
8. As a Learner, I want Japanese Business mode to continue working without assertion generation, so that the V1 change is transparent to Japanese learners.
9. As a Developer, I want the new pipeline to coexist with the existing MemoryCue table, so that I can run both systems in production and validate assertions before cutting over.
10. As a Developer, I want the Manager phase to process assertions sequentially to avoid race conditions during merge, so that no redundant merged assertions are created.

---

## Implementation Decisions

### 1. Schema: 三张新表 + 独立 EmbeddingStore

**assertion_group**
- `id` (PK), `name`, `description`
- 种子数据一行: `("error-pattern", "user在对话中出现的语法/用词错误类型")`
- `description` 作为 Step1 LLM prompt 的 `{groupDescription}` 参数，定义"在该维度下什么算一个概念"

**memory_assertion**
- `id` (PK, UUID), `group_id` (FK→assertion_group), `session_id`, `user_id`, `mode`
- `topic` (VARCHAR) — 简短概念词，由 Step1 LLM 抽取，作为统计锚点
- `state` (CLOB) — 一句自然语言断言，由 Step2 LLM 生成，也是 embedding 向量化的唯一输入
- `enabled` (BOOLEAN, DEFAULT true) — 软删除标志，合并后旧断言设为 false
- `create_time`, `update_time`

**assertion_lineage**
- `parent_id` (FK→memory_assertion), `child_id` (FK→memory_assertion)
- `operation` (VARCHAR, DEFAULT 'MERGE')
- PRIMARY KEY (parent_id, child_id)
- 演化链追溯: WITH RECURSIVE CTE from child_id

**assertion_embedding_store**
- 独立 `InMemoryEmbeddingStore<TextSegment>`
- 与 MemoryCue 的 embedding store 物理隔离
- `enabled=false` 的断言从 store 中移除

### 2. 管线: 两阶段异步执行

**Phase 1 — Extractor:** 话题段分割 → per-group Step1 (topic 抽取) → per-topic Step2 (state 生成) → INSERT + 索引。Step1 和 Step2 的 per-segment、per-topic 维度可并行。

**Phase 2 — Manager:** 新断言逐条串行处理。每条: Search(top-3) → 并行 Judge → 收集 YES → 串行 Merge。串行化避免竞态（两条新断言同时合并同一条旧断言导致冗余）。

### 3. 四个 LLM 任务 (TaskName 枚举新增)

| TaskName | 输入 | 输出 | ε 层级 |
|---|---|---|---|
| `EXTRACT_TOPICS` | `{groupName}`, `{groupDescription}`, `{messages}` | `["topic1", ...]` | 低 ε — 反复出现×概念提取 |
| `EXTRACT_STATE` | `{groupName}`, `{topic}`, `{messages}` | 纯文本 state | 低 ε — topic 约束下的填空 |
| `JUDGE_SAME` | `{newState}`, `{oldState}` | `YES` / `NO` | 极低 ε |
| `MERGE_ASSERTION` | `{stateA}`, `{stateB}` | 合并 state 文本 | 低 ε — 合并两条现成文本 |

### 4. Prompt 设计原则

- **通用模板:** 四份 prompt 均通过 `{groupName}` 和 `{groupDescription}` 参数化，不与具体 group 耦合
- **Step1 的"反复出现":** 用"反复出现或在多轮对话中被多次提及"替代"高频"——LLM 能做模式识别，不擅长精确计数
- **Step2 的约束填空:** LLM 不需要判断"该提取什么"，只需回答"这个 topic 在对话里怎么样"
- **Merge 后 topic 的处理:** 取两条断言中较新那条的 topic，不让 LLM 额外输出 topic

### 5. Step1+2 不合并

用户明确要求保留两阶段设计。虽然合并为一次 LLM 调用可减少 HTTP 请求数，但会退化 topic 生成的稳定性——topic 失去"先收敛概念再生成断言"的约束，变成高 ε 任务。

### 6. Error Strategy

所有 LLM 步骤采用 `ErrorStrategy.THROW`：任一调用失败即抛异常，后续步骤全部跳过。与 MemoryCue 的 SWALLOW 策略不同——断言管线更复杂，部分失败导致的不一致状态比完全跳过更危险。

### 7. 插入点

`SessionComplete.complete()` 的方法级 seam：`memoryCueService.generateCuesAsync(...)` 替换为 `assertionService.generateAssertionsAsync(...)`。同受 `mode != JAPANESE_BUSINESS` guard 保护。

### 8. 代码组织

- `AssertionService` — 管线编排，直接通过 `TaskRunner.requestModel()` 调用 LLM，无独立 Agent 层
- 复用 `MemoryCueAgent.detectSwitches()` — 不重复实现话题分割
- `embeddingExecutor` 线程池复用 — `indexAsync` 使用现有线程池

### 9. 日志

每个 pipeline 步骤打印耗时日志（`log.info("AssertionService: detectSwitches done in {}ms"...)`），便于首版上线后评估成本。

---

## Testing Decisions

### Seam

**唯一 seam: `SessionComplete` 的依赖注入。** `MemoryCueService` mock 替换为 `AssertionService` mock。所有现有测试通过此 seam 隔离。

### 测试原则

- 只测试外部可观测行为（断言是否生成、lineage 是否正确、enabled flag 是否切换）
- 不测试 LLM 输出内容（由 LLM 自身保证，不写死期望文本）
- 不测试 Manager 串行化顺序（实现细节）

### Unit Tests (新增)

1. **`AssertionServiceTest`** — Extractor → Manager 完整管线:
   - 正常流程: detectSwitches → Step1 → Step2 → INSERT → Search → Judge → Merge → lineage
   - 空对话: 所有 steps 产出空数组，无 INSERT
   - 单 topic: Step1 返回 1 个 topic，Step2 返回 1 条 state
   - LLM 失败: THROW 错误，后续步骤未执行
   - Manager 无匹配: Judge 返回全部 NO → 无 Merge 调用

2. **`MemoryAssertionRepositoryTest`** — 数据层:
   - `enabled` 过滤: `findByEnabled(true)` 不返回 `enabled=false` 的记录
   - userId + mode 隔离
   - 递归 CTE 演化链查询: 给定 child_id，返回完整祖先链

3. **`DataInitializerTest`**（追加） — assertion_group 种子数据验证

4. **`SessionCompleteTest`**（修改） — mock 目标从 MemoryCueService 换为 AssertionService

### E2E Tests (新增 + 替换)

5. **`ChatAgentAssertionIT.java`** (新增，替换 `ChatAgentMemoryCueIT.java`):
   - 多轮跨话题对话 → end session
   - 验证 `memory_assertion` 表有 `enabled=true` 记录
   - 验证 `assertion_lineage` 有合并边
   - 验证 assertion_embedding_store 有独立持久化
   - 先行测试: `ChatAgentMemoryIT.java`（LearningProfile 管道不受影响）

### 不测试

- LLM prompt 内容的匹配（由 LLM 自身行为保证）
- embedding 向量精度（ONNX 模型由库保证）
- 性能/延迟阈值（日志观察阶段，暂不定指标）

---

## Out of Scope

- **检索端 (RAG 注入):** ConversationAgent 继续使用 MemoryCue 管道。下次迭代再切换到 Assertion 检索
- **多 group 扩展:** V1 仅 `error-pattern`。`learning-goal`、`personal-fact` 等 group 及用户自定义 group 延后
- **BM25 混合检索 (§1.3):** 依赖检索端改造，本次不做
- **Embedding 阈值调优 (§1.4):** 依赖检索端改造，本次不做
- **置信度分流去重 (§1.5):** Judge 的 YES/NO 二元判断已足够 V1 使用。三级分流（definitely_same / maybe_same / definitely_different）延后
- **MemoryCue 代码删除:** 并存期保留，确认 assertions 管道稳定后再移除
- **前端变更:** 无（写入端只有后端变更）
- **Anki 集成:** 不相关

---

## Further Notes

- **成本估算:** V1 单会话约 15 次 LLM 调用（1×detectSwitches + 每段×1×Step1 + 每 topic×1×Step2 + 每新断言×Judge + 若干 Merge）。当前 MemoryCue 约 4 次。线上运行一周后根据日志评估是否可接受。
- **无递归合并假设:** 接受首次 Manager 可能产出互相重叠但未被合并的断言（如 N+A→M1, N+B→M2 而 M1 和 M2 共享 A 的信息）。假设下次会话的 Manager 会收束。如果线上出现冗余积累，后续迭代考虑递归合并或 Search 前置所有权分配。
- **ADR 建议:** 本次设计满足 ADR 三条件（难逆转的 schema 变更、无上下文会困惑的 pipeline 设计、LLM 调用次数 vs 记忆精度的真实权衡），建议同步写入 `docs/adr/`。
- **CONTEXT.md 更新:** 新术语（MemoryAssertion、AssertionGroup、AssertionLineage、Extractor、Manager）需写入领域术语表。
