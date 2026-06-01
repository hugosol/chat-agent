# Chat Agent — 完整架构蓝图

## 一、项目定位

一个**个人英语口语练习工具**，通过 AI Agent 角色扮演进行**全语音双向英文对话**，在对话中自然纠正表达错误，对话后生成分析报告并追踪学习进度。

**学习目标**：深度掌握 LangChain4j + langgraph4j 的 Agent 开发，重点实践 StateGraph、Multi-Agent 协作、Human-in-the-Loop、Checkpoint、持久化。

---

## 二、全量决策日志（35 项）

| # | 决策点 | 选择 |
|---|--------|------|
| 1 | 核心场景 | AI 英语对话伙伴 |
| 2 | 对话场景 | 职场英语 + 技术演讲练习 |
| 3 | 交互模态 | 文本输入 + TTS 朗读（语音输入通过 OpenAI Whisper API 预留到 V2） |
| 4 | 交付形态 | Web 应用 (Spring Boot + 浏览器) |
| 5 | LLM 提供商 | DeepSeek V4 FAST，LangChain4j 抽象层保证可替换 |
| 6 | 输入方式 | 文本输入框，iOS 用户可借助键盘原生听写。浏览器 SpeechSynthesis 做 TTS 输出（需用户手势触发） |
| 7 | Agent 核心能力 | 角色扮演 + 自然纠错 + 对话后报告 + 学习进度追踪 + 跨会话记忆（User Memory + MemoryCue 双轨） |
| 8 | 纠错机制 | Agent 口头自然纠正（融入对话不打断） |
| 9 | LangGraph 深度 | 深度：HITL + Checkpoint + 持久化 |
| 10 | Agent 架构 | 五 Agent 协作：Conversation + Correction + Report + Learning + MemoryCue，同步 Agent 统一委托 TaskRunner |
| 11 | 前端技术 | 原生 HTML + Vanilla JS → React + TypeScript 渐进迁移中（ADR: frontend-react-migration） |
| 12 | 通信协议 | WebSocket |
| 13 | 数据库 | H2 文件模式 |
| 14 | 构建工具 | Maven + Java 17 |
| 15 | 会话控制 | 纯 UI 按钮（开始/切换/结束） |
| 16 | 纠错类型 | 5 类全追踪：语法/用词/中式英语/发音/流利度 |
| 17 | LangGraph 库 | `org.bsc.langgraph4j:langgraph4j-core:1.8.16` |
| 18 | V1 范围 | 两个 AgentMode (Workplace Standup + Daily Talk) + 三 Agent + 完整报告 |
| 19 | Prompt 管理 | `resources/prompts/` 目录，per-AgentMode 子目录存放 description.txt + rules.txt |
| 20 | WS 消息协议 | JSON：START_SESSION / USER_INPUT / END_SESSION / AGENT_STREAM_DELTA / CORRECTION_RESULT / SESSION_REPORT |
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
| 35 | Topic Memory 模式隔离 | `UserLearningProfile` 新增可空 `mode` 字段：`LEARNING_PROFILE` 记 null（跨模式共享）。模式越多行数越多，但避免了引入 `TOPIC_SUMMARY_DAILY_TALK` 等新 LearningType |
| 36 | 双轨记忆系统 | User Memory（Topic 直接写入最新版本 + Learning Profile LLM 合并，注入 System Prompt，首轮生效）与 MemoryCue（结构化 topic/summary，独立 `memory_cues` 表）并存。MemoryCue 通过两步 LLM（话题切换检测 + 分段摘要）在会话结束时异步生成，完成后由 EmbeddingService 向量化存入 InMemoryEmbeddingStore（JSON 磁盘持久化）。messageId ≤ 1 注入 User Memory，messageId ≥ 2 通过 MemoryCueQueue（LRU 队列，capacity topK+1，跨 Turn 存活于 ChatState，去重刷新驱逐）管理 RAG 语义检索结果，编号列表注入 System Prompt |
| 37 | LLM 调用日志 + 文件日志 | 新建 `llm_call_logs` 表持久化每次 LLM 调用的完整上下文（request_prompt / system_prompt / chat_history / response_text / tokens / duration）。同步 Agent 通过 `TaskRunner` 统一管理 LLM 调用生命周期与日志，ConversationAgent 通过 `TurnProcessor` 手动注入。写入异步执行不阻塞业务。启动时自动清理 3 天前记录。新增 `logback-spring.xml`，仅 local profile 启用文件日志（DEBUG 级别，按天滚动）。 |
| 40 | TaskRunner 同步 Agent 模式 | 抽取 `TaskRunner` 深模块统一管理同步 Agent 的 LLM 调用生命周期。Agent 构造时通过 `runner.register(name, task)` 注册 `TaskDefinition`（模板 + paramBuilder + parser + errorStrategy），运行时通过 `runner.requestModel(name, params, ctx)` 触发 LLM 调用。`TaskName` 枚举管理 5 个任务标识（CORRECTION / REPORT / MERGE_LEARNING / CHAT_SWITCHES / GENERATE_MEMORY_CUE）。删除 `LoggableChatModel` 包装层，日志能力由 TaskRunner 内生提供，含完整 sessionId/userId/agentType/mode 上下文字段。`MemoryAgent` 重命名为 `LearningAgent`（职责退化，仅保留 learningProfile 合并）。
| 41 | 会话结束管线抽取 | 抽取 `SessionComplete` 深模块：将 `ChatMessageHandler.onEndSession()` 中的报告生成、持久化、异步记忆触发的 3 步管线集中到一个简单接口后面。Handler 依赖从 7 降至 4（移除 ReportAgent/SessionDbStore/LearningProfileService/MemoryCueService，新增 SessionComplete），`onEndSession()` 从 45 行缩至 20 行。`SessionDbStore.completeSession()` 支持 null report → `SessionStatus.FAILED`。报告 LLM 失败时返回降级报告（fluencyScore=-1 哨兵值），前端条件渲染隐藏评分行。 |
| 38 | ~~Tag Consolidation~~ (废弃) | 已由 RAG 向量检索替代。tags 字段及 `StringListConverter`、`consolidateTags()` 方法、`tag-consolidation.txt` prompt 均已删除。详见 ADR `rag-memory-retrieval.md` |
| 39 | RAG 向量检索 | 用 ONNX all-MiniLM-L6-v2 (384 维) 对 MemoryCue 的 topic+summary 做向量化，存入 InMemoryEmbeddingStore（JSON 磁盘持久化到 `./data/embedding-store.json`）。每轮用户输入 (messageId ≥ 2) 触发语义检索，结果通过 MemoryCueQueue（LRU 队列，capacity topK+1）管理：首次加载（队列为空）search topK+1 条，后续 search topK 条，去重时同 cueId 刷新到队头，满容时驱逐队尾（最久未访问）。注入 System Prompt 时按 tail→head（旧→新）生成编号列表。userId × AgentMode 隔离。专用 `embeddingExecutor` 线程池 (core=2, max=2)。磁盘文件损坏时自动从 H2 重建。 |
| 42 | TimeLabel 时间感知增强 | `TimeLabel` 计算逻辑从 Duration 桶遍历改为日期+时段判断。≤5分钟 "just now"，≤1小时 "a few minutes ago"，其余按日期分段：今天按时段（last night / this morning / this afternoon / this evening / tonight），昨天同样按时段（last night / yesterday morning / yesterday afternoon / yesterday evening / last night），2天以上保持 "a few days ago" 等模糊标签。`computeLabel(LocalDateTime, LocalDateTime)` API 签名不变。 |
| 43 | LLM max output tokens 按 Agent 配置 | 新增 `app.llm.max-output-tokens` 配置，支持按 Agent 类型独立设置最大输出 token 数。默认 2048，ReportAgent 使用 4096（报告需更长输出）。`LangChain4jConfig` 创建独立的 `ChatLanguageModel` bean（default / report），`TaskRunner` 按 `TaskName.REPORT` 路由到对应模型。`MaxOutputTokens` 通过 `@ConfigurationProperties` 绑定，未配置的 Agent 自动回退到 default。 |
| 44 | MemoryCueQueue LRU 淘汰设计 | `MemoryCueQueue` 为有容量上限的 LRU 有序集合（capacity = topK+1），跨 Turn 存活于 ChatState。首次加载（队列空）search topK+1 条，后续 search topK 条。push 时去重：同 cueId 刷新到队头；满容时淘汰队尾（最久未访问）。fallback anchor（最新 completed session 的 last cue）生命周期约 1 轮——下一轮被 RAG 结果替代。注入 System Prompt 时按 tail→head（旧→新）生成编号列表。 |
| 45 | 闪卡模块解耦 | 独立 JPA 实体 (Card, Tag) + REST API (`FlashcardController`) + 前端 `flashcard.js`。闪卡模块与现有聊天功能完全解耦——不依赖 WebSocket，不依赖 Practice session。Tag 有可空 `type` 字段，为未来 Deck 概念预留。 |
| 46 | FSRS-6 调度算法 | 纯 Java 重写 FSRS-6（21 参数，~300 行），无 JNI 依赖。`FsrsScheduler` 为无状态纯函数——`initNewCard()` 返回 py-fsrs 兼容初始态（用于测试验证），`createInitState()` 返回 PRD 默认值（用于 Card 实体初始化）。12 个单元测试：8 个来自 PRD 测试向量 + 4 个来自 ts-fsrs（首次复习值、retrievability）。Fuzz 使用自研 `AleaPrng`（Johannes Baagøe 算法 Java 端口）替代 `java.util.Random`——seed(42)→12 天、seed(12345)→11 天，精准匹配 PRD 预期值。 |
| 47 | REST API 模式引入 | `FlashcardController` 为代码库首个 `@RestController`（`POST /api/cards/add` + `GET /api/tags`）。认证走 JSESSIONID cookie（与 WebSocket 一致），`/api/**` 不走 `permit-all-paths`（需要认证），CSRF 对 `/api/**` 在 `SecurityConfig` 中禁用。 |
| 48 | React 渐进迁移 | 引入 Vite + React 18 + TypeScript 作为前端构建工具链。首阶段：`shared/nav.js` → `Nav.tsx`。React 本地托管在 `static/shared/`，CSS Modules 隔离样式，Vitest 做组件测试。Vite Library Mode 输出 IIFE bundle，暴露全局命名空间。构建挂接到 Maven `exec-maven-plugin` 的 `process-resources` 阶段。不引入路由/状态管理库，不做 SPA。详见 ADR `frontend-react-migration.md`。 |

