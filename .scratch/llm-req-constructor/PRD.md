# PRD: Structured LLM Message Pipeline — LlmReqConstructor

**Status:** `ready-for-agent`

## Problem Statement

当前代码库中所有非流式 LLM 请求通过 `TaskRunner` 的 `chat(String)` 方法发送——将 system 指令和 user 数据烘焙进单条字符串，LLM 无法区分 system/user 语义边界。同时：

- `LlmCallLog` 表的 `systemPrompt`、`chatHistory`、`inputTokens`、`outputTokens` 列始终为 null
- 对话数据使用 `[MSG#N] USER:` 纯文本标签格式，结构和 token 效率均不及行业标准
- few-shot 示例嵌入在 System 模板文本中，与真实数据格式耦合
- `CardEnhanceService` 绕过 TaskRunner 直接调用 `chatLanguageModel.chat(prompt)`，无日志、无错误策略

## Solution

创建 `LlmReqConstructor` 组件替代 `TaskRunner`，统一所有非流式 LLM 调用为 `generate(List<ChatMessage>)`（LangChain4j 结构化消息 API）。核心改进：

1. **SystemMessage / UserMessage 分离**——模板拆为 system/user 两部分，调用 `ChatLanguageModel.generate()` 发送原生 role 标签
2. **对话数据使用 XML 格式**——`<turn role="user">...</turn><turn role="assistant">...</turn>`，token 效率优于 JSON，语义明确优于纯文本标签
3. **few-shot 示例从模板迁移到 Java 静态列表**——`exampleMessages`（`List<ChatMessage>`），System 模板仅保留纯指令
4. **日志完整化**——`systemPrompt`、`chatHistory`、`inputTokens`、`outputTokens` 从 `Response<AiMessage>` 中提取
5. **CardEnhanceService 纳入统一管道**——走 `LlmReqConstructor.chat()` 无 Agent 路径

## User Stories

1. As a developer debugging LLM calls, I want `system_prompt` and `chat_history` logged separately in `llm_call_logs`, so that I can inspect the system instructions and user data independently.
2. As a developer reviewing LLM logs, I want `input_tokens` and `output_tokens` populated from the API response, so that token usage tracking is accurate.
3. As an agent developer, I want to separate system instructions from user data in prompt templates, so that the LLM receives proper `role: system` and `role: user` semantics.
4. As an agent developer, I want conversation transcripts formatted in XML with explicit role attributes, so that the LLM parses speaker identity unambiguously and token usage is optimized.
5. As an agent developer, I want few-shot examples defined as static `ChatMessage` lists in Java code rather than embedded in template text, so that examples are structurally identical to real data and easier to maintain.
6. As a developer working on the flashcard module, I want `CardEnhanceService` LLM calls to go through the same unified pipeline as chat agents, so that logging and error handling are consistent.
7. As a developer using the debug replay endpoint, I want `LlmReplayController` to work with the new structured message format, so that I can replay historical LLM calls.
8. As an agent developer, I want to keep the `register(TaskName, ...)` pattern for task definitions, so that agent registration remains declarative and discoverable.
9. As a developer, I want `reportChatLanguageModel` (4096 maxTokens) routing based on task type to continue working as before.
10. As a developer, I want `ErrorStrategy.SWALLOW` and `ErrorStrategy.THROW` to behave identically to the current TaskRunner.
11. As a developer, I want `requestRaw()` semantics preserved for AssertionService's custom retry logic, so that topic extraction retries 3 times with 500ms delay before falling back.

## Implementation Decisions

### 1. New component: `LlmReqConstructor`

Replaces `TaskRunner` as the single entry point for all non-streaming LLM calls. Absorbs template filling, message assembly, model selection, LLM invocation, and async logging. Retains the `register(TaskName, ...)` registry pattern.

**Public API:**

- `register(TaskName, LlmTaskDefinition)` — register a task definition
- `<P, R> R execute(TaskName, P, TaskContext)` — execute a registered task (standard path)
- `<P, R> R execute(TaskName, P, TaskContext, String systemTemplateOverride)` — execute with per-call template override (ReportAgent)
- `<P> String executeRaw(TaskName, P, TaskContext)` — execute without parsing (AssertionService retry)
- `String chat(List<ChatMessage>, TaskContext, String agentType, ModelType)` — direct call without registration (CardEnhanceService)

**Message assembly order:**

```
[SystemMessage]      ← fillTemplate(systemTemplate, placeholders)
[UserMessage #1]     ┐
[AiMessage #1]       │ exampleMessages (static few-shot, nullable)
[UserMessage #2]     │
[AiMessage #2]       ┘
[UserMessage]         ← fillTemplate(userTemplate, placeholders) ← real user data
```

### 2. New data structure: `LlmTaskDefinition<P, R>`

Replaces `TaskDefinition`. Fields:

