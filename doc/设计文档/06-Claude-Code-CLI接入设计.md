# Claude Code CLI 接入设计

## 一、概念映射

| OpenClaw 概念 | Claude Code 对应 |
|---------------|------------------|
| Agent | 项目空间（一个 Claude Code 实例） |
| Session | 命令框/会话（一次交互过程） |
| `openclaw agent --agent xxx --message` | 发送消息给某个 Agent |

## 二、接入场景

**目标方向**: 其他 Agent → Claude Code

- OpenClaw Agent 发送消息给 Claude Code Agent
- Claude Code 收到消息后处理，回复结果

## 三、过往方案记录

| 方案 | 原理 | 优点 | 缺点 | 状态 |
|------|------|------|------|------|
| HTTP 监听 | Claude Code 启动 HTTP 服务端点 | 实时接收 | 需额外服务、端口管理 | 已排除 |
| AgentCommProxy 中转 | Claude Code 注册为 Agent，轮询获取消息 | 无需额外服务 | 需轮询有延迟 | 已排除 |
| Skills 集成 | 创建 Skills 提供消息收发能力 | 符合机制 | 需手动调用 | 已排除 |
| MCP 工具 | AgentCommProxy 作为 MCP 服务器 | MCP 标准 | 需实现 MCP 服务器 | 已排除 |
| Remote Control | `--remote-control --input-format stream-json` | 实时双向 | stdin 网桥复杂、进程持续占用 | 已排除 |

## 四、最终方案：Session-Resume

### 原理

- Claude Code 使用 `--session-id` 预定义会话 ID
- AgentCommProxy 维护 session-id 映射关系
- 通过 `--resume` 恢复会话处理新消息
- 类似 OpenClaw 调用模式，每次消息独立进程

### CLI 关键参数

| 参数 | 说明 |
|------|------|
| `--session-id <uuid>` | 预定义会话 ID，持久化会话 |
| `--resume <session-id>` | 恢复指定会话，继续对话 |
| `-p, --print` | 非交互打印模式，输出结果到 stdout |

### 调用示例

```bash
# 首次启动，创建会话
claude --session-id <uuid> -p "就绪"

# 处理消息，恢复会话
claude --resume <uuid> -p "帮我分析这段代码"
```

### 方案特点

| 特点 | 说明 |
|------|------|
| Claude Code 执行 | 同步执行，必须等待结果返回 |
| 入口层支持 | 支持同步和异步两种模式 |
| 异步模式 | 守护进程后台执行，调用方轮询查询状态 |
| 进程模式 | 每次消息启动独立进程 |
| 会话持久 | 会话文件持久化，进程退出不丢失 |
| 容错性 | 每次调用独立，不影响其他消息 |
| 资源占用 | 按需启动，结束后释放 |
| 实现成本 | 类似 OpenClaw，复用现有架构 |

## 五、详细设计

### 1. Agent 注册

```bash
# Claude Code Agent ID 格式：claude-code-{projectId}
agentproxy register --agent claude-code-myproject --type claude-code
```

### 2. 数据存储策略

| 数据类型 | 存储位置 | 原因 |
|---------|---------|------|
| 消息状态、请求记录 | SQLite requests 表 | 复用现有架构，支持 HTTP_POLL、回调、历史查询 |
| session-id 映射 | JSON 配置文件 | SQLite 可能被清空，agent 配置需持久保存 |
| agentId ↔ sessionId | JSON 配置文件 | 同上 |

### 3. Session-ID 管理

- session-id 在首次对话时创建（非注册时预生成）
- AgentCommProxy 维护 `agentId ↔ sessionId` 映射关系
- 存储：JSON 配置文件（持久化，不受 SQLite 清空影响）

### 3. 消息流程

**同步模式（sync=true）**：

```
其他 Agent 发送消息（sync=true）
    ↓
AgentCommProxy 直接执行 claude 命令
    ↓
调用 claude --session-id {uuid} -p "{message}"（首次）
或 claude --resume {sessionId} -p "{message}"（后续）
    ↓
同步等待 stdout 输出结果
    ↓
返回结果给发送方（状态：DONE）
```

**异步模式（sync=false）**：

```
其他 Agent 发送消息（sync=false）
    ↓
AgentCommProxy 立即返回 requestId（状态：PENDING）
    ↓
守护进程后台执行 claude 命令
    ↓
调用 claude --session-id/--resume，等待结果
    ↓
结果完成，更新状态为 DONE
    ↓
调用方轮询查询状态，获取最终结果
```