---

## 三、技术栈

| 层 | 技术 | 说明 |
|---|------|------|
| 语言 | Java 17 | LTS |
| 框架 | Spring Boot 3.4.x | Web + WebSocket + JPA |
| 构建 | Maven | 标准项目结构 |
| LLM 框架 | LangChain4j | OpenAI-compatible 适配 DeepSeek |
| Agent 编排 | **langgraph4j** 1.8.16 | `org.bsc.langgraph4j` (独立组织，非 langchain4j 子项目) |
| 输入 | 文本输入框 | iOS 键盘原生听写作为降级方案 |
| TTS | 浏览器 SpeechSynthesis | 通过消息气泡 🔊 按钮用户手势触发播放（规避 iOS Safari 自动播放限制） |
| 通信 | WebSocket | Spring WebSocketHandler |
| 数据库 | H2 File | Spring Data JPA |
| 前端 | React 18 + TypeScript (Vite) + Vanilla JS（渐进迁移）| 多页面（Library Mode bundle 共用）|

### 关键 Maven 依赖

```xml
<properties>
    <langchain4j.version>1.0.0-beta1</langchain4j.version>
    <langgraph4j.version>1.8.16</langgraph4j.version>
</properties>
<dependencies>
    <!-- langgraph4j -->
    <dependency>
        <groupId>org.bsc.langgraph4j</groupId>
        <artifactId>langgraph4j-core</artifactId>
    </dependency>
    <!-- LangChain4j -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j</artifactId>
    </dependency>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-open-ai</artifactId>
    </dependency>
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-websocket</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
    </dependency>
    <!-- Spring Security -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
</dependencies>
```

---

## 四、LangGraph 状态机（实现版本）

**架构决策**: LangGraph 图处理**单轮纠错**（仅 `correction` 节点），对话流式生成由 Service 层 (`TurnProcessor`) 管理。

### 状态定义 (ChatState extends AgentState)

```java
public class ChatState extends AgentState {
    static final String SESSION_ID        = "sessionId";
    static final String MODE              = "mode";               // AgentMode 枚举名 (String)
    static final String USER_ID           = "userId";             // 当前用户标识
    static final String STATE_STATUS      = "stateStatus";        // IDLE/PROCESSING/SPEAKING
    static final String MESSAGES          = "messages";           // List<MessageData> (Appender)
    static final String USER_INPUT        = "userInput";          // 用户输入文本
    static final String CORRECTIONS       = "corrections";        // List<CorrectionData> (Appender)
}
```

> **注意**: `Channels.base(() -> defaultValue)` 用于标量值，`Channels.appender(ArrayList::new)` 用于列表累加。这是 langgraph4j 1.8.16 的正确 API（非 `Channels.of()`）。

### 单轮图结构

```
START → correction → END
          (调用 DeepSeek, 分析 5 类错误)
```

> **实现版本**: 图只有 1 个节点。`ConversationNode` 和 `MergeResponseNode` 已移除——对话流式生成改为 `TurnProcessor` 直接调用 `ConversationAgent.generateStream()`，token 计数由 `TokenTracker` 管理。记忆注入窗口仅在首轮（messageId ≤ 1），判断逻辑下沉到 `ConversationAgent` 内部。

### 节点职责（1 个节点）

| 节点 | 实现类 | 职责 |
|------|--------|------|
| `correction` | `CorrectionNode` | 从 AgentState 读取 `userInput`，调用 `CorrectionAgent.analyze()`，输出 `corrections` 列表 |

