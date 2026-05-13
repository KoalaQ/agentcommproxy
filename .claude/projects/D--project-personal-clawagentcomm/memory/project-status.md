---
name: project-status
description: AgentCommProxy 项目当前状态和开发进展
metadata:
  type: project
---

## 项目状态 (2026-05-13)

**已完成功能**：
- CLI 和 HTTP API 入口
- 同步/异步消息发送
- 回调机制（CLI/HTTP_CALLBACK/HTTP_POLL）
- Proxy 类型扩展（OPENCLAW/HTTP/WEBSOCKET/CUSTOM）
- 守护进程后台处理
- 定期清理功能
- **独立会话支持**（刚完成）

**独立会话功能** (最新)：
- 入参：taskId（业务ID）+ sessionMode（main/independent）+ clearSession
- 默认规则：未指定 sessionMode → MAIN
- 独立会话从 main 复制配置，entry key 使用 taskId 命名
- sessionId 存储在 openclaw 的 sessions.json 和 SQLite requests 表

**待验证**：
- 独立会话的完整流程测试
- clearSession 清空历史功能测试

**相关文档**：
- 设计文档：`doc/设计文档/05-独立会话支持设计.md`
- CLI 接口：`doc/接口文档/01-CLI接口说明.md`
- HTTP 接口：`doc/接口文档/02-HTTP接口说明.md`