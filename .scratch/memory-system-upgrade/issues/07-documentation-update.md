# 文档更新

**Status:** `ready-for-agent`

## Parent

`.scratch/memory-system-upgrade/PRD.md` — 记忆系统升级

## What to build

更新项目文档，反映记忆系统升级后的新架构和新行为。新增 Architecture Decision Record 记录双轨记忆系统设计决策。

## Acceptance criteria

- [ ] `CONTEXT.md`：新增术语 `MemoryCue`（结构化话题记忆条目）、`Memory Cue Split`（话题切换检测）、`Memory Cue Entry`（单 segment 摘要生成）
- [ ] `CONTEXT.md`：修正 `Memory Injection` 定义——注入窗口从第一轮改为前三轮（messageId ≤ 3）
- [ ] `AGENTS.md`：补充记忆系统新行为——三轮注入机制、MemoryCue 模块概览、onEndSession 新增并行支路
- [ ] 新建 `docs/adr/dual-memory-system.md`：记录双轨记忆系统架构决策，包括旧 User Memory 与 MemoryCue 并存的原因、各自职责边界、未来统一路径
- [ ] `docs/architecture.md` 同步更新记忆系统架构：新增 MemoryCue 模块的组件图/数据流描述
- [ ] `README.md` 同步更新记忆系统功能描述

## Blocked by

- #1-#6 — 所有功能切片完成后，文档反映最终实现
