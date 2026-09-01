# Chat Agent — 完整架构蓝图

> 快速参考见 [AGENTS.md](../AGENTS.md)，领域术语见 [CONTEXT.md](../CONTEXT.md)，数据模型见 [data-model.md](data-model.md)，FSRS 算法见 [fsrs.md](fsrs.md)。

## 一、项目定位

一个**个人 AI 口语练习工具**，通过 AI Agent 角色扮演进行实时对话（支持英语、日语），在对话中自然纠正表达错误，对话后生成分析报告并追踪学习进度。

**学习目标**：深度掌握 LangChain4j + langgraph4j 的 Agent 开发，重点实践 StateGraph、Multi-Agent 协作、Human-in-the-Loop、Checkpoint、持久化。

---

## 二、全量决策日志（54 项）

| # | 决策点 | 选择 |
|---|--------|------|
| 1 | 核心场景 | AI 对话伙伴 |
| 2 | 对话场景 | 职场英语 + 日常闲聊 + 商务日语 |
| 3 | 交互模态 | 文本输入 + TTS 朗读（语音输入通过 OpenAI Whisper API 预留到 V2） |
| 4 | 交付形态 | Web 应用 (Spring Boot + 浏览器) |
| 5 | LLM 提供商 | DeepSeek V4 Flash Vision Exp（`deepseek-v4-flash-vision-exp`），LangChain4j 抽象层保证可替换 |
| 6 | 输入方式 | 文本输入框，iOS 用户可借助键盘原生听写。浏览器 SpeechSynthesis 做 TTS 输出（需用户手势触发） |
| 7 | Agent 核心能力 | 角色扮演 + 自然纠错 + 对话后报告 + 学习进度追踪 + 跨会话记忆（LearningProfile + MemoryCue RAG 检索） |
| 8 | 纠错机制 | Agent 口头自然纠正（融入对话不打断） |
| 9 | LangGraph 深度 | 深度：HITL + Checkpoint + 持久化 |
| 10 | Agent 架构 | 五 Agent 协作：Conversation + Correction + Report + Learning + MemoryCue，同步 Agent 统一委托 LlmReqConstructor |
| 11 | 前端技术 | 原生 HTML + Vanilla JS → React + TypeScript 全面迁移完成（Phase 4，ADR: frontend-react-migration） |
| 12 | 通信协议 | WebSocket |
| 13 | 数据库 | H2 文件模式 |
| 14 | 构建工具 | Maven + Java 17 |
| 15 | 会话控制 | 纯 UI 按钮（开始/切换/结束） |
| 16 | 纠错类型 | 5 类全追踪：语法/用词/中式英语/发音/流利度 |
| 17 | LangGraph 库 | `org.bsc.langgraph4j:langgraph4j-core:1.8.16` |
| 18 | V1 范围 | 三个 AgentMode (Workplace Standup + Daily Talk + Japanese Business) + 三 Agent + 完整报告 |
| 19 | Prompt 管理 | `resources/prompts/` 目录，per-AgentMode 子目录存放 description.txt + rules.txt + conversation-system.txt（per-Mode 骨架，fallback 根骨架）+ report.txt（per-Mode 报告，fallback 根 report） |
| 20 | WS 消息类型 | JSON：START_SESSION / USER_INPUT / END_SESSION / AGENT_STREAM_DELTA / CORRECTION_RESULT / SESSION_REPORT（详见 AGENTS.md） |
| 21 | Token 窗口 | 手动分段：UI 显示用量，80% 提醒用户结束会话 |
| 22 | 持久化粒度 | 逐条存储 Message + ErrorRecord |
| 23 | 会话恢复 | MemorySaver：页面刷新可恢复，服务重启丢失 |
| 24 | 前端展示 | 全部消息 + 折叠旧消息 |
| 25 | 持久化时机 | 会话结束时统一写入 H2 |
| 26 | E2E 回归测试 | Playwright (Java) + WireMock 3.x，DOM 级断言 |
| 27 | 用户认证 | Spring Security form login + JSESSIONID cookie + Remember-Me |
| 28 | 密码加密 | BCrypt，通过 `PasswordEncoderConfig` 提供 bean |
| 29 | 初始用户 | `application.yml` 的 `app.initial-users` 通过 `DataInitializer`（CommandLineRunner）BCrypt 哈希后插入 |
| 30 | 数据隔离 (多租户) | `Session.userId` (NOT NULL)。按 sessionId (UUID) 隔离所有会话内数据；跨会话查询 (`getHistory`, `UserProgress`) 按 userId 过滤 |
| 31 | 权限控制策略 | `app.security.permit-all-paths` YAML 配置驱动，SecurityConfig 无条件注解 |
| 32 | E2E 认证绕过 | `application-e2e.yml` 设 `permit-all-paths: [/**]` 全放行；`requireUserId` fallback 返回 `"anonymous"` |
| 33 | 模式合并 | `ScenarioType` + `PersonaType` 合并为单一 `AgentMode` 枚举，前端仅一个下拉框；提示词拆分为 per-Mode 的 `description.txt` + `rules.txt` 文件，由 `conversation-system.txt` 骨架模板组装 |
| 34 | DAILY_TALK 模式 | 新增 `AgentMode.DAILY_TALK`，以 Chris 为 persona（朋友 + 外教混搭角色）。提示词模板通用化：从 `conversation-system.txt` 移除身份硬编码，下沉到各 mode 的 `description.txt`。correction.txt / report.txt 中 "Chinese Java developer" 改为 "Chinese adult" |
| 35 | ~~Topic Memory 模式隔离~~（已过时） | Topic Memory 已被 MemoryCue 替代。MemoryCue 生成时就携带 `mode` 字段实现模式隔离，RAG 检索时通过 `userId × AgentMode` 双重过滤。详见 ADR `mode-scoped-topic-memory.md`（弃用标记） |
| 36 | 双轨记忆系统（已统一） | 原始设计为 User Memory（摘要注入）+ MemoryCue（RAG 检索）双轨并存。实施中 Topic Memory 的 Summary 直写被移除，整个记忆系统统一在 MemoryCue 管道。**V2：写入端并行运行 Assertion + MemoryCue 双管线**（共享 detectSwitches 一次调用，各自独立写入）。Assertion 提供结构化断言（Extractor + Manager 管线，per-segment × per-group 两步 LLM + Search→Judge→Merge 串行合并），MemoryCue 维持 RAG 检索端正常运行。检索端保持 MemoryCue。 |
| 37 | LLM 调用日志 + 文件日志 | 新建 `llm_call_logs` 表持久化每次 LLM 调用的完整上下文（request_prompt / system_prompt / chat_history / response_text / tokens / duration）。同步 Agent 通过 `LlmReqConstructor` 统一管理 LLM 调用生命周期与日志（含完整 sessionId/userId/agentType/mode 上下文字段），ConversationAgent 通过 `TurnProcessor` 手动注入。写入异步执行不阻塞业务。启动时自动清理 3 天前记录。新增 `logback-spring.xml`，仅 local profile 启用文件日志（DEBUG 级别，按天滚动）。 |
| 40 | LlmReqConstructor 同步 Agent 模式 | 抽取 `LlmReqConstructor` 深模块统一管理同步 Agent 的 LLM 调用生命周期，替换原有的 `TaskRunner`。Agent 构造时通过 `llmReqConstructor.register(name, task)` 注册 `LlmTaskDefinition`（systemTemplate + userTemplate + exampleMessages + paramBuilder + parser + errorStrategy），运行时通过 `llmReqConstructor.execute(name, params, ctx)` 触发 LLM 调用。Prompt 模板采用子目录结构（`system.txt` + 可选 `examples.txt`），`userTemplate` 内联为 Java 字面量。`ExampleMsgFormatter` 集中管理 XML 格式转换（`toXml` / `toXmlUserOnly`）和 few-shot 解析（`parseFewShot`），保证 few-shot 示例与真实对话数据使用一致的 `<turn>` XML 格式。调用 `ChatLanguageModel.generate(List<ChatMessage>)` 替代旧 `chat(String)`，日志完整记录 `system_prompt`、`chat_history`、`input_tokens`、`output_tokens`。`CardEnhanceService` 通过 `llmReqConstructor.chat()` 直接调用（无 Agent 注册）。`TaskName` 枚举管理 9 个任务标识（CORRECTION / REPORT / MERGE_LEARNING / CHAT_SWITCHES / GENERATE_MEMORY_CUE / EXTRACT_TOPICS / EXTRACT_STATE / JUDGE_SAME / MERGE_ASSERTION）。 |
| 41 | 会话结束管线抽取 | 抽取 `SessionComplete` 深模块：将 `ChatMessageHandler.onEndSession()` 中的报告生成、持久化、异步记忆触发的管线集中到一个简单接口后面。Handler 依赖从 7 降至 4（移除 ReportAgent/SessionDbStore/LearningProfileService/MemoryCueAgent/MemoryCueService，新增 SessionComplete），`onEndSession()` 从 45 行缩至 20 行。`SessionComplete` 内部编排：shared `detectSwitches` → `splitBySwitches` → 并行触发 `LearningProfileService` + `AssertionService` + `MemoryCueService`（后恢复并行运行，共享一次 detectSwitches 调用，消除重复 LLM 成本）。`SessionDbStore.completeSession()` 支持 null report → `SessionStatus.FAILED`。报告 LLM 失败时返回降级报告（fluencyScore=-1 哨兵值），前端条件渲染隐藏评分行。 |
| 38 | ~~Tag Consolidation~~ (废弃) | 已由 RAG 向量检索替代。tags 字段及 `StringListConverter`、`consolidateTags()` 方法、`tag-consolidation.txt` prompt 均已删除。详见 ADR `rag-memory-retrieval.md` |
| 39 | RAG 向量检索 | 用 ONNX all-MiniLM-L6-v2 (384 维) 对 MemoryCue 的 topic+summary 做向量化，存入 InMemoryEmbeddingStore（JSON 磁盘持久化到 `./data/embedding-store.json`）。每轮用户输入 (messageId ≥ 2) 触发语义检索，结果通过 MemoryCueQueue（LRU 队列，capacity topK+1）管理：首次加载（队列为空）search topK+1 条，后续 search topK 条，去重时同 cueId 刷新到队头，满容时驱逐队尾（最久未访问）。注入 System Prompt 时按 tail→head（旧→新）生成编号列表。userId × AgentMode 隔离。专用 `embeddingExecutor` 线程池 (core=2, max=2)。磁盘文件损坏时自动从 H2 重建。 |
| 42 | TimeLabel 时间感知增强 | `TimeLabel` 计算逻辑从 Duration 桶遍历改为日期+时段判断。≤5分钟 "just now"，≤1小时 "a few minutes ago"，其余按日期分段：今天按时段（last night / this morning / this afternoon / this evening / tonight），昨天同样按时段（last night / yesterday morning / yesterday afternoon / yesterday evening / last night），2天以上保持 "a few days ago" 等模糊标签。`computeLabel(Instant, Instant, ZoneId)` API 签名——时区作为显式参数传入，内部转为用户墙面时间计算标签。 |
| 43 | LLM max output tokens 按 Agent 配置 | 新增 `app.llm.max-output-tokens` 配置，支持按 Agent 类型独立设置最大输出 token 数。默认 2048，ReportAgent 使用 4096（报告需更长输出）。`LangChain4jConfig` 创建独立的 `ChatLanguageModel` bean（default / report），`LlmReqConstructor` 按 `TaskName.REPORT` 路由到对应模型。`MaxOutputTokens` 通过 `@ConfigurationProperties` 绑定，未配置的 Agent 自动回退到 default。 |
| 44 | MemoryCueQueue LRU 淘汰设计 | `MemoryCueQueue` 为有容量上限的 LRU 有序集合（capacity = topK+1），跨 Turn 存活于 ChatState。首次加载（队列空）search topK+1 条，后续 search topK 条。push 时去重：同 cueId 刷新到队头；满容时淘汰队尾（最久未访问）。fallback anchor（最新 completed session 的 last cue）生命周期约 1 轮——下一轮被 RAG 结果替代。注入 System Prompt 时按 tail→head（旧→新）生成编号列表。 |
| 45 | 闪卡模块解耦 | 独立 JPA 实体 (Card, Tag) + REST API (`FlashcardController`) + React 前端 `FlashcardPanel.tsx`。闪卡模块与现有聊天功能完全解耦——不依赖 WebSocket，不依赖 Practice session。Tag 有可空 `type` 字段，为未来 Deck 概念预留。 |
| 46 | FSRS-6 调度算法 | 纯 Java 重写 FSRS-6（21 参数，~500 行），`FsrsScheduler` 实例类 + `FsrsSchedulerConfig` 不可变配置。→ 详见 `docs/fsrs.md` |
| 47 | REST API 模式引入 | `FlashcardController` 为代码库首个 `@RestController`（`POST /api/cards/add` + `GET /api/tags`）。认证走 JSESSIONID cookie（与 WebSocket 一致），`/api/**` 不走 `permit-all-paths`（需要认证），CSRF 对 `/api/**` 在 `SecurityConfig` 中禁用。 |
| 48 | React 渐进迁移 | 引入 Vite + React 18 + TypeScript 作为前端构建工具链。Phase 1：Header.tsx + CorrectionSidebar 迁入 React。Phase 2：WebSocket 服务层 + `useReducer + context` 集中状态管理。Phase 3：MessageList + ChatInput + Footer 迁入 React，`useChatWebSocket` 移除。**Phase 4 完成**：StatusBar、ReportModal、DebugPanel、FlashcardPanel 全部迁入 React；`app.js`、`flashcard.js`、`style.css` 及 manage 页面 vanilla JS 文件全部删除；Chat 页面单根渲染（无 Portal）；`ChatProvider` 直接处理所有 WS 消息（无 vanilla bridge）。React 本地托管在 `static/shared/`，CSS Modules 隔离样式，Vitest 做组件测试，E2E 测试使用 `data-testid` 属性选择器。不引入路由/状态管理库，不做 SPA。详见 ADR `frontend-react-migration.md`。 |
| 49 | Chat 页面 React 集中状态管理 | 四期路线图：Phase 1（CorrectionSidebar 独立模块）→ Phase 2（WebSocket 服务层 + `useReducer + context`）→ Phase 3（MessageList + ChatInput + Footer）→ **Phase 4 完成**（StatusBar + ReportModal + DebugPanel + FlashcardPanel）。Phase 4 成果：`app.js` 完全删除，Chat 页面单根 `ChatPage` 组件渲染；`ChatProvider` 统一处理所有 WS 消息类型（SESSION_REPORT, ERROR, TOKEN_WARNING, STATE_UPDATE, WS_CLOSED 全部通过 `dispatch(action)` 进入 reducer）；`appStatus` 替代 `sessionStatus`，覆盖完整生命周期（Connecting→Connected→UserTurn→Processing→Warning→Error→Disconnected）；组件依赖关系完全通过 `useChatContext()`。详见 ADR `centralized-chat-state.md`。 |
| 50 | CSV 批量导入导出 | Apache Commons CSV，单 deck tag 导入/导出，前置全量校验+整体事务，cardState 文本映射。→ 详见 `docs/fsrs.md` |
| 51 | Review 模块：双端点架构 | `GET /api/review/start` + `POST /api/review/next`，统一返回 `{card, stats, preview}`。→ 详见 `docs/fsrs.md` |
| 52 | ReviewStats：日累计 | 后端 COUNT/MIN 实时查询，`todayStart` 按用户时区。→ 详见 `docs/fsrs.md` |
| 53 | 每日新卡上限后端化 | 存储于 `UserPreferences`，后端 `ReviewService` 统一读取。→ 详见 `docs/fsrs.md` |
| 54 | JAPANESE_BUSINESS 模式 | 新增 `AgentMode.JAPANESE_BUSINESS`（ビジネス日本語），取引先角色扮演。日语骨架独立（`japanese_business/conversation-system.txt`，日语标签如 `ルール:`）。Report 模式感知化（per-Mode report.txt）。Correction/MemoryCue/LearningProfile 跳过（`TurnProcessor` 和 `SessionComplete` 按 mode guard） |

