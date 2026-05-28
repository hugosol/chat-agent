# 03: Cancel Topic Memory Merge

**Status:** `ready-for-agent`

## Parent

`.scratch/memory-optimization/PRD.md`

## What to build

取消 Topic Memory 的 LLM 合并步骤。会话结束时 Report 生成的 `topicSummary` 不再通过 `MemoryAgent.mergeTopic()` 与旧版本合并，而是直接作为新版本写入 Topic Memory（INSERT 新行，version 递增，旧版本保留不删）。每次新会话首轮注入只携带最近一次会话的话题摘要，历史话题由 RAG MemoryCues 语义检索补充。

**代码变更：**
- 删除 `MemoryAgent.mergeTopic()` 方法及其加载的 `memory-topic.txt` 模板（文件保留在磁盘但不加载）
- `MemoryService.generateMemoryAsync()` 中 TOPIC_SUMMARY 分支：不再调用 `memoryAgent.mergeTopic()`，改为 lambda 直接返回 `report.topicSummary()` 供 `generateSingle()` 写入
- Learning Profile 的 `mergeProfile()` 完全保留不变
- `MemoryType.TOPIC_SUMMARY` 枚举值保留不变

**E2E 测试调整：**
- `EnglishCoachMemoryIT`：当前验证 "User Memory v1→v2 merge"（两场连续会话后 v2 是 v1 和新话题的合并结果）。改为验证 v2 仅包含第二场会话话题，v1 内容不变（旧版本保留在历史行中）
- WireMock stubs：移除 topic merge 相关的 WireMock stub（`memory-topic.txt` 对应的 LLM 调用 mock），因为不再有 merge LLM 调用
- `memory-topic.txt` 文件的测试版（`src/test/resources/prompts/memory-topic.txt`）保留不动（仅主代码不加载）

## Acceptance criteria

- [ ] `MemoryAgent.mergeTopic()` 方法已删除
- [ ] `MemoryAgent` 不再加载 `memory-topic.txt` 模板（字段和构造参数移除）
- [ ] `MemoryService.generateMemoryAsync()` 中 TOPIC_SUMMARY 分支直接使用 `report.topicSummary()` 写入，无 LLM 调用
- [ ] `mvn test` 全部通过（现有单元测试无 `MemoryAgent.mergeTopic()` 直接调用）
- [ ] `EnglishCoachMemoryIT` E2E 通过：验证 v2 topicSummary 仅含第二场话题，v1 不变
- [ ] WireMock stubs 无 topic merge 相关的 mock（无对应 `/chat/completions` 的 WireMock 映射）
- [ ] `MemoryType.TOPIC_SUMMARY` 枚举值保留，语义不变
- [ ] `memory-topic.txt`（main）文件保留在磁盘上，不被代码引用

## Blocked by

None — can start immediately

## User stories covered

#7, #8, #9, #15
