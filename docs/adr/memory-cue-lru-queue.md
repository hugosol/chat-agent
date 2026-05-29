# ADR: MemoryCueQueue — LRU 队列替代 Flat Top-K

**Status:** Accepted  
**Date:** 2026-05-29

## 背景

MemoryCue RAG 检索每轮独立执行 flat top-K search，结果直接注入 System Prompt。存在两个问题：

1. **无跨轮记忆迁移**：每轮独立 search，上一轮命中的相关记忆到下一轮可能被完全替换。用户在持续聊同一话题时，关联记忆无法"停留"在上下文中。

2. **字符串拼接脆弱**：`", as well as, "` 拼接多条 MemoryCue 文本，然后在 ConversationAgent 中按同分隔符 split 回来。若 summary 原文包含此分隔符字符串，会产生错误拆分。

## 决策

用 **MemoryCueQueue**（LRU 有序集合，capacity = topK + 1）替代 flat top-K 注入。

### 核心机制

- **创建**：SessionService.init() 随 Practice session 创建，存入 CoachState
- **首次加载**（队列为空）：search `topK + 1` 条结果
- **后续加载**（队列非空）：search `topK` 条结果，逐个处理：同 cueId 去重 refresh 到 head，新条目 push 到 head
- **驱逐**：容量满时驱逐 tail（最久未访问）
- **空搜索保护**：RAG 返回 0 条时队列保持不变
- **注入格式**：按 tail→head（旧→新）生成编号列表，每条含时间标签
- **Format**：ConversationAgent.formatMemoryCuesForPrompt() 直接从 `List<CueMatch>` 生成，无字符串拆分
- **销毁**：Session 结束随 CoachState 销毁
- **恢复**：checkpoint Java 序列化自动恢复

## 替代方案

| 方案 | 优点 | 缺点 |
|------|------|------|
| **Flat top-K**（当前） | 简单，无状态 | 无跨轮记忆迁移；字符串拼接脆弱 |
| **LRU 队列（MemoryCueQueue）** | 模拟人类记忆迁移；去重 + 刷新位置；编号列表无拼接脆弱性 | 引入有状态数据结构；需要序列化支持 |
| **全量注入** | 无信息丢失 | 上下文窗口爆炸；大量无关噪音 |
| **滑动窗口** | 简单 | 纯时间驱除，不区分相关性 |

## Trade-off

- **复杂度增加**：引入新的有状态 DTO（`MemoryCueQueue`），需实现 `Serializable` 以支持 checkpoint 序列化
- **连贯性提升**：连续相关话题的记忆自然保留，不被无关的新 search 结果驱逐
- **无新增配置项**：复用现有 `top-k` 配置，`topK + 1` 推导首次加载量和队列容量
- **纯后端行为**：前端无感知变更
