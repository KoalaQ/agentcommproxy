---
name: openclaw-sessions
description: OpenClaw sessions.json 结构和关键约定
metadata:
  type: reference
---

## OpenClaw sessions.json 关键信息

**文件路径**：
```
~/.openclaw/agents/{agentId}/sessions/sessions.json
```

**会话文件路径**：
```
~/.openclaw/agents/{agentId}/sessions/{sessionId}.jsonl
```

**Entry Key 格式**：
```
agent:{agentId}:{sessionType}
```
示例：
- `agent:agentclaw-lyd:main` - 主会话
- `agent:agentclaw-lyd:task-001` - 独立会话（使用 taskId）

**创建会话核心字段**：
| 字段 | 来源 |
|------|------|
| sessionId | 生成新 UUID |
| updatedAt | 当前时间戳 |
| sessionFile | `{sessionsDir}/{sessionId}.jsonl` |
| skillsSnapshot | 从 main 复制 |

**openclaw 命令参数**：
- 主会话：`openclaw agent --agent {agentId} --message "{msg}" --timeout {sec}`
- 独立会话：`openclaw agent --session-id {sessionId} --message "{msg}" --timeout {sec}`
- 注意：使用 `--session-id` 时**不需要** `--agent` 参数（session 已关联 agent）

**无会话时处理**：
- 发送 "hi" 消息触发 openclaw 创建 main 会话
- 等待 1 秒让 openclaw 完成文件写入

**配置项**：
- `openclaw.data.dir`: OpenClaw 数据目录，默认 `.openclaw`（即 `~/.openclaw`)