### 会话生命周期（由 Service 层管理）

```
[用户 Start Session]
  → SessionDbStore.createSession(mode, userId) → H2 写入 Session + userId
  → SessionService.init(sessionId, mode, userId, wsId) → 创建 ChatState (含 MODE) → activeStates Map + TokenTracker 初始化

[每轮对话]
  → WebSocket 收到 USER_INPUT
  → TurnProcessor.processTurn(sessionId, userInput, messageId, callback)
    → messageId ≤ 1: 注入 UserLearningProfile (learningProfile)
    → messageId ≥ 2: MemoryCueQueue.isEmpty() ? search(topK+1) : search(topK) → push results → MemoryContent(cueMatches)
    → 两路 CompletableFuture 并行:
      A) ConversationAgent.generateStream(history, mode, MemoryContent, messageId, handler) → 流式推送到前端
      B) graph.stream(input, config) → CorrectionNode → 纠错结果异步推送
  → 回调通过 TurnProcessor.TurnCallback 通知 ChatMessageHandler

[用户 End Session]
  → SessionService.waitForPendingCorrections(sessionId, 10s) → 等待所有 pending correction 完成（超时则 cancel）
  → SessionService 收集状态数据（messages, corrections, userId, mode）
  → SessionComplete.complete(sessionId, messages, corrections, userId, mode)
    → 报告 LLM 成功 → 生成 ReportResult；失败 → 降级报告（fluencyScore=-1）
    → SessionDbStore.completeSession(sessionId, messages, corrections, report) → H2 持久化（null report → SessionStatus.FAILED）
    → LearningProfileService.generateMemoryAsync(userId, report, mode, sessionId) → 异步保存 Topic 摘要 + 合并 Profile 记忆
    → MemoryCueService.generateCuesAsync(sessionId, userId, mode, messages) → 异步生成结构化 MemoryCue
  → SessionService.remove(sessionId) → 释放 state + TokenTracker + sessionToWs 映射
  → 发送 SESSION_REPORT 到前端（降级报告时 fluencyscore=-1，前端隐藏评分行）

[会话恢复 RESUME_SESSION]
  → ChatMessageHandler.onResumeSession() 校验 userId 匹配
  → sessionToWs.put(sessionId, newWsId) 覆盖旧绑定
  → 返回 SESSION_RESUMED 包含完整 messages + corrections + tokenUsage
  → 前端 handleSessionResumed() 全量重建 DOM

[多标签]
  → sessionToWs 是一对一映射 (sessionId → wsId)
  → Tab B RESUME_SESSION → put 覆盖 Tab A 绑定
  → Tab A 重新激活 → Page Visibility API → 自动 RESUME_SESSION → 全量重建 UI
```

### Checkpoint 配置

```java
var compiled = stateGraph.compile(CompileConfig.builder()
    .checkpointSaver(new MemorySaver())  // 页面刷新可恢复，服务重启丢失
    .build());

compiled.stream(input, RunnableConfig.builder()
    .threadId(sessionId)
    .build());
```

---

## 五、三 Agent Prompt 设计

### 1. ConversationAgent (骨架模板 `prompts/conversation-system.txt` + per-Mode 文件)

**骨架模板** — 所有 AgentMode 共用：
```
{Description}

{Rules}

{lastConversation}

{memoryCues}

{learningProfile}
{activeEngagement}
```

**Per-Mode 模板** — 存放在 `prompts/{templatePath}/` 子目录下：

```
prompts/workplace_standup/
├── description.txt    ← 身份声明 + 场景描述（完整自然语言）
└── rules.txt          ← 行为约束规则（回复长度、纠错方式、语气等）

prompts/daily_talk/
├── description.txt    ← Chris 人设：朋友+外教混搭
└── rules.txt          ← 10 条：轻松闲聊、教地道表达、补充词汇、文化背景解释等
```

**ConversationAgent 构造时**遍历所有 `AgentMode.values()`，通过 `PromptLoader` 加载每个 Mode 的 `description.txt` 和 `rules.txt` 到 `EnumMap`，请求时 O(1) 查取并替换 `{Description}` / `{Rules}` 占位符。

> `{Description}` → `description.txt` 内容（含 "You are..." 身份声明 + 场景）  
> `{Rules}` → `rules.txt` 内容  
> 模板内容完全由 `.txt` 文件定义，Java 枚举只携带 `templatePath` 定位子目录。

### 2. CorrectionAgent (`prompts/correction.txt`)

```
You are an English coach analyzing a Chinese adult's spoken English.

CRITICAL RULE — Speech-to-Text Misrecognition: If an apparent "error" could be
a speech recognition mistake (homophone confusion: their/there; phonetically
similar words: think/sink), do NOT flag it. Only flag errors with clear evidence
(grammar structure errors, Chinese-to-English literal translations).

Given the user's latest utterance, identify errors in these 5 categories:

1. GRAMMAR: tense, subject-verb agreement, articles, prepositions, word order
2. WORD_CHOICE: incorrect word, register mismatch (formal/casual)
3. CHINGLISH: literal Chinese→English translation, unnatural phrasing
4. PRONUNCIATION: (text-inferred) likely pronunciation issues (th→s, r→l, etc.)
5. FLUENCY: missing transitions, filler words, choppy sentences

Output format (JSON):
[
  {
    "type": "GRAMMAR|WORD_CHOICE|CHINGLISH|PRONUNCIATION|FLUENCY",
    "original": "...",
    "corrected": "...",
    "explanation": "Brief explanation in Chinese for the learner"
  }
]

If no errors found, return empty array [].

User's utterance: "{userInput}"
```

### 3. ReportAgent (`prompts/report.txt`)

```
You are an English coach generating a session summary report for a learner.

Given the full conversation history and all error corrections from this session, generate:

1. **Overall Assessment** (3-4 sentences in English + Chinese translation)
2. **Error Summary**: Group by error type, count each, list top 3 most frequent errors
3. **Fluency Score**: 1-10 rating with 1-sentence justification
4. **Key Takeaway**: One actionable improvement focus for next session

Conversation: {fullConversation}
Errors: {allCorrections}
```

---

## 六、数据模型（H2 / JPA）

