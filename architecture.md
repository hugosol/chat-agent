# English Coach — 完整架构蓝图

## 一、项目定位

一个**个人英语口语练习工具**，通过 AI Agent 角色扮演进行**全语音双向英文对话**，在对话中自然纠正表达错误，对话后生成分析报告并追踪学习进度。

**学习目标**：深度掌握 LangChain4j + langgraph4j 的 Agent 开发，重点实践 StateGraph、Multi-Agent 协作、Human-in-the-Loop、Checkpoint、持久化。

---

## 二、全量决策日志（25 项）

| # | 决策点 | 选择 |
|---|--------|------|
| 1 | 核心场景 | AI 英语对话伙伴 |
| 2 | 对话场景 | 职场英语 + 技术演讲练习 |
| 3 | 交互模态 | 文本输入 + TTS 朗读（语音输入通过 OpenAI Whisper API 预留到 V2） |
| 4 | 交付形态 | Web 应用 (Spring Boot + 浏览器) |
| 5 | LLM 提供商 | DeepSeek V4 FAST，LangChain4j 抽象层保证可替换 |
| 6 | 输入方式 | 文本输入框，iOS 用户可借助键盘原生听写。浏览器 SpeechSynthesis 做 TTS 输出（需用户手势触发） |
| 7 | Agent 核心能力 | 角色扮演 + 自然纠错 + 对话后报告 + 学习进度追踪 |
| 8 | 纠错机制 | Agent 口头自然纠正（融入对话不打断） |
| 9 | LangGraph 深度 | 深度：HITL + Checkpoint + 持久化 |
| 10 | Agent 架构 | 三 Agent 协作：Conversation + Correction + Report |
| 11 | 前端技术 | 原生 HTML + Vanilla JS |
| 12 | 通信协议 | WebSocket |
| 13 | 数据库 | H2 文件模式 |
| 14 | 构建工具 | Maven + Java 17 |
| 15 | 会话控制 | 纯 UI 按钮（开始/切换/结束） |
| 16 | 纠错类型 | 5 类全追踪：语法/用词/中式英语/发音/流利度 |
| 17 | LangGraph 库 | `org.bsc.langgraph4j:langgraph4j-core:1.8.16` |
| 18 | V1 范围 | 单场景(职场英语) + 三 Agent + 完整报告 |
| 19 | Prompt 管理 | `resources/prompts/*.txt` 文件 |
| 20 | WS 消息协议 | JSON：START_SESSION / USER_INPUT / END_SESSION / STATE_UPDATE / AGENT_RESPONSE / SESSION_REPORT |
| 21 | Token 窗口 | 手动分段：UI 显示用量，80% 提醒用户结束会话 |
| 22 | 持久化粒度 | 逐条存储 Message + ErrorRecord |
| 23 | 会话恢复 | MemorySaver：页面刷新可恢复，服务重启丢失 |
| 24 | 前端展示 | 全部消息 + 折叠旧消息 |
| 25 | 持久化时机 | 会话结束时统一写入 H2 |

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
| 前端 | HTML + CSS + Vanilla JS | 单文件 SPA |

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
</dependencies>
```

---

## 四、LangGraph 状态机（实现版本）

**架构决策**: LangGraph 图处理**单轮纠错**（仅 `correction` 节点），对话流式生成由 Service 层 (`TurnProcessor`) 管理。

### 状态定义 (CoachState extends AgentState)

```java
public class CoachState extends AgentState {
    static final String SESSION_ID        = "sessionId";
    static final String SCENARIO          = "scenario";
    static final String PERSONA           = "persona";
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

> **实现版本**: 图只有 1 个节点。`ConversationNode` 和 `MergeResponseNode` 已移除——对话流式生成改为 `TurnProcessor` 直接调用 `ConversationAgent.generateStream()`，token 计数由 `TokenTracker` 管理。

### 节点职责（1 个节点）

| 节点 | 实现类 | 职责 |
|------|--------|------|
| `correction` | `CorrectionNode` | 从 AgentState 读取 `userInput`，调用 `CorrectionAgent.analyze()`，输出 `corrections` 列表 |

### 会话生命周期（由 Service 层管理）

```
[用户 Start Session]
  → SessionService.createSession() → H2 写入 Session
  → SessionStateStore.init() → 创建 CoachState → activeStates Map + TokenTracker 初始化

[每轮对话]
  → WebSocket 收到 USER_INPUT
  → TurnProcessor.processTurn(sessionId, userInput, messageId, callback)
    → 两路 CompletableFuture 并行:
      A) ConversationAgent.generateStream() → 流式推送到前端
      B) graph.stream(input, config) → CorrectionNode → 纠错结果异步推送
  → 回调通过 TurnProcessor.TurnCallback 通知 CoachMessageHandler

[用户 End Session]
  → ReportGenerator.generate(messages, corrections) → 调用 ReportAgent
  → SessionService.completeSession() → messages + errors + report → H2
  → SessionStateStore.remove() → 释放 state + TokenTracker
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

### 1. ConversationAgent (`prompts/conversation.txt`)

```
You are an English conversation partner helping a Chinese Java developer
practice workplace English. Your persona: {persona_description}

Rules:
- Engage in natural, flowing conversation in English.
- Match your language level to the user's (intermediate).
- Stay in character as {persona_role}.
- Keep responses concise and natural for spoken conversation (2-4 sentences).
- Do NOT explicitly list or number errors. Instead, model correct English naturally.
- If the user makes a significant error, rephrase their meaning correctly within your response.
- Be encouraging and supportive.

Current scenario: {scenario}
Recent conversation history: {history}
User just said: "{userInput}"

Respond naturally as your persona. Do not use markdown or special formatting.
```

> `{persona_description}` = `PersonaType.getFullDescription()` (e.g. "You are a friendly teammate...")  
> `{persona_role}` = `PersonaType.getRoleDescription()` (e.g. "a team colleague")  
> `{scenario}` = `ScenarioType.getDescription()` (e.g. "a daily standup meeting...")  
> Placeholder values come from enum fields, not raw enum names. No hardcoded switch statements.

### 2. CorrectionAgent (`prompts/correction.txt`)

```
You are an English coach analyzing a learner's spoken English.
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
3. **Vocabulary Suggestions**: 3-5 better words/phrases the user could have used
4. **Fluency Score**: 1-10 rating with 1-sentence justification
5. **Key Takeaway**: One actionable improvement focus for next session

Conversation: {fullConversation}
Errors: {allCorrections}
```

---

## 六、数据模型（H2 / JPA）

```
┌─────────────┐     ┌──────────────┐     ┌──────────────┐
│   Session   │1───*│   Message    │1───*│ ErrorRecord  │
│─────────────│     │──────────────│     │──────────────│
│ id (PK)     │     │ id (PK)      │     │ id (PK)      │
│ scenario    │     │ sessionId(FK)│     │ messageId(FK)│
│ persona     │     │ role (Enum)  │     │ type (Enum)  │
│ startTime   │     │ content      │     │ originalText │
│ endTime     │     │ timestamp    │     │ correctedText│
│ status      │     │ tokenCount   │     │ explanation  │
└─────────────┘     └──────────────┘     └──────────────┘
      1
      │
      ├──────────────┐
      │              │
┌──────────────┐  ┌──────────────┐
│SessionReport │  │ UserProgress │
│──────────────│  │──────────────│
│ id (PK)      │  │ id (PK)      │
│ sessionId(FK)│  │ totalSessions│
│ summary      │  │ totalMinutes │
│ fluencyScore │  │ errorStats   │
│ vocabulary   │  │ (JSON text)  │
│ keyTakeaway  │  │ updatedAt    │
└──────────────┘  └──────────────┘

Enum: MessageRole { USER, AGENT, CORRECTION }
Enum: ErrorType  { GRAMMAR, WORD_CHOICE, CHINGLISH, PRONUNCIATION, FLUENCY }
Enum: SessionStatus { ACTIVE, COMPLETED }
Enum: ScenarioType { WORKPLACE_STANDUP("Standup Meeting", "a daily standup meeting..."), WORKPLACE_ONE_ON_ONE(...) }
Enum: PersonaType { TEAM_COLLEAGUE("Team Colleague", "a team colleague", "You are a friendly teammate..."), MANAGER(...) }
```

---

## 七、WebSocket 通信协议

### 端点：`ws://localhost:8080/ws/coach`

#### 前端 → 后端

```json
{ "type": "START_SESSION", "scenario": "WORKPLACE_STANDUP", "persona": "TEAM_COLLEAGUE" }
{ "type": "USER_INPUT", "text": "Yesterday I worked on the login module..." }
{ "type": "END_SESSION" }
{ "type": "LOAD_HISTORY", "sessionId": "xxx" }
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
| **写入时机** | 会话结束时统一持久化 | `reportAgent` → `saveSession` 顺序执行，一次事务写入 |

### 会话生命周期

```
[对话中]
  AgentState.messages (MemorySaver checkpoint)  ← 只在内存
  UI token bar 实时更新

[用户点击 End Session]
  ↓
reportAgent → 生成报告
  ↓
saveSession → Session + Messages + ErrorRecords + SessionReport → H2
  ↓
AgentState 释放，checkpoint 清除

[用户重新打开]
  从 H2 加载历史会话列表（只读）
  新建会话 → 新 threadId → 新 MemorySaver checkpoint
```

---

## 九、前端 UI 布局（实现版本）

```
┌──────────────────────────────────────────────────────────────────────┐
│  English Coach    [Token: ████░]              [Corrections 3]        │ ← 顶部栏 (场景+tokens+correction按钮)
│──────────────────────────────────────────────────────────────────────│
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │                                                                │  │
│  │   [Conversation scroll area]                         ┌───────┐ │  │ ← correction sidebar
│  │   Agent: Good morning! How was your weekend?🔊       │ Corre-│ │ │    绝对定位浮层
│  │   You: I went to park with my family.                │ ctions│ │ │    不挤压对话区
│  │   Correction: 1. went to park → went to the park     │ ───── │ │ │
│  │                2. ...                                │ 1.GRAM│ │ │
│  │   Agent: Sounds lovely! ...               🔊        │  ...  │ │ │
│  │   ─── [Show earlier messages] ───                    │ 2.CHIN│ │ │
│  │                                                      │  ...  │ │ │
│  └──────────────────────────────────────────────────────└───────┘ │ │
│────────────────────────────────────────────────────────────────────│
│  Status: Type your message                                         │
│────────────────────────────────────────────────────────────────────│
│  [Type or use 🎤 on keyboard...              ] [Send]              │
│────────────────────────────────────────────────────────────────────│
│  [Standup Meeting ▼] [Team Colleague ▼] [Start] [End & Report]    │
├────────────────────────────────────────────────────────────────────┤
│  [Log] [Clear]                                                     │
└────────────────────────────────────────────────────────────────────┘
```

### 关键交互细节

| 功能 | 实现 |
|------|------|
| 输入 | `<input>` 文本框 + Enter/Send 按钮。iOS 键盘麦克风可做原生听写 |
| TTS 播放 | 每条 Agent 消息右上角 🔊 按钮。**用户点击手势触发**（规避 iOS Safari 禁止无需用户手势的音频播放） |
| Token 用量 | 顶部进度条，≥80% 红色警告 |
| 旧消息 | 前 10 条可见，更早的折叠在 "Show earlier" 后 |
| Correction 侧边栏 | 绝对定位浮层（不挤压对话区），默认隐藏。Header "Corrections N" 按钮/侧边栏 × 按钮双向 toggle |
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
│  Vocabulary Suggestions:               │
│  • "went to park" → "went to the park" │
│  • ...                                 │
│  ────────────────────────────────────  │
│  Key Takeaway: ...                     │
│  ────────────────────────────────────  │
│  [Start New Session] [Close]           │
└────────────────────────────────────────┘
```

---

## 十、项目文件结构（实现版本）

```
web-agent/
├── pom.xml
├── src/main/java/com/hugosol/webagent/
│   ├── WebAgentApplication.java
│   │
│   ├── graph/                              // LangGraph 核心
│   │   ├── CoachState.java                 // AgentState 定义 + Schema (7 channels)
│   │   ├── CoachGraphBuilder.java          // StateGraph 构建 + compile (1节点线性图)
│   │   ├── CorrectionData.java             // 纠错数据结构 (type 为 ErrorType 枚举, Serializable)
│   │   ├── MessageData.java                // 消息数据结构 (role + content + messageId，Serializable)
│   │   └── nodes/
│   │       └── CorrectionNode.java         // 调用 CorrectionAgent（仅存的图节点）
│   │
│   ├── agent/                              // Agent 调用封装
│   │   ├── ConversationAgent.java          // 角色扮演对话（Prompt 模板替换 + DeepSeek 调用）
│   │   ├── CorrectionAgent.java            // 5类纠错分析（JSON 解析 LLM 输出）
│   │   └── ReportAgent.java                // 会话报告生成
│   │
│   ├── websocket/
│   │   ├── CoachWebSocketHandler.java      // WS 端点 (TextWebSocketHandler)
│   │   └── CoachMessageHandler.java        // 协议消息处理 + wsToSession 映射 (实现 MessageHandler)
│   │
│   ├── protocol/                           // WS 消息协议
│   │   ├── ClientMessage.java              // 密封接口 + 5 个子类型 (Jackson 多态反序列化)
│   │   ├── ServerMessage.java              // 密封接口 + 11 个子类型
│   │   ├── MessageHandler.java             // 接口: 5 个 handler 方法
│   │   └── ProtocolDispatcher.java         // JSON 解析/序列化 + 消息分发 + synchronized send()
│   │
│   ├── speech/                             // STT/TTS（预留，V2 按实际需求定义接口）
│   │
│   ├── model/                              // JPA Entity
│   │   ├── Session.java
│   │   ├── Message.java
│   │   ├── ErrorRecord.java
│   │   ├── SessionReport.java
│   │   ├── UserProgress.java
│   │   ├── MessageRole.java                // 枚举: USER / AGENT / CORRECTION
│   │   ├── ErrorType.java                  // 枚举: GRAMMAR / WORD_CHOICE / CHINGLISH / PRONUNCIATION / FLUENCY
│   │   ├── SessionStatus.java              // 枚举: ACTIVE / COMPLETED
│   │   ├── ScenarioType.java               // 枚举: WORKPLACE_STANDUP / WORKPLACE_ONE_ON_ONE (含 displayName + description)
│   │   └── PersonaType.java                // 枚举: TEAM_COLLEAGUE / MANAGER (含 displayName + roleDescription + fullDescription)
│   │
│   ├── repository/                         // Spring Data JPA
│   │   ├── SessionRepository.java
│   │   ├── MessageRepository.java
│   │   ├── ErrorRecordRepository.java
│   │   ├── SessionReportRepository.java
│   │   └── UserProgressRepository.java
│   │
│   ├── service/                            // 业务服务
│   │   ├── SessionStateStore.java           // State 生命周期 + TokenTracker 封装 + 所有 state 读写
│   │   ├── TurnProcessor.java              // 回合并行编排 (Conversation 流式 + Correction 图)
│   │   ├── SessionArchiver.java            // 运行时数据 → JPA 实体转换（纯计算，按 messageId 精确关联纠错）
│   │   ├── ReportGenerator.java            // 报告生成透传
│   │   ├── SessionService.java             // 会话生命周期 + H2 持久化编排
│   │   └── TokenTracker.java               // 按 AgentType 分计 token
│   │
│   └── config/                             // 配置类
│       ├── LangChain4jConfig.java          // DeepSeek (OpenAiChatModel) Bean 配置
│       ├── WebSocketConfig.java            // WebSocket Handler 注册
│       └── PromptLoader.java               // resources/prompts/*.txt 文件加载
│
├── src/main/resources/
│   ├── application.yml                     // DeepSeek API key 通过环境变量注入
│   └── prompts/
│       ├── conversation.txt
│       ├── correction.txt
│       └── report.txt
│
└── src/main/resources/static/
    ├── index.html                          // 单页 UI（无构建工具）
    ├── app.js                              // WebSocket 客户端 + 文本输入 + TTS 控制 + Debug 日志
    └── style.css                           // 深色主题
```

> **图结构简化**: `ConversationNode` 和 `MergeResponseNode` 已移除。对话流式生成由 `TurnProcessor` 直接调用 `ConversationAgent`，token 计数由 `TokenTracker` 封装在 `SessionStateStore` 中管理。

---

## 十一、V1 vs V2 边界

| | V1（已实现） | V2 |
|---|-------------|----|
| **场景** | 职场英语 (Standup / 1:1) | 技术演讲练习 |
| **Agent** | 三 Agent 全协作 | 场景自动切换 |
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

### 实现阶段

| 阶段 | 范围 | 产出 |
|------|------|------|
| **1. 骨架** | Spring Boot 项目 + Maven 依赖 + application.yml | pom.xml, WebAgentApplication, 目录结构 |
| **2. 模型层** | 5 个 JPA Entity + 4 个枚举 + 5 个 Repository | 表结构 + 数据访问层 |
| **3. LangGraph 核心** | CoachState (7 channels) + 1 Node + CoachGraphBuilder | 编译通过的单轮线性图 |
| **4. Agent 接入** | 3 个 Agent + PromptLoader + 3 个 Prompt 文件 | LangChain4j DeepSeek 调用链路 |
| **5. 服务层** | SessionStateStore + TurnProcessor + ReportGenerator + SessionService + TokenTracker | State 读写、并行编排、报告生成、H2 持久化 |
| **6. WebSocket** | CoachWebSocketHandler + CoachMessageHandler + ProtocolDispatcher + 协议类型 + WebSocketConfig + LangChain4jConfig | JSON 消息路由、前后端通讯 |
| **7. 前端 V1** | 文本输入栏 + Send 按钮 + TTS 🔊 按钮 + Token 进度条 + Debug 面板 | 可用 UI |
| **8. 移动端适配** | iOS Safari 兼容：🔊 按钮手势触发 TTS、输入框键盘原生听写、Debug 面板 | iPhone 13 可用 |
| **9. 端到端验证** | `mvn compile` BUILD SUCCESS（40 个源文件） | 编译通过 |
| **10. Correction UX 优化** | 侧边栏绝对定位浮层 + 默认隐藏 + header toggle；correction 气泡分行编号；Safe-area CSS；移除 LISTENING 状态 | 移动端体验提升 |
| **11. Scenario/Persona 枚举重构** | `ScenarioType` + `PersonaType` 加描述字段；`ConversationAgent` 用 enum 访问器；`CoachWebSocketHandler` persona 入口校验；prompt 占位符修正 | 自然语言 prompt、可扩展、类型安全 |
| **12. 归档深化** | `MessageData` 加 `messageId` 字段；`SessionArchiver` 纯计算模块提取；删除 `speech/` 空壳接口 | 修复 ErrorRecord 重复绑定；实体转换可脱离 DB 测试；消除无 leverage 模块 |
