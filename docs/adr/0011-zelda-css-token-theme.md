# CSS 自定义属性作为 Zelda 主题 Token 层

Chat Agent 前端引入 Zelda: Breath of the Wild 风格的暗色主题。我们不引入 `zelda-hyrule-ui` npm 包，改用 CSS 自定义属性作为 Design Token 载体，在现有 CSS Modules 中逐组件替换硬编码颜色值。CSS 规则分为功能层（布局/ARIA/浏览器补丁，不改）和主题层（颜色/边框/阴影/字体，引用 Token），两者逻辑分离。

**Status**: proposed

## Considered Options

| 方案 | 拒绝原因 |
|------|---------|
| npm 包直接引用 | 引入 83 个组件 + Less 预处理器 + Hylia Serif 字体——对现有架构侵入大，Zelda 组件与 Chat Agent 功能需求不完全匹配 |
| Less 预处理器 | 增加构建依赖；Less 变量编译时展开为字面值，无法实现功能层/主题层分离；不能运行时动态切换 |
| CSS 自定义属性 | **选中** — 零构建依赖，CSS 标准，Vite 原生支持，`var()` 在 CSS Modules 哈希类中正常工作，支持未来运行时主题切换 |

## Choice

- **`tokens.css`** 作为单一真相源，定义 `:root` 级 CSS 自定义属性（47 个 Token，覆盖颜色、阴影、排版、圆角、动效、层级）
- **功能层不动**：布局属性（`display`/`position`/`flex`/`padding`/`margin`）、ARIA 驱动显隐（`[aria-expanded]`/`[data-active]`）、iOS Safari 补丁（`@media (hover: hover)`/`env(safe-area-inset-*)`）永不修改
- **主题层引用 Token**：所有颜色/边框/阴影/字体值从硬编码 hex 替换为 `var(--token-name)` 引用
- **Zelda 双层边框**：通过 `::after` 伪元素手写实现（`inset: 3px; border: 1px solid var(--border)`），不依赖 npm 组件

## Consequences

- **可逆转**：删除 `tokens.css` + 恢复 `var()` 为原始 hex 值即可回滚
- **零构建依赖增加**：无需 `less`/`postcss`/`sass`
- **运行时主题切换潜力**：JS 可动态修改 `document.documentElement.style.setProperty()` 换肤
- **每页独立构建**：多页面 Vite Library Mode 架构下，每个 entry 需独立 `import '../tokens.css'`，构建产物中 tokens 重复但体积小（~2KB）
