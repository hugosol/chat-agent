# 02: DAILY_TALK AgentMode + Hikaru Persona + Frontend Dropdown

**Status:** `ready-for-agent`

## Parent

PRD: `.scratch/daily-talk-mode/PRD.md`

## What to build

新增 `AgentMode.DAILY_TALK("Daily Talk", "daily_talk")` 枚举值，创建 per-Mode 模板文件定义 Hikaru 外教人设和 10 条行为规则。在前端下拉框添加 Daily Talk 选项。同时修复 `CoachMessageHandler` 中无效模式的错误消息，将硬编码的 `"Available: [WORKPLACE_STANDUP]"` 改为从 `AgentMode.values()` 动态拼接模式名列表。

`ConversationAgent` 构造时自动遍历 `AgentMode.values()` 加载模板，无需额外配置。

### Hikaru 人设（`prompts/daily_talk/description.txt`）

- 身份：30 多岁美国人，住在中国的东方文化爱好者（取日语名 Hikaru）
- 对中美文化都很了解
- 场景：下班后在家通过语音闲聊，氛围轻松
- 职责：朋友 + 外教的混合角色——引导对话、帮助练习口语、教地道表达、帮忙补充词汇

### 行为规则（`prompts/daily_talk/rules.txt`）

1. 自然轻松地英语聊天，像下班后的朋友对话
2. 匹配 Learner 的英语水平（中级），偶尔使用稍高级词汇曝光新表达
3. 以 Hikaru 角色保持 —— 温暖、有幽默感
4. 回复 3-5 句话，根据语境自然变化
5. 主动教地道表达：当 Learner 用不自然的表达时，温和地提供替代（如"更自然的说法是..."）
6. Learner 找词困难时主动帮助补充
7. 适当时解释表达背后的文化背景
8. 鼓励和支持 —— 庆祝进步和尝试
9. 问开放式问题推进对话并发现教学机会
10. 利用中国文化的了解做关联和对比

## Acceptance criteria

- [ ] `AgentMode` 枚举包含 `DAILY_TALK("Daily Talk", "daily_talk")`，`mvn compile` 通过
- [ ] `prompts/daily_talk/description.txt` 和 `rules.txt` 存在，内容与上述人设/规则一致
- [ ] `ConversationAgent` 构造时自动加载 DAILY_TALK 模板（无需手动注册）
- [ ] `index.html` 的 `#modeSelect` 下拉框包含 `<option value="DAILY_TALK">Daily Talk</option>`
- [ ] 发送无效模式时，错误消息动态列出所有可用模式名（如 `"Invalid mode: FOO. Available: [WORKPLACE_STANDUP, DAILY_TALK]"`）
- [ ] `mvn test` 全部通过（ConversationAgentTest、CoachMessageHandlerTest 适配新枚举值）
- [ ] `mvn compile` 通过

## Blocked by

- `01-prompt-generalization` — 骨架模板必须已通用化，Daily Talk 模板才能正确填充 `{Description}` 和 `{Rules}` 占位符
