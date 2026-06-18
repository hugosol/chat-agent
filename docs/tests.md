# Tests — 测试清单与规范

Chat Agent 测试体系分为三层：后端单元测试（Mockito + Spring Test）、前端单元测试（Vitest + React Testing Library）、E2E 集成测试（Playwright + WireMock）。

---

## 运行命令

```bash
# 后端单元测试 + 前端 Vitest 测试（不含 E2E）
mvn test

# E2E 回归测试（首次运行需下载 Chromium ~150MB）
mvn verify

# 仅前端测试
cd src/main/frontend && npm test
```

- `mvn test` 由 surefire 执行（排除 `*IT.java`），同时通过 `frontend-test` 执行阶段运行 Vitest
- `mvn verify` 由 failsafe 执行（包含 `*IT.java`），覆盖完整浏览器-服务端流程

---

## 后端测试清单

### Agent（6 个测试类）

| 文件 | 说明 |
|------|------|
| `agent/ConversationAgentTest.java` | 对话提示构建、MemoryContent 注入、流式回调 · 含日语模式骨架加载测试 |
| `agent/ReportAgentTest.java` | 报告生成、ReportResult 解析 · 含日语报告模板测试 |
| `agent/LearningAgentTest.java` | Learning Profile 合并逻辑 |
| `agent/MemoryCueAgentTest.java` | 话题切换检测、分段摘要生成 |
| `agent/common/TaskRunnerTest.java` | 同步引擎、LLM 调用日志、ErrorStrategy |

### Service（16 个测试类）

| 文件 | 说明 |
|------|------|
| `service/SessionServiceTest.java` | 会话生命周期、状态管理、userId 隔离 |
| `service/TurnProcessorTest.java` | 并行回合处理、流式推送、null guard · 含日语模式跳过纠错测试 |
| `service/SessionCompleteTest.java` | 会话结束流水线（Report + MemoryCue + Profile）· 含日语模式跳过记忆生成测试 |
| `service/SessionDbStoreTest.java` | H2 持久化、EntityMapper 转换 |
| `service/TokenTrackerTest.java` | LLM token 计数、80% 警告阈值 |
| `service/EmbeddingServiceTest.java` | ONNX 向量化、语义搜索、EmbeddingStore 持久化 |
| `service/LearningProfileServiceTest.java` | Profile 合并、版本递增 |
| `service/MemoryCueServiceTest.java` | MemoryCue 异步生成、状态跟踪 |
| `service/LlmCallLogServiceTest.java` | LLM 调用日志写入、启动清理 |
| `service/EntityMapperTest.java` | DTO ↔ Entity 映射 |
| `service/FlashcardServiceTest.java` | 闪卡创建、FSRS 初始化、Tag upsert |
| `service/ReviewServiceTest.java` | 复习调度、评分、统计、遗忘 |
| `service/UserPreferencesServiceTest.java` | 用户偏好 CRUD |
| `service/card/CardBatchServiceTest.java` | 批量导入/导出编排 |
| `service/card/CardCsvParserTest.java` | CSV 解析、FSRS 状态序列化 |
| `service/MovieServiceTest.java` | 电影查询（Specification 分页/搜索/排序）、批量导入、字幕下载触发 |

### Flashcard（4 个测试类）

| 文件 | 说明 |
|------|------|
| `flashcard/FsrsSchedulerTest.java` | FSRS-6 repeat()、init、preview、forgettingCurve |
| `flashcard/FsrsSchedulerConfigTest.java` | 配置合并、defaults()、两步/重学步解析 |
| `flashcard/FsrsOptimizerTest.java` | Adam 优化、数值梯度、BCELoss、py-fsrs 交叉验证 |
| `flashcard/AleaPrngTest.java` | 确定性 PRNG、fuzz 分布 |

### Controller（3 个测试类）

| 文件 | 说明 |
|------|------|
| `controller/FlashcardControllerTest.java` | REST API：卡片 CRUD、Tag 列表、导入导出 |
| `controller/ReviewControllerTest.java` | REST API：复习开始、评分、统计、牌组列表 |
| `controller/MovieControllerTest.java` | REST API：电影列表（分页/搜索/排序）、TMDB 搜索、批量导入 |

### Repository & Model（13 个测试类）

| 文件 | 说明 |
|------|------|
| `repository/CardRepositoryIsolationTest.java` | 卡片数据隔离（userId 过滤） |
| `repository/LlmCallLogRepositoryTest.java` | LLM 日志查询、清理 |
| `repository/MemoryCueRepositoryTest.java` | MemoryCue 按 mode 查询 |
| `repository/UserLearningProfileRepositoryTest.java` | Profile 按类型查询 |
| `model/ErrorTypeTest.java` | ErrorType 枚举 JSON 反序列化 |
| `model/TimeLabelTest.java` | 时间标签格式化 |
| `model/*AuditingTest.java` | JPA 审计（createdAt/updatedAt），7 个实体各一个 |

