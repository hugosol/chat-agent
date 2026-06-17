# 07: Movie Match Dispersion

**Status:** `ready-for-agent`

## Problem

`searchSubtitle()` 拿到 LIKE 结果后 `matches.get(0)` 取第一条，等价于按 `imdbId, lineIndex` 排序后的第一条——同一个词的增强结果几乎总是来自同一部电影（字幕行数最多的那部）。

## Approach

在 `CardEnhanceService.searchSubtitle()` 中：

1. 按 `imdbId` 分组（`Collectors.groupingBy`）
2. 每组取 `lineIndex` 最小的那条
3. 如果只有 1 组（词仅在一部电影出现），直接用
4. 如果多组，`Collections.shuffle(groups)` 随机选一组

## Files

- `src/main/java/com/hugosol/chatagent/service/CardEnhanceService.java`
- `src/test/java/com/hugosol/chatagent/service/CardEnhanceServiceTest.java`
- `src/test/java/com/hugosol/chatagent/e2e/CardEnhanceIT.java`（放宽断言）

## Acceptance

- 同一单词在多部电影中匹配时，增强结果在不同电影间均匀分布
- 单词仅在一部电影中匹配时，直接使用该结果
- 无匹配时返回 null（行为不变）
- E2E 测试断言放宽为 "匹配到某部电影" 而非特定电影

## Comments