---

## 三、技术栈

| 层 | 技术 | 版本 |
|---|------|------|
| 语言 | Java 17 | LTS |
| 框架 | Spring Boot | 3.4.x |
| 构建 | Maven | — |
| LLM 框架 | LangChain4j（OpenAI-compatible 适配 DeepSeek） | 1.0.0-beta2 |
| Agent 编排 | langgraph4j | 1.8.16 |
| 输入 | 文本输入框（iOS 键盘原生听写作为降级方案） | — |
| TTS | 浏览器 SpeechSynthesis（🔊 按钮手动触发） | — |
| 通信 | WebSocket | — |
| 数据库 | H2 File + Spring Data JPA | — |
| 前端 | React 18 + TypeScript（Vite Library Mode, CSS Modules） | — |
| RAG | ONNX all-MiniLM-L6-v2（384 维） | — |

> 完整 Maven 依赖见 `pom.xml`。

---

## 四、LangGraph 状态机

**架构决策**: LangGraph 图处理**单轮纠错**（仅 `correction` 节点），对话流式生成由 Service 层 (`TurnProcessor`) 管理。

### 状态定义 (ChatState extends AgentState)

```java
public class ChatState extends AgentState {
    static final String SESSION_ID   = "sessionId";
    static final String MODE         = "mode";        // AgentMode 枚举名 (String)
    static final String USER_ID      = "userId";
    static final String STATE_STATUS = "stateStatus"; // IDLE/PROCESSING/SPEAKING
    static final String MESSAGES     = "messages";    // List<MessageData> (Appender)
    static final String USER_INPUT   = "userInput";
    static final String CORRECTIONS  = "corrections"; // List<CorrectionData> (Appender)
}
```

