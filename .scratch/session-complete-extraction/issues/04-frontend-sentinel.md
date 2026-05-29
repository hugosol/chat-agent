# 04: 前端 app.js 条件渲染 fluencyScore

**Status:** `ready-for-agent`

## 目标

修改 `showReport()` 函数，当 `fluencyScore < 0`（哨兵值，表示报告生成失败）时隐藏 "Fluency Score" 行。

## 变更

### 修改 `app.js` → `showReport()`

当前代码：

```javascript
function showReport(report) {
    els.reportContent.innerHTML =
        '<div class="report-section"><strong>Overall Assessment:</strong><p>' + escapeHtml(report.summary) + '</p></div>' +
        '<div class="report-section"><strong>Topic Summary:</strong><p>' + escapeHtml(report.topicSummary || '') + '</p></div>' +
        '<div class="report-section"><strong>Fluency Score:</strong> ' + report.fluencyScore + '/10</div>' +
        '<div class="report-section"><strong>Error Summary:</strong><p>' + escapeHtml(report.errorSummary || '') + '</p></div>' +
        '<div class="report-section"><strong>Key Takeaway:</strong><p>' + escapeHtml(report.keyTakeaway || '') + '</p></div>';
    els.reportModal.classList.remove('hidden');
}
```

改为：

```javascript
function showReport(report) {
    var html = '<div class="report-section"><strong>Overall Assessment:</strong><p>' + escapeHtml(report.summary) + '</p></div>' +
        '<div class="report-section"><strong>Topic Summary:</strong><p>' + escapeHtml(report.topicSummary || '') + '</p></div>';

    if (report.fluencyScore >= 0) {
        html += '<div class="report-section"><strong>Fluency Score:</strong> ' + report.fluencyScore + '/10</div>';
    }

    html += '<div class="report-section"><strong>Error Summary:</strong><p>' + escapeHtml(report.errorSummary || '') + '</p></div>' +
        '<div class="report-section"><strong>Key Takeaway:</strong><p>' + escapeHtml(report.keyTakeaway || '') + '</p></div>';

    els.reportContent.innerHTML = html;
    els.reportModal.classList.remove('hidden');
}
```

## 前端行为

| `fluencyScore` 值 | 渲染效果 |
|-------------------|---------|
| 0-10 | 正常显示 "Fluency Score: N/10" |
| -1 | 该行完全隐藏，Learner 不会看到误导的 "-1/10" |

降级报告时的展示效果：
```
Overall Assessment:  Sorry, the session report generation failed...
Topic Summary:       N/A
Error Summary:       N/A
Key Takeaway:        N/A
```

## 阻塞于

无（前端改动与后端独立）

## Comments

- 不涉及 CSS 样式修改，仅 JS 条件分支
- `ServerMessage.ReportData.fluencyScore` 通过 JSON 序列化传递，`int` 类型，前端无需特殊处理
