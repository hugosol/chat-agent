# 07: ReviewPage 编排 + StatsBar + CompletePage + 上限弹窗 + 偏好持久化

## Parent

[PRD: 闪卡复习功能](../PRD.md)

## What to build

实现复习页面的编排逻辑和收尾组件。`ReviewPage`：串联 CardDisplay + RatingButtons + StatsBar，管理"取卡→展示→翻面→评分→取下一张"的循环。每评分完一张调用 POST /api/review/rate（响应中已包含下一张卡），更新 StatsBar 的实时计数。当 API 返回 `card: null` 时切换到 CompletePage。`StatsBar`：底部状态栏，显示"已复习 N | 剩余 M | 新卡 X/Y"。`CompletePage`：全部完成页面，显示统计摘要 + 下次最早到期时间（`nextDueAt`）+ "返回"按钮回到 DeckPicker。实现每日新卡上限超出的确认弹窗逻辑。"开始复习"时将 Deck/模式保存到 UserPreferences。

## Acceptance criteria

- [ ] `ReviewPage` 组件：进入时调用 GET /api/review/next 获取第一张卡，随后每次评分后使用 rateCard 响应中的 card 字段
- [ ] 评分流程：POST /api/review/rate → 响应中包含 `{ card, stats }` → 更新 StatsBar → 如果 card 为 null → 切换到 CompletePage
- [ ] 翻面状态管理：卡片初始未翻面（`flipped: false`），点击翻面后显示 Back 和 RatingButtons
- [ ] 评分后立即翻回正面（`flipped: false`），加载下一张卡
- [ ] `StatsBar` 组件：底部固定栏，"已复习 N 张 | 剩余 M 张 | 新卡 X/Y"
- [ ] StatsBar 数据来源：每次 rateCard 响应中的 stats 字段直接更新（不额外调 /stats）
- [ ] `CompletePage` 组件：显示 "本轮复习完成"、"复习 X 张"、"新学 Y 张"、"下一张卡片将在约 Z 小时后到期"、"返回 Deck 选择"按钮
- [ ] 每日新卡上限弹窗：标准复习/仅新卡模式下，开始复习时如果 "已学 >= 上限" → 弹出确认框 "今日新卡已达上限（已学 N / 上限 M），今日不再引入新卡。仍然继续复习吗？" → 确认后以不引入新卡方式继续
- [ ] UserPreferences 持久化：开始复习时调用 `PUT /api/user/preferences` 保存 lastDeckId 和 lastMode（或复用 #02 的 save 端点）
- [ ] TopBar：顶部栏显示当前 Deck 名称 + "← 返回" 按钮（点击回到 DeckPicker）
- [ ] 所有交互元素添加 data-testid（`stats-bar`、`stats-reviewed`、`stats-remaining`、`stats-new`、`complete-page`、`complete-next-due`、`complete-back-btn`、`topbar-back`、`topbar-deck-name`、`limit-exceeded-dialog`、`limit-confirm-btn`）
- [ ] Vitest 测试：ReviewPage 编排逻辑（mock fetch）、CompletePage 渲染

## Blocked by

- #05: DeckPicker 组件（需要 DeckPicker 的 Deck/模式选择作为 ReviewPage 的入参）
- #06: CardDisplay + RatingButtons 组件（ReviewPage 直接使用这两个组件）