### Config, DTO, Graph, Protocol, WebSocket（共 8 个测试类）

| 文件 | 说明 |
|------|------|
| `config/SecurityConfigTest.java` | 安全配置、permit-all-paths |
| `config/DataInitializerTest.java` | 初始用户/FSRS 参数创建 |
| `config/AppPropertiesMaxOutputTokensTest.java` | 配置属性验证 |
| `config/PromptLoaderTest.java` | Prompt 模板加载 |
| `dto/CorrectionDataTest.java` | 纠错数据序列化 |
| `dto/MemoryCueQueueTest.java` | LRU 队列操作 |
| `dto/MessageDataTest.java` | 消息数据角色验证 |
| `graph/ChatStateTest.java` | ChatState 通道操作 |
| `protocol/ProtocolDispatcherTest.java` | 客户端消息分发 |
| `websocket/ChatMessageHandlerTest.java` | 协议消息处理、会话绑定 |

### E2E 集成测试（12 个测试类）

| 文件 | 覆盖场景 |
|------|---------|
| `e2e/ChatAgentSessionIT.java` | 完整会话：开始 → 3 轮对话 → 纠错侧栏 → 结束报告 → H2 持久化 |
| `e2e/ChatAgentResumeIT.java` | 页面刷新 → localStorage sessionId 存活 → 消息/纠错恢复 |
| `e2e/ChatAgentMemoryIT.java` | 两次会话前后 → Learning Profile v1→v2 合并 → RAG MemoryCue 检索 |
| `e2e/DailyTalkIT.java` | DAILY_TALK 模式 → 3 轮闲聊 → 教学式纠错 |
| `e2e/ChatAgentMemoryCueIT.java` | 会话结束 → MemoryCue 两步 LLM → memory_cues COMPLETED 记录 |
| `e2e/ManagePageIT.java` | 管理页完整流程：Tag CRUD → Card CRUD → 搜索 → 排序 → 牌组过滤 → 分页 → 详情弹窗 |
| `e2e/FlashcardIT.java` | 闪卡录入：两阶段面板 → 标签创建 → 保存 → H2 数据验证 |
| `e2e/FlashcardBatchIT.java` | 批量导入导出：导出 CSV → 删卡 → 导入 CSV → FSRS 状态还原 |
| `e2e/ReviewIT.java` | 复习：牌组/模式选择 → 翻牌评分 → 统计栏 → 完成页 → REVIEW_ONLY/NEW_ONLY/CRAM |
| `e2e/SettingsPageIT.java` | 设置页：偏好保存验证 |
| `e2e/AuthIT.java` | 登录/登出/会话验证 |
| `e2e/MoviesPageIT.java` | 电影管理完整流程：列表分页/搜索/排序 → TMDB 搜索添加 → CSV 批量导入 → 删除确认 → 字幕下载重试 |

> **计划**：`e2e/JapaneseBusinessIT.java`（JAPANESE_BUSINESS 模式完整会话）推迟到后续迭代，届时需验证：无纠错气泡、无 MemoryCue 生成、日语报告内容。

> E2E 测试基类: `e2e/helper/E2ETestBase.java`、`e2e/helper/WireMockStubs.java`

---

## 前端测试清单

### Chat 页面（9 个测试文件）

| 文件 | 说明 |
|------|------|
| `chat/chatReducer.test.ts` | 状态机：消息追加、delta 合并、纠错挂载 · 含 mode 字段持久化测试 |
| `chat/CorrectionSidebar.test.tsx` | 侧栏展开/折叠、纠错项显示 |
| `chat/DebugPanel.test.tsx` | 调试面板日志显示 |
| `chat/FlashcardPanel.test.tsx` | 闪卡面板两阶段交互 |
| `chat/Footer.test.tsx` | 底部操作栏按钮状态 |
| `chat/MessageList.test.tsx` | 消息列表渲染、气泡样式 |
| `chat/ReportModal.test.tsx` | 报告弹窗内容显示 |
| `chat/StatusBar.test.tsx` | 状态栏 token 用量/会话状态 |

### Manage 页面（8 个测试文件）

| 文件 | 说明 |
|------|------|
| `manage/CardBlock.test.tsx` | 卡片展示块 |
| `manage/CardList.test.tsx` | 卡片列表交互 |
| `manage/CardsTab.test.tsx` | 卡片 Tab 完整流程 |
| `manage/CardToolbar.test.tsx` | 工具栏搜索/排序/导入导出 |
| `manage/ManageApp.test.tsx` | 管理页面整体集成 |
| `manage/TabBar.test.tsx` | Tab 切换 |
| `manage/TagsTab.test.tsx` | 标签 Tab CRUD |
| `manage/TagTable.test.tsx` | 标签表格交互 |

