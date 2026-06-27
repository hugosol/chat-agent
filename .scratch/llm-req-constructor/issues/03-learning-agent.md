# 03: LearningAgent — 最简 3 参 execute

**Status:** `ready-for-agent`

## Parent

`.scratch/llm-req-constructor/PRD.md`

## What to build

迁移 `LearningAgent` 到 `LlmReqConstructor`，这是最简单的 Agent——`execute` 3 参基础路径，`String.trim` parser，无 XML，无 exampleMessages。验证 Foundation 的 `execute` 3 参路径在第二个消费者上正确工作。

`memory-profile.txt` 模板拆分后 system 部分含规则指令和 `{profileMaxLength}`（构造器内联替换的配置值），user 部分为旧 profile 和新 error summary。`{profileMaxLength}` 保留在 system 模板中作为静态文本（已在构造器中替换为具体数字），不走 `paramBuilder`。

## Acceptance criteria

- [ ] `LearningAgent.mergeProfile()` 走 `LlmReqConstructor.execute` 3 参路径
- [ ] `memory-profile.txt` 模板拆分为 system/user 两部分
- [ ] `{profileMaxLength}` 替换逻辑不变（构造器内联 replace）
- [ ] `LearningAgentTest` 全部通过
- [ ] E2E `memory-profile.txt` 模板同步拆分，标记关键词在 system 部分

## Blocked by

- `01-foundation`
