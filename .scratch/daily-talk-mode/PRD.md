# PRD: 新增 Daily Talk 对话模式

**Status:** `ready-for-agent`

## Problem Statement

当前 English Coach 只有一个对话模式 —— **Workplace Standup**（职场站会）。Learner 只能在「软件开发团队同事」的角色设定下练习英语，无法满足非职场场景的口语练习需求。Learner 需要一个轻松、以教学为导向的闲聊模式，让外教以朋友口吻引导日常英语对话，在聊天中自然地教地道口语、补充词汇，并利用跨会话的话题记忆保持对话连续性。

## Solution

新增 `AgentMode.DAILY_TALK` 对话模式，以外教 Hikaru（30 多岁美国人、住在中国的东方文化爱好者）为主导身份。Hikaru 在下班后家中语音聊天的轻松氛围中与 Learner 对话，既像朋友一样自然地闲聊和推进话题，又兼顾外教的教学职责 —— 适当地教地道表达、帮 Learner 补充想不起的词汇、解释表达背后的文化背景。纠错和统计仍复用现有 CorrectionAgent，教学通过 system prompt 规则驱动。

同时在 Topic Memory 层面实现模式级隔离 —— standup 聊的工作话题和 DAILY_TALK 聊的生活话题不会相互污染，但 Learning Profile 跨模式共享（同一个 Learner 的英语水平）。

## User Stories

1. 作为英语学习者，我想要在工作之外还有一个轻松闲聊的练习模式，以便在非职场场景下也能锻炼口语。
2. 作为英语学习者，我启动 Daily Talk 会话时，外教应以朋友般的口吻和我聊天，让我感到放松、不紧张。
3. 作为英语学习者，对话中外教应适时教我地道的英语表达（比如"其实你可以说..."），帮助我提升口语自然度。
4. 作为英语学习者，当我卡住想不起某个词时，外教应主动帮忙补充（比如"你是不是想找...这个词？"）。
5. 作为英语学习者，外教应基于上次话题自然地开启新对话（比如"你上次说的那个川菜馆后来去试了吗？"），保持对话连贯性。
6. 作为英语学习者，我希望 Daily Talk 的话题记忆和 standup 的话题记忆互不干扰——闲聊不会混入工作话题。
7. 作为英语学习者，无论我选哪个模式练习，我的英语学习进度（Learning Profile）应该是一致的——我的语法弱点是同一个人。
8. 作为英语学习者，在我发言后仍能看到纠正结果（侧边栏气泡），以便知道刚才哪里说得不地道。
9. 作为英语学习者，会话结束后的 Report 应包含 fluencyScore、errorSummary、vocabularySuggestions，和 standup 一样。
10. 作为前端用户，模式下拉框中应看到 Daily Talk 选项，和 Standup Meeting 并列。
11. 作为开发者，新增 Daily Talk 模式时只需添加 AgentMode 枚举值和对应的 description.txt / rules.txt 模板文件，无需改动 ConversationAgent 核心逻辑。
12. 作为开发者，UserMemory 的 mode 字段为空时表示跨模式共享（Learning Profile），不为空时表示模式隔离（Topic Memory），语义清晰。
13. 作为开发者，当客户端发送无效模式值时，服务端返回的错误消息应动态列出所有可用模式，而非硬编码。
14. 作为测试工程师，Daily Talk 模式应有独立的 E2E 测试，覆盖完整会话流程。

## Implementation Decisions

### 1. AgentMode 枚举扩展

- 新增 `DAILY_TALK("Daily Talk", "daily_talk")`
- `displayName` 用于前端下拉框显示，`templatePath` 指向 per-Mode 模板目录
- 枚举不持有提示词内容，全部由模板文件定义
- `ConversationAgent` 构造时遍历 `AgentMode.values()` 自动加载，无需额外配置

### 2. Daily Talk 提示词模板

`prompts/daily_talk/description.txt` 定义 Hikaru 人设：
- 身份：30 多岁美国人，住在中国的东方文化爱好者（因此取日语名 Hikaru）
- 文化背景：对中美文化都很了解
- 场景：下班后在家通过语音闲聊，氛围轻松
- 职责：朋友 + 外教的混合角色——引导对话、帮助练习口语、教地道表达、帮忙补充词汇

