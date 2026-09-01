# ExampleMsgFormatter — extract XML formatting utility

- **Status**: `ready-for-agent`
- **Created**: 2026-06-29

## Parent

PRD: `.scratch/prompt-refactor/PRD.md`

## What to build

Create a new static utility class `ExampleMsgFormatter` in `agent.common` that centralizes conversation-to-XML conversion and few-shot template parsing. Delete the duplicated `buildLabeledMessages`, `buildSegmentText`, and `escapeXml` methods from `SessionComplete` and `MemoryCueAgent`, replacing all call sites with `ExampleMsgFormatter` methods.

This is a pure internal refactor — no prompt files change, no behavior change.

The class has three public methods:

- `toXml(List<MessageData>)` — full conversation to `<turn>` XML (replaces both `buildLabeledMessages` and `buildSegmentText`)
- `toXmlUserOnly(List<MessageData>)` — user-only subset (same format, skips assistant turns)
- `parseFewShot(String content, boolean userOnly)` — parses human-readable `.txt` format into `List<ChatMessage>` ready for `LlmTaskDefinition.exampleMessages()`

The few-shot template format uses `User:` / `Assistant:` prefixed blocks separated by `---`:

```
User: Yesterday I go to the park.
Assistant: Ah, you went to the park? Was it crowded?
---
["past tense"]
---
User: I have a idea for the meeting.
...
```

Odd blocks (0-indexed) are conversation → `toXml()` → `UserMessage`. Even blocks are expected AI output → `AiMessage`.

## Acceptance criteria

- [ ] `ExampleMsgFormatter.toXml(List<MessageData>)` produces identical output to current `buildLabeledMessages` for the same input
- [ ] `ExampleMsgFormatter.parseFewShot()` correctly parses the few-shot template format into alternating `UserMessage`/`AiMessage` pairs
- [ ] `SessionComplete.buildLabeledMessages()` is deleted; its sole caller in `AssertionService.extract()` uses `ExampleMsgFormatter.toXml()` instead
- [ ] `MemoryCueAgent.buildLabeledMessages()` and `buildSegmentText()` are deleted; their callers use `ExampleMsgFormatter.toXml()`
- [ ] `escapeXml()` is deleted from both `SessionComplete` and `MemoryCueAgent`
- [ ] All existing unit tests pass (`mvn test`)
- [ ] Unit tests for `ExampleMsgFormatter` cover: basic XML generation, XML escaping, user-only mode, few-shot parsing with 1/2/3 example pairs, empty input handling

## Blocked by

None — can start immediately.
