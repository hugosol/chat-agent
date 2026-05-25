# 三轮记忆注入

**Status:** `ready-for-agent`

## Parent

`.scratch/memory-system-upgrade/PRD.md` — 记忆系统升级

## What to build

将 Topic Memory 和 Learning Profile 的注入窗口从仅第一轮扩展到前三轮（messageId ≤ 3）。核心改动：合并 `ConversationAgent` 的两个公开方法（`generateStream` + `generateStreamFirstTurn`）为一个带 `int messageId` 参数的统一入口，将注入判断逻辑从 `TurnProcessor` 的 `isFirstTurn` 条件分支下沉到 `ConversationAgent` 内部。`TurnProcessor` 不再关心是否首轮，只需透传 `messageId`。

内部注入判断：
```java
boolean injectMemory = messageId <= 3 && (!topicSummary.isBlank() || !learningProfile.isBlank());
```

## Acceptance criteria

- [ ] `ConversationAgent` 删除 `generateStream()` 和 `generateStreamFirstTurn()` 两个公开方法
- [ ] `ConversationAgent` 新公开方法签名：`generateStream(List<MessageData> history, AgentMode mode, String topicSummary, String learningProfile, int messageId, StreamingChatResponseHandler handler)`
- [ ] 内部 `buildSystemContent` 根据 `messageId <= 3 && hasMemory` 决定是否注入 Topic Memory 和 Learning Profile
- [ ] `messageId > 3` 时不再注入记忆（system prompt 不包含 `[Conversation Memory]` / `[Your Learning Profile]` / `[Active Engagement]` 区块）
- [ ] 无记忆数据时（topicSummary 和 learningProfile 均为空），即使 `messageId=1` 也不注入
- [ ] `TurnProcessor` 移除 `isFirstTurn` 变量及其条件分支
- [ ] `TurnProcessor` 直接透传 `messageId` 给 `ConversationAgent.generateStream()`
- [ ] `ConversationAgentTest` 更新：messageId=1 注入、messageId=3 注入、messageId=4 不注入、无记忆不注入
- [ ] `TurnProcessorTest` 更新：不再测试 `isFirstTurn` 条件分支路径

## Blocked by

None — 可立即开始