`prompts/daily_talk/rules.txt` 定义 10 条行为规则：
- 自然轻松地英语聊天，像下班后的朋友对话
- 匹配 Learner 的英语水平（中级），偶尔使用稍高级词汇曝光新表达
- 以 Hikaru 角色保持 —— 温暖、有幽默感
- 回复 3-5 句话，根据语境自然变化
- 主动教地道表达：当 Learner 用不自然的表达时，温和地提供替代（如"更自然的说法是..."）
- Learner 找词困难时主动帮助补充
- 适当时解释表达背后的文化背景
- 鼓励和支持 —— 庆祝进步和尝试
- 问开放式问题推进对话并发现教学机会
- 利用中国文化的了解做关联和对比

### 3. 现有提示词模板通用化

- `conversation-system.txt` 去掉硬编码开场白（"helping a Chinese Java developer practice workplace English"），退化为纯骨架模板，只含 `{Description}` / `{Rules}` / `{topicSummary}` / `{learningProfile}` / `{activeEngagement}` 占位符。所有身份/场景信息下沉到各 mode 的 `description.txt`
- `workplace_standup/description.txt` 补入从骨架搬下来的信息："English conversation partner helping a Chinese Java developer practice workplace English. You are a friendly teammate..."
- `correction.txt` 中 "Chinese Java developer" → "Chinese adult"
- `report.txt` 中 "Chinese Java developer" → "Chinese adult"

### 4. UserMemory 模式级隔离

- `UserMemory` 实体新增 `mode` 字段（`@Enumerated(EnumType.STRING)`，可空）
- `TOPIC_SUMMARY` 类型记录：`mode` = 当前 AgentMode（隔离），每个模式有独立的话题记忆
- `LEARNING_PROFILE` 类型记录：`mode` = null（跨模式共享），同一个 Learner 的学习档案保持一致
- `UserMemoryRepository` 查询方法变更为 `findTopByUserIdAndTypeAndModeOrderByVersionDesc`，mode 参数支持 null 查询
- `MemoryService` 所有方法加 `mode` 参数：
  - `loadLatestContent(userId, type, mode)` — mode 为 null 时查 null（共享），非 null 时查特定 mode（隔离）
  - `generateMemoryAsync(userId, report, mode)` — 生成记忆时区分 mode
- `MemoryAgent.mergeTopic()` 调用无需改（合并逻辑与模式无关）
- `SessionService.init()` 加载记忆时传当前 mode
- `CoachMessageHandler.onEndSession()` 生成记忆时传当前 mode
- `CoachState.initialState()` 已有 `topicMemory` 和 `learningProfile` 参数，无需变更签名

### 5. 前端变更

- `index.html` 的 `modeSelect` 下拉框新增 `<option value="DAILY_TALK">Daily Talk</option>`
- `sendStart()` 逻辑不变，始终发送 `mode: els.modeSelect.value`
- 无需新增 CSS 或 JS 逻辑

### 6. CoachMessageHandler 错误消息动态化

- 硬编码 `"Available: [WORKPLACE_STANDUP]"` 改为从 `AgentMode.values()` 动态拼接模式名列表
- 不影响协议层（错误消息仍是 `ErrorMessage` 类型）

### 7. 文档更新

- `CONTEXT.md`：Topic Memory 定义从"跨所有 Practice session"改为"跨同 AgentMode 的 Practice session"，AgentMode 示例加入 DAILY_TALK
- `AGENTS.md`：更新 AgentMode 相关描述，加入 DAILY_TALK 模式说明
- `README.md`：How to Use 表格加入 Daily Talk 模式引用；项目结构图中 workplace_standup 后加入 daily_talk 示例；TODO 中标记 DAILY_TALK 已完成
- `docs/architecture.md`：决策日志新增第 34 项（DAILY_TALK 模式）；V1 范围从"单 AgentMode"更新为"两个 AgentMode"；文件结构更新；Prompt 设计章节更新示例
- `docs/adr/`：新建 ADR `NNNN-mode-scoped-topic-memory.md`，记录 Topic Memory 模式隔离的决策和 trade-off

