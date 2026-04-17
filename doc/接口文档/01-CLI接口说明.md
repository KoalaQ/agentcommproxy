# CLI 接口说明

## 基本用法

```bash
agentproxy <command> [options]
```

全局选项：

- `-h, --help`: 显示帮助信息
- `-V, --version`: 显示版本信息
- `--http-port <port>`: HTTP 服务端口（覆盖配置）
- `--no-http`: 禁用 HTTP 服务自动启动
- `--no-daemon`: 禁用守护进程自动启动

## agent 命令

发送消息到目标 Agent。

### 基本语法

```bash
agentproxy agent --agent <target> --sender <sender> --message <message> [--sync] [--timeout N] [--proxy <type>]
```

### 必需参数

| 参数 | 说明 |
|-----|------|
| `--agent <name>` | 目标 Agent 名称 |
| `--sender <name>` | 发送方 Agent 名称（用于回调） |
| `--message <content>` | 消息内容 |

### 可选参数

| 参数 | 说明 | 默认值 |
|-----|------|-------|
| `--sync` | 同步模式，等待响应 | 异步模式 |
| `--timeout <seconds>` | 超时时间（秒） | 300 |
| `--request-id <id>` | 指定请求 ID（用于重试） | 自动生成 UUID |
| `--proxy <type>` | Proxy 类型（openclaw/http/websocket） | openclaw |

### 示例

```bash
# 异步发送消息（默认 OpenClaw）
agentproxy agent --agent AgentB --sender AgentA --message "hello"

# 同步发送消息（等待响应）
agentproxy agent --agent AgentB --sender AgentA --message "check status" --sync

# 指定超时时间
agentproxy agent --agent AgentB --sender AgentA --message "long task" --sync --timeout 600

# 指定请求 ID
agentproxy agent --agent AgentB --sender AgentA --message "retry task" --request-id "abc-123"

# 指定 Proxy 类型
agentproxy agent --agent AgentB --sender AgentA --message "hello" --proxy openclaw
```

### 输出说明

**异步模式输出:**
```
已收到、稍后回复您，消息唯一标识: <request-id>
```

**同步模式输出:**
- 成功: 直接输出响应内容
- 失败: 输出错误信息

## status 命令

查询指定消息的状态。

### 基本语法

```bash
agentproxy status --request-id <id>
```

### 必需参数

| 参数 | 说明 |
|-----|------|
| `--request-id <id>` | 要查询的请求 ID |

### 示例

```bash
agentproxy status --request-id abc-123-def-456
```

### 输出说明

```
Request ID: abc-123-def-456
Status: DONE
Sender: AgentA
Target Agent: AgentB
Message: hello
Execute Retry: 0
Callback Retry: 0
Response: Hello, AgentA!
```

## list 命令

查询消息列表。

### 基本语法

```bash
agentproxy list [--limit N] [--status <STATUS>] [--full]
```

### 可选参数

| 参数 | 说明 | 默认值 |
|-----|------|-------|
| `--limit <N>` | 返回记录数量 | 10 |
| `--status <STATUS>` | 按状态筛选 | 全部 |
| `--full` | 显示完整内容（不截断） | 截断显示 |

### 状态值

| 状态 | 说明 |
|-----|------|
| PENDING | 待处理 |
| EXECUTING | 正在执行 |
| EXECUTE_SUCCESS | 执行成功 |
| EXECUTE_FAILED | 执行失败 |
| EXECUTE_TIMEOUT | 执行超时 |
| CALLBACKING | 正在回调 |
| CALLBACK_FAILED | 回调失败 |
| DONE | 完成 |
| ERROR | 错误 |

### 示例

```bash
# 查看最近 10 条消息
agentproxy list

# 查看最近 20 条消息
agentproxy list --limit 20

# 查看所有待处理消息
agentproxy list --status PENDING

# 查看所有失败消息
agentproxy list --status ERROR

# 查看完整内容
agentproxy list --full
```

### 输出说明

```
Found 3 records:
--------------------------------------------------------------------------------
Request ID:  abc-123-def-456
Status:      DONE
Sender:      AgentA
Target:      AgentB
Sync:        NO
Timeout:     300s
Exec Retry:  0
Callback Retry: 0
Message:     hello
Response:    Hello, AgentA!
--------------------------------------------------------------------------------
Total: 3 records
Database: ~/.agentcommproxy/messages.db
```

## daemon 命令

管理后台守护进程。

### 基本语法

```bash
agentproxy daemon [start|stop|status] [options]
```

### 子命令

#### daemon start

启动守护进程。

```bash
agentproxy daemon start [interval] [--foreground] [--http-port PORT]
```

| 参数 | 说明 | 默认值 |
|-----|------|-------|
| `interval` | 扫描间隔（秒），可选 | 配置文件默认值 |
| `-f, --foreground` | 前台运行模式 | 后台模式 |
| `--http-port` | HTTP 服务端口 | 配置文件默认值 |

#### daemon stop

停止守护进程。

```bash
agentproxy daemon stop
```

#### daemon status

查看守护进程状态。

```bash
agentproxy daemon status
```

### 示例

```bash
# 查看守护进程帮助
agentproxy daemon

# 前台运行守护进程（阻塞模式）
agentproxy daemon start --foreground

# 指定 HTTP 端口启动
agentproxy daemon start --foreground --http-port 9091

# 或使用全局选项
agentproxy --http-port 9091 daemon start --foreground

# 指定扫描间隔
agentproxy daemon start 10 --foreground

# 后台运行（使用 nohup）
nohup agentproxy daemon start --foreground > ~/.agentcommproxy/logs/daemon.log 2>&1 &

# 后台运行并指定端口
nohup agentproxy --http-port 9091 daemon start --foreground > ~/.agentcommproxy/logs/daemon.log 2>&1 &

# 查看守护进程状态
agentproxy daemon status

# 停止守护进程
agentproxy daemon stop

# 强制杀死后台进程
pkill -f 'agentproxy daemon start'
```

