# E2E 测试桩 + EnglishCoachMemoryCueIT

**Status:** `ready-for-agent`

## Parent

`.scratch/memory-system-upgrade/PRD.md` — 记忆系统升级

## What to build

新增 WireMock 测试桩和端到端测试，验证 MemoryCue 的完整 happy path：启动会话 → 多轮对话 → 话题切换 → 结束会话 → 数据库出现结构化记忆记录。遵循已有 E2E 模式：Playwright + WireMock 场景状态机 + DOM 等待 + H2 断言。

### E2E 场景

- 启动 WORKPLACE_STANDUP 会话
- 发送 3 轮用户消息（模拟 MSG#3 处话题切换）
- 结束会话
- 验证 `memory_cues` 表存在两条 `COMPLETED` 记录，topic 和 tags 各不相同

### WireMock 桩

新增两个 E2E marker（匹配 prompt 文件的第一行）：
- `E2E_MARKER_MEMORY_CUE_SPLIT`：匹配第一次 LLM 调用（话题切换检测），返回 `[3]`
- `E2E_MARKER_MEMORY_CUE_ENTRY`：匹配第二次 LLM 调用（摘要生成），返回标准 JSON

## Acceptance criteria

- [ ] `WireMockStubs` 新增 `registerMemoryCueStubs()` 方法，注册两个标桩（SPLIT + ENTRY），使用场景状态机确保 ENTRY 桩可被多次调用返回不同内容
- [ ] WireMock 响应文件 `memory-cue-split-two-switch.json`：返回 `[3]`（MSG#3 后切换）
- [ ] WireMock 响应文件 `memory-cue-entry-success.json`：返回 `{"topic": "...", "summary": "...", "tags": ["...", "..."]}`
- [ ] E2E 测试用 Prompt 文件（`src/test/resources/prompts/memory-cue-split.txt`、`memory-cue-entry.txt`）以 `E2E_MARKER_` 作为首行标记
- [ ] `EnglishCoachMemoryCueIT`：启动 WORKPLACE_STANDUP 会话 → 发送 3 条用户消息 → 每轮等待 Agent 回复 → 结束会话 → 等待 Report 弹窗
- [ ] `EnglishCoachMemoryCueIT`：通过 `MemoryCueRepository` 断言 `memory_cues` 表有 2 行 `COMPLETED` 记录
- [ ] `EnglishCoachMemoryCueIT`：两条记录的 topic 不同，tags 非空且不同
- [ ] `EnglishCoachMemoryCueIT`：两条记录 `sessionId` 相同，`userId` 正确，`mode=WORKPLACE_STANDUP`，`segmentIndex` 分别为 0 和 1
- [ ] `mvn verify` 全部通过
- [ ] 测试出错时自动保存截图到 `target/e2e-screenshots/`

## Blocked by

- #5 — MemoryCueService + Handler 集成（需要完整的 end-to-end 流程）
