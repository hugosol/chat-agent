# PRD: Memory System Optimization

**Status:** `ready-for-agent`

## Problem Statement

当前 English Coach 的记忆系统存在五个痛点：

1. **LLM 调用日志结构单一**：`llm_call_logs` 表的 `request_prompt` 字段是一个整体文本 blob，同步 Agent 和流式 Agent 的格式完全不同（前者是 `{"text":"..."}` 对象，后者是 `[{"role":"system",...},...]` 消息数组）。无法按 system_prompt 或 chat_history 维度独立查询和分析。

2. **记忆加载缺乏时间感知**：Topic Memory 和 RAG MemoryCues 注入 System Prompt 时不携带任何时间信息。Learner 无法感知"这条记忆是昨天产生的还是三个月前的"，Agent 也无法据此调整对话策略（例如追问最近的话题、跳过太旧的记忆）。

3. **Topic Memory 合并流程过度设计**：每次会话结束通过 LLM 将新旧话题摘要合并为 500 字符文本，实现跨会话话题累积。但实际上 RAG MemoryCues 已经在第二轮后提供语义检索兜底，Topic Memory 的合并不仅增加一次 LLM 调用，还使话题记忆膨胀到首轮注入的 token 预算边缘。Learner 连续对话时前后话题相关性强，合并带来的增量信息有限。

4. **修正系统对语音识别误差过于敏感**：Learner 通过系统语音输入（iOS 键盘麦克风）产生的文本可能包含语音识别错误（如 their/there、to/too 等同音异义词），修正 Agent 以文本形式接收输入后无法区分"Learner 英语用错了"和"语音识别转错了"，导致过度修正。

5. **Prompt 中的数字硬编码**：`memory-profile.txt` 的 400 字符上限、`memory-cue-entry.txt` 的 3-7 词/2-4 句约束直接写在 prompt 模板中。调整这些参数需要修改文件重新部署，无法通过 `application.yml` 运行时配置。

## Solution

五个独立优化协同改进记忆系统：

1. **LLM 调用日志字段拆分**：`llm_call_logs` 表新增 `system_prompt` 和 `chat_history` 两个独立 CLOB 字段，替代单一 `request_prompt` blob。同步 Agent 的 prompt 存入 `system_prompt`（`chat_history` 为 null）；流式 Agent 的 JSON 消息数组解析后分别存入。旧 `request_prompt` 字段保留，不删除。

2. **时间感知记忆加载**：新建 `TimeLabel` 枚举（9 个英文时间分段：just now → a while ago），以绝对时间阈值（5 分钟到 365 天）按顺序匹配。Topic Memory 和每条 RAG MemoryCue 在注入 System Prompt 时自动添加 `[from yesterday]` 前缀标签，让 Agent 感知记忆的时效性。

3. **取消 Topic Memory 合并**：删除 `MemoryAgent.mergeTopic()` LLM 合并步骤，会话结束时 Report 生成的 `topicSummary` 直接作为新版本写入 Topic Memory（INSERT 新行，version 递增，旧版本保留）。首轮注入保留（从 H2 加载最新版），Learning Profile 合并保留。历史话题由 RAG MemoryCues 语义检索补充。

4. **修正提示词宽容规则**：在 `correction.txt` 开头新增一条通用规则——如果某个错误可能是语音转文字误判导致的（同音异义词混淆、发音相近词误识别），则不标注。应用于所有 5 个错误类别。

5. **Prompt 数字参数化**：将 `memory-profile.txt` 的 "under 400 characters" 和 `memory-cue-entry.txt` 的 "3-7 words" / "2-4 sentences" 替换为 `AppProperties` 注入的占位符。新增 `application.yml` 配置项：`app.memory.profile-max-length`、`app.memory.cue-topic-max-words`、`app.memory.cue-summary-max-sentences`。

## User Stories

