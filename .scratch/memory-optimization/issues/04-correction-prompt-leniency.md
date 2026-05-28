# 04: Correction Prompt Leniency Rule

**Status:** `ready-for-agent`

## Parent

`.scratch/memory-optimization/PRD.md`

## What to build

在 `correction.txt` 开头新增一条通用规则：如果某个"错误"可能是语音转文字（speech-to-text misrecognition）导致的——包括同音异义词混淆（their/there、to/too）或发音相近词误识别（think/sink）——则不标注。仅当证据确凿（明显的语法结构错误、不符合英语表达习惯的中式直译等）时才标注。

该规则适用于所有 5 个错误类别（GRAMMAR、WORD_CHOICE、CHINGLISH、PRONUNCIATION、FLUENCY），不改变现有类别定义和输出 JSON 格式。

**文件变更：**
- `src/main/resources/prompts/correction.txt`：在文件开头插入新规则段落（在现有类别定义之前）
- `src/test/resources/prompts/correction.txt`：同步更新测试版 prompt（当前只有一行 `Correction prompt: {userInput}`，需扩展为包含新规则）

**WireMock stubs：**
- `src/test/resources/wiremock/` 中的 correction 响应 mock 文件可能使用 `matchingJsonPath` 对 prompt body 做关键词匹配。如果 stubs 依赖 prompt 的特定文本片段做场景路由，需确认新规则不影响匹配逻辑。当前 stubs 使用 `containing("Correction prompt:")` 匹配，若测试版 prompt 保留 `"Correction prompt:"` 前缀则无需调整。

## Acceptance criteria

- [ ] `correction.txt`（main）开头新增 speech-to-text misrecognition 宽容规则段落
- [ ] 规则覆盖同音异义词（their/there、to/too 等）和发音相近词（think/sink 等）两个场景
- [ ] 规则明确"仅当证据确凿时才标注"的例外条件
- [ ] 现有 5 个错误类别定义不变，JSON 输出格式不变
- [ ] `correction.txt`（test）同步更新
- [ ] `mvn verify` 通过（E2E 测试中 WireMock stubs 匹配新 prompt 文本后断言正常）

## Blocked by

None — can start immediately

## User stories covered

#10, #11