**关键点**：
- Claude Code 本身无异步机制，每次调用必须等待结果
- 异步模式由 AgentCommProxy 守护进程提供（后台执行 + 状态管理）
- 调用方通过 HTTP_POLL 訡式轮询获取结果

### 4. ClaudeCodeProxy 实现

```java
public class ClaudeCodeProxy implements CommandProxy {

    @Override
    public CommandResult execute(String agent, String message, int timeout, String sessionId) {
        // 构建 claude 命令
        String command = String.format(
            "claude --resume %s -p \"%s\"",
            sessionId, escapeMessage(message)
        );

        // 执行命令，捕获输出
        ProcessResult result = ProcessExecutor.run(command, timeout);

        return new CommandResult(result.getOutput(), result.isSuccess());
    }
}
```

### 5. 需要开发的组件

| 组件 | 职责 |
|------|------|
| ClaudeCodeProxy | 执行 `claude --session-id/--resume` 命令，捕获结果 |
| ClaudeCodeAgentConfig | 管理 agentId ↔ sessionId 映射，JSON 配置文件读写 |
| ClaudeCodeCommand | Claude Code Agent 注册/管理 CLI |

### 6. ProxyType 扩展

```java
public enum ProxyType {
    OPENCLAW,      // OpenClaw CLI
    HTTP,          // HTTP API
    WEBSOCKET,     // WebSocket
    CUSTOM,        // 自定义命令
    CLAUDE_CODE    // Claude Code Agent
}
```

## 六、与 OpenClaw 能力适配核对

| 能力 | OpenClaw | Claude Code | 适配方案 | 状态 |
|------|----------|-------------|----------|------|
| **agent** | Agent ID | `claude-code-{projectId}` | 新增 Proxy 类型后完全适配 | ✅ |
| **sender** | 发送方 Agent | 发送方 Agent | 无需改造，用于回调标识 | ✅ |
| **message** | 消息内容 | `-p` 参数传递 | 消息内容转 `-p "{message}"` | ✅ |
| **sync** | 同步/异步 | stdout 输出 | Claude Code 同步执行；入口层支持异步（守护进程后台执行+HTTP_POLL） | ✅ |
| **timeout** | 超时时间 | 进程超时控制 | ProcessExecutor 设置超时 | ✅ |
| **callbackUrl** | 回调地址 | 回调发送方 | 复用现有回调机制 | ✅ |
| **proxy** | Proxy 类型 | 新增 `claude-code` | 新增 ProxyType.CLAUDE_CODE | ✅ |
| **taskId** | 业务任务 ID | session-id 映射 | Claude Code 用 session-id 管理，taskId→sessionId | ✅ |
| **sessionMode** | 会话模式 | session-id 管理策略 | MAIN: agentId 对应固定 session-id；INDEPENDENT: taskId 对应独立 session-id | ✅ |
| **clearSession** | 清空会话历史 | 创建新 session-id | 不清空文件，直接生成新 session-id 替换 | ✅ |

## 七、类型区分机制

### 两个类型概念

| 类型 | 作用时机 | 决定内容 |
|------|---------|---------|
| **ProxyType** | 执行阶段 | 调用哪个 CLI 代理（OpenClaw/Claude Code） |
| **SenderType** | 回调阶段 | 回调方式（CLI/HTTP_CALLBACK/HTTP_POLL） |

### ProxyType（执行代理区分）

**CLI 入口**：
```bash
# 调用 OpenClaw Agent（默认）
agentproxy agent --agent xxx --sender yyy --message "hello"

# 调用 Claude Code Agent（指定 proxy）
agentproxy agent --agent claude-code-myproject --sender yyy --message "hello" --proxy claude-code
```

**HTTP 入口**：
```json
{
  "agent": "claude-code-myproject",
  "sender": "AgentA",
  "message": "hello",
  "proxy": "claude-code"
}
```

### SenderType（回调方式区分）

**自动推断规则**：
- CLI 命令发起 → SenderType.CLI
- HTTP + callbackUrl → SenderType.HTTP_CALLBACK
- HTTP 无 callbackUrl → SenderType.HTTP_POLL

### ProxyType 扩展