1. 作为一名开发者，当需要分析某次纠正的 LLM 行为时，我希望能在 `llm_call_logs` 表中独立查询 `system_prompt` 字段，而不是在一个整体文本 blob 中手动解析格式。
2. 作为一名开发者，当需要复现一次 ConversationAgent 调用的上下文时，我希望 `chat_history`（用户/Agent 消息历史）和 `system_prompt`（系统指令 + 记忆注入）在日志表中分开存储，一眼看清调用结构。
3. 作为一名开发者，我希望旧的 `request_prompt` 字段保留不动，已有的查询和数据分析脚本不受影响。
4. 作为一名 Learner，当我开始一次新的 Practice Session 时，我希望 Agent 知道我的话题记忆是"昨天的"还是"几周前的"，从而自然地问"你昨天提到的那个 pipeline 迁移完成了吗？"而不是泛泛地说"你之前提过 pipeline 迁移"。
5. 作为一名 Learner，在连续对话中，如果 RAG 检索到一条旧的话题提示，我希望 Agent 能标注"这是你一周前提到的"，帮助我更好地跟随对话上下文。
6. 作为一名开发者，我希望时间分段（just now、yesterday、a week ago 等）作为 `TimeLabel` 枚举定义在代码中，新增或调整时间段只需修改枚举常量，无需散落到配置文件。
7. 作为一名 Learner，我希望每次新会话的首轮注入只携带最近一次会话的话题摘要（而非累积的跨会话话题），让首轮注入保持简洁和相关。
8. 作为一名 Learner，在会话第 2 轮后，历史话题仍然可以通过语义搜索找回——即使首轮注入不再累积历史话题。
9. 作为一名开发者，取消 Topic Memory 合并后，每次会话结束少一次 LLM 调用，简化了异步流程，也降低了 `memory-topic.txt` prompt 的维护成本。
10. 作为一名 Learner，当我通过手机麦克风说出 "I went to their house" 而被 iOS 识别为 "I went to there house" 时，修正系统不应标注为 WORD_CHOICE 错误，因为可能是语音识别导致的。
11. 作为一名 Learner，当我通过手机麦克风说出 "I think so" 而识别为 "I sink so" 时，修正系统不应标注为 PRONUNCIATION 或 WORD_CHOICE 错误，给它"可能是语音识别误判"的怀疑空间。
12. 作为一名开发者，当我想调整 Learner 画像（Learning Profile）合并后的文本长度上限时，我只需修改 `application.yml` 中的 `app.memory.profile-max-length`，无需改动 prompt 文件重新部署。
13. 作为一名开发者，当我想调整 MemoryCue 生成时 topic 名称的词汇数或 summary 的句子数约束时，我只需修改 `application.yml` 中的对应配置项，无需修改 prompt 文件。
14. 作为一名 Learner，这些优化对我的日常使用体验是透明的——对话仍然流畅，修正仍然准确，记忆仍然有效。
15. 作为一名 Learner，取消 Topic Memory 合并意味着首轮注入的话题更精炼（仅上一次会话），不再混杂几个月前的旧话题。

## Implementation Decisions

### 1. LLM Call Log schema 扩展

`llm_call_logs` 表新增两个 CLOB 列，`request_prompt` 列保留不动。

同步 Agent（Correction、Report、Memory、MemoryCue）通过 `LoggableChatModel` 包装器拦截：原有的单文本 prompt 存入 `system_prompt`，`chat_history` 为 null。同步 Agent 日志的 `sessionId`、`userId`、`agentType`、`mode` 字段保持为 null（不改动）。

流式 Agent（Conversation）通过 `TurnProcessor.onCompleteResponse()` 回调写入：`conversationAgent.buildPromptJson()` 构建的 JSON 消息数组解析为 system 消息（存入 `system_prompt`）和 user/assistant 消息列表（序列化为 JSON 数组存入 `chat_history`）。

### 2. TimeLabel 枚举设计

新建 `TimeLabel` Java 枚举，每个常量包含英文标签（`label`）和最大时长（`Duration`）。按声明顺序（从小到大）匹配第一个满足 `elapsed ≤ maxDuration` 的条件。超过最大阈值（365 天）的统一 fallback 到最后一个标签 `"a while ago"`。

