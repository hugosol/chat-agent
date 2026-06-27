# 07: 清理 — 删除 TaskRunner + 全量回归

**Status:** `ready-for-agent`

## Parent

`.scratch/llm-req-constructor/PRD.md`

## What to build

删除旧的 `TaskRunner` 和 `TaskDefinition`，重写 `TaskRunnerTest` 为 `LlmReqConstructorTest`（覆盖消息组装验证、日志字段验证、ErrorStrategy、模型选择、exampleMessages 插入位置）。全量回归通过。

## Acceptance criteria

- [ ] `TaskRunner.java` 删除，确认无遗留引用
- [ ] `TaskDefinition.java` 删除
- [ ] 新建 `LlmReqConstructorTest`，覆盖：消息组装顺序、systemPrompt/chatHistory/tokenUsage 日志字段、ErrorStrategy.SWALLOW/THROW、未注册 task 异常、Report task 路由
- [ ] `mvn test` 全部通过
- [ ] `mvn verify` E2E 全部通过

## Blocked by

- `02-report-agent`
- `03-learning-agent`
- `04-memory-cue-agent`
- `05-assertion-service`
- `06-bypass-callers`