> `Channels.base(() -> defaultValue)` 用于标量值，`Channels.appender(ArrayList::new)` 用于列表累加。**非** `Channels.of()`。

### 单轮图

```
START → correction → END  (调用 DeepSeek, 分析 5 类错误)
```

1 个节点（`CorrectionNode`）。对话流式生成由 `TurnProcessor` 直接调用 `ConversationAgent.generateStream()`，token 计数由 `TokenTracker` 管理。记忆注入窗口仅在首轮（messageId ≤ 1），判断逻辑下沉到 `ConversationAgent` 内部。

### 会话生命周期

```
[Start]
  SessionDbStore.createSession(mode, userId) → H2
  SessionService.init(sessionId, mode, userId, wsId) → ChatState + TokenTracker

[每轮 Turn]
  WebSocket USER_INPUT → TurnProcessor.processTurn()
    → memory injection (messageId ≤ 1: LearningProfile; ≥ 2: RAG via MemoryCueQueue)
    → 并行: ConversationAgent.generateStream() + graph.stream() → CorrectionNode
         （日语模式跳过 Correction）

[End Session]
  SessionService.waitForPendingCorrections(10s)
  SessionComplete.complete():
    reportAgent.generate() → 成功生成 ReportResult / 失败则 fluencyScore=-1 降级
    learningProfileService.generateLearningProfileAsync()   ─┐
    assertionService.generateAssertionsAsync(segments)       ─┤ 并行
    memoryCueService.generateCuesAsync(segments)             ─┘
  SessionService.remove() → 释放 state + TokenTracker
  → 前端 SESSION_REPORT

[Resume]
  RESUME_SESSION → 校验 userId → sessionToWs.put 覆盖旧绑定 → 返回完整 messages + corrections
  前端全量 rebuild DOM；多标签通过 Page Visibility API 自动恢复
```