- `systemTemplate` (String) — System message template with `{placeholders}`
- `userTemplate` (String) — User message template with `{placeholders}`
- `exampleMessages` (List<ChatMessage>) — Static few-shot example pairs; null when unused
- `paramBuilder` (Function<P, Map<String,String>>) — Fills placeholders for both system and user templates
- `parser` (Function<String, R>) — Response parser
- `errorStrategy` (ErrorStrategy) — SWALLOW or THROW

### 3. Template splitting

Each production `.txt` template file split at a `---USER---` delimiter into system and user portions. The user portion contains only data placeholders (`{userInput}`, `{messages}`, etc.). Few-shot examples removed from templates entirely, defined in Java as `exampleMessages`.

E2E test templates in `src/test/resources/prompts/` follow the same split pattern. Marker keywords (`E2E_MARKER_*`, `Correction prompt:`, `Report prompt.`) placed at the start of the system portion so WireMock `matchingJsonPath("$.messages[0].content")` continues to match.

### 4. XML conversation format

`buildLabeledMessages()` and `buildSegmentText()` in MemoryCueAgent changed to produce XML:

```xml
<turn role="user">Yesterday I go to park with my friend.</turn>
<turn role="assistant">You mean "went to the park" — "go" in past tense is "went."</turn>
```

System template instructions updated accordingly (e.g., "Each turn is an XML element with a role attribute...").

### 5. Few-shot examples moved to Java

Only two templates have complete input/output few-shot examples:

- `assertion/extract-topics.txt` — 2 example pairs
- `assertion/judge-same.txt` — 3 example pairs

Examples constructed as static `List<ChatMessage>` in the agent's registration code:

```
UserMessage(example input as XML transcript) → AiMessage(expected JSON output)
```

`memory-cue-split.txt` and `memory-cue-entry.txt` lack complete input/output pairs — their format examples remain in the system template for this iteration.

### 6. LLM call upgrade: `chat(String)` → `generate(List<ChatMessage>)`

LangChain4j 1.0.0-beta1's `ChatLanguageModel.generate(List<ChatMessage>)` returns `Response<AiMessage>`, providing:

- `response.content().text()` — response text
- `response.tokenUsage().inputTokenCount()` — input tokens
- `response.tokenUsage().outputTokenCount()` — output tokens

### 7. Logging upgrade

`LlmCallLogService.saveAsync()` called with the 13-parameter overload (currently unused), filling:

| Column | Old value | New value |
|--------|-----------|-----------|
| `system_prompt` | `null` | SystemMessage text |
| `chat_history` | `null` | JSON serialization of UserMessage list |
| `input_tokens` | `null` | `tokenUsage().inputTokenCount()` |
| `output_tokens` | `null` | `tokenUsage().outputTokenCount()` |

### 8. CardEnhanceService integration

`generateSceneSummary()` changed from direct `chatLanguageModel.chat(prompt)` to `llmReqConstructor.chat(messages, ctx, "CARD_ENHANCE", ModelType.DEFAULT)`. System and user messages constructed explicitly — system message with role description, user message with subtitle context.

### 9. LlmReplayController adaptation

`/llm-replay` endpoint updated to handle the new message format. Since stored `LlmCallLog` records will have `system_prompt` and `chat_history` populated, the replay can reconstruct the full `List<ChatMessage>`.

### 10. ConversationAgent unchanged

Streaming path (`ConversationAgent.generateStream()`) already uses `StreamingChatLanguageModel.chat(List<ChatMessage>, handler)` with proper `SystemMessage` separation. Not in scope.

### 11. Model selection preserved

`TaskName.REPORT` → `reportChatLanguageModel` (maxTokens=4096), all others → `chatLanguageModel` (maxTokens=2048). Logic unchanged from current TaskRunner.

## Testing Decisions

### Seam

The single seam is `LlmReqConstructor` — all non-streaming LLM calls go through it. This is the **only point** where unit tests inject mock `ChatLanguageModel` instances.

### What makes a good test

- Verify message assembly order (SystemMessage → exampleMessages → UserMessage)
- Verify template filling places correct values in correct template sections
- Verify XML format is produced correctly for conversation data
- Verify token usage and structured log fields are populated
- Do NOT verify LangChain4j internal behavior or HTTP-level request formatting

### Unit tests

- **`LlmReqConstructorTest`** (new, replaces `TaskRunnerTest`): Mock `ChatLanguageModel`, verify message assembly, logging, error strategies, model selection
- **Agent tests** (5 files): Mock `LlmReqConstructor` instead of `TaskRunner`; verify parameter assembly and response parsing unchanged; verify templates split correctly
- **`LlmCallLogServiceTest`**: Add assertions for `systemPrompt` and `chatHistory` fields

### E2E tests