### 8. E2E 测试

- 新建 `DailyTalkIT.java`（`@ActiveProfiles("e2e")`），覆盖：
  - 启动 DAILY_TALK session → 验证 SESSION_STARTED 回传 mode 为 DAILY_TALK
  - 2-3 轮对话（mock SSE stream 含教学风格回复）
  - sidebar 纠正结果到达
  - 结束 session → report modal 展示
  - H2 断言：Session.mode = DAILY_TALK、Messages/ErrorRecords/SessionReport 正确写入、UserMemory 的 TOPIC_SUMMARY 带 mode 字段
- 新建 WireMock stubs（JSON Path 匹配 DAILY_TALK 关键词）+ mock 回复文件（conversation SSE + correction JSON + report JSON）
- 新增 E2E 测试 prompt 覆盖（如 `src/test/resources/prompts/` 下 conversation-system 测试版本如需调整）

### 9. 影响范围：变更文件清单

| 操作 | 文件 |
|---|---|
| 新建 | `prompts/daily_talk/description.txt`、`prompts/daily_talk/rules.txt` |
| 新建 | `DailyTalkIT.java` + WireMock stubs + mock 回复文件 |
| 新建 | `docs/adr/NNNN-mode-scoped-topic-memory.md` |
| 修改 | `AgentMode.java` |
| 修改 | `conversation-system.txt`、`workplace_standup/description.txt`、`correction.txt`、`report.txt` |
| 修改 | `UserMemory.java`、`UserMemoryRepository.java`、`MemoryService.java`、`MemoryAgent.java`、`SessionService.java`、`CoachMessageHandler.java` |
| 修改 | `index.html` |
| 修改 | `CONTEXT.md`、`AGENTS.md`、`README.md`、`docs/architecture.md` |

## Testing Decisions

### 测试原则

- 只测试外部可观测行为，不测试模板文件加载顺序、EnumMap 缓存等内部实现
- 利用已有测试框架：JUnit 5 + AssertJ + Mockito（单元测试）、Playwright + WireMock（E2E）

### 需要新增的测试

1. **`DailyTalkIT.java`** — E2E 测试：完整 DAILY_TALK 会话流程 + H2 持久化断言。对标 `EnglishCoachSessionIT.java` 的结构和模式

### 需要更新的测试

1. **`EnglishCoachSessionIT`** — 检查 WireMock stubs 的 JSON Path 匹配关键词在 `conversation-system.txt` 通用化后是否仍然命中（骨架去掉 "workplace" 后，该词仅存在于 `workplace_standup/description.txt` 中）
2. **现有单元测试**（`ConversationAgentTest`、`CoachMessageHandlerTest` 等）— 检查 `AgentMode.values()` 变化（多了一个枚举值）和 `conversation-system.txt` 内容变更是否影响断言

### 不需要新增的测试

- `AgentMode` 枚举本身无需单测（纯数据类）
- `PromptLoader` 加载逻辑不变，无需新增测试
- 现有 `CorrectionAgent`、`ReportAgent` 逻辑不变，无需新增测试

## Out of Scope

- 语音输入（STT）集成
- 前端历史记录页面
- 模板热加载或动态刷新
- AgentMode 的前端国际化（当前均为英文 displayName）
- CorrectionAgent 的模式感知（纠错提示词不变）
- ReportAgent 的模式感知（报告提示词不变）
- 数据库迁移工具

## Further Notes

- `ConversationAgent` 构造时遍历 `AgentMode.values()` 的特性使得新增模式只需：添加枚举值 + 创建模板文件 + 前端加选项，无需改核心 Agent 代码
- 骨架模板通用化后，standup 的描述文件已包含原本在骨架中的身份信息，standup 行为应完全不变
- `ACTIVE_ENGAGEMENT_TEXT` 常量保持共享，TOPIC_SUMMARY 的模式隔离使得不同模式看到不同的话题记忆，无需差异化 Active Engagement 指令
- 错误消息中硬编码的 `[WORKPLACE_STANDUP]` 改为从 `AgentMode.values()` 动态生成后，无需在每次新增 Mode 时单独更新此消息
