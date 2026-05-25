# MemoryCueAgent + Prompt 模板

**Status:** `ready-for-agent`

## Parent

`.scratch/memory-system-upgrade/PRD.md` — 记忆系统升级

## What to build

实现 `MemoryCueAgent`——通过两步 LLM 调用完成对话话题的结构化拆分。此切片仅覆盖 Agent 层（LLM 输入输出），不依赖 `MemoryCue` 实体或 `MemoryCueService`。依赖方（MemoryCueService）将在后续切片串联 Agent 输出与数据层。

两步 LLM 调用：
1. `detectSwitches(messages, mode)` — 话题切换点检测，输出 `List<Integer>`
2. `generateCue(messages, mode, segmentIndex)` — 单 segment 摘要生成，输出 `{topic, summary, tags}` JSON

防御性 JSON 解析：`detectSwitches` 需容错 LLM 在 JSON 数组外附加的额外文本（提取首个 `[...]` 片段）。

## Acceptance criteria

- [ ] 新建 `src/main/resources/prompts/memory-cue-split.txt`：话题切换检测 prompt，占位符 `{messages}`，要求 LLM 输出纯 JSON 数组（如 `[]` 或 `[3]`）
- [ ] 新建 `src/main/resources/prompts/memory-cue-entry.txt`：segment 摘要生成 prompt，占位符 `{segment}`，要求 LLM 输出 `{topic, summary, tags}` JSON，三个字段均为英语
- [ ] `MemoryCueAgent` 使用同步 `ChatLanguageModel` bean（与 `MemoryAgent`、`ReportAgent` 一致）
- [ ] `MemoryCueAgent` 通过 `PromptLoader` 加载两个 prompt 模板
- [ ] `detectSwitches(messages, mode)` → `List<Integer>`：输入全部 messages（每条前注入 `[MSG#N]` 行号标记），输出切换点索引列表
- [ ] `detectSwitches` 防御性 JSON 解析：LLM 返回额外文本时正确提取数组
- [ ] `generateCue(messages, mode, segmentIndex)` → 结构化 JSON：输入 segment 的 messages 子集（不注入行号标记），输出 topic/summary/tags
- [ ] `MemoryCueAgentTest`：`detectSwitches` 无切换返回空列表
- [ ] `MemoryCueAgentTest`：`detectSwitches` 单切换返回正确索引（如 `[3]`）
- [ ] `MemoryCueAgentTest`：`detectSwitches` 多切换返回多个索引
- [ ] `MemoryCueAgentTest`：`detectSwitches` LLM 返回额外文本时防御性解析正确
- [ ] `MemoryCueAgentTest`：`generateCue` 返回正确 `{topic, summary, tags}` JSON 结构
- [ ] `MemoryCueAgentTest`：`generateCue` LLM 返回非法 JSON 时抛异常（由 Service 层处理）

## Blocked by

None — 可立即开始
