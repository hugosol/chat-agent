# 03: 前端下拉框合并 + JS 协议适配

**Status:** `ready-for-agent`

## Parent

PRD: `.scratch/agent-mode-merge/PRD.md`

## What to build

合并 `index.html` 中两个独立的 `<select>`（`scenarioSelect` + `personaSelect`）为单个 `modeSelect`，仅包含 `WORKPLACE_STANDUP`（显示 `"Standup Meeting"`）选项。`app.js` 中 `sendStart()` 发送 `{ type: "START_SESSION", mode: els.modeSelect.value }`，`SESSION_STARTED` handler 从 `msg.mode` 读取并仅禁用 modeSelect，`SESSION_RESUMED` handler 恢复 modeSelect 到正确的 mode 值。`resetUI()` 中禁用/启用逻辑同步简化。所有旧的 scenario/persona 元素引用和发送逻辑移除。

## Acceptance criteria

- [ ] `index.html` 中只有一个 `<select id="modeSelect">`，option 为 `<option value="WORKPLACE_STANDUP">Standup Meeting</option>`
- [ ] `scenarioSelect` 和 `personaSelect` 元素移除
- [ ] `app.js` 中 `els` 对象只有 `modeSelect` 引用（无 scenarioSelect、personaSelect）
- [ ] `sendStart()` 发送 `{ type: "START_SESSION", mode: els.modeSelect.value }`
- [ ] `SESSION_STARTED` handler 中 `els.modeSelect.disabled = true`（仅一行，不操作已删除的元素）
- [ ] `SESSION_RESUMED` handler 中 `els.modeSelect.value = msg.mode`，恢复正确的模式显示
- [ ] `resetUI()` 中 `els.modeSelect.disabled = false`
- [ ] 手动 smoke test：`mvn spring-boot:run -Dspring-boot.run.profiles=local` → 浏览器打开 → 下拉框只有 "Standup Meeting" → 点击 Start → 发一条消息 → 刷新页面 → 会话恢复且下拉框显示 "Standup Meeting"

## Blocked by

- `02-protocol-persistence-handler` — 后端协议消息已改为 `mode` 字段
