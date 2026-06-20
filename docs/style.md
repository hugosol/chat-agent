# Style Guide — Zelda Theme

Chat Agent 前端 Zelda: Breath of the Wild 风格改造的视觉决策记录。
与 `docs/adr/0011-zelda-css-token-theme.md`（架构决策）互补——
本文档记录具体的颜色选择、组件变体分配、以及试错历史。

## 设计原则

| 原则 | 说明 |
|------|------|
| CSS Token 单一真相源 | `src/main/frontend/src/tokens.css` |
| 功能层/主题层分离 | 布局/ARIA/iOS补丁不动，仅替换颜色/边框/阴影 |
| 不引入 npm 依赖 | 纯 CSS 自定义属性，不改 JSX |
| Zelda 原作色彩 | 色值取自 zelda-hyrule-ui v0.4.0，按需调整不透明度 |

## Token 速查

### 背景色

| Token | 值 | 用途 |
|-------|-----|------|
| `--bg-page` | `#1a1a1a` | 页面底色 |
| `--bg-surface` | `rgba(0, 0, 0, 0.6)` | 标准面板/按钮 |
| `--bg-sheikah` | `rgba(10, 20, 40, 0.85)` | 希卡按钮 |
| `--bg-golden` | `#2e2418` | 金色面板（不透明） |
| `--bg-item` | `rgba(60, 58, 52, 0.8)` | 物品槽（备用） |
| `--bg-elevated` | `rgba(30, 30, 28, 0.9)` | 浮层面板 |
| `--bg-input` | `rgba(0, 0, 0, 0.4)` | 输入框 |
| `--bg-overlay` | `rgba(0, 0, 0, 0.85)` | 遮罩 |
| `--bg-correction` | `rgba(10, 20, 10, 0.6)` | 纠错气泡 |

### 文字色

| Token | 值 | 用途 |
|-------|-----|------|
| `--text-main` | `#E9E1D1` | 主文字 |
| `--text-muted` | `rgba(233, 225, 209, 0.6)` | 次要文字 |
| `--text-accent` | `#E2D146` | 黄色强调 |
| `--text-danger` | `#F15050` | 红色警告 |
| `--text-success` | `#6FD49C` | 绿色成功 |

### 希卡蓝（强调色）

| Token | 值 |
|-------|-----|
| `--accent` | `#3CD3FC` |
| `--accent-dark` | `#0A8DD7` |
| `--accent-glow` | `#4FC0FF` |
| `--accent-dim` | `rgba(60, 211, 252, 0.5)` |

### 坦色（边框）

| Token | 值 |
|-------|-----|
| `--tan` | `#E2DED3` |
| `--border` | `rgba(226, 222, 211, 0.3)` |
| `--border-active` | `rgba(226, 222, 211, 0.6)` |
| `--border-focus` | `#3CD3FC` |

### 辉光

| Token | 场景 |
|-------|------|
| `--glow-blue` | 希卡蓝悬停 |
| `--glow-sheikah` | 希卡强辉光 |
| `--glow-golden` | 金色辉光 |
| `--glow-hover` | 选中/通用悬停 |

### 功能色

| Token | 值 | 用途 |
|-------|-----|------|
| `--danger` | `#F15050` | 危险/删除 |
| `--success` | `#6FD49C` | 成功 |
| `--warning` | `#FCC413` | 金色/警告 |
| `--rating-again` | `#e74c3c` | FSRS Again |
| `--rating-hard` | `#e67e22` | FSRS Hard |
| `--rating-good` | `#27ae60` | FSRS Good |
| `--rating-easy` | `#3498db` | FSRS Easy |

## 组件变体分配

### Review 模块

| 组件 | 变体 | 底色 | 装饰 |
|------|------|------|------|
| **CardDisplay 闪卡** | sheikah midnight | `linear-gradient(180deg, #0c2440 0%, #0a1e36 100%)` | 扫描线纹理 + 蓝辉光边框 + 常驻微光晕 |
| **CompletePage 卡片** | golden | `#2e2418` | 金色顶线 + 金辉光边框 |
| **CompletePage 返回按钮** | golden | `--bg-golden` | 金辉光悬停 |
| **DeckPicker 开始按钮** | sheikah | `--bg-sheikah` | 蓝辉光悬停 |
| **ReviewPage 返回按钮** | ghost | 透明 | 悬停显形 |
| **RatingButtons** | 保留原色 | 红/橙/绿/蓝 | Zelda 双层 `::after` 边框 + 辉光悬停 |
| **enhanceBtn** | sheikah | `--bg-sheikah` | 蓝辉光 |
| **editCancelBtn** | ghost | 透明 | 悬停显形 |
| **editSaveBtn** | 保留绿 | `--success` | 不变 |

### 闪卡纹理配方（SheikahScanlines 纯 CSS 版）

```css
.card::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  mix-blend-mode: overlay;
  background: repeating-linear-gradient(
    0deg,
    transparent,
    transparent 1px,
    rgba(60, 211, 252, 0.07) 1px,
    rgba(60, 211, 252, 0.07) 2px
  );
  border-radius: var(--radius-sm);
}
```

### Zelda 双层边框模式

```css
/* 所有 Zelda 风格按钮/面板的标准模式 */
.component {
  position: relative;
  background: var(--bg-surface);
  border-radius: var(--radius-sm);
}
.component::after {
  content: '';
  position: absolute;
  inset: 3px;
  border: 1px solid var(--border);
  border-radius: 2px;
  pointer-events: none;
}
```

## 试错历史

| 尝试 | 结果 | 原因 |
|------|------|------|
| 页面背景 `#66645D`（Zelda 标准暖灰） | ❌ 太灰 | 与本项目暗色定位冲突 |
| 闪卡 blueGrey `#1a2a3a` | ❌ 不满意 | 蓝调不够 |
| 闪卡 darkBlue `#0a1628` | ⚠️ 尚可 | 太暗，与背景区分度不够 |
| 闪卡 `#0A8DD7`（希卡暗蓝） | ❌ 太亮 | 作为面板底色过亮 |
| 闪卡 golden 半透明 `rgba(30, 25, 15, 0.85)` | ❌ 不可见 | 半透明叠加纯黑底消散 |
| 闪卡 golden 不透明 `#2e2418` | ⚠️ 可见但无质感 | 纯色平面缺乏层次 |
| 闪卡 deep navy `#0c2440` | ⚠️ 可用 | 不如 midnight 有科技感 |
| **闪卡 midnight + 渐变 + 扫描线 + 光晕** | ✅ 选中 | 蓝调清晰 + 石板纹理 + 深度感 |

## 未改动（功能层保护）

- `position: fixed` + `env(safe-area-inset-*)` iOS 适配
- `@media (hover: hover)` iOS sticky hover 修复
- `aria-expanded` / `data-active` 驱动显隐
- 所有布局属性（`display` / `flex` / `padding` / `margin`）
- `data-testid` E2E 测试属性
- 流式光标闪烁动画
- Token 进度条色阶
