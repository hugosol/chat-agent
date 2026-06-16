# PRD: 闪卡增强（Card Enhance）

**Status:** `ready-for-agent`

## Problem Statement

Learner 在复习闪卡时遇到一个痛点：有些单词反复按 Again 但始终记不住，原因是平时根本用不到这个词，大脑缺少"第一道刻痕"——没有在真实语境中听过、见过、感受过这个单词。

现有的卡片只包含 front（单词）和 back（释义），信息量不足以为大脑建立牢固的记忆编码。Learner 需要一种方式，将单词与真实的生活体验（看过的电影）和语言结构（词源）联系起来，为记忆提供多个锚点。

## Solution

在复习翻牌后，Learner 看到卡背时，可以点击 **"Card Enhance"** 按钮。系统并行执行两个外部查询，将结果写入卡片增强数据，当场刷新卡背展示：

- **字幕搜索**：从 Learner 已导入的已看电影字幕中，全文搜索包含该单词的台词，找到后取上下文发给 LLM 生成中文场景摘要。
- **词源查询**：从 Wiktionary 获取单词的词根拆分。

增强后的卡背变为三区域布局（释义 + 电影台词 + 词源），可滚动查看。下次复习同一张卡时，无需再点按钮，增强内容直接展示。

已看电影通过 CSV 导入（兼容豆瓣/Letterboxd/Trakt/IMDb 多种导出格式，自动识别列名）或 TMDB 手动搜索添加。导入时系统自动下载所有电影的字幕文件到本地数据库，后续"Card Enhance"搜索在本地毫秒级完成，零外部 API 调用。

## User Stories

1. 作为一名 Learner，我可以在复习翻牌后点击"Card Enhance"按钮，系统自动从我看过的电影中搜索该单词，同时查询词源，将结果展示在卡背上。
2. 作为一名 Learner，点击"Card Enhance"后看到 loading 状态，等待 1-2 秒后卡背自动刷新显示增强内容，不需要手动刷新。
3. 作为一名 Learner，增强后的卡背分为释义、电影台词（含片名、时间戳、台词原文）、场景摘要（中文一两句）、词源拆分三个区域，我可以上下滚动查看全部内容。
4. 作为一名 Learner，下次复习同一张卡时，增强内容直接显示在卡背上，不需要再次点击按钮。
5. 作为一名 Learner，我可以导入自己的已看电影列表（CSV 上传），系统自动识别豆瓣/Letterboxd/Trakt/IMDb 等多种列名格式。
6. 作为一名 Learner，我可以通过 TMDB 搜索电影名，手动添加单部电影到已看列表。
7. 作为一名 Learner，导入已看电影后，系统自动下载所有电影的字幕文件，我可以在电影列表中看到每部电影的字幕下载状态。
8. 作为一名 Learner，如果某部电影的字幕下载失败，我可以在电影列表中手动重新下载。
9. 作为一名 Learner，如果词源查询失败（如 Wiktionary 中没有该词），我仍然可以看到字幕搜索结果，不会因为一个失败而全部丢失。
10. 作为一名 Learner，如果字幕搜索在已看电影中找不到，系统告诉我未在已看电影中找到匹配。
11. 作为一名 Learner，如果我在"Card Enhance"加载中途翻走了，增强数据已经保存到数据库，下次回来直接可见。
12. 作为一名 Learner，增强数据持久保存，不受服务重启影响。

## Implementation Decisions

### 1. 数据模型：三张新表 + 一张已有表扩展

- **CardEnhancement**：一张卡有多行，一行 = 一次外部 API 调用。字段包括 `cardId`（String 软关联，无 FK）、`type`（枚举：`SUBTITLE` / `ETYMOLOGY`）、`status`（`PENDING` / `SUCCESS` / `FAILED`）、`data`（TEXT）、`error`（TEXT）、`requestUrl`（TEXT）。`subtitle` 类型的 `data` 存 JSON（`{ movieTitle, imdbId, quote, timestamp, sceneSummary }`），`etymology` 类型的 `data` 存纯文本。
- **WatchedMovie**：Learner 的已看电影列表。字段包括 `userId`、`imdbId`、`title`、`year`、`subtitleStatus`（`PENDING` / `DOWNLOADING` / `DONE` / `FAILED`）、`subtitleLineCount`、`subtitleError`。
- **SubtitleLine**：解析后的字幕行。字段包括 `imdbId`、`movieTitle`、`startTime`、`endTime`、`text`（原始台词）、`wordsLower`（小写去标点，用于 LIKE 精确词匹配）、`lineIndex`（SRT 行序号，用于取前后文）。

