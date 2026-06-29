# Data Model — Chat Agent

> 实体 schema 与关系图。FSRS 调度器详解见 [docs/fsrs.md](fsrs.md)。领域术语定义见 [CONTEXT.md](../CONTEXT.md)。

---

## Core Entities (Chat & Memory)

```
┌─────────────┐     ┌──────────────┐     ┌────────────────┐
│   Session   │     │   Message    │     │  Correction    │
│─────────────│     │──────────────│     │────────────────│
│ id (PK)     │────→│ id (PK)      │     │ id (PK)        │
│ userId      │     │ sessionId FK │────→│ messageId FK   │
│ mode        │     │ role         │     │ type           │
│ status      │     │ content      │     │ original       │
│ createdAt   │     │ createdAt    │     │ corrected      │
└─────────────┘     └──────────────┘     │ explanation    │
                                         └────────────────┘
```

### Core Enums

```
Enum: MessageRole { USER, AGENT, CORRECTION }
Enum: ErrorType { GRAMMAR, WORD_CHOICE, CHINGLISH, PRONUNCIATION, FLUENCY }
Enum: SessionStatus { ACTIVE, COMPLETED, FAILED }
```

---

## Memory Entities

```
┌──────────────────────┐     ┌──────────────────────┐
│  UserLearningProfile │     │      MemoryCue       │
│──────────────────────│     │──────────────────────│
│ id (PK)              │     │ id (PK)              │
│ userId               │     │ sessionId            │
│ type                 │     │ userId               │
│ version              │     │ mode                 │
│ summary              │     │ topic                │
│ createdAt            │     │ summary              │
└──────────────────────┘     │ status (MemoryCueStatus)
                             │ createdAt
                             └──────────────────────┘

Enum: LearningType { LEARNING_PROFILE }
Enum: MemoryCueStatus { COMPLETED, SEGMENT_FAILED }
```

---

## MemoryAssertion Entities (V2 Structured Memory)

```
┌─────────────────────────┐
│    MemoryAssertion      │     ┌─────────────────────────┐
│─────────────────────────│     │    AssertionGroup       │
│ id (PK)                 │     │─────────────────────────│
│ group_id (FK→Group)     │────→│ id (PK)                 │
│ session_id              │     │ name                    │
│ user_id                 │     │ description             │
│ mode                    │     └─────────────────────────┘
│ topic                   │
│ state                   │     ┌─────────────────────────┐
│ enabled                 │     │   AssertionLineage      │
│ create_time             │     │─────────────────────────│
│ update_time             │     │ parent_id (PK, FK)      │
└─────────────────────────┘     │ child_id (PK, FK)       │
                                │ operation               │
                                └─────────────────────────┘

Enum: AssertionOperation { MERGE }
```

Assertion embedding store is an independent `InMemoryEmbeddingStore` (separate from MemoryCue store), persisted to `./data/assertion-embedding-store.json`.

---

## Logging Entities

```
┌──────────────────────┐
│     LlmCallLog       │
│──────────────────────│
│ id (PK)              │
│ sessionId            │
│ userId               │
│ agentType            │
│ mode                 │
│ requestPrompt        │
│ systemPrompt         │
│ chatHistory          │
│ responseText         │
│ inputTokens          │
│ outputTokens         │
│ durationMs           │
│ status               │
│ errorMessage         │
│ createTime           │
└──────────────────────┘
```

Records cleaned up on startup (3+ days old). Async write via `llmLogExecutor` (core=2, max=4).

---

## Flashcard Module

> Full FSRS algorithm reference: [docs/fsrs.md](fsrs.md). Domain definitions: [CONTEXT.md](../CONTEXT.md).

```
┌──────────┐    ┌──────────────┐    ┌──────────┐
│   Tag    │    │     Card     │    │ UserPref │
│──────────│    │──────────────│    │──────────│
│ id (PK)  │←──→│ id (PK)      │    │ id (PK)  │
│ userId   │    │ userId       │    │ userId   │
│ name     │    │ front        │    │ ...config│
│ type     │    │ back         │    └──────────┘
└──────────┘    │ cardState    │
                │ due          │    ┌──────────────┐
                │ stability    │    │  ReviewLog   │
                │ difficulty   │    │──────────────│
                │ ...FSRS attrs│←───│ id (PK)      │
                └──────────────┘    │ cardId (FK)  │
                                    │ rating       │
┌─────────────────┐                 │ elapsedDays  │
│  FsrsParameters │                 │ ...snapshots │
│─────────────────│                 └──────────────┘
│ userId          │
│ w0..w20         │
│ enableShortTerm │
└─────────────────┘

Enum: CardState { NEW(0), LEARNING(1), REVIEW(2), RELEARNING(3) }
Enum: CardStatus { ACTIVE, SUSPENDED }
```

---

## Movie Module

```
┌──────────────────┐     ┌──────────────────┐
│   WatchedMovie   │     │   SubtitleLine   │
│──────────────────│     │──────────────────│
│ id (PK)          │────→│ id (PK)          │
│ userId           │     │ imdbId           │
│ imdbId           │     │ movieTitle       │
│ title            │     │ startTime        │
│ releaseYear      │     │ endTime          │
│ subtitleStatus   │     │ text             │
└──────────────────┘     │ wordsLower       │
                         │ lineIndex        │
Enum: SubtitleStatus     └──────────────────┘
{ PENDING, DOWNLOADING,
  DONE, FAILED }

┌──────────────────────┐
│  CardEnhancement     │
│──────────────────────│
│ cardId (PK, FK)      │
│ imdbId               │
│ movieTitle           │
│ quoteText            │
│ quoteTimestamp       │
│ sceneSummary         │
└──────────────────────┘
```

---

## TimeLabel

```
Enum: TimeLabel {
  JUST_NOW, A_FEW_MINUTES_AGO,
  LAST_NIGHT, THIS_MORNING, THIS_AFTERNOON, THIS_EVENING, TONIGHT,
  YESTERDAY_MORNING, YESTERDAY_AFTERNOON, YESTERDAY_EVENING,
  A_FEW_DAYS_AGO, ABOUT_A_WEEK_AGO, A_FEW_WEEKS_AGO, ABOUT_A_MONTH_AGO,
  A_WHILE_AGO
}
```

Computed via date+period judgment (not duration-bucket traversal). `computeLabel(Instant, Instant, ZoneId)` signature — timezone passed explicitly, internally converted to user wall-clock time.