```
┌─────────────┐     ┌──────────────┐     ┌────────────────┐                            
│    User     │1───*│   Session   │1───*│    Message      │1───*│ ErrorRecord     │
│─────────────│     │─────────────│     │────────────────│     │─────────────────│
│ id (PK)     │     │ id (PK)     │     │ id (PK)        │     │ id (PK)         │
│ username    │     │ userId (FK) │     │ sessionId(FK)  │     │ messageId(FK)   │
│ password    │     │ mode        │     │ role (Enum)    │     │ type (Enum)     │
│ createTime  │     │ startTime   │     │ content        │     │ originalText    │
│ updateTime  │     │ endTime     │     │ tokenCount     │     │ correctedText   │
└─────────────┘     │ status      │     └────────────────┘     │ explanation     │
                    │ status      │                            └─────────────────┘
                    └─────────────┘
                          1
                          │
              ┌───────────┼───────────┬───────────────┐
              │           │           │               │
    ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌─────────────────────────┐
     │SessionReport │ │ UserProgress │ │UserLearningProfile│ │       MemoryCue         │
    │──────────────│ │──────────────│ │──────────────│ │─────────────────────────│
    │ id (PK)      │ │ id (PK)      │ │ id (PK)      │ │ id (PK)                 │
    │ sessionId(FK)│ │ userId (UQ)  │ │ userId       │ │ sessionId                │
    │ summary      │ │ totalSessions│ │ type (Enum)  │ │ userId                   │
    │ fluencyScore │ │ totalMinutes │ │ content      │ │ mode (AgentMode Enum)    │
    │ keyTakeaway  │ │ errorStats   │ │ version      │ │ segmentIndex             │
    │              │ │              │ │              │ │ topic                    │
    └──────────────┘ └──────────────┘ │ sessionId    │ │ summary                 │
                                      └──────────────┘  │ status (MemoryCueStatus) │
                                                        └─────────────────────────┘
                                                         ┌─────────────────────────┐
                                                         │      LlmCallLog         │
                                                         │─────────────────────────│
                                                         │ id (PK)                 │
                                                         │ sessionId (nullable)    │
                                                         │ userId (nullable)       │
                                                         │ agentType               │
                                                         │ mode                    │
                                                         │ model                   │
                                                         │ requestPrompt (CLOB)    │
                                                         │ systemPrompt (CLOB)     │
                                                         │ chatHistory (CLOB)      │
                                                         │ responseText (CLOB)     │
                                                         │ inputTokens (nullable)  │
                                                         │ outputTokens (nullable) │
                                                         │ durationMs              │
                                                         │ status                  │
                                                         │ errorMessage (nullable) │
                                                         └─────────────────────────┘

Enum: MessageRole { USER, AGENT, CORRECTION }
Enum: ErrorType  { GRAMMAR, WORD_CHOICE, CHINGLISH, PRONUNCIATION, FLUENCY }
Enum: SessionStatus { ACTIVE, COMPLETED, FAILED }
Enum: AgentMode { WORKPLACE_STANDUP("Standup Meeting", "workplace_standup"), DAILY_TALK("Daily Talk", "daily_talk") }
Enum: LearningType { LEARNING_PROFILE }
Enum: MemoryCueStatus { COMPLETED, SEGMENT_FAILED, FIRST_CALL_FAILED }
Enum: TimeLabel { JUST_NOW, A_FEW_MINUTES_AGO, LAST_NIGHT, THIS_MORNING, THIS_AFTERNOON, THIS_EVENING, TONIGHT, YESTERDAY_MORNING, YESTERDAY_AFTERNOON, YESTERDAY_EVENING, A_FEW_DAYS_AGO, ABOUT_A_WEEK_AGO, A_FEW_WEEKS_AGO, ABOUT_A_MONTH_AGO, A_WHILE_AGO }（计算方式为日期+时段判断，非 Duration 桶遍历）
```

### Flashcard 模块数据模型

闪卡模块独立于聊天会话，拥有自己的 JPA 实体和表：

```
┌─────────────┐     ┌──────────────────┐     ┌─────────────┐
│    Card      │     │   card_tags      │     │     Tag     │
│─────────────│     │──────────────────│     │─────────────│
│ id (PK)     │────▶│ card_id (FK)     │◀────│ id (PK)     │
│ userId      │     │ tag_id (FK)      │     │ name        │
│ front       │     └──────────────────┘     │ type (null) │
│ back        │                              │ userId      │
│ stability   │                              └─────────────┘
│ difficulty  │
│ cardState   │
│ due         │
│ reps        │
│ lapses      │
│ lastReview  │
└─────────────┘
```

Card 和 Tag 均继承 `BaseEntity`（UUID id + createTime + updateTime）。Card-Tag 通过 `@ManyToMany` 关联，Card 侧为 owning side。FSRS 字段在创建卡片时由 `FsrsScheduler.createInitState()` 自动初始化（stability=2.5, difficulty=0.0, state=0, reps=0, lapses=0）。

> **数据隔离**: 所有实体使用纯字符串 FK（无 JPA `@ManyToOne` 关系）。`Session.userId` 是唯一的多租户分界点——子实体通过 UUID sessionId 自然隔离，无需额外 `userId` 字段。闪卡模块的 Card 和 Tag 均携带 `userId` 实现独立的数据隔离。

---

## 七、WebSocket 通信协议

### 端点：`ws://localhost:8080/ws/chat`

#### 前端 → 后端

```json
{ "type": "START_SESSION", "mode": "WORKPLACE_STANDUP" }
{ "type": "USER_INPUT", "text": "Yesterday I worked on the login module..." }
{ "type": "END_SESSION" }
{ "type": "RESUME_SESSION", "sessionId": "xxx" }
{ "type": "LOAD_HISTORY" }
```

#### 后端 → 前端

```json
{ "type": "STATE_UPDATE", "state": "PROCESSING", "tokenUsage": 0.15 }
{ "type": "AGENT_STREAM_DELTA", "delta": "Sounds", "messageId": 1 }
{ "type": "AGENT_STREAM_END", "text": "full text", "messageId": 1, "tokenUsage": 0.23 }
{ "type": "CORRECTION_RESULT",
  "corrections": [
    { "type": "CHINGLISH", "original": "...", "corrected": "...", "explanation": "..." }
  ],
  "messageId": 1
}
{ "type": "STATE_UPDATE", "state": "SPEAKING", "tokenUsage": 0.23 }
{ "type": "SESSION_REPORT", "report": { "summary": "...", "fluencyScore": 6, ... } }
{ "type": "SESSION_RESUMED", "sessionId": "xxx", "mode": "...", "messages": [...], "corrections": [...], "tokenUsage": 0.15 }
{ "type": "SESSION_HISTORY", "sessions": [{ "id": "...", "mode": "...", "startTime": "...", "status": "..." }] }
{ "type": "TOKEN_WARNING", "usage": 0.80, "message": "Approaching context limit" }
{ "type": "ERROR", "message": "..." }
```

---

## 八、会话历史管理策略

