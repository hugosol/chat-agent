# 02: ReportAgent — 4 参 execute 模板覆盖

**Status:** `ready-for-agent`

## Parent

`.scratch/llm-req-constructor/PRD.md`

## What to build

迁移 `ReportAgent` 到 `LlmReqConstructor`，这是唯一使用 `execute` 4 参重载（per-mode 模板覆盖）的 Agent。`report.txt` 模板拆分后 system 部分含报告格式指令，user 部分为 `"Full conversation:\n{fullConversation}\n\nAll errors:\n{allCorrections}"`。

`Ll mReqConstructor` 新增 `execute(TaskName, P, TaskContext, String systemTemplateOverride)` 方法——使用调用方传入的 system 模板覆盖注册表中的默认值，user 模板和 parser 仍从注册表获取。`ReportAgent.generate()` 根据 `AgentMode` 选择 per-mode report 模板（从 `EnumMap` 获取）作为 `systemTemplateOverride` 传入。

`reportChatLanguageModel` 路由已在 Foundation 中实现（`TaskName.REPORT → reportModel`），此 slice 只需验证。

## Acceptance criteria

- [ ] `LlmReqConstructor.execute` 4 参重载正确覆盖 system 模板
- [ ] `ReportAgent.generate()` per-mode 模板选择逻辑不变，传入 `systemTemplateOverride`
- [ ] `report.txt` 模板拆分后 System 部分含格式指令，User 部分含 `{fullConversation}` 和 `{allCorrections}`
- [ ] 日语报告模板（`japanese_business/report.txt`）同步拆分
- [ ] `reportChatLanguageModel` 路由正确（maxTokens=4096）
- [ ] `ReportAgentTest` 全部通过
- [ ] E2E report 模板同步拆分，标记关键词在 system 部分

## Blocked by

- `01-foundation`
