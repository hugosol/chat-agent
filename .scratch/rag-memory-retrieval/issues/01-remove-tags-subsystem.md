# 移除标签子系统

**Status:** `ready-for-agent`

## Parent

`.scratch/rag-memory-retrieval/PRD.md` — RAG-based MemoryCue Retrieval

## What to build

完全移除 MemoryCue 中的标签（tags）子系统，包括实体层、转换器、Agent、Service、提示词、WireMock 桩和测试。同时将 `CueResult` 从 3 字段 `(topic, summary, tags)` 简化为 2 字段 `(topic, summary)`，将 `memory-cue-entry.txt` 提示词从 3 字段 JSON 简化为 2 字段。

端到端行为：会话结束后仍生成 MemoryCue，但不再有标签字段、不再运行标签合并流水线。系统其他部分（会话、对话、修正）无影响。

## Acceptance criteria

- [ ] `MemoryCue` 实体：移除 `tags` 字段、`@Convert(converter = StringListConverter.class)` 注解、`getTags()`/`setTags()` 访问器，构造器从 8 参数降为 7 参数
- [ ] 删除 `StringListConverter.java` 文件
- [ ] `MemoryCueAgent`：删除 `consolidateTags()` 方法；`CueResult` 记录简化为 `(String topic, String summary)`
- [ ] `MemoryCueService`：删除 `consolidateTags()` 私有方法及 `consolidationLock` 静态字段；删除步骤 4（合并调用）
- [ ] `src/main/resources/prompts/memory-cue-entry.txt`：移除 `tags` 字段，将 "three fields" 改为 "two fields"，删除矛盾的 7 标签示例
- [ ] 删除 `src/main/resources/prompts/tag-consolidation.txt`
- [ ] 删除 `src/test/resources/prompts/tag-consolidation.txt`
- [ ] `MemoryCueRepository`：移除 `findByUserIdAndMode(userId, mode)` 方法
- [ ] `EnglishCoachMemoryCueIT`：删除 `shouldConsolidateTagsAfterSessionEnd()` 测试方法；更新 `memoryCueGeneratedAtSessionEndWithTopicSwitch()` 移除 `getTags()` 断言
- [ ] `WireMockStubs.java`：删除标签合并相关的场景桩方法及注册调用
- [ ] 删除 WireMock 响应文件：`tag-consolidation-response.json`
- [ ] 更新 `memory-cue-entry-success.json` 和 `memory-cue-entry-seg2.json`：移除 JSON 响应中的 `tags` 字段
- [ ] `MemoryCueAgentTest`：删除 `consolidateTags` 相关测试方法，更新 `CueResult` 构造为 2 字段
- [ ] `MemoryCueServiceTest`：删除所有合并相关测试（含合并映射、无变化跳过保存、部分失败跳过、首次调用失败跳过、幂等性）
- [ ] `MemoryCueRepositoryTest`：删除 `findByUserIdAndMode_filtersByMode` 测试
- [ ] `mvn compile` 通过
- [ ] `mvn test` 通过
- [ ] `mvn verify`（E2E）通过

## Blocked by

None — 可立即开始