```java
public enum ProxyType {
    OPENCLAW("openclaw", "OpenClaw CLI 命令"),
    HTTP("http", "HTTP API 调用"),
    WEBSOCKET("websocket", "WebSocket 通信"),
    CUSTOM("custom", "自定义命令"),
    CLAUDE_CODE("claude-code", "Claude Code Agent");  // 新增
}
```

### 处理流程

**同步模式**：
```
入口层接收请求（proxy=claude-code, sync=true）
    ↓
AgentService 直接执行 ClaudeCodeProxy
    ↓
同步等待 Claude Code 输出结果
    ↓
返回结果给调用方
```

**异步模式**：
```
入口层接收请求（proxy=claude-code, sync=false）
    ↓
立即返回 requestId（状态：PENDING）
    ↓
守护进程检测到待处理消息
    ↓
执行 ClaudeCodeProxy，等待结果
    ↓
更新状态为 DONE，调用方轮询获取
```

## 八、Claude Code Agent 配置持久化

### 问题

- SQLite 数据库可能被清空
- Claude Code 项目空间信息需持久保存：
  - 项目路径（工作目录）
  - session-id（会话标识）
  - 启动命令

### 方案：JSON 配置文件

**文件路径**：`~/.agentcommproxy/claude-code-agents.json`

**配置结构**：
```json
{
  "agents": [
    {
      "agentId": "claude-code-myproject",
      "projectPath": "/path/to/myproject",
      "sessionId": "uuid-xxx-xxx",
      "createdAt": "2026-05-13T10:00:00",
      "updatedAt": "2026-05-13T15:30:00"
    }
  ]
}
```

### 配置生效方式

- 启动时加载：AgentCommProxy 启动时读取配置文件
- 实时写入：每次注册/更新 Agent 时写入配置文件，无需重启即可生效

### 配置管理命令

```bash
# 注册 Claude Code Agent
agentproxy claude-code register --agent claude-code-myproject --path /path/to/project

# 查看已注册 Agent
agentproxy claude-code list

# 刷新配置（重新加载）
agentproxy claude-code reload

# 删除 Agent
agentproxy claude-code remove --agent claude-code-myproject
```

## 九、已确认事项

| 问题 | 确认结果 |
|------|---------|
| Agent ID 唯一性 | 同一项目可启动多个 Claude Code 实例 |
| session-id 初始化时机 | 首次对话时创建，非注册时预生成 |
| Claude Code 执行模式 | 同步执行，每次调用必须等待结果返回 |
| 入口层支持模式 | 支持同步和异步两种模式（异步由守护进程提供） |
| 异步模式实现 | 守护进程后台执行 Claude Code，调用方 HTTP_POLL 轮询 |
| 超时处理 | 不取消会话，超时后重试（可能是超长任务） |
| 超时配置 | Claude Code 超时需单独配置更长时间 |
| 回调机制 | 使用已有的 HTTP_CALLBACK，由 SenderType 决定 |

## 十、超时与重试设计

### 超时配置

Claude Code 处理可能耗时较长（如复杂代码分析），需要单独配置：

```properties
# Claude Code 超时配置（秒）
proxy.claude-code.timeout=1800  # 30分钟
```

### 重试机制

```
调用 claude --resume
    ↓
超时 → 重试（使用同一 session-id）
    ↓
重试 N 次 → 成功/最终失败
```

**重试规则**：
- 不取消会话（session-id 保持不变）
- 重试时继续使用 `--resume` 恢复同一会话
- 重试次数可配置

```properties
# Claude Code 重试配置
proxy.claude-code.retry.count=3
proxy.claude-code.retry.interval=10
```

## 十一、配置文件示例

```properties
# Claude Code Proxy 配置
proxy.claude-code.timeout=1800
proxy.claude-code.retry.count=3
proxy.claude-code.retry.interval=10

# Claude Code Agent 配置文件
# ~/.agentcommproxy/claude-code-agents.json
```

---

## 十三、代码改动对比（参考 OpenClaw 实现）

### 对比 OpenClawProxy 实现

| 模块 | OpenClaw 实现 | Claude Code 改动 |
|------|--------------|------------------|
| **getName()** | 返回 `"openclaw"` | 返回 `"claude-code"` |
| **buildCommand()** | `openclaw agent --agent/session-id --message --timeout` | `claude --resume {sessionId} -p "{message}"` 或 `claude --session-id {uuid} -p "{message}"` |
| **execute()** | 执行 openclaw 命令，过滤日志输出 | 执行 claude 命令，直接捕获 stdout |
| **isLogLevel()** | 过滤 OpenClaw 日志格式 | 可能需要过滤 Claude Code 日志格式（待测试） |
| **session 管理** | 通过 SessionManager 管理 openclaw sessions.json | 通过 ClaudeCodeAgentConfig 管理 JSON 配置文件 |