### Movies 页面（6 个测试文件）

| 文件 | 说明 |
|------|------|
| `movies/MoviesPage.test.tsx` | 电影列表：搜索/分页/排序/空状态/loading |
| `movies/MovieBlock.test.tsx` | 行渲染：字幕状态图标（PENDING/DOWNLOADING/DONE/FAILED）、按钮显隐 |
| `movies/MovieToolbar.test.tsx` | 工具栏：搜索输入、排序下拉、添加/导入按钮触发 |
| `movies/MovieImportModal.test.tsx` | CSV 导入：文件选择、CSV 解析（跳表头）、成功/失败结果展示 |
| `movies/MovieSearchModal.test.tsx` | TMDB 搜索：输入防抖、候选列表渲染、添加回调 |
| `movies/MovieDeleteModal.test.tsx` | 删除确认弹窗：文案含字幕行数 |

### Review 页面（6 个测试文件）

| 文件 | 说明 |
|------|------|
| `review/CardDisplay.test.tsx` | 卡片正反面翻转 |
| `review/CompletePage.test.tsx` | 复习完成统计页 |
| `review/DeckPicker.test.tsx` | 牌组选择、模式切换 |
| `review/RatingButtons.test.tsx` | 评分按钮 + 间隔预览 |
| `review/ReviewApp.test.tsx` | 复习页面整体流程 |
| `review/StatsBar.test.tsx` | 进度统计栏 |

### Shared 通用组件（7 个测试文件）

| 文件 | 说明 |
|------|------|
| `shared/InlineChipInput.test.tsx` | Chip 标签输入组件 |
| `shared/Modal.test.tsx` | 模态框通用组件 |
| `shared/Pagination.test.tsx` | 分页组件 |
| `shared/tts.test.ts` | TTS 播放逻辑 · 含日语模式语音选择测试 |
| `shared/useTagAutocomplete.test.ts` | Tag 自动补全 Hook |
| `shared/utils.test.ts` | 工具函数（escapeHtml、formatDate 等） |

### 其余页面（4 个测试文件）

| 文件 | 说明 |
|------|------|
| `header/Header.test.tsx` | 顶部导航（Chat→Review→Manage→Tune→Movies→设置→Profile）、TokenBar、面板切换 |
| `profile/ProfileApp.test.tsx` | 个人信息页 |
| `profile/PasswordChangeForm.test.tsx` | 密码修改表单 |
| `profile/UserManagement.test.tsx` | 用户管理（管理员） |
| `settings/SettingsPage.test.tsx` | 学习设置页（9 个配置项） |

---

## 测试规范

### E2E 测试（Playwright + WireMock）

- 测试类命名: `*IT.java`，位于 `src/test/java/com/hugosol/chatagent/e2e/`
- Playwright 使用 **headless Chromium**，mobile Safari viewport (390×844)，`setIsMobile(true)`
- WireMock 固定端口 `19090`，mock DeepSeek API HTTP 响应
- **DOM 等待**：不使用 WebSocket frame interception（不稳定），统一用 `page.waitForFunction()` 监听 DOM 状态变化
- Mock 响应文件位于 `src/test/resources/wiremock/`（SSE 流 + JSON）
- E2E 配置: `@ActiveProfiles("e2e")` → `application-e2e.yml`（内存 H2，permit-all-paths: [/**]）
- 截图自动保存到 `target/e2e-screenshots/`

### 前端单元测试（Vitest）

- 测试目录 `src/main/frontend/src/__tests__/`，镜像源码目录结构
- 使用 `data-testid` 属性选择 DOM 元素（CSS Modules 类名被 hash）
- React Context Provider wrapper 模式：`render(<ChatContext.Provider value={mockContext}>{children}</ChatContext.Provider>)`
- Mock 模式: `vi.mock()` 替代 fetch/WebSocket，`@testing-library/react` 的 `fireEvent` / `waitFor`
- 需要 `process.env.NODE_ENV === "test"` 时通过 `vite.config.ts` 注入

### 后端单元测试（JUnit 5 + Mockito）

- `@ExtendWith(MockitoExtension.class)` 用于纯 Mockito 测试
- `@WebMvcTest` 用于 Controller 层
- `@DataJpaTest` 用于 Repository 层
- `@SpringBootTest` 用于集成级测试（E2E base）
- AssertJ (`assertThat`) 为项目标准断言库