### Checkpoint

```java
stateGraph.compile(CompileConfig.builder()
    .checkpointSaver(new MemorySaver())  // 页面刷新可恢复，服务重启丢失
    .build());

compiled.stream(input, RunnableConfig.builder()
    .threadId(sessionId).build());
```

---

## 五、Prompt 设计

所有 Prompt 模板位于 `src/main/resources/prompts/`，由 `PromptLoader` 加载。每个 Task 使用独立子目录：`system.txt`（系统提示词）+ 可选的 `examples.txt`（few-shot 示例，通过 `ExampleMsgFormatter.parseFewShot()` 加载）。`userTemplate` 内联为 Java 字面量（在 Agent 构造函数中）。`ExampleMsgFormatter` 集中管理 XML 格式转换（`toXml` / `toXmlUserOnly`），保证 few-shot 示例与真实对话数据使用完全一致的 `<turn>` XML 格式。

| Agent | 模板目录 | 设计要点 |
|-------|---------|---------|
| ConversationAgent | `conversation-system.txt`（骨架）+ per-Mode `description.txt` + `rules.txt` | 骨架包含 `{Description}` `{Rules}` `{memoryCues}` `{learningProfile}` 占位符；per-Mode 子目录（如 `japanese_business/`）可覆盖骨架，提供语言特定的标签（`ルール:` 等）。启动时加载到 `EnumMap`，运行时 O(1) 查取 |
| CorrectionAgent | `correction/system.txt` | 5 类纠错（GRAMMAR / WORD_CHOICE / CHINGLISH / PRONUNCIATION / FLUENCY），JSON 输出。含语音识别误判过滤规则。userTemplate: `"User's utterance: {userInput}"` |
| ReportAgent | `report/system.txt` | Per-Mode 报告模板（日语模式使用 `japanese_business/report.txt` 覆盖），JSON 格式，fluencyScore=-1 为降级哨兵值 |
| MemoryCueAgent | `memory-cue/split/system.txt` + `memory-cue/entry/system.txt` | 两步 LLM：分割检测 → 每段生成 (topic, summary)。对话 XML 由 `ExampleMsgFormatter.toXml()` 生成 |
| AssertionService | `assertion/extract-topics/` `assertion/extract-state/` `assertion/judge-same/` `assertion/merge-assertion/` | 四步 LLM：topic 抽取 → state 生成 → Judge 判断 → Merge 合并。参数化于 `{groupName}` `{groupDescription}`。few-shot 示例外置到 `examples.txt`（extract-topics + judge-same） |