| 层面 | 方案 | 关键实现 |
|------|------|---------|
| **运行时上下文** | AgentState 全量累积 + Token 用量可视化 | `Channel.Appender` 追加消息，`decideNext` 节点计算 token，前端进度条，80% 提醒 |
| **持久化存储** | H2 逐条存 Message + ErrorRecord | `saveSession` 节点在会话结束时批量写入 JPA。跨会话通过 Session 关联 |
| **Checkpoint / 恢复** | MemorySaver | `CompileConfig.builder().checkpointSaver(new MemorySaver())`，每个会话 `threadId`，刷新页面恢复 |
| **前端展示** | 全部消息 + 折叠旧消息 | 可滚动聊天区，顶部 token 进度条，旧消息折叠到 "Show earlier" 后 |
| **写入时机** | 会话结束时统一持久化 | `SessionComplete.complete()` 内部串联 `reportAgent.generate()` → `sessionStore.completeSession()` → `memoryService.generateMemoryAsync()` + `memoryCueService.generateCuesAsync()` |
| **日志写入** | LLM 调用时即时异步写入 | `TaskRunner.requestModel()`（同步 Agent）在调用点内生写入完整上下文字段；`TurnProcessor`（ConversationAgent）在 `onCompleteResponse` 时写入。通过 `llmLogExecutor` (core=2, max=4) 异步写 `llm_call_logs` 表 |
| **日志清理** | 每次启动时自动清理 | `LlmCallLogService.cleanupOnStartup()` 在 `@PostConstruct` 中通过 `CompletableFuture.runAsync` 删除 3 天前记录 |
| **记忆写入** | LearningProfileService + MemoryCueService 异步触发 | `llmRequestExecutor` (core=4, max=8) 上同时运行 Topic 直接写入 + Profile Merge + MemoryCue Split + 多段 MemoryCue Entry。每条 COMPLETED 后触发 `EmbeddingService.indexAsync` 向量化 |
| **RAG 检索** | TurnProcessor Round 2+ 每轮触发 | `EmbeddingService.search()` 语义搜索历史 MemoryCue，userId×AgentMode 隔离。结果通过 MemoryCueQueue（LRU 队列，capacity topK+1）管理：首次加载 search topK+1 条，后续 search topK 条，去重刷新驱逐，按 tail→head 编号列表注入 System Prompt `{memoryCues}` |

### 会话生命周期

```
[对话中]
  AgentState.messages (MemorySaver checkpoint)  ← 只在内存
  TurnProcessor → ConversationAgent.generateStream(messageId ≤ 1 注入 User Memory, messageId ≥ 2 触发 MemoryCueQueue 管理的 RAG 检索)
  UI token bar 实时更新

[用户点击 End Session]
  ↓
  SessionService 收集状态数据（messages, corrections, userId, mode）
  ↓
  SessionComplete.complete(sessionId, messages, corrections, userId, mode)
    ├── reportAgent.generate() → ReportResult（失败则降级）
    ├── sessionStore.completeSession() → H2（null report → FAILED）
    ├── memoryService.generateMemoryAsync() → Topic + Profile
    └── memoryCueService.generateCuesAsync() → MemoryCue 生成
  ↓
  SessionService.remove() → 释放 state
  SESSION_REPORT → 前端弹窗

[用户重新打开]
  从 H2 加载历史会话列表（只读）
  新建会话 → 新 threadId → 新 MemorySaver checkpoint
  加载最新 User Memory → 注入首轮 System Prompt
```

---

## 九、前端 UI 布局（实现版本）

```
┌──────────────────────────────────────────────────────────────────────┐
│  [Logout]    [Token: █████████░]   90%          ☰                    │ ← 顶部栏
│──────────────────────────────────────────────────────────────────────│
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │                                                                │  │
│  │   [Conversation scroll area]                                   │  │
│  │   Agent: Good morning! How was your weekend?🔊                 │  │ ← ⚠️ 浮动按钮
│  │   You: I went to park with my family.                          │  │    居中右侧
│  │   Correction: 1. went to park → went to the park               │  │
│  │                2. ...                                   ⚠️ 3 ◂ │  │
│  │   Agent: Sounds lovely! ...               🔊                   │  │
│  │   ─── [Show earlier messages] ───                              │  │
│  │                                                                │  │
│  └────────────────────────────────────────────────────────────────┘  │
│────────────────────────────────────────────────────────────────────│
│  Status: Type your message                                         │
│────────────────────────────────────────────────────────────────────│
│  [Type or use 🎤 on keyboard...              ] [Send]              │
│────────────────────────────────────────────────────────────────────│
│  [Standup Meeting ▼]                    [Start] [End & Report]    │
├────────────────────────────────────────────────────────────────────┤
│  [Log] [Clear]                                         [anki]     │
└────────────────────────────────────────────────────────────────────┘

  展开 correction sidebar 后：

┌─────────────────────────────────────────────────┬──────────────────┐
│   [Conversation scroll area]                     │ ▸ Correction 3   │
│   ...                                           │ ──────────────── │
│                                                 │ 1.GRAMMAR        │
│                                                 │   I go → I went  │
│                                                 │   ...            │
│                                                 │ 2.CHINGLISH      │
│                                                 │   ...            │
└─────────────────────────────────────────────────┴──────────────────┘
```

### 关键交互细节

| 功能 | 实现 |
|------|------|
| 输入 | `<input>` 文本框 + Enter/Send 按钮。iOS 键盘麦克风可做原生听写 |
| TTS 播放 | 每条 Agent 消息右上角 🔊 按钮。**用户点击手势触发**（规避 iOS Safari 禁止无需用户手势的音频播放） |
| Token 用量 | 顶部进度条，≥80% 红色警告 |
| 旧消息 | 前 10 条可见，更早的折叠在 "Show earlier" 后 |
| Correction 侧边栏 | 绝对定位浮层（不挤压对话区），默认隐藏（`display: none`）。Correction 到达时右上角出现浮动 ⚠️ N ◂ badge（垂直居中），点击展开 260px 面板，header ▸ 按钮收起。打开 ☰ 菜单自动收起 sidebar。 |
| Correction 聊天气泡 | 插入用户消息下方，多条时分行编号展示（`1. original → corrected`），`white-space: pre-line` |
| Debug | 页面底部半透明面板，实时打印所有 WebSocket/状态/错误事件，可折叠/清空 |
| Safe Area | `padding-top: env(safe-area-inset-top)` — iOS 刘海/状态栏留白 |

### 报告弹层（会话结束后弹出）

```
┌────────────────────────────────────────┐
│  Session Report                        │
│  Fluency Score: 6/10                   │
│  ────────────────────────────────────  │
│  Grammar Errors: 3                     │
│  Word Choice: 2                        │
│  Chinglish: 4                          │
│  ────────────────────────────────────  │
│  Key Takeaway: ...                     │
│  ────────────────────────────────────  │
│  [Start New Session] [Close]           │
└────────────────────────────────────────┘
```

---

## 十、项目文件结构（实现版本）

