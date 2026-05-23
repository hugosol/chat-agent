# 01: Prompt Template Generalization

**Status:** `ready-for-agent`

## Parent

PRD: `.scratch/daily-talk-mode/PRD.md`

## What to build

将 `conversation-system.txt` 退化为纯骨架模板，移除其中硬编码的身份信息（"helping a Chinese Java developer practice workplace English"），只保留 `{Description}` / `{Rules}` / `{topicSummary}` / `{learningProfile}` / `{activeEngagement}` 占位符。把被移除的身份信息下沉到 `workplace_standup/description.txt`（补充 "English conversation partner helping a Chinese Java developer practice workplace English. You are a friendly teammate..."），确保 standup 模式行为不变。

同时将 `correction.txt` 和 `report.txt` 中的 "Chinese Java developer" 替换为 "Chinese adult"，消除行业/职业硬编码。同步更新 `src/test/resources/prompts/` 下的测试用 prompt 副本，并修复受影响的单元测试断言。

## Acceptance criteria

- [ ] `conversation-system.txt` 首行不再包含 "Chinese Java developer" 或 "workplace English"，仅含占位符
- [ ] `workplace_standup/description.txt` 包含从骨架移下来的身份信息
- [ ] `correction.txt`（main + test）中 "Chinese Java developer" → "Chinese adult"
- [ ] `report.txt`（main + test）中 "Chinese Java developer" → "Chinese adult"
- [ ] `mvn test` 全部通过（含 ConversationAgentTest 等受影响的单元测试）
- [ ] `mvn verify` 全部通过（EnglishCoachSessionIT + EnglishCoachResumeIT 不受影响）

## Blocked by

None — can start immediately