枚举常量定义：
- `JUST_NOW("just now", 5分钟)`
- `A_FEW_MINUTES_AGO("a few minutes ago", 1小时)`
- `EARLIER_TODAY("earlier today", 12小时)`
- `YESTERDAY("yesterday", 48小时)`
- `A_FEW_DAYS_AGO("a few days ago", 7天)`
- `ABOUT_A_WEEK_AGO("about a week ago", 14天)`
- `A_FEW_WEEKS_AGO("a few weeks ago", 30天)`
- `ABOUT_A_MONTH_AGO("about a month ago", 60天)`
- `A_WHILE_AGO("a while ago", 365天，同时作为 >365天的兜底)`

`UserMemory` 和 `MemoryCue` 实体各新增 `@Transient getTimeLabel(LocalDateTime referenceTime)` 方法，调用 `TimeLabel.computeLabel()` 静态方法。

### 3. 时间标签注入格式

采用 `[from yesterday]` 前缀标签格式，直接拼接到记忆文本前。Topic Memory 为单条：`[from yesterday] Learner discussed Q4 deliverables...`。RAG MemoryCues 为逐条标注：`[from yesterday] Python CI: discussed migration... , as well as, [from last week] Q4 deliverables: prioritizing backend...`。

时间标签计算和注入在 `ConversationAgent.buildSystemContent()` 中完成，以当前时刻为参考时间。

### 4. Topic Memory 合并取消

删除 `MemoryAgent.mergeTopic()` 方法。`MemoryService.generateMemoryAsync()` 中取消对应的异步任务，改为异步将 Report 的 `topicSummary` 直接保存为新的 `UserMemory` 行（INSERT 新行，`version` 递增，旧版本保留不删）。

`memory-topic.txt` prompt 模板文件不再被调用，标记为废弃但不删除（保留作为历史参考）。

Learning Profile 的 `mergeProfile()` 流程完全保留（LLM 合并 + 版本递增）。

首轮注入不变：`SessionService.init()` 仍然加载最新 Topic Memory 和 Learning Profile 到 CoachState，`TurnProcessor.processTurn()` 在 messageId ≤ userMemoryRounds 时注入 `MemoryContent(topicSummary, learningProfile, null)`。

### 5. 修正宽容规则

在 `correction.txt` 文件开头新增一条通用规则段落，不受类别限制，适用于所有 5 个错误类型。措辞要点：如果某个"错误"可能是语音转文字（speech-to-text misrecognition）导致的——包括同音异义词混淆（their/there）或发音相近词误识别——应给予怀疑空间，不标注。仅当证据确凿（明显的语法结构错误、不符合英语表达习惯的中式直译等）时才标注。

不影响现有 5 个类别的定义和输出 JSON 格式。

### 6. Prompt 参数化

新增三个 `application.yml` 配置项，统一在 `app.memory` 命名空间下：

| 配置键 | 默认值 | 说明 |
|--------|--------|------|
| `app.memory.profile-max-length` | `400` | Learning Profile 合并后文本最大字符数 |
| `app.memory.cue-topic-max-words` | `7` | MemoryCue topic 名称最大词汇数 |
| `app.memory.cue-summary-max-sentences` | `4` | MemoryCue summary 最大句子数 |

`AppProperties` 新增对应字段及 `@ConfigurationProperties` 绑定。`MemoryAgent` 和 `MemoryCueAgent` 通过构造注入读取配置值，在调用 `PromptLoader` 后自行 `replace()` 占位符。

`memory-profile.txt` 中 `"under 400 characters"` 替换为 `"under {profileMaxLength} characters"`。`memory-cue-entry.txt` 中 `"3-7 words"` 替换为 `"3-{cueTopicMaxWords} words"`，`"2-4 sentences"` 替换为 `"2-{cueSummaryMaxSentences} sentences"`。注意保留最小值的硬编码（3 词、2 句），仅将上限参数化——因为 topic 名称最少 3 词和 summary 最少 2 句是质量底线，不应参数化。

### 7. 不影响项