```
chat-agent/
├── pom.xml
├── src/main/java/com/hugosol/chatagent/
│   ├── ChatAgentApplication.java
│   │
│   ├── graph/                              // LangGraph 核心
│   │   ├── ChatState.java                 // AgentState 定义 + Schema (6 channels, 含 USER_ID + MODE)
│   │   ├── ChatGraphBuilder.java          // StateGraph 构建 + compile (1节点线性图)
│   │   └── nodes/
│   │       └── CorrectionNode.java         // 调用 CorrectionAgent（仅存的图节点）
│   │
│   ├── flashcard/                          // 闪卡模块核心
│   │   ├── FsrsScheduler.java             // FSRS-6 调度器（initNewCard + createInitState + repeat + retrievability）
│   │   ├── CardState.java                 // 卡片状态记录（Learning/Review/Relearning 状态机 + step + hasStability）
│   │   ├── Rating.java                    // AGAIN/HARD/GOOD/EASY（pyValue 1-4 映射，匹配 py-fsrs 索引）
│   │   └── AleaPrng.java                  // Alea PRNG（Johannes Baagøe 算法，替代 java.util.Random 用于确定性 fuzz）
│   │
│   ├── controller/                         // REST API（首次引入 @RestController）
│   │   └── FlashcardController.java       // POST /api/cards/add + GET /api/tags
│   │
│   ├── agent/                              // Agent 调用封装
│   │   ├── common/                         // 横切关注点：TaskRunner 公共模块
│   │   │   ├── TaskRunner.java
│   │   │   ├── TaskDefinition.java
│   │   │   ├── TaskName.java
│   │   │   ├── TaskContext.java
│   │   │   └── ErrorStrategy.java
│   │   ├── ConversationAgent.java          // 角色扮演对话（Prompt 模板替换 + DeepSeek 流式调用）
│   │   ├── CorrectionAgent.java            // 5类纠错分析（JSON 解析 LLM 输出，委托 TaskRunner）
│   │   ├── ReportAgent.java                // 会话报告生成（委托 TaskRunner）
│   │   ├── LearningAgent.java              // Learning Profile 合并（跨会话记忆，委托 TaskRunner，原 MemoryAgent）
│   │   └── MemoryCueAgent.java             // 两步 LLM：话题切换检测 + 分段结构化摘要生成（委托 TaskRunner）
│   │
│   ├── dto/                              // 数据传输
│   │   ├── MemoryContent.java            // 记忆注入负载 (learningProfile, cueMatches)
│   │   ├── CueMatch.java                 // RAG 检索结果 (cueId, topic, summary, score, createdAt)
│   │   ├── MemoryCueQueue.java           // LRU 有序集合 (capacity topK+1)，跨 Turn 存活于 ChatState
│   │   ├── MessageData.java              // 前端消息序列化
│   │   ├── CorrectionData.java           // 纠错结果序列化
│   │   ├── AddCardRequest.java           // 闪卡创建请求 (front, back, tags)
│   │   ├── AddCardResponse.java          // 闪卡创建响应 (id, front, back, tags, due)
│   │   └── TagResponse.java              // 标签响应 (name, type)
│   │
│   ├── websocket/
│   │   ├── ChatWebSocketHandler.java      // WS 端点 (TextWebSocketHandler)
│   │   └── ChatMessageHandler.java        // 协议消息处理 + sessionToWs 映射 + requireUserId (实现 MessageHandler)
│   │
│   ├── protocol/                           // WS 消息协议
│   │   ├── ClientMessage.java              // 密封接口 + 5 个子类型 (Jackson 多态反序列化)
│   │   ├── ServerMessage.java              // 密封接口 + 10 个子类型
│   │   ├── MessageHandler.java             // 接口: 5 个 handler 方法
│   │   └── ProtocolDispatcher.java         // JSON 解析/序列化 + 消息分发 + synchronized send()
│   │
│   ├── speech/                             // STT/TTS（预留，V2 按实际需求定义接口）
│   │
│   ├── model/                              // JPA Entity
│   │   ├── User.java                       // 用户（username + BCrypt password）
│   │   ├── Session.java                    // 会话（含 userId）
│   │   ├── Message.java
│   │   ├── Card.java                       // 闪卡（front/back + FSRS 状态字段, @ManyToMany Tag）
│   │   ├── Tag.java                        // 标签（name + 可空 type, userId 隔离）
│   │   ├── ErrorRecord.java
│   │   ├── SessionReport.java
│   │   ├── UserProgress.java               // 学习进度（含 userId unique，每用户一行）
│   │   ├── UserLearningProfile.java                 // 跨会话记忆（Topic Memory + Learning Profile，含 sessionId 追溯）
│   │   ├── MemoryCue.java                  // 结构化话题记忆（topic/summary，含状态追踪）
│   │   ├── LlmCallLog.java                 // LLM API 调用日志（prompt/response/tokens/duration/status）
│   │   ├── MemoryCueStatus.java            // 枚举: COMPLETED / SEGMENT_FAILED / FIRST_CALL_FAILED
│   │   ├── MessageRole.java                // 枚举: USER / AGENT / CORRECTION
│   │   ├── ErrorType.java                  // 枚举: GRAMMAR / WORD_CHOICE / CHINGLISH / PRONUNCIATION / FLUENCY
│   │   ├── SessionStatus.java              // 枚举: ACTIVE / COMPLETED / FAILED
│   │   └── AgentMode.java                  // 枚举: WORKPLACE_STANDUP (含 displayName + templatePath)
│   │
│   ├── repository/                         // Spring Data JPA（11 个）
│   │   ├── UserRepository.java             // findByUsername
│   │   ├── SessionRepository.java          // findByUserIdOrderByStartTimeDesc
│   │   ├── MessageRepository.java
│   │   ├── CardRepository.java             // 闪卡 CRUD
│   │   ├── TagRepository.java              // findByNameAndUserId + findByUserId
│   │   ├── ErrorRecordRepository.java
│   │   ├── SessionReportRepository.java
│   │   ├── UserProgressRepository.java     // findByUserId
│   │   ├── UserMemoryRepository.java       // findByUserIdAndTypeAndModeOrderByVersionDesc
│   │   ├── MemoryCueRepository.java        // findBySessionId, findAllByStatus
│   │   └── LlmCallLogRepository.java       // deleteByCreateTimeBefore
│   │
│   ├── service/                            // 业务服务
│   │   ├── SessionService.java             // State 生命周期 + sessionToWs 映射 + TokenTracker
│   │   ├── TurnProcessor.java              // 回合并行编排 (Conversation 流式 + Correction 图 + RAG 检索)
│   │   ├── FlashcardService.java           // 闪卡创建（FSRS 初始化 + Tag upsert）+ 标签查询
│   │   ├── SessionComplete.java            // 会话结束管线 (report+persist+async memory)
│   │   ├── SessionDbStore.java               // 会话 CRUD + 归档（createSession/getHistory 含 userId）
│   │   ├── LearningProfileService.java              // 异步保存 Topic 摘要 + Learning Profile 合并（含 sessionId 追溯）
│   │   ├── MemoryCueService.java           // 异步 MemoryCue 生成（话题分割 + 分段摘要 → EmbeddingService.indexAsync）
│   │   ├── EmbeddingService.java            // ONNX 向量化 + InMemoryEmbeddingStore 管理（init/search/indexAsync/saveToDisk）
│   │   ├── LlmCallLogService.java          // 异步 LLM 调用日志写入 + 启动时清理 3 天前记录
│   │   ├── SessionCleanupLogoutHandler.java // 登出时清理 activeStates
│   │   ├── EntityMapper.java              // 运行时数据 → JPA 实体转换
│   │   └── TokenTracker.java               // 按 AgentType 分计 token
│   │
│   └── config/                             // 配置类
│       ├── LangChain4jConfig.java          // DeepSeek (OpenAiChatModel + OpenAiStreamingChatModel) Bean 配置
│       ├── SecurityConfig.java             // Spring Security filter chain + 登录事件日志
│       ├── WebSocketConfig.java            // WebSocket Handler 注册（同源策略）
│       ├── AsyncConfig.java                // llmRequestExecutor + llmLogExecutor + embeddingExecutor 线程池配置
│       ├── AppProperties.java              // @ConfigurationProperties(prefix="app") 包含 security.permit-all-paths
│       ├── PasswordEncoderConfig.java      // BCryptPasswordEncoder bean（独立配置，非 web 环境可用）
│       ├── DataInitializer.java            // CommandLineRunner：从 app.initial-users 种子用户
│       ├── JpaConfig.java                  // JPA Auditing
│       └── PromptLoader.java               // resources/prompts/*.txt 文件加载
│
├── src/main/resources/
│   ├── application.yml                     // 默认配置 + app.security.permit-all-paths: [/login/**]
│   ├── application-local.yml               // 本地覆盖（H2 console, initial-users, /h2-console/** 放行）
│   ├── logback-spring.xml                  // 日志配置（local profile 启用文件日志，INFO 控制台）
│   └── prompts/
│       ├── conversation-system.txt         // 骨架模板（{Description}, {Rules} 占位符）
│       ├── workplace_standup/              // per-AgentMode 子目录
│       │   ├── description.txt
│       │   └── rules.txt
│       ├── daily_talk/                     // per-AgentMode 子目录 (Chris)
│       │   ├── description.txt
│       │   └── rules.txt
│       ├── correction.txt
│       ├── report.txt
│       ├── memory-topic.txt
│       ├── memory-profile.txt
│       ├── memory-cue-split.txt
│       └── memory-cue-entry.txt
│
└── src/main/resources/static/
    ├── login/                              // 登录页（公开目录）
    │   ├── main.html                       // 登录表单 + 暗色主题
    │   ├── main.js                         // ?error 参数检测
    │   └── main.css                        // 暗色主题卡片样式
    ├── manage/                             // Manage 页面 (Cards + Tags 管理)
    │   ├── index.html                      // 双 tab 布局（Cards / Tags) + tab 切换逻辑
    │   ├── card.js                         // Card 列表、CRUD modal、搜索、排序、deck 筛选、分页、TTS
    │   ├── tag.js                          // Tag 表格、CRUD modal、deck checkbox、内联编辑
    │   ├── modal.js                        // 通用 Modal 组件（open/close/save）
    │   └── manage.css                      // Manage 页面专用样式
    ├── shared/                             // 跨页面共享资源
    │   ├── nav.js                          // 顶部导航栏 + ☰ 侧边栏（Chat / Cards 链接）
    │   └── base.css                        // 共享基础样式（btn-primary, scrollbar, toast 等）
    ├── index.html                          // 聊天页（认证后访问，header 含 Logout + 闪卡按钮）
    ├── app.js                              // WebSocket 客户端 + Visibility API 自动 resume + window.activePanel
    ├── flashcard.js                        // 闪卡面板（两阶段录入 + chip 标签 + 保存 + debug 面板互斥）
    └── style.css                           // 深色主题 + 闪卡面板样式 + toast 动画
```

