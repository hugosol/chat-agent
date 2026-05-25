# MemoryCue 数据层（Entity + Enum + Converter + Repository）

**Status:** `ready-for-agent`

## Parent

`.scratch/memory-system-upgrade/PRD.md` — 记忆系统升级

## What to build

新建 `memory_cues` 表及完整的数据访问层，为结构化话题记忆提供持久化支持。包含：
- `MemoryCueStatus` 枚举：三种生成状态
- `StringListConverter`：`List<String>` ↔ JSON 字符串的 JPA 转换器
- `MemoryCue` 实体：继承 `BaseEntity`，包含 session_id / user_id / mode / segment_index / topic / summary / tags / status 八个字段
- `MemoryCueRepository`：Spring Data JPA 接口，含 mode 隔离和 session 追溯查询

此切片仅覆盖数据层，不包含 Agent 或 Service 逻辑。依赖方（MemoryCueAgent、MemoryCueService）均不导入 `MemoryCue` 实体，可并行开发。

### MemoryCueStatus

| 值 | 含义 |
|----|------|
| `COMPLETED` | segment 成功生成，有完整 topic、summary、tags |
| `SEGMENT_FAILED` | 话题切换检测成功，但该 segment 的摘要生成失败 |
| `FIRST_CALL_FAILED` | 话题切换检测（第一次 LLM 调用）失败，segment_index = -1 |

### MemoryCue 字段

| 字段 | 类型 | Nullable | 说明 |
|------|------|----------|------|
| `sessionId` | String | NOT NULL | 关联 Practice session（无 FK） |
| `userId` | String | NOT NULL | Learner 数据隔离 |
| `mode` | AgentMode (@Enumerated STRING) | NOT NULL | AgentMode 数据隔离 |
| `segmentIndex` | int | NOT NULL | 段索引（0 起始；-1 表示分割失败） |
| `topic` | String | nullable | LLM 生成的话题名称 |
| `summary` | String | nullable | LLM 生成的话题摘要（无长度截断） |
| `tags` | String (@Convert StringListConverter) | nullable | 自由文本标签列表，JSON 列 |
| `status` | MemoryCueStatus (@Enumerated STRING) | NOT NULL | 生成状态 |

## Acceptance criteria

- [ ] `StringListConverter`：实现 `AttributeConverter<List<String>, String>`，使用 Jackson `ObjectMapper` 序列化/反序列化，空集合序列化为 `[]`
- [ ] `MemoryCueStatus` 枚举：三个值 `COMPLETED` / `SEGMENT_FAILED` / `FIRST_CALL_FAILED`
- [ ] `MemoryCue` 实体继承 `BaseEntity`（获取 `id`、`createTime`、`updateTime`），字段和注解如上表
- [ ] `MemoryCueRepository` 继承 `JpaRepository<MemoryCue, String>`
- [ ] `MemoryCueRepository.findByUserIdAndMode(userId, mode)` — 按用户和模式查询
- [ ] `MemoryCueRepository.findBySessionId(sessionId)` — 按会话追溯
- [ ] `memory_cues` 表通过 Hibernate `ddl-auto=update` 自动创建，`session_id` 和 `user_id` 无 FK 约束
- [ ] `MemoryCueRepositoryTest`：mode 隔离验证（WORKPLACE_STANDUP 和 DAILY_TALK 数据互不可见）
- [ ] `MemoryCueRepositoryTest`：session 关联验证（`findBySessionId` 返回正确记录）
- [ ] `MemoryCueRepositoryTest`：基础 `save` / `findById`

## Blocked by

None — 可立即开始