### 需要改动的文件

| 文件 | 改动类型 | 改动内容 |
|------|---------|---------|
| `model/ProxyType.java` | **修改** | 新增 `CLAUDE_CODE("claude-code", "Claude Code Agent")` 枚举 |
| `proxy/ClaudeCodeProxy.java` | **新增** | 参考 OpenClawProxy，实现 `getName()`、`buildCommand()`、`execute()` |
| `proxy/CommandProxyFactory.java` | **修改** | static 块新增 `registerProxy(new ClaudeCodeProxy())` |
| `config/ClaudeCodeAgentConfig.java` | **新增** | 管理 agentId↔sessionId 映射，JSON 文件读写 |
| `config/ConfigManager.java` | **修改** | 新增 `proxy.claude-code.timeout`、`proxy.claude-code.retry.count` 配置项 |
| `command/ClaudeCodeCommand.java` | **新增** | Claude Code Agent 注册/管理 CLI 命令 |

### ClaudeCodeProxy 核心代码

```java
package org.openclaw.agentcommproxy.proxy;

public class ClaudeCodeProxy implements CommandProxy {
    private static final Logger log = LoggerFactory.getLogger(ClaudeCodeProxy.class);

    @Override
    public String getName() {
        return "claude-code";
    }

    @Override
    public String buildCommand(String agent, String message, int timeout, String sessionId) {
        String escapedMessage = escapeMessage(message);
        if (sessionId != null && !sessionId.isEmpty()) {
            // 恢复已有会话
            return String.format("claude --resume %s -p \"%s\"", sessionId, escapedMessage);
        } else {
            // 首次创建会话（sessionId 应由 ClaudeCodeAgentConfig 预生成）
            throw new IllegalStateException("Claude Code requires sessionId");
        }
    }

    @Override
    public CommandResult execute(String agent, String message, int timeout, String sessionId) {
        String command = buildCommand(agent, message, timeout, sessionId);
        // 参考 OpenClawProxy 的 ProcessBuilder 执行逻辑
        // 注意：工作目录需要切换到 projectPath
        // ...
    }
}
```

### ClaudeCodeAgentConfig 核心代码

```java
package org.openclaw.agentcommproxy.config;

public class ClaudeCodeAgentConfig {
    private static final String CONFIG_FILE = "claude-code-agents.json";
    
    // JSON 结构
    // {
    //   "agents": [
    //     { "agentId": "...", "projectPath": "...", "sessionId": "...", ... }
    //   ]
    // }
    
    public String getOrCreateSessionId(String agentId, String projectPath);
    public void registerAgent(String agentId, String projectPath);
    public String getSessionId(String agentId);
    public void updateSessionId(String agentId, String newSessionId);
    public void removeAgent(String agentId);
    public List<AgentInfo> listAgents();
}
```

### ConfigManager 新增配置

```java
// 新增配置项
private static final int DEFAULT_CLAUDE_CODE_TIMEOUT = 1800;  // 30分钟
private static final int DEFAULT_CLAUDE_CODE_RETRY_COUNT = 3;
private static final int DEFAULT_CLAUDE_CODE_RETRY_INTERVAL = 10;

// 新增方法
public int getClaudeCodeTimeout() {
    return getInt("proxy.claude-code.timeout", DEFAULT_CLAUDE_CODE_TIMEOUT);
}

public int getClaudeCodeRetryCount() {
    return getInt("proxy.claude-code.retry.count", DEFAULT_CLAUDE_CODE_RETRY_COUNT);
}

public int getClaudeCodeRetryInterval() {
    return getInt("proxy.claude-code.retry.interval", DEFAULT_CLAUDE_CODE_RETRY_INTERVAL);
}
```

---

## 十四、复用分析

### 完全复用（无需改动）

| 模块 | 文件 | 说明 |
|------|------|------|
| **核心服务** | `AgentService.java` | 根据 proxyType 选择 proxy，整个流程无需改动 |
| **存储层** | `SQLiteStore.java` | 消息状态管理，完全复用 |
| **守护进程** | `DaemonManager.java` | 后台执行逻辑，完全复用 |
| **回调机制** | `CallbackHandler*.java` | 回调处理，完全复用 |
| **HTTP 入口** | `HttpServerManager.java` | proxy 参数已支持，完全复用 |
| **CLI 入口** | `AgentCommand.java` | `--proxy` 参数已支持，完全复用 |
| **接口定义** | `CommandProxy.java` | 接口设计通用，完全复用 |

