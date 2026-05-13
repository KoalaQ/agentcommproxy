---
name: design-decisions
description: AgentCommProxy 重要设计决策记录
metadata:
  type: project
---

## sessionMode 默认规则

**决策**：未指定 sessionMode 时默认使用 **MAIN**

**Why**：由发起方明确决定是否使用独立会话，避免隐式行为

**How to apply**：
- 未指定 → MAIN（主会话）
- 指定 `independent` + 有 taskId → 创建/复用独立会话
- 同一 taskId，每次请求由发起方明确指定模式

## clearSession 参数

**决策**：仅在 `sessionMode=independent` 时有效

**Why**：MAIN 模式是共享会话，清空会影响其他任务

**How to apply**：
- MAIN 模式设置 clearSession → 忽略，输出警告日志
- INDEPENDENT 模式 → 正常生效，清空 .jsonl 文件

## taskId 和 sessionMode 关系

**决策**：不自动关联，每次请求由发起方明确指定

**Why**：保持清晰的设计，每次请求都明确决定用哪个会话模式

**示例**：
```
第一次: taskId=task-001, sessionMode=independent → 创建独立会话
第二次: taskId=task-001, sessionMode 未指定 → 使用 MAIN（不复用）
```