各表之间通过 `cardId` / `imdbId` 字符串软关联，无 FK 约束。

### 2. 字幕搜索策略：本地 H2 全文搜索

导入已看电影时，系统调用 Wyzie Subs API（按 IMDB ID 获取字幕下载链接）下载所有电影的字幕 SRT 文件，解析为 `SubtitleLine` 行存入 H2。

"Card Enhance" 时搜索：`SELECT * FROM SubtitleLine WHERE imdbId IN (已看电影列表) AND wordsLower LIKE '% word %' ORDER BY imdbId, lineIndex LIMIT 1`。命中的行取前后共 5 句上下文发给 LLM 生成中文场景摘要。

性能：100 部电影 × ~1500 行 = 15 万行，H2 LIKE 查询毫秒级。

### 3. 字幕下载通道：Wyzie Subs

Wyzie Subs（sub.wyzie.io）提供免费 API（1000 次/天），按 IMDB ID 返回字幕文件下载链接。作为 Phase 1 主下载通道。Phase 2 可加 OpenSubtitles 作为 fallback。

### 4. 词源查询：Wiktionary REST API

`GET https://en.wiktionary.org/api/rest_v1/page/definition/{word}` → 返回 JSON 中的 `etymology` 字段（纯文本数组），无需认证。

### 5. 场景摘要：DeepSeek LLM

将字幕命中的台词 + 前后共 5 句上下文 + 电影名发给 DeepSeek，生成 1-2 句中文字场景摘要，让 Learner 能想象出画面感。不计入已有的 LLM Call Log 体系。

### 6. 已看电影导入：CSV 自动列名匹配 + TMDB 搜索

CSV 导入支持自动识别四种平台的列名格式（标准化为小写去空格下划线后匹配）：
- IMDB ID: `imdbid` / `imdb_id` / `imdb id` / `const`
- 片名: `title` / `name` / `film` / `movie`
- 年份: `year`

TMDB API 用于手动搜索电影名 → 选择添加（支持中英文模糊搜索）。

导入分批次 POST（每批约 15 部），前端顺序发送多批，每批同步等待。每批约 30 秒，保证浏览器不超时。

导入后 WatchedMovie 状态为 `DONE`（字幕下载成功）或 `FAILED`（带错误原因），支持单部重试（`POST /api/movies/{imdbId}/download`）。

### 7. API 端点设计

**卡片增强：**
- `POST /api/cards/{id}/enhance` → 触发增强，并行调用本地字幕搜索 + Wiktionary + LLM，写入 CardEnhancement，返回 card + enhancement 数据。已有完整增强（两个 type 均为 SUCCESS）时幂等返回。

**已看电影管理：**
- `GET /api/movies` → 列出已导入电影（含 subtitleStatus）
- `POST /api/movies/import/batch` → 接收一批电影列表，保存 WatchedMovie + 下载字幕
- `DELETE /api/movies/{imdbId}` → 删除电影及其 SubtitleLine 数据
- `POST /api/movies/search` → TMDB 搜索
- `POST /api/movies` → 手动添加单部电影（从搜索结果选择）
- `POST /api/movies/{imdbId}/download` → 重新下载字幕

**复习接口扩展：**
- `POST /api/review/next` 和 `GET /api/review/start` 返回的 card JSON 新增 `enhancement` 字段，包含 movieQuote、sceneSummary、etymology。

### 8. 前端交互

**卡背布局**（CardDisplay 改造）：
- 始终显示：释义区域
- 有增强时显示：电影台词区域（片名 + 时间戳 + 台词原文 + 场景摘要）+ 词源区域，分割线隔开
- 无增强时显示："Card Enhance" 按钮（小字体）
- 点击按钮 → loading 遮罩覆盖卡背 → 1-2 秒后 → 遮罩消失，刷新显示增强内容
- 卡背整体可上下滚动（`overflow-y: auto`）
- 如果翻走中断，增强数据已入库，下次展示

**电影列表页**：Phase 1.5 再做前端页面，Phase 1 先备好 API。

### 9. 模块分解

