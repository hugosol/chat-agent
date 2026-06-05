# 03: 前端 RatingButtons 显示间隔时间

**Status:** `ready-for-agent`

## 范围

修改 `RatingButtons` 和 `ReviewPage` 组件，在评分按钮旁显示预计复习间隔。

## 实现内容

### RatingButtons 组件改造
- 新增 prop：`preview: Record<string, CardState> | null`
- 当 `preview` 非 null 时，每个按钮文字变为 `"{label} · {间隔}"`（如 `"Good · 约15天后"`）
- 当 `preview` 为 null 时，退化为现有按钮文字（纯 label）
- `data-testid` 不变（`rating-again`、`rating-hard`、`rating-good`、`rating-easy`）

### 间隔格式化（前端工具函数）
- 输入：`due`（ISO 时间字符串）和当前时间
- 计算差值 `due - now`，按量级格式化：
  - `< 60秒` → `"<1分钟"`
  - `< 60分钟` → `"X分钟"`
  - `< 24小时` → `"X小时"`
  - `< 30天` → `"X天"`
  - `< 365天` → `"X个月"`
  - `≥ 365天` → `"X年"`
- 放在 `shared/` 下作为通用工具

### ReviewPage 状态管理
- `GET /api/review/start` 响应中提取 `preview` → 存入 state
- `POST /api/review/next` 响应中提取新卡片 `preview` → 更新 state
- 翻出卡片后，`preview` 传给 `RatingButtons`

### 类型定义（reviewTypes.ts）
- 新增 `PreviewInfo` 类型：
```ts
type PreviewInfo = Record<string, {
  stability: number;
  difficulty: number;
  state: number;
  step: number;
  due: string;
  reps: number;
  lapses: number;
  lastReview: string | null;
  elapsedDays: number;
}>;
```
- API 响应类型中新增 `preview?: PreviewInfo`

## 依赖
Issue 02（API 响应包含 preview）

## 验证
- 前端单元测试（Vitest）：RatingButtons 渲染含间隔的按钮文字
- E2E：现有 9 个场景不受影响（data-testid 不变），可选新增断言验证间隔文字可见