### 输出说明

**daemon start --foreground:**
```
Starting daemon in foreground mode...
Interval: 5 seconds
Press Ctrl+C to stop
```

**daemon status:**
```
Daemon status:
  Running: YES (in current process)
  Interval: 5s

To check background daemon process:
  ps aux | grep 'agentproxy daemon'
```

## http 命令

管理 HTTP API 服务。

### 基本语法

```bash
agentproxy http [start|stop|status|gen-key] [options]
```

### 子命令

#### http start

启动 HTTP 服务。

```bash
agentproxy http start [--port <port>]
```

| 参数 | 说明 |
|-----|------|
| `-p, --port <port>` | HTTP 端口，可选 | 配置文件默认值 |

#### http stop

停止 HTTP 服务。

```bash
agentproxy http stop
```

#### http status

查看 HTTP 服务状态。

```bash
agentproxy http status
```

#### http gen-key

生成新的 API Key。

```bash
agentproxy http gen-key
```

### 示例

```bash
# 查看 HTTP 命令帮助
agentproxy http

# 启动 HTTP 服务
agentproxy http start

# 指定端口启动
agentproxy http start --port 9090

# 查看 HTTP 服务状态
agentproxy http status

# 停止 HTTP 服务
agentproxy http stop

# 生成新的 API Key
agentproxy http gen-key
```

### 输出说明

**http start:**
```
HTTP service started on port 8080
API Key: abc-123-def-456
```

**http status:**
```
HTTP service status:
  Running: YES
  Port: 8080

API Key: abc-123-def-456
HTTP Enabled: true
```

**http gen-key:**
```
New API key generated:
  Old key: abc-123-def-456
  New key: xyz-789-uvw-012

Note: Restart HTTP service to use the new key
```

### 全局 HTTP 启动参数

程序启动时可使用以下参数控制 HTTP 服务：

```bash
# 指定 HTTP 端口（覆盖配置）
agentproxy --http-port 9090

# 禁用 HTTP 服务自动启动
agentproxy --no-http

# 禁用守护进程自动启动
agentproxy --no-daemon
```

## clear 命令

清理历史消息记录。

### 基本语法

```bash
agentproxy clear [--all] [--status <STATUS>] [--request-id <ID>] [-y]
```

### 可选参数

| 参数 | 说明 |
|-----|------|
| `--all` | 清理所有消息 |
| `--status <STATUS>` | 按状态清理 |
| `--request-id <ID>` | 清理指定消息 |
| `-y, --yes` | 跳过确认提示 |

### 示例

```bash
# 查看清理帮助
agentproxy clear

# 清理所有消息（需确认）
agentproxy clear --all

# 清理所有消息（跳过确认）
agentproxy clear --all -y

# 清理已完成消息
agentproxy clear --status DONE

# 清理错误消息
agentproxy clear --status ERROR

# 清理指定消息
agentproxy clear --request-id abc-123-def-456
```

### 输出说明

**agentproxy clear --all:**
```
Clear all 100 messages? (y/N): y
Cleared 100 messages
```

**agentproxy clear --status DONE:**
```
Cleared 50 messages with status: DONE
```

## 配置说明

配置文件位置: `~/.agentcommproxy/config.properties`

### 配置项

| 配置项 | 说明 | 默认值 |
|-------|------|-------|
| default.timeout | 默认超时时间（秒） | 300 |
| async.retry.count | 异步重试次数 | 3 |
| async.retry.interval | 重试间隔（秒） | 5 |
| daemon.enabled | 是否启用守护进程 | true |
| daemon.thread.pool.size | 守护线程池大小 | 4 |
| daemon.scan.interval | 守护扫描间隔（秒） | 5 |
| http.enabled | 是否启用 HTTP 服务 | true |
| http.port | HTTP 服务端口 | 8080 |
| http.api.key | API Key | 自动生成 UUID |
| proxy.default | 默认 Proxy 类型 | openclaw |
| cleanup.enabled | 是否启用定期清理 | true |
| cleanup.days | 清理保留天数 | 7 |
| cleanup.status | 清理状态 | DONE |

### 配置示例

```properties
# 默认超时时间（秒）
default.timeout=300

# 异步重试次数
async.retry.count=3

# 重试间隔（秒）
async.retry.interval=5

# 守护进程配置
daemon.enabled=true
daemon.thread.pool.size=4
daemon.scan.interval=5

# HTTP 服务配置
http.enabled=true
http.port=8080
http.api.key=abc-123-def-456

# Proxy 配置
proxy.default=openclaw

# 定期清理配置
cleanup.enabled=true
cleanup.days=7
cleanup.status=DONE
```

## Claude Code Skills 集成

本项目提供 Claude Code Skills，可在 Claude Code CLI 中直接调用。

### /agentproxy

在 Claude Code 中发送消息。

```bash
/agentproxy --agent <target-agent> --sender <my-agent> <message> [--sync] [--timeout N]
```

### Skills 示例

```bash
# 异步发送
/agentproxy --agent AgentB --sender AgentA "hello"

# 同步发送
/agentproxy --agent AgentB --sender AgentA "check status" --sync
```

### Skills 输出

**异步模式:**
```
已收到、稍后回复您，消息唯一标识: <request-id>
```

**同步模式:**
```
Request ID: <request-id>
Sender: <my-agent>
Target Agent: <target-agent>
Message: <message>
Response: <response>
```