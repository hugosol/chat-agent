# user_memory 增加 session_id 追溯

**Status:** `ready-for-agent`

## Parent

`.scratch/memory-system-upgrade/PRD.md` — 记忆系统升级

## What to build

给 `user_memory` 表增加 `session_id` 列，使每条 User Memory 记录可追溯到产生它的 Practice session。这是在已有 `UserMemory` 实体上新增一个透传字段，调用链为 `CoachMessageHandler.onEndSession()` → `MemoryService.generateMemoryAsync()` → `UserMemory` 入库，仅需沿调用链传递 sessionId。

## Acceptance criteria

- [ ] `UserMemory` 实体新增 `sessionId` 字段（`String`, nullable, 无 FK 约束）
- [ ] `UserMemory` 构造器新增接受 `sessionId` 的版本，保留旧构造器兼容测试
- [ ] `MemoryService.generateMemoryAsync()` 签名新增 `sessionId` 参数，向下透传至 `generateSingle()`
- [ ] `CoachMessageHandler.onEndSession()` 调用 `generateMemoryAsync` 时传入 `sessionId`
- [ ] 完成一个 Practice session 后，`user_memory` 表中新记录的 `session_id` 不为 NULL
- [ ] 旧数据行 `session_id` 为 NULL，Hibernate `ddl-auto=update` 自动添加列
- [ ] `MemoryServiceTest` 验证 `generateMemoryAsync` 调用链路包含 sessionId

## Blocked by

None — 可立即开始