### 部分复用

| 模块 | 文件 | 复用比例 | 改动内容 |
|------|------|---------|---------|
| **执行逻辑** | `OpenClawProxy.execute()` | 80%+ | ClaudeCodeProxy 参考 ProcessBuilder 逻辑，调整 `pb.directory()` 设置工作目录 |
| **日志过滤** | `OpenClawProxy.isLogLevel()` | 可能复用 | Claude Code 输出格式待测试，可能需要调整 |

### 需新增

| 模块 | 文件 | 代码量估算 | 说明 |
|------|------|-----------|------|
| **枚举值** | `ProxyType.java` | 1 行 | 新增 `CLAUDE_CODE` 枚举 |
| **注册** | `CommandProxyFactory.java` | 1 行 | `registerProxy(new ClaudeCodeProxy())` |
| **Proxy 实现** | `ClaudeCodeProxy.java` | ~150 行 | 参考 OpenClawProxy，execute() 复用大部分 |
| **配置管理** | `ClaudeCodeAgentConfig.java` | ~100 行 | JSON 文件读写，agentId↔sessionId 映射 |
| **CLI 命令** | `ClaudeCodeCommand.java` | ~100 行 | Agent 注册/管理命令 |
| **配置项** | `ConfigManager.java` | ~20 行 | 3 个新配置方法 |

### 复用比例估算

```
总代码量估算：~370 行新增
复用代码量：核心架构 + OpenClawProxy 执行逻辑（约 80%）

复用占比：约 95%（大部分架构和流程无需改动）
新增占比：约 5%（主要是 ClaudeCodeProxy 实现和配置管理）
```

### 关键复用点

**AgentService 核心流程完全复用**：
```java
// 第 76-78 行，根据 proxyType 选择 proxy
CommandProxy proxy = CommandProxyFactory.getProxy(request.getProxyType());
CommandResult result = proxy.execute(...);

// 新增 CLAUDE_CODE 后，这段代码无需任何改动
// 自动支持 Claude Code Proxy
```

**OpenClawProxy.execute() 复用**：
```java
// ClaudeCodeProxy 可以直接复用：
// - ProcessBuilder 构建
// - 输出流读取（outputThread/errorThread）
// - 超时处理逻辑
// - exitCode 判断

// 只需改动：
// - pb.directory(new File(projectPath))  // 设置工作目录
// - buildCommand() 返回 claude 命令格式
```

### 总结

Claude Code 接入设计充分利用了现有的 ProxyType 扩展机制，**95% 的代码复用**，只需新增约 370 行代码（主要是 ClaudeCodeProxy 实现和配置管理）。

| 差异 | OpenClaw | Claude Code | 处理方案 |
|------|----------|-------------|---------|
| 工作目录 | 无需切换 | 需切换到 projectPath | ClaudeCodeProxy 执行时设置 `pb.directory(new File(projectPath))` |
| session 首次调用 | 发送 "hi" 自动创建 main | 预定义 session-id | ClaudeCodeAgentConfig 预生成 sessionId，首次用 `--session-id` |
| session 恢复 | `--session-id` | `--resume` | buildCommand 区分首次和后续 |
| 超时配置 | 默认 300s | 需更长（1800s） | ConfigManager 单独配置 |
| 输出过滤 | OpenClaw 日志格式 | Claude Code 输出格式 | 可能需要调整 isLogLevel() |

| 文件 | 操作 | 说明 |
|------|------|------|
| `model/ProxyType.java` | 修改 | 新增 CLAUDE_CODE 枚举 |
| `proxy/ClaudeCodeProxy.java` | 新增 | Claude Code 命令执行代理 |
| `proxy/CommandProxyFactory.java` | 修改 | 注册 ClaudeCodeProxy |
| `config/ClaudeCodeAgentConfig.java` | 新增 | Claude Code Agent 配置管理（JSON 文件读写） |
| `config/ConfigManager.java` | 修改 | 新增 Claude Code 超时配置项 |
| `command/ClaudeCodeCommand.java` | 新增 | Claude Code Agent 注册/管理 CLI |