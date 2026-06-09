Status: `ready-for-agent`

## What to build

将 `TimeLabel.computeLabel()` 改为时区感知签名 `(Instant, Instant, ZoneId)`，并在 `ConversationAgent` 和 `TurnProcessor` 中注入 `UserPreferencesService` 以获取用户时区，使 AI 对话中引用的记忆时间标签反映 Learner 本地时间，而非服务器 UTC。

**端到端流程**：Conversation 会话中 AI 生成系统提示词 → `formatMemoryCuesForPrompt()` 对每个 MemoryCue 调用 `TimeLabel.computeLabel(cue.createdAt, now, userZoneId)` → "今天早上 10 点创建的卡片"（而非错误的 "last night"）→ 用户看到正确的记忆提示。

**关键实现点**：

1. **`TimeLabel.computeLabel()` 签名变更**：
   - 旧：`(LocalDateTime eventTime, LocalDateTime referenceTime)`
   - 新：`(Instant eventTime, Instant referenceTime, ZoneId zoneId)`
   - 内部：`eventTime.atZone(zoneId).toLocalDateTime()` 转换后，后续日历/时间槽逻辑不变

2. **`ConversationAgent`**：
   - 构造函数新增 `UserPreferencesService` 参数（Spring 自动注入）
   - `formatMemoryCuesForPrompt()` 从 `private static` 改为实例方法
   - 内部通过 `userPreferencesService.get(userId).getTimezone()` 获取 `ZoneId`，传入 `TimeLabel.computeLabel()`
   - 需要从上方调用链传入 `userId`（`buildSystemContent` 已有上下文可扩展）

3. **`TurnProcessor`**：
   - 构造函数新增 `UserPreferencesService` 参数
   - `resolveMemoryContext()` 中：`var zoneId = userPreferencesService.get(userId).getTimezone()`，传入 `TimeLabel.computeLabel()`

4. **连带适配**：
   - `MemoryCue.getTimeLabel()`：签名改为接受 `Instant referenceTime` + `ZoneId zoneId`（`@Transient`，当前仅 MemoryCueAgent 系统提示词使用）
   - `UserLearningProfile.getTimeLabel()`：同步更新签名（`@Transient`，当前无生产调用方，仅做编译适配）
   - `ChatMessageHandler`：若其构造函数也需要传递 `UserPreferencesService` 给 `TurnProcessor`，同步添加参数

## Acceptance criteria

- [ ] `TimeLabel.computeLabel()` 签名为 `(Instant, Instant, ZoneId)`，内部逻辑通过 `atZone(zoneId)` 转换后保持不变
- [ ] `ConversationAgent` 注入 `UserPreferencesService`，`formatMemoryCuesForPrompt` 为实例方法，传入 ZoneId
- [ ] `TurnProcessor` 注入 `UserPreferencesService`，`resolveMemoryContext` 中传入 ZoneId
- [ ] `MemoryCue.getTimeLabel()` 和 `UserLearningProfile.getTimeLabel()` 签名适配新 TimeLabel 接口
- [ ] AI 对话中记忆提示的时间标签反映用户本地时区（如北京时间上午 10 点显示"今天早上"而非"last night"）
- [ ] `TimeLabelTest` 全部 52 处调用适配新签名，所有断言通过
- [ ] `ConversationAgentTest`：构造函数适配 + 5 处 CueMatch 类型变更 + formatMemoryCuesForPrompt 测试用 ZonedDateTime 构造时间
- [ ] `TurnProcessorTest`：构造函数适配 + 9 处 CueMatch 创建 `LocalDateTime.of()` → `Instant.parse()`
- [ ] `ChatMessageHandlerTest`：若构造函数参数增加则同步适配
- [ ] `mvn test` 全绿

## Blocked by

- 03-instant-migration（依赖 Instant 类型已就位）
