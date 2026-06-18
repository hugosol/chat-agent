# 06: Subtitle Word-Boundary Matching Fix

**Status:** `ready-for-agent`

## Problem

`wordsLower` 使用 `LIKE '%word%'` 子串匹配，搜索 `test` 会匹配 `greatest`、`testing` 等。此前端注释称 "handles word at start/end of line"，但实际上无法区分单词边界。

## Approach

**数据层**：`SrtParser` 构建 `wordsLower` 时首尾各加一个空格，使每个词都被空格包围。

**查询层**：`SubtitleLineRepository` LIKE 模式从 `'%' + word + '%'` 改为 `'% ' + word + ' %'`。

**Java 标记层**：`generateSceneSummary()` 中的 `contains(targetWord)` 同步改为 `contains(" " + targetWord + " ")`。

**数据迁移**：启动时执行 `UPDATE subtitle_lines SET words_lower = ' ' || words_lower || ' '`。

## Files

- `src/main/java/com/hugosol/chatagent/service/SrtParser.java`
- `src/main/java/com/hugosol/chatagent/repository/SubtitleLineRepository.java`
- `src/main/java/com/hugosol/chatagent/service/CardEnhanceService.java`
- `src/test/java/com/hugosol/chatagent/service/SrtParserTest.java`
- `src/test/java/com/hugosol/chatagent/repository/SubtitleLineRepositoryTest.java`
- `src/test/java/com/hugosol/chatagent/service/CardEnhanceServiceTest.java`

## Acceptance

- 搜索 "test" 不匹配 "greatest" 所在行
- 搜索 "dream" 匹配行首 "Dream is..."、行中 "...a dream."、行尾 "...your dream"
- 单字行 "Dream." 正确匹配
- 现有 SRT 解析测试通过（3 个测试断言更新）
- 现有 Repository 测试通过（2 个测试数据更新）
- CardEnhanceService 测试通过（pattern mock 更新）

## Comments

