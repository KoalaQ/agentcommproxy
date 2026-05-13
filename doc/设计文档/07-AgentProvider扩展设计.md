# AgentProvider 扩展设计

## 背景

为支持多种 Agent 工具接入（OpenClaw、Claude Code、Cursor 等），设计 AgentProvider 抽象层，封装各工具的接入逻辑，实现主工程零改动扩展。

## 设计目标

1. **主工程零改动**：接入新 Agent 工具只需实现接口并注册
2. **职责清晰**：Provider 层封装工具特性，Service 层只依赖接口
3. **会话管理统一**：不同工具的会话创建机制差异由 Provider 处理

## 核心概念

### needsPreCreateSession 的含义

不是"是否需要创建会话"，而是"是否需要在 execute **之前**单独创建会话"。

| 值 | 含义 | 适用工具 |
|---|---|---|
| `false` | 工具支持命令行参数直接创建会话 | Claude Code（`--session-id`） |
| `true` | 工具需要额外操作才能创建会话 | OpenClaw（需编辑 sessions.json） |

### 会话创建机制对比

**Claude Code（一条命令完成）：**
```bash
claude --session-id {uuid} -p "message"
# 创建新会话 + 发送消息 一体化
```

**OpenClaw（两步操作）：**
```bash
# 步骤1：编辑 sessions.json 创建会话配置
# 步骤2：执行命令
openclaw agent --session-id {uuid} --message "hello"
```

## 接口设计

```java
public interface AgentProvider {
    // 基础信息
    String getName();
    ProxyType getProxyType();
    
    // 核心组件
    SessionManager getSessionManager();
    CommandProxy getProxy();
    
    // 会话创建（可选）
    default String createIndependentSession(String agentId, String taskId, boolean clearSession) {
        return null;
    }
    
    // 是否需要预创建
    default boolean needsPreCreateSession() {
        return false;
    }
}
```

## 调用流程

### MAIN 模式

```
AgentService.sendSync()
  ↓
getSessionId() → sessionManager.getMainSessionId()
  ↓
（可能为 null，首次调用）
  ↓
proxy.execute(..., sessionId=null)
  ↓
【Claude Code】
  Proxy 内部生成 UUID，执行命令，返回 sessionId
  
【OpenClaw】
  直接执行 --agent 命令，无 sessionId 概念
```

### INDEPENDENT 模式首次调用

```
AgentService.sendSync() / processExecute()
  ↓
getSessionId() → 返回 null（requests 表无记录）
  ↓
【检查】provider.needsPreCreateSession()
  ↓
【Claude Code：false】
  直接 execute(sessionId=null)
  Proxy 内部：生成 UUID + 发送消息（一条命令）
  返回 sessionId → 存入 requests 表
  
【OpenClaw：true】
  先调用 createIndependentSession()
    → ensureMainSessionExists()（发送 "hi"）
    → 编辑 sessions.json
    → 返回 sessionId
  再 execute(sessionId)
  返回结果 → 存入 requests 表
```

### INDEPENDENT 模式后续调用

```
getSessionId() → sessionManager.findSessionIdByTaskId()
  ↓
返回已有 sessionId
  ↓
【Claude Code】
  execute(..., sessionId) → 使用 --resume {sessionId}
  
【OpenClaw】
  execute(..., sessionId) → 使用 --session-id {sessionId}
```

## 文件结构

| 文件 | 职责 |
|---|---|
| `AgentProvider.java` | 接口定义 |
| `AgentProviderFactory.java` | 注册/获取 Provider |
| `OpenClawProvider.java` | OpenClaw 完整接入封装 |
| `ClaudeCodeProvider.java` | Claude Code 完整接入封装 |
| `OpenClawSessionHelper.java` | sessions.json 操作（OpenClaw 专用） |
| `BaseSessionManager.java` | requests 表查询（通用） |
| `OpenClawSessionManager.java` | OpenClaw 会话管理（mainSessionId 无概念） |
| `ClaudeCodeSessionManager.java` | Claude Code 会话管理（mainSessionId 存 JSON 配置） |

## 接入新 Agent 工具

### 步骤1：添加 ProxyType 枚举

```java
public enum ProxyType {
    OPENCLAW,
    CLAUDE_CODE,
    CURSOR,  // 新增
    // ...
}
```

### 步骤2：实现 AgentProvider

```java
public class CursorProvider implements AgentProvider {
    
    private final CursorSessionManager sessionManager;
    private final CursorProxy proxy;
    
    public CursorProvider(SQLiteStore store) {
        this.sessionManager = new CursorSessionManager(store);
        this.proxy = new CursorProxy();
    }
    
    @Override
    public String getName() { return "cursor"; }
    
    @Override
    public ProxyType getProxyType() { return ProxyType.CURSOR; }
    
    @Override
    public SessionManager getSessionManager() { return sessionManager; }
    
    @Override
    public CommandProxy getProxy() { return proxy; }
    
    // 根据工具特性决定
    @Override
    public boolean needsPreCreateSession() {
        // 如果 Cursor 支持命令行参数创建会话，返回 false
        // 如果需要额外操作，返回 true
        return false;
    }
}
```

### 步骤3：注册到 Factory

```java
// AgentProviderFactory.initialize() 中添加
registerProvider(new CursorProvider(store));
```

**主工程（AgentService）无需任何改动。**

## 判断 needsPreCreateSession 的标准

| 场景 | 值 |
|---|---|
| 工具支持 `--session-id` 类似参数，命令执行时自动创建会话 | `false` |
| 工具不支持命令行创建，需要编辑配置文件或调用 API | `true` |
| 工具未来可能支持命令行创建，当前不支持 | `true`（未来可改为 false） |

## 注意事项

1. **sessionId 来源**：
   - Claude Code：Proxy 层生成
   - OpenClaw：Provider 层生成

2. **sessionId 存储**：
   - MAIN 模式：Claude Code 存 JSON 配置，OpenClaw 无此概念
   - INDEPENDENT 模式：存 requests 表

3. **扩展性**：
   - 工具特性变化只需修改对应 Provider
   - 主工程逻辑稳定不变