- WireMock matching unchanged (`matchingJsonPath("$.messages[0].content")`)
- Template files in `src/test/resources/prompts/` split into system/user portions
- Marker keywords placed at start of system portion
- Response JSON fixtures unchanged

### Prior art

- `TaskRunnerTest` current mock pattern (StubChatModel) — extended to verify `List<ChatMessage>` instead of `String`
- `CorrectionAgentTest` current pattern (real PromptLoader + StubChatModel + real TaskRunner) — adapted to LlmReqConstructor
- `AssertionServiceTest` current pattern (`@Mock TaskRunner`) — adapted to `@Mock LlmReqConstructor`

## Out of Scope

- ConversationAgent streaming path — already uses structured messages
- `memory-cue-split.txt` and `memory-cue-entry.txt` few-shot optimization — no complete input/output pairs exist
- `LlmReplayController. /rerun-assertions` — already goes through AssertionService (which uses TaskRunner/LlmReqConstructor internally)
- Japanese Business mode — conversation and report templates in scope for splitting, but mode-specific logic unchanged
- Any changes to `ChatLanguageModel` bean configuration (temperature, baseUrl, etc.)
- Frontend changes — none required

## Further Notes

### Affected templates (production, `src/main/resources/prompts/`)

All 9 templates split at `---USER---`:

| Template | System portion contains | User portion contains |
|----------|------------------------|-----------------------|
| `correction.txt` | Role + rules + 5 categories + output format | `User's utterance: {userInput}` |
| `report.txt` | Role + JSON format + output instructions | `Full conversation: {fullConversation}` + `All errors: {allCorrections}` |
| `memory-profile.txt` | Role + rules + output instructions | `Previous learning profile: {oldLearningProfile}` + `Latest session: {errorSummary}` |
| `memory-cue-split.txt` | Role + rules + output format + Examples | `{messages}` (XML format) |
| `memory-cue-entry.txt` | Role + JSON format + Example | `{segment}` (XML format) |
| `assertion/extract-topics.txt` | Role + `{groupName}: {groupDescription}` + rules + output format | `{messages}` (XML format) |
| `assertion/extract-state.txt` | Role + `{groupName}` + `{topic}` + instructions | `{messages}` (XML format) |
| `assertion/judge-same.txt` | Role + rules + output format | `Statement A: {newState}` + `Statement B: {oldState}` |
| `assertion/merge-assertion.txt` | Role + instructions + output format | `Statement A: {stateA}` + `Statement B: {stateB}` |

### Task to LlmTaskDefinition mapping

| TaskName | `userTemplate` | `exampleMessages` |
|----------|---------------|-------------------|
| CORRECTION | `"User's utterance: {userInput}"` | — |
| REPORT | `"Full conversation:\n{fullConversation}\n\nAll errors:\n{allCorrections}"` | — |
| MERGE_LEARNING | `"Previous learning profile:\n---\n{oldLearningProfile}\n---\n\nLatest session data:\n- Error Summary: {errorSummary}"` | — |
| CHAT_SWITCHES | `"{messages}"` | — |
| GENERATE_MEMORY_CUE | `"{segment}"` | — |
| EXTRACT_TOPICS | `"{messages}"` | 2 pairs (XML transcript → JSON array) |
| EXTRACT_STATE | `"{messages}"` | — |
| JUDGE_SAME | `"Statement A: {newState}\nStatement B: {oldState}"` | 3 pairs (statements → YES/NO) |
| MERGE_ASSERTION | `"Statement A: {stateA}\nStatement B: {stateB}"` | — |

### Files to create

- `LlmReqConstructor.java` — new component
- `LlmTaskDefinition.java` — new data structure

### Files to delete

- `TaskRunner.java`
- `TaskDefinition.java`

### Files to modify

- All 9 production prompt templates — split at `---USER---`
- All 9 E2E test prompt templates — split at `---USER---`, markers in system portion
- `CorrectionAgent.java` — TaskDefinition → LlmTaskDefinition, add userTemplate
- `ReportAgent.java` — same, plus per-mode template override adaptation
- `LearningAgent.java` — same
- `MemoryCueAgent.java` — same, buildLabeledMessages → XML
- `AssertionService.java` — same, register 4 tasks with exampleMessages, extractTopicsWithRetry uses executeRaw
- `CardEnhanceService.java` — use LlmReqConstructor.chat()
- `LlmReplayController.java` — adapt to new message/log format
- `TaskRunnerTest.java` → rewrite as `LlmReqConstructorTest.java`
- `CorrectionAgentTest.java` — mock adapter
- `ReportAgentTest.java` — mock adapter
- `LearningAgentTest.java` — mock adapter
- `MemoryCueAgentTest.java` — mock adapter + XML format assertion
- `AssertionServiceTest.java` — mock adapter
- `LlmCallLogServiceTest.java` — add systemPrompt/chatHistory assertions
