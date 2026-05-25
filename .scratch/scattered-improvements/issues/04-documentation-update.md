# Update documentation for vocabularySuggestions removal

**Status:** `ready-for-agent`

## Parent

`.scratch/scattered-improvements/PRD.md` — 零星优化

## What to build

After removing `vocabularySuggestions` from the codebase (Issue 02), update all markdown documentation to reflect the change. Remove all references to the deleted field.

## Files

### CONTEXT.md
| Change |
|--------|
| Line 34: "Report" definition → delete "vocabulary suggestions" from the description |

After: `Post-session analysis: summary, fluency score, error summary, key takeaway`

### docs/architecture.md
| Location | Change |
|----------|--------|
| Section 五, ReportAgent prompt 描述 | 删除 "3. **Vocabulary Suggestions**: 3-5 better words/phrases..." 行；重编号 4→3, 5→4 |
| Section 六, ER 图 SessionReport | 删除 `vocabulary` 列（Line 307） |
| Section 九, 前端 report modal 布局 | 删除 "Vocabulary Suggestions:" 区块（Lines 476-478） |

### README.md
| Change |
|--------|
| Line 171: ReportAgent 描述 → 删除 "vocabulary suggestions" |

After: `Generates end-of-session summary: fluency score, error breakdown, key takeaway`

### docs/prd-persistent-memory.md
| Location | Change |
|----------|--------|
| Line 108: Memory 输入表 Learning Profile 行 | 删除 `+ SessionReport.vocabularySuggestions` |
| Line 116: MemoryAgent 接口描述 | `mergeProfile(oldProfile, errorSummary, vocabularySuggestions)` → `mergeProfile(oldProfile, errorSummary)` |
| Line 125: memory-profile.txt 描述 | "new error summary and vocabulary suggestions" → "new error summary" |

## Acceptance criteria

- `grep -r "vocabularySuggestions\|vocabulary suggestions\|vocabulary suggestion" docs/ CONTEXT.md README.md` returns zero results (excluding `.scratch/` and `.md` files intentionally updated for this PRD itself)
- All markdown files render without broken formatting
