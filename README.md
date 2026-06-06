# Chat Agent

AI 英语口语练习工具，兼具聊天练习和闪卡复习两大功能。通过 AI Agent 角色扮演进行实时英文对话，自动纠正语法/措辞错误，生成学习报告，并内置 FSRS-6 间隔重复算法管理词汇复习。

## Quick Start

### 环境依赖

- **Java 17+** / **Maven 3.9+**
- **DeepSeek API Key**（[获取](https://platform.deepseek.com/api_keys)）

### 本地启动

```bash
git clone <repo-url>
cd chat-agent

# 方式 A：local profile（推荐，key 写入 gitignored 文件）
# 创建 src/main/resources/application-local.yml，写入：
#   langchain4j.openai.chat-model.api-key: sk-your-key
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 方式 B：环境变量
# Windows:
set DEEPSEEK_API_KEY=sk-your-key
# macOS / Linux:
export DEEPSEEK_API_KEY=sk-your-key
mvn spring-boot:run
```

浏览器打开 http://localhost:8080，默认账号 `admin` / `admin123`。

### Docker 部署

**使用已发布镜像：**

```bash
git clone <repo-url>
cd chat-agent
# 编辑 docker/docker-compose.yml，替换 DEEPSEEK_API_KEY、INITIAL_USER_PASSWORD 及宿主机路径（data + logs）
docker compose -f docker/docker-compose.yml up -d
```

**自行构建镜像：**

```bash
mvn package -DskipTests
docker build -f docker/Dockerfile -t ghcr.io/你的用户名/chat-agent:latest .
docker push ghcr.io/你的用户名/chat-agent:latest
```

部署后配置 Nginx Proxy Manager 反向代理，开启 SSL（Let's Encrypt 自动签发）。

## 技术栈

| 技术 | 版本 | 用途 | Reference |
|------|------|------|-----------|
| Java | 17 | 主语言 | — |
| Spring Boot | 3.4.7 | Web 框架，整合 Spring Security / Data JPA | [architecture.md](docs/architecture.md) |
| LangChain4j | 1.0.0-beta2 | LLM 调用封装（OpenAI 兼容适配器连接 DeepSeek） | [architecture.md](docs/architecture.md#二完整决策日志) |
| langgraph4j | 1.8.16 | Agent 状态图编排 | [architecture.md](docs/architecture.md#四langgraph-状态机) |
| H2 | — | 嵌入式文件数据库 | [architecture.md](docs/architecture.md#六数据模型) |
| WebSocket | — | 客户端-服务端实时双向通信（JSON 协议） | [architecture.md](docs/architecture.md#七websocket-协议) |
| React + TypeScript | 18 | 前端 UI（Vite Library Mode，CSS Modules） | [frontend-notes.md](docs/frontend-notes.md) |
| ONNX Runtime | — | RAG 语义检索（all-MiniLM-L6-v2，384 维向量化） | [architecture.md](docs/architecture.md) |
| FSRS-6 | — | 间隔重复调度算法，决定卡片何时复习 | [fsrs.md](docs/fsrs.md) |

## 核心模块

| 模块 | 解决什么问题 | Reference |
|------|-------------|-----------|
| 聊天页面 | 实时 WebSocket 流式对话、异步语法纠错、多轮 RAG 记忆注入 | [architecture.md](docs/architecture.md) |
| MemoryCue | 跨会话语义记忆，RAG 检索 + 向量化存储，跨回合上下文注入 | [architecture.md](docs/architecture.md) / [CONTEXT.md](CONTEXT.md) |
| FSRS 调度器 | 间隔重复算法，决定每张闪卡的下次复习时间 | [fsrs.md](docs/fsrs.md) |
| FSRS 优化器 | 基于复习历史自动调参，Adam 梯度下降优化 W[21] | [fsrs.md](docs/fsrs.md) |
| 多 Tab 会话管理 | 页面切换自动恢复，防旧数据干扰，一 Session 一 Tab 绑定 | [frontend-notes.md](docs/frontend-notes.md) |

## 测试与参与开发

### 覆盖范围

- **后端单元测试** — Mockito + Spring Test，覆盖 Agent / Service / Config / Controller / Repository 等层
- **前端单元测试** — Vitest + React Testing Library，覆盖 Chat / Manage / Review / Settings 全部页面组件
- **E2E 集成测试** — Playwright (Java) + WireMock，模拟完整浏览器-服务端交互流程

### 回归命令

```bash
# 后端单元测试 + 前端 Vitest 测试
mvn test

# E2E 回归测试（首次运行需下载 Chromium ~150MB）
mvn verify

# 仅前端测试
cd src/main/frontend && npm test
```

> 测试清单与规范详见 [docs/tests.md](docs/tests.md)

## 文档结构

| 文档 | 职责 |
|------|------|
| [README.md](README.md) | 项目概览、快速上手、Roadmap |
| [CONTEXT.md](CONTEXT.md) | 领域术语表 |
| [docs/architecture.md](docs/architecture.md) | 架构蓝图与设计决策 |
| [docs/frontend-notes.md](docs/frontend-notes.md) | 前端实现规范与浏览器兼容 |
| [docs/fsrs.md](docs/fsrs.md) | FSRS 算法、调度器、优化器参考 |
| [docs/tests.md](docs/tests.md) | 测试清单与规范 |
| [AGENTS.md](AGENTS.md) | AI Agent 工作手册 |
| [docs/adr/](docs/adr/) | 架构决策记录（历史参考，以代码为准） |

## Roadmap

- [x] 基础对话与流式输出
- [x] 语法纠错与学习报告
- [x] 闪卡系统（FSRS-6 调度 + 复习面板 + CSV 导入导出）
- [x] MemoryCue 跨会话语义记忆（RAG 检索 + 向量化）
- [x] 多用户与数据隔离
- [x] DAILY_TALK 闲聊模式 + WORKPLACE_STANDUP 站会模式
- [x] FSRS 参数优化器（Adam 梯度下降自动调参）
- [x] 前端全量 React + TypeScript 迁移
- [ ] STT/TTS 语音交互
- [ ] 更多 AgentMode 场景
- [ ] 学习进度趋势图表
- [ ] Redis/Postgres checkpoint 持久化（跨重启恢复会话）
