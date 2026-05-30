# 02 — 前端闪卡面板：两阶段录入 + Chip 标签 + 面板互斥 + 闪烁提示

**Status:** `ready-for-agent`

## Parent

[PRD: 闪卡模块 —— 卡片录入 MVP](../PRD.md)

## What to build

在现有聊天页面上实现闪卡录入面板的完整前端交互。Learner 点击 header 上的"闪卡"按钮打开面板，通过两阶段流录入 card（front → back + 标签），保存后自动折叠，出现闪烁提示。闪卡面板与 Debug 面板互斥展开。

**面板 DOM 结构（插入 `index.html`，在 `<div id="debugPanel">` 之前）：**

```html
<div id="flashcardPanel" class="flashcard-panel collapsed">
    <div class="flashcard-header">
        <span class="flashcard-title">闪卡</span>
        <button id="flashcardClose" class="flashcard-close">&times;</button>
    </div>
    <div id="flashcardStage1" class="flashcard-stage">
        <input id="flashcardFront" type="text" placeholder="单词或表达...">
        <button id="flashcardContinue" class="flashcard-btn">继续</button>
    </div>
    <div id="flashcardStage2" class="flashcard-stage hidden">
        <textarea id="flashcardBack" placeholder="释义或校正文..." rows="2"></textarea>
        <div id="flashcardTagArea">
            <div id="flashcardTagChips"></div>
            <input id="flashcardTagInput" type="text" placeholder="添加标签...">
            <div id="flashcardTagSuggestions" class="tag-suggestions hidden"></div>
        </div>
        <button id="flashcardSave" class="flashcard-btn primary">保存</button>
    </div>
    <div id="flashcardToast" class="flashcard-toast hidden">已保存</div>
</div>
```

**Header 按钮：**

在 `<header>` 内 "Corrections N" 按钮旁新增 "闪卡" toggle 按钮：
```html
<button id="flashcardToggle" class="header-btn">闪卡</button>
```

**CSS（追加到 `style.css` 末尾）：**

- `.flashcard-panel`：`position: fixed; bottom: 0; left: 0; right: 0; z-index: 201; background: rgba(0,0,0,0.92); border-top: 1px solid #333;`，collapsed 时 `display: none`
- 阶段一（stage 1）：面板高度 ~60px，仅 front input + "继续"按钮水平排列，不遮挡聊天记录
- 阶段二（stage 2）：`max-height: 70vh; overflow-y: auto`，容纳 back textarea + chip 标签区 + "保存"按钮
- Chip 标签：inline 圆角背景条，内含 tag 名 + × 删除按钮，自动换行
- Tag 建议列表：绝对定位浮层，匹配高亮，点击或回车确认
- `.flashcard-toast`：`position: fixed; bottom: 10px; left: 50%; transform: translateX(-50%)`，CSS animation 闪烁（fade-in-out，持续 ~2 秒）
- 移动端兼容：390px 宽度下 chip 自动换行，面板不超出视口

**`flashcard.js` 核心逻辑：**

- 面板两阶段：
  - 初始为 collapsed。点击"闪卡"按钮 → 展开阶段一。
  - 阶段一中输入 front → 点击"继续" → 隐藏 stage1、显示 stage2。
  - 面板折叠（`×` 按钮点击或保存成功）→ 回到 collapsed，清空所有输入，重置到阶段一。
- Chip 标签输入：
  - 在 `#flashcardTagInput` 中打字 → `fetch("GET /api/tags")` 获取已有标签 → 前端过滤匹配 → 浮动建议列表。
  - 点击建议项或按回车 → 选中的 tag 变为 chip（显示在 `#flashcardTagChips`），清空 input。
  - 退格：input 为空时退格删除最后一个 chip。
  - × 按钮：点击 chip 上的 × 删除该 chip。
  - 重复 tag 名不添加。
- 保存：
  - 点击"保存" → 收集 front、back 和 chip 中的 tag 名数组 → `fetch("POST /api/cards/add", { body: JSON.stringify(...) })`
  - 成功（200）：面板折叠 + 显示 `#flashcardToast`（2 秒后自动隐藏）→ 清空所有状态
  - 失败：在面板内显示错误信息
- 面板互斥（`window.activePanel`）：
  - 展开闪卡面板时 → 自动折叠 Debug 面板（`debugLog.classList.add('hidden')`），设置 `activePanel = 'flashcard'`
  - 折叠闪卡面板时 → `activePanel = null`
  - Debug 面板展开时 → 如果 `activePanel === 'flashcard'`，先折叠闪卡面板
  - Header "闪卡"按钮点击 → 如果 `activePanel === 'flashcard'` 则折叠，否则展开
- 事件监听：Enter 键在 front input 触发"继续"，在 tag input 触发选择第一个建议（或确认当前文本为新 tag）

**HTML 文件引入：**

在 `index.html` 的 `</body>` 前，`<script src="/app.js">` 之后，添加：
```html
<script src="/flashcard.js"></script>
```

## Acceptance criteria

- [ ] 点击 header "闪卡"按钮 → 面板从底部滑入，显示阶段一（仅 front 输入框 + "继续"按钮，高度 ~60px）
- [ ] 阶段一输入 front 后点击"继续" → stage1 隐藏，stage2 展开（back textarea + 标签输入 + "保存"按钮）
- [ ] 在标签输入框打字 → 下方出现已有标签的匹配建议列表（数据来自 `GET /api/tags`）
- [ ] 点击建议项或回车 → 标签变为 chip 显示在输入框上方
- [ ] 点击 chip 上的 × → chip 被移除
- [ ] 输入框为空时按退格 → 删除最后一个 chip
- [ ] 点击"保存" → 面板自动折叠，底部闪现"已保存"文字（~2 秒后消失），所有输入清空并重置到阶段一
- [ ] 闪卡面板展开时 → Debug 面板自动折叠；Debug 面板展开时 → 闪卡面板自动折叠
- [ ] 闪卡面板 `×` 按钮 → 面板折叠，输入清空
- [ ] 390px 移动端宽度下，chip 标签自动换行，面板不溢出
- [ ] 无 JSESSIONID 时 API 调用失败，面板内显示错误提示

## Blocked by

- [01 — 后端基础](./01-fsrs-backend.md)