> **图结构简化**: `ConversationNode` 和 `MergeResponseNode` 已移除。对话流式生成由 `TurnProcessor` 直接调用 `ConversationAgent`，token 计数由 `TokenTracker` 封装在 `SessionStateStore` 中管理。

---

## 十一、V1 vs V2 边界

| | V1（已实现） | V2 |
|---|-------------|----|
| **场景** | 职场英语 (Standup) + 日常闲聊 (Daily Talk) | 技术演讲练习 + 更多 AgentMode |
| **Agent** | 五 Agent 全协作（Conversation + Correction + Report + Memory + MemoryCue） | 场景自动切换 |
| **记忆** | 双轨：User Memory (摘要注入, Round 1) + MemoryCue (RAG 向量检索, Round 2+) | 可探索条件边 + 子图 |
| **报告** | 错误汇总 + 评分 | 进度趋势图表 |
| **输入** | 文本输入框 + iOS 键盘听写 | 前端录音 + 后端 OpenAI Whisper API |
| **TTS** | 浏览器 SpeechSynthesis（🔊 按钮手动触发） | OpenAI TTS（自然度更高） |
| **LangGraph** | 1 节点线性图（仅 correction） | 可探索条件边 + 子图 |
| **持久化** | H2 File | H2 File（不变） |
| **恢复** | MemorySaver | 可升级到 Postgres/Redis Saver |
| **纠错** | Agent 口头自然纠正 | 用户可选审核确认 |

---

## 十二、实现记录（实际执行）

### 设计偏差与原因

| 偏差 | 原始设计 | 实现 | 原因 |
|------|---------|------|------|
| 图结构 | 8 节点循环图（含 HITL awaitInput） | 1 节点线性图（仅 correction） | 避免 langgraph4j 复杂中断机制；对话流式由 Service 层管理更可控 |
| Conversation/Merge 节点 | ConversationNode + MergeResponseNode | 移除，`TurnProcessor` 直接调用 Agent | 流式对话需要异步回调，不适合放在同步图节点中 |
| 语音输入 | Web Speech API（`SpeechRecognition`） | 文本输入框 + iOS 键盘听写 | iOS Safari/Chrome 均不支持 `SpeechRecognition` API |
| E2E 测试 | WebSocket 帧拦截（`ws.onFrameReceived`） | DOM 级等待（`page.waitForFunction`） | Playwright Java 的 WS 帧回调不可靠；DOM 变化更贴近"用户看到什么"的验证目标 |
| TTS 触发 | 收到回复后自动播放 | 🔊 按钮手动触发 | iOS Safari 禁止非用户手势触发的音频播放 |
| TTS onend 恢复 | `utterance.onend` → `showTextInput()` | `showTextInput()` 在 `AGENT_STREAM_END` 后调用 | iOS `SpeechSynthesis.onend` 经常不触发 |
| 按住说话按钮 | `pointerdown` 启动 / `pointerup` 停止 | 移除 | 依赖已被移除的 `SpeechRecognition` |
| Mute 按钮 | 存在 | 移除 | 语音输入已改为文本，无需静音麦克风 |
| Channels API | `Channels.of()` | `Channels.base()` | langgraph4j 1.8.16 实际 API 是 `base(Supplier)` |
| langgraph4j 集成层 | `langgraph4j-langchain4j` 依赖 | 直接使用 `langchain4j` + `langchain4j-open-ai` | 减少间接依赖，直接调用 `ChatLanguageModel` |
| Debug 工具 | 无 | 内嵌 Debug 面板（页面底部半透明日志） | 移动端调试 console 不可见，需要屏幕日志 |
| Correction 侧边栏 | 固定宽度 flex 挤压对话区 | 绝对定位浮层，默认隐藏，header 按钮 toggle | 移动端不挤压对话空间 |
| Scenario/Persona 描述 | switch 硬编码 + 裸枚举名传入 prompt | `ScenarioType`/`PersonaType` 枚举字段 + enum 访问器 | 自然语言描述、方便扩展、入口校验 |
| Prompt 占位符 | `{scenario_role}` 填入枚举名 | `{persona_role}` 填入 `PersonaType.getRoleDescription()` | 修正语义混淆，LLM 看到自然语言 |
| LISTENING 状态 | session 启动后发送 STATE_UPDATE "LISTENING" | 移除，showTextInput() 设置 "Type your message" | 与键盘语音输入模式不匹配 |
| 归档逻辑 | 双层循环全量绑定（Auto→所有 USER Message） | `SessionArchiver` 按 `messageId` 精确关联 + `MessageData` 携带 `messageId` | 修复 ErrorRecord 重复绑定 bug；实体转换抽成纯计算模块，可脱离 DB 测试 |
| Speech 接口 | V1 预留接口 `SpeechToTextService` / `TextToSpeechService` | 删除（零实现者，V2 再按需定义） | 空壳接口不产生 leverage，徒增探索成本 |
| Scenario/Persona 合并 | `ScenarioType` + `PersonaType` 两个独立枚举，前端两个下拉框，prompt 3 个占位符 | `AgentMode` 统一枚举，前端单一下拉框，prompt 拆为 per-Mode `description.txt` + `rules.txt` 文件 | 消除不合理组合、降低用户选择成本、提示词可按 Mode 独立定制、新增 Mode 只需加文件夹和模板文件 |

