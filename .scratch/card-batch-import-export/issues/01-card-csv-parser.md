# 01 — CardCsvParser 解析器 + 单测

**Status:** `ready-for-agent`

## Parent

[PRD: 闪卡批量导入/导出](../PRD.md)

## What to build

实现 CSV 到卡片数据的解析器 `CardCsvParser`——接收 CSV 文件的 `InputStream`，输出 `List<ParsedCardRow>`。只做解析不做校验，空字段等逻辑问题留给 Service 层统一处理。

**CSV 格式**：表头 `front,back,stability,difficulty,cardState,due,reps,lapses,lastRevision`，按列名匹配（非列序号）。列缺失时对应字段为 null，多余列忽略。字段内含逗号、换行符时由 Apache Commons CSV 自动处理（RFC 4180 标准）。

**cardState 文本映射**：CSV 中为文本 `New`/`Learning`/`Review`/`Relearning`，解析为对应整数（0/1/2/3）。非法文本记录为 null（由 Service 校验层报错）。

**BOM 兼容**：使用 `BOMInputStream` 自动跳过 UTF-8 BOM 头（`\uFEFF`），兼容 Windows 记事本等编辑器生成的 CSV。

**ParsedCardRow**：一个内部 record，包含 `int rowNumber`、`String front`、`String back`、`FsrsFields fsrs`（六个 Optional 字段：stability、difficulty、cardState、due、reps、lapses、lastReview）。

**单元测试**：`CardCsvParserTest` 穷举以下场景：
- 正常 CSV 全字段解析
- 字段内含逗号的引号转义
- 字段内含换行符的多行字段
- 字段内含双引号（转义为 `""`）
- cardState 四种合法文本映射
- cardState 非法文本 → null
- FSRS 部分列缺失 → 对应字段 null
- BOM 头自动跳过
- 多余列忽略
- 表头名大小写不敏感匹配（可选——看 Apache Commons CSV 默认行为）

## Acceptance criteria

- [ ] `CardCsvParser.parse(InputStream)` 正确解析标准 CSV，返回 `List<ParsedCardRow>`
- [ ] 字段内含逗号的字段被正确识别（不错误分割）
- [ ] 字段内含换行符的行被正确解析（不错误断行）
- [ ] 字段内含双引号正确转义
- [ ] `New`/`Learning`/`Review`/`Relearning` 正确映射为 0/1/2/3
- [ ] 非法 cardState 文本解析为 null
- [ ] BOM 头 CSV 文件正确跳过首字节、表头名不受污染
- [ ] 缺失列对应的字段为 null（不抛异常）
- [ ] 多余列被忽略
- [ ] `CardCsvParserTest` 全部通过（`mvn test -Dtest=CardCsvParserTest`）
- [ ] `pom.xml` 添加 `org.apache.commons:commons-csv:1.11.0` 依赖

## Blocked by

None — can start immediately.