- `memory-topic.txt` 的 "under 500 characters" 不参数化（文件已废弃不再调用）
- `report.txt` 的 "3-4 sentences" / "5-8 sentences" 不参数化（用户未选择）
- `memory-cue-split.txt` 不涉及数字硬编码，不改动
- AgentMode prompt（`description.txt` / `rules.txt`）不改动
- WebSocket 协议不改动
- 前端 HTML/JS/CSS 不改动
- `correction.txt` 的输出 JSON 格式不变
- 同步 Agent 日志的 sessionId/userId/agentType/mode 继续为 null（不补全）

## Testing Decisions

### 测试原则

E2E 测试验证外部可观察行为（WebSocket 消息、DOM 状态、H2 数据），不测试实现细节。单元测试验证纯逻辑模块（TimeLabel 枚举、AppProperties 绑定）。

### 需要测试的模块

**单元测试：**
- `TimeLabel` 枚举：验证每个阈值边界的标签计算正确性（边界值测试：刚好 5 分钟、5 分 1 秒、1 小时、365 天+1 秒等）

**E2E 测试调整：**
- `EnglishCoachMemoryIT`：当前验证 "User Memory v1→v2 merge"。需调整为验证 Topic Memory 直接写入为新版本（两场连续会话后 v2 的 topicSummary 仅包含第二场会话话题，v1 包含的第一场话题保留在历史版本中不变）。Learning Profile merge 保留的验证不变。
- `EnglishCoachMemoryCueIT`：验证 MemoryCue 生成流程（detectSwitches + generateCue）不受影响，但需确认时间标签在注入中的正确性（可通过检查 System Prompt 内容验证）。

### 测试先例

- E2E 测试：参照 `EnglishCoachMemoryIT.java` 的模式（Playwright + WireMock，DOM 等待断言，H2 查询验证）
- 单元测试：参照 `LoggableChatModelTest.java` 的单元测试风格（Mock Bean，验证返回值和异常处理）
- 时间标签测试可参照 `LlmCallLogServiceTest.java` 的数据驱动测试模式

## Out of Scope

- 同步 Agent 日志的 sessionId/userId/agentType/mode 补全（保持为 null）
- `request_prompt` 字段的删除或数据迁移
- Topic Memory 合并的历史数据清理（旧版本 UserMemory 行保留）
- Report 生成的 topicSummary 长度压缩（取消合并后无 LLM 压缩步骤，topicSummary 直接写入）
- 时间标签的多语言支持（固定英文）
- `TimeLabel` 枚举的动态加载（编译时固定，不支持运行时新增分段）
- 修正系统的 PRONUNCIATION 类别删除
- `report.txt` 的数字参数化
- ConversationAgent 的 token 预算参数化
- `memory-topic.txt` 文件的删除（保留但不调用）

## Further Notes

- 取消 Topic Memory 合并后，`MemoryType` 枚举的 `TOPIC_SUMMARY` 值保留不变——它仍然标识 Topic Memory 类型的 UserMemory 行，只是写入方式从"LLM 合并"变为"直接写入新版本"。
- `EnglishCoachMemoryIT` E2E 测试的 Mock 数据需要更新：移除 topic merge 相关的 WireMock stub，添加 topicSummary 直写的验证逻辑。
- 同步 Agent 日志补全（注入 sessionId/userId/agentType/mode）是下一步优化方向，本次不做。补齐后 `agentType` 枚举应为：`CONVERSATION`、`CORRECTION`、`REPORT`、`MEMORY_TOPIC`、`MEMORY_PROFILE`、`MEMORY_CUE_SPLIT`、`MEMORY_CUE_ENTRY`。
- `llm_call_logs` 新增字段后，旧记录的 `system_prompt` 和 `chat_history` 为 null，数据仍在 `request_prompt` 中可查，无数据丢失。
- TimeLabel 处于 `a while ago` 范围的记忆（>365天）仍然可能被 RAG 检索出来——这是期望行为，语义相似性独立于时效性。时间标签仅提供上下文提示，不参与检索过滤。
