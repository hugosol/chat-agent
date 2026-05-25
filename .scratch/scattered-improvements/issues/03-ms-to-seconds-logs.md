# Latency log format: ms → seconds

**Status:** `ready-for-agent`

## Parent

`.scratch/scattered-improvements/PRD.md` — 零星优化

## What to build

Change two latency log statements from milliseconds to seconds with one decimal place, for readability.

| File | Line (current) | Before | After |
|------|----------------|--------|-------|
| `websocket/CoachMessageHandler.java` | `log.info("Conversation close latency: {}ms", elapsed)` | `12345ms` | `12.3s` |
| `service/MemoryCueService.java` | `log.info("Background task duration (MemoryCue + consolidation): {}ms", elapsed)` | `12345ms` | `12.3s` |

## Implementation

In both locations:
```java
log.info("Conversation close latency: {}s", String.format("%.1f", elapsed / 1000.0));
```

```java
log.info("Background task duration (MemoryCue + consolidation): {}s", String.format("%.1f", elapsed / 1000.0));
```

## Acceptance criteria

- Log output shows `12.3s` instead of `12345ms`
- Integer values display as e.g. `5.0s`, not `5s`
- `mvn compile` passes