### 实现阶段

| 阶段 | 范围 | 产出 |
|------|------|------|
| **1. 骨架** | Spring Boot 项目 + Maven 依赖 + application.yml | pom.xml, ChatAgentApplication, 目录结构 |
| **2. 模型层** | 5 个 JPA Entity + 4 个枚举 + 5 个 Repository | 表结构 + 数据访问层 |
| **3. LangGraph 核心** | ChatState (6 channels) + 1 Node + ChatGraphBuilder | 编译通过的单轮线性图 |
| **4. Agent 接入** | 3 个 Agent + PromptLoader + 3 个 Prompt 文件 | LangChain4j DeepSeek 调用链路 |
| **5. 服务层** | SessionStateStore + TurnProcessor + ReportGenerator + SessionService + TokenTracker | State 读写、并行编排、报告生成、H2 持久化 |
| **6. WebSocket** | ChatWebSocketHandler + ChatMessageHandler + ProtocolDispatcher + 协议类型 + WebSocketConfig + LangChain4jConfig | JSON 消息路由、前后端通讯 |
| **7. 前端 V1** | 文本输入栏 + Send 按钮 + TTS 🔊 按钮 + Token 进度条 + Debug 面板 | 可用 UI |
| **8. 移动端适配** | iOS Safari 兼容：🔊 按钮手势触发 TTS、输入框键盘原生听写、Debug 面板 | iPhone 13 可用 |
| **9. 端到端验证** | `mvn compile` BUILD SUCCESS（40 个源文件） | 编译通过 |
| **10. Correction UX 优化** | 侧边栏绝对定位浮层 + 默认隐藏 + header toggle；correction 气泡分行编号；Safe-area CSS；移除 LISTENING 状态 | 移动端体验提升 |
| **11. Scenario/Persona 枚举重构** | `ScenarioType` + `PersonaType` 加描述字段；`ConversationAgent` 用 enum 访问器；`ChatWebSocketHandler` persona 入口校验；prompt 占位符修正 | 自然语言 prompt、可扩展、类型安全 |
| **12. 归档深化** | `MessageData` 加 `messageId` 字段；`SessionArchiver` 纯计算模块提取；删除 `speech/` 空壳接口 | 修复 ErrorRecord 重复绑定；实体转换可脱离 DB 测试；消除无 leverage 模块 |
| **13. E2E 测试** | Playwright + WireMock：`ChatAgentSessionIT`（完整会话+3轮+sidebar+H2断言）、`ChatAgentResumeIT`（页面刷新→会话恢复）。WireMock Scenario 状态机轮转 mock 数据，`matchingJsonPath` 区分 conversation/correction/report 请求。DOM 级等待（input 状态、correction bubble 数量、report modal 可见性）。截图自动保存到 `target/e2e-screenshots/`。 | 零外部依赖的全链路回归测试；WireMock 固定端口 19090 + shutdown hook 支持全量并行跑 |
| **14. User 模块** | Spring Security form login + remember-me + BCrypt。User entity + UserRepository。AppProperties 配置 `permit-all-paths` 驱动权限。ChatState 加 `USER_ID` channel。`Session.userId` 数据隔离。`sessionToWs` 一对一翻转。SessionCleanupLogoutHandler 登出清理。E2E 用 `application-e2e.yml` + `permit-all-paths: [/**]` 绕过认证。`requireUserId` fallback `"anonymous"`。前端登录页 `login/main.html` + Visibility API 多标签自动 resume。 | 多用户认证 + 数据隔离 + 配置驱动权限 |
| **15. AgentMode 合并** | `ScenarioType` + `PersonaType` 合并为单一 `AgentMode` 枚举（`displayName` + `templatePath`）；前端两个下拉框合并为一个；提示词拆分为 per-Mode `description.txt` + `rules.txt`，`conversation-system.txt` 退化为骨架模板；ChatState `SCENARIO` + `PERSONA` → `MODE`；协议 `scenario` + `persona` → `mode`；删除旧枚举类 | 消除不合理组合、降低选择成本、提示词按 Mode 独立定制、新增 Mode 只需加文件夹和模板 |
| **16. 会话结束管线抽取** | `SessionComplete` 深模块：报告生成+持久化+异步记忆管线统一；`SessionDbStore.completeSession(null report)` → FAILED；`SessionStatus.FAILED` 枚举值；降级报告（fluencyScore=-1）+ 前端条件渲染；Handler 依赖 7→4 | 会话结束逻辑局部化，降级路径明确，前端不再展示 "0/10" |
| **17. REST API 模式引入** | 全 WebSocket 通信 | `FlashcardController` 为 `@RestController`，POST /api/cards/add + GET /api/tags | 闪卡录入为独立 CRUD 操作，天然适配 REST 语义而非 WS 长连接；SecurityConfig 中 `/api/**` CSRF 豁免 |
| **18. Fuzz PRNG 替换** | `java.util.Random` | `AleaPrng`（Johannes Baagøe 算法 Java 端口），通过 `DoubleSupplier` 接口注入 `repeat()` | 需跨实现 fuzz 确定性；Alea seed(42)→12 天、seed(12345)→11 天 精准匹配 PRD 预期值 |
| **19. FSRS 代码量** | ~150 行预估 | ~300 行实际 | 算法复杂度被低估：含学习步进逻辑（Learning→Review→Relearning 4 状态机）、同日复习修正（short-term stability）、Fuzz 区间计算、retrievability 公式 |