- `CardEnhanceService`：增强编排（字幕搜索 → Wiktionary → LLM → 写入 CardEnhancement）
- `SubtitleService`：Wyzie 下载 SRT → 解析 → SubtitleLine 写入；重试
- `MovieService`：CSV 解析（列名自动匹配）、TMDB 搜索、WatchedMovie CRUD
- `MovieController`：5 个 REST 端点
- `CardEnhanceController`：`POST /api/cards/{id}/enhance`
- `CsvColumnDetector`：列名标准化匹配工具类
- 前端：`CardDisplay.tsx` 改造（增强区域 + 按钮 + loading）

### 10. 场景摘要 Prompt 示例

```
以下是一段来自电影《Inception》的台词及其前后文。请用一两句中文字描述这句话发生的情境：

台词上下文：
- L3: "You keep telling people what to do."
- L4: "I'm the only one who sees the danger."
- L5: "You really accentuated the problem, didn't you?"  ← 目标词
- L6: "No, I'm trying to solve it."
- L7: "By tearing everything apart?"

场景摘要：
```

## Testing Decisions

### 测试原则

- 行为导向，不测试实现细节。断言外部可观察行为（API 响应字段、前端 DOM 渲染、数据库行状态）。
- 使用已有测试基类和 mock 模式，不引入新的测试框架。
- E2E 测试跑完整链路，Mock 外部 API（Wyzie、Wiktionary、TMDB、DeepSeek）。

### 新增测试模块

**后端单元测试：**
- `CardEnhanceServiceTest`：增强编排全场景（成功/部分成功/幂等/卡片不存在）
- `SubtitleServiceTest`：SRT 下载解析、失败重试、重试清旧数据
- `MovieServiceTest`：CSV 四种格式列名匹配、TMDB 搜索解析、CRUD
- `MovieControllerTest`：5 个端点（`@WebMvcTest`）
- `CardEnhanceControllerTest`：enhance 端点各种状态
- `CardEnhancementTest`、`WatchedMovieTest`、`SubtitleLineTest`：实体/枚举行为
- `CsvColumnDetectorTest`：列名匹配、缺失列异常、空 CSV

**后端已有测试修改：**
- `ReviewControllerTest`：响应 JSON 新增 `enhancement` 字段断言
- `ReviewServiceTest`：card DTO 包含 enhancement 数据

**前端测试新增/修改：**
- `CardDisplay.test.tsx`：改动最大——"Card Enhance" 按钮渲染、loading 遮罩、增强区域（台词+场景摘要+词源）、可滚动、无增强时不显示区域、按钮点击 mock fetch 调用
- `ReviewApp.test.tsx`：enhancement 数据在 card 响应中传递

**E2E 测试新增：**
- `CardEnhanceIT.java`：端到端完整流程——导入电影 → 开始复习 → 翻牌 → 点击"Card Enhance" → 等待 loading 结束 → 验证卡背显示台词+词源 → 评分 → 下次复习直接显示增强（无按钮）→ H2 验证 CardEnhancement 行写入
- `MovieImportIT.java`：CSV 上传 → 验证 WatchedMovie + SubtitleLine 写入 → GET /api/movies 验证列表

### 已有测试不影响的模块

- 聊天/Agent/Correction/Report：不相关
- FSRS 调度器/优化器：不相关
- 批量导入导出：不相关
- 设置页/用户管理：不相关

## Out of Scope

- 豆瓣 OAuth 授权导入（Phase 2）
- 电影列表前端管理页面（Phase 1.5）
- OpenSubtitles API 作为字幕下载 fallback（Phase 2）
- 已看电影字幕全量本地缓存（已完成预下载，后续监控是否需要倒排索引优化）
- Phase 2（Cloze Deck 提取练习）、Phase 3（对话暴露）
- 字幕搜索结果多匹配处理（同一单词在多部电影出现时的展示策略）
- AI 记忆术（关键词联想法/叙事链）——已在 brainStrom 中评估放弃
- LLM 生成的例句（改为词典优先，LLM 仅做场景摘要）

## Further Notes

- 本 PRD 基于 brainStrom Phase 1 设计决策文档，方向已定，仅处理实现层面决策。
- Wyzie Subs 是个人开源项目，非商业 API。Phase 2 需评估稳定性后决定是否加 OpenSubtitles fallback。
- 字幕预下载的磁盘占用：一部电影 SRT ~100KB，100 部 = 10MB，可忽略。