> 完整 Prompt 文本见源文件。TaskName 枚举与 LlmTaskDefinition 映射见 [AGENTS.md](../AGENTS.md#tech-stack-summary)。

---

## 六、数据模型

完整实体关系图与枚举定义见 **[data-model.md](data-model.md)**。

核心表：`sessions` `messages` `corrections` `user_learning_profiles` `memory_cues` `memory_assertions` `assertion_groups` `assertion_lineage` `llm_call_logs` `cards` `tags` `card_enhancements` `watched_movies` `subtitle_lines`。

---

## 七、会话管理

| 层面 | 方案 | 关键实现 |
|------|------|---------|
| **前端展示** | 全部消息 + 折叠旧消息 | 可滚动聊天区，顶部 token 进度条，旧消息折叠到 "Show earlier" 后 |
| **写入时机** | 会话结束时统一持久化 | `SessionComplete.complete()` 内部：`reportAgent.generate()` → `sessionStore.completeSession()` → `learningProfileService.generateLearningProfileAsync()` + `assertionService.generateAssertionsAsync()` + `memoryCueService.generateCuesAsync()`（并行运行，共享 detectSwitches 一次调用） |
| **日志写入** | LLM 调用时即时异步写入 | `LlmReqConstructor.execute()`（同步 Agent）在调用点内生写入完整上下文字段；`TurnProcessor`（ConversationAgent）在 `onCompleteResponse` 时写入。通过 `llmLogExecutor` (core=2, max=4) 异步写 `llm_call_logs` 表 |
| **日志清理** | 每次启动时自动清理 | `LlmCallLogService.cleanupOnStartup()` 在 `@PostConstruct` 中删除 3 天前记录 |
| **记忆写入** | `LearningProfileService` + `AssertionService` + `MemoryCueService` 并行异步触发 | `llmRequestExecutor` (core=4, max=8) 上同时运行 Learning Profile Merge + Assertion Extractor（detectSwitches → Step1 → Step2 → embedding 索引）+ Assertion Manager（Search→Judge→Merge 串行）+ MemoryCue 两步生成（split → entry 并行）。V1: error-pattern + dev-progress 两个 group，按 `AssertionGroup.mode` 列匹配 AgentMode |
| **RAG 检索** | TurnProcessor Round 2+ 每轮触发 | `EmbeddingService.search()` 语义搜索历史 MemoryCue，userId×AgentMode 隔离。结果通过 MemoryCueQueue（LRU 队列，capacity topK+1）管理：首次加载 search topK+1 条，后续 search topK 条，去重刷新驱逐，按 tail→head 编号列表注入 System Prompt `{memoryCues}` |

### 报告弹层

```
┌────────────────────────────────────────┐
│  📊 Session Report       [X]          │
│────────────────────────────────────────│
│  角色扮演评分    82/100 (82%)          │
│  整体评估(英文)                       │
│  错误总结 (按类型分组)                │
│  关键要闻 (改进建议)                  │
└────────────────────────────────────────┘
```

> 降级报告时（fluencyScore=-1，LLM 调用失败）不显示评分行。详见 ADR `session-complete-extraction.md`。

---

## 八、模块概览

| 模块 | 包路径 | 职责 |
|------|--------|------|
| Graph | `graph/` | ChatState 容器 + CorrectionNode |
| Agents | `agent/` + `agent/common/` | Conversation·Correction·Report·Learning·MemoryCue，通过 LlmReqConstructor 统一调度。`ExampleMsgFormatter` 集中管理 XML 转换和 few-shot 解析 |
| Flashcard | `flashcard/` | FSRS-6 调度器 + 复习模式 |
| WebSocket | `websocket/` | 协议处理 + 会话生命周期 |
| REST API | `controller/` | FlashcardController · ReviewController · TuneController · MovieController · LlmReplayController |
| Services | `service/` | SessionComplete（编排）、SessionService、TurnProcessor、AssertionService、MemoryCueService、EmbeddingService 等 |
| Model | `model/` | JPA 实体 + 枚举（见 [data-model.md](data-model.md)） |
| Repository | `repository/` | Spring Data JPA |

> 完整文件树见 [AGENTS.md](../AGENTS.md)。

---

## 九、当前状态

### V1（已实现） → V2 规划

| | V1（已实现） | V2 |
|---|-------------|-----|
| **对话** | 英语（Workplace + Daily Talk）+ 日语（Business） | 更多 AgentMode |
| **Agent** | 五 Agent 全协作（Conversation + Correction + Report + Learning + MemoryCue） | 场景自动切换 |
| **记忆** | MemoryCue (RAG 向量检索, Round 2+) + LearningProfile (首轮注入) + MemoryAssertion 结构化断言写入（并行） | 检索端升级至 Assertion |
| **报告** | 错误汇总 + 评分 | 进度趋势图表 |
| **输入** | 文本输入框 + iOS 键盘听写 | 前端录音 + 后端 OpenAI Whisper API |
| **TTS** | 浏览器 SpeechSynthesis（🔊 按钮手动触发） | OpenAI TTS（自然度更高） |
| **持久化** | MemorySaver（服务重启丢失） | Redis/Postgres checkpoint（跨重启恢复） |

### 设计偏差

| 偏差 | 原始设计 | 实现 | 原因 |
|------|---------|------|------|
| 图结构 | 5 个节点（Conversation + Correction + Merge + Report + Memory） | 1 个节点（仅 Correction） | 对话流式生成不适合 StateGraph；同步 Agent 统一通过 LlmReqConstructor 管理 |
| Agent 统一 | 无正式统一，各 Agent 独立调用 ChatLanguageModel | LlmReqConstructor 深模块（system/user 模板拆分、XML 对话格式、few-shot 迁移至 Java、完整日志字段） | 统一 LLM 调用生命周期、启用结构化日志 |
| 记忆写入 | MemoryCue 替代 | MemoryCue + MemoryAssertion 并行（共享 detectSwitches） | 需要双管线：MemoryCue 维持 RAG 检索，Assertion 提供结构化去重 |
| FSRS 调度器 | ~150 行预估 | ~500 行（FsrsScheduler + FsrsSchedulerConfig） | 算法复杂度被低估；重构后新增 preview/reschedule 方法 + FsrsSchedulerConfig.merge() + parseSteps() |

### 实现阶段

| 阶段 | 范围 | 产出 |
|------|------|------|
| Phase 1 (v0.1) | 基础对话 | Spring Boot + WebSocket + H2 + 单页聊天 UI |
| Phase 2 (v0.2) | Multi-Agent + Memory | CorrectionAgent + ReportAgent + LangGraph 集成 |
| Phase 3 (v0.3) | LangGraph 深度应用 | StateGraph + Checkpoint + MemorySaver |
| Phase 4 (v1.0) | 功能完善 | LearningAgent + MemoryCueAgent 完善 + DAILY_TALK + Multi-user |
| Phase 5 (v1.5) | 表达优化 | MemoryCue RAG 检索 + FSRS 闪卡 + FSRS 优化器 + 前端 React 迁移 + 多样化 AgentMode |
| Phase 6 (v2.0) | 记忆升级 | LlmReqConstructor 统一管线 + MemoryAssertion 写入端 + MemoryCue 并行恢复 |
