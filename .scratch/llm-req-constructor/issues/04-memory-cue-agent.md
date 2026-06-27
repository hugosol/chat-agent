# 04: MemoryCueAgent — XML 对话格式

**Status:** `ready-for-agent`

## Parent

`.scratch/llm-req-constructor/PRD.md`

## What to build

迁移 `MemoryCueAgent` 到 `LlmReqConstructor`，同时将对话数据格式从 `[MSG#0] USER:` 纯文本标签改为 XML。两个 task（CHAT_SWITCHES、GENERATE_MEMORY_CUE）的 user 数据从 `buildLabeledMessages()` / `buildSegmentText()` 产出的纯文本改为 XML 格式：

```xml
<turn role="user">Yesterday I go to park with my friend.</turn>
<turn role="assistant">You mean "went to the park."</turn>
```

`memory-cue-split.txt` 和 `memory-cue-entry.txt` 的 system 部分更新格式说明文字（"Each turn is an XML element with a role attribute..."）。两模板不含 exampleMessages（无完整 few-shot 对），本次不优化。`{cueTopicMaxWords}` 和 `{cueSummaryMaxSentences}` 保留在 system 模板中作为静态文本。

## Acceptance criteria

- [ ] `MemoryCueAgent.detectSwitches()` 和 `generateCue()` 走 `LlmReqConstructor.execute`
- [ ] 对话数据以 XML 格式发送（`<turn role="user">` / `<turn role="assistant">`）
- [ ] `buildLabeledMessages()` 和 `buildSegmentText()` 改为产出 XML
- [ ] `memory-cue-split.txt` 和 `memory-cue-entry.txt` 模板拆分，system 部分更新格式说明
- [ ] `MemoryCueAgentTest` 全部通过，新增 XML 格式验证断言
- [ ] E2E memory-cue 模板同步拆分，标记关键词在 system 部分

## Blocked by

- `01-foundation`
