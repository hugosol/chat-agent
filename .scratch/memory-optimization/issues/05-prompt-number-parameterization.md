# 05: Prompt Number Parameterization

**Status:** `ready-for-agent`

## Parent

`.scratch/memory-optimization/PRD.md`

## What to build

将 `memory-profile.txt` 和 `memory-cue-entry.txt` 中硬编码的数字约束替换为 `application.yml` 可配置参数，使开发者无需修改 prompt 文件即可调整这些阈值。

**配置项：**

在 `application.yml` 的 `app.memory` 下新增三个配置键：

| 配置键 | 默认值 | 说明 |
|--------|--------|------|
| `app.memory.profile-max-length` | `400` | Learning Profile 合并后文本最大字符数 |
| `app.memory.cue-topic-max-words` | `7` | MemoryCue topic 名称最大词汇数 |
| `app.memory.cue-summary-max-sentences` | `4` | MemoryCue summary 最大句子数 |

**代码变更：**
- `AppProperties.Memory` 新增三个字段（`profileMaxLength`、`cueTopicMaxWords`、`cueSummaryMaxSentences`）及 getter/setter，通过 `@ConfigurationProperties(prefix = "app")` 自动绑定
- `MemoryAgent` 构造注入 `AppProperties`，在 `mergeProfile()` 中将 `memory-profile.txt` 模板的 `"{profileMaxLength}"` 占位符替换为配置值
- `MemoryCueAgent` 构造注入 `AppProperties`，在生成 cue entry 时将 `memory-cue-entry.txt` 模板的 `"{cueTopicMaxWords}"` 和 `"{cueSummaryMaxSentences}"` 占位符替换为配置值

**Prompt 模板变更：**
- `memory-profile.txt`：`"under 400 characters"` → `"under {profileMaxLength} characters"`
- `memory-cue-entry.txt`：`"3-7 words"` → `"3-{cueTopicMaxWords} words"`，`"2-4 sentences"` → `"2-{cueSummaryMaxSentences} sentences"`
- 注意保留最小值的硬编码（3 词、2 句），仅将上限参数化

**测试变更：**
- 测试版 `memory-profile.txt` 和 `memory-cue-entry.txt` 同步更新占位符
- WireMock stubs 中依赖 prompt 文本匹配的 mock 同步更新关键词

## Acceptance criteria

- [ ] `application.yml` 中 `app.memory.profile-max-length`、`cue-topic-max-words`、`cue-summary-max-sentences` 配置项生效
- [ ] `AppProperties.Memory` 三个新字段可通过 `@ConfigurationProperties` 绑定
- [ ] `memory-profile.txt` 中 `"under {profileMaxLength} characters"` 占位符在运行时被替换为实际数值
- [ ] `memory-cue-entry.txt` 中 `"3-{cueTopicMaxWords} words"` 和 `"2-{cueSummaryMaxSentences} sentences"` 占位符在运行时被替换
- [ ] 默认值未经覆盖时行为与硬编码版本完全一致（400 字符、7 词、4 句）
- [ ] `mvn test` 全部通过
- [ ] `mvn verify` 通过（E2E 测试 WireMock stubs 匹配新 prompt 文本）

## Blocked by

- `03-cancel-topic-memory-merge` — 建议先完成，避免在已删除 `mergeTopic()` 的 `MemoryAgent` 上做参数化改造时产生合并冲突

## User stories covered

#12, #13
