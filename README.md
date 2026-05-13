# AgentCommProxy

Agent 通信代理 CLI 工具，支持 Agent 之间的同步/异步消息传递。

## 功能特性

- **同步消息**: 发送消息并等待响应
- **异步消息**: 发送消息后立即返回，后台处理并回调
- **HTTP API**: 提供 REST API 接口，支持第三方系统集成
- **消息持久化**: SQLite 存储所有请求记录
- **重试机制**: 执行失败和回调失败分别重试
- **守护进程**: 后台线程池处理异步消息
- **状态追踪**: 完整的消息状态流转

## 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                      CliRunner (入口)                        │
│                                                              │
│   ┌──────────────┐              ┌──────────────┐            │
│   │ CLI Handler  │              │ HTTP Handler │            │
│   │  (Picocli)   │              │ (HttpServer) │            │
│   └──────────────┘              └──────────────┘            │
│          │                              │                    │
│          └──────────────┬───────────────┘                    │
│                         │                                    │
│                         ▼                                    │
┌─────────────────────────────────────────────────────────────┐
│                      Service Layer                           │
│                     AgentService                             │
│         sendSync() │ sendAsync() │ processCallback()        │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      Proxy Layer                             │
│              CommandProxy / OpenClawProxy                    │
│                  执行外部命令                                 │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      Store Layer                             │
│                     SQLiteStore                              │
│                  消息持久化存储                               │
└─────────────────────────────────────────────────────────────┘
```

## 核心命令

```bash
# 发送消息（同步模式）
agentproxy agent --agent <target> --sender <my-agent> --message "hello" --sync

# 发送消息（异步模式）
agentproxy agent --agent <target> --sender <my-agent> --message "hello"

# 查询消息状态
agentproxy status --request-id <id>

# 查看消息列表
agentproxy list --limit 10 --status PENDING

# 启动守护进程
agentproxy daemon start

# 启动守护进程（指定 HTTP 端口）
agentproxy daemon start --http-port 8080

# 启动/停止 HTTP 服务
agentproxy http start
agentproxy http stop

# 清理历史记录
agentproxy clear --days 7
```

## HTTP API

| 端点 | 方法 | 说明 |
|-----|------|------|
| `/api/v1/send` | POST | 发送消息（同步/异步） |
| `/api/v1/status/{id}` | GET | 查询消息状态 |
| `/api/v1/list` | GET | 查询消息列表 |
| `/api/v1/health` | GET | 健康检查 |

认证方式：请求头 `X-API-Key`

### 发送消息示例

```bash
# 同步发送
curl -X POST http://localhost:8080/api/v1/send \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d '{"sender":"agent1","targetAgent":"agent2","message":"hello","sync":true}'

# 异步发送（带回调）
curl -X POST http://localhost:8080/api/v1/send \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d '{"sender":"agent1","targetAgent":"agent2","message":"hello","callbackUrl":"http://callback.url"}'

# 异步发送（轮询模式）
curl -X POST http://localhost:8080/api/v1/send \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d '{"sender":"agent1","targetAgent":"agent2","message":"hello"}'
```

## 回调模式

| SenderType | 说明 | 回调方式 |
|------------|------|---------|
| CLI | CLI 命令发起 | 执行 proxy.execute() |
| HTTP_CALLBACK | HTTP + callbackUrl | POST 回调地址 |
| HTTP_POLL | HTTP 无 callbackUrl | 无回调，调用方轮询 |

## Proxy 类型

| ProxyType | 说明 | 实现方式 |
|-----------|------|---------|
| OPENCLAW | OpenClaw CLI 命令 | 执行 openclaw agent 命令 |
| HTTP | HTTP API 调用 | 发送 HTTP POST 请求 |
| WEBSOCKET | WebSocket 通信 | WebSocket 连接通信 |
| CUSTOM | 自定义命令 | 自定义实现 |

## 消息状态流转

### 同步模式

```
PENDING → EXECUTING → DONE/ERROR
```

### 异步模式

```
PENDING → EXECUTING → EXECUTE_SUCCESS → CALLBACKING → DONE
                   ↘ EXECUTE_FAILED   ↘ CALLBACK_FAILED
                   ↘ EXECUTE_TIMEOUT
                   ↘ ERROR (重试耗尽)
```

## 配置文件

配置文件位置: `~/.agentcommproxy/config.properties`

```properties
# 默认超时时间（秒）
default.timeout=300

# 异步重试次数
async.retry.count=3

# 重试间隔（秒）
async.retry.interval=60

# 守护线程池大小
daemon.thread.pool.size=5

# 守护扫描间隔（秒）
daemon.scan.interval=5

# HTTP 服务配置
http.enabled=true
http.port=8080
http.api.key=your-api-key
```

## 部署说明

### 环境要求

- JDK 17+
- Maven 3.6+ (构建时)

### 构建

```bash
mvn clean package
```

构建产物位于 `target/agentcommproxy-1.0.0.jar`

### Linux 部署

```bash
# 1. 复制文件到目标机器
scp target/agentcommproxy-1.0.0.jar user@server:/tmp/
scp install.sh user@server:/tmp/

# 2. 安装
ssh user@server
sudo /tmp/install.sh

# 3. 验证
agentproxy --help

# 4. 启动守护进程
/opt/agentproxy/daemon.sh start
```

### Windows 部署

1. 将 `target/agentcommproxy-1.0.0.jar` 和 `install.bat` 复制到同一目录
2. 右键 `install.bat`，选择"以管理员身份运行"
3. 重新打开命令提示符
4. 运行 `agentproxy --help` 验证安装

### 手动部署

```bash
# 直接运行 JAR
java -jar agentcommproxy-1.0.0.jar --help

# 创建别名（Linux）
alias agentproxy='java -jar /path/to/agentcommproxy-1.0.0.jar'

# 创建批处理（Windows）
echo @java -jar "C:\path\to\agentcommproxy-1.0.0.jar" %* > agentproxy.bat
```

## 目录结构

```
src/main/java/org/openclaw/agentcommproxy/
├── CliRunner.java              # 入口类
├── command/                    # CLI 命令层
│   ├── AgentCommand.java       # agent 命令
│   ├── ListCommand.java        # list 命令
│   ├── StatusCommand.java      # status 命令
│   ├── ClearCommand.java       # clear 命令
│   ├── DaemonCommand.java      # daemon 命令
│   └── HttpCommand.java        # http 命令
├── callback/                   # 回调处理层
│   ├── CallbackHandler.java    # 回调处理器接口
│   ├── CallbackHandlerFactory.java # 工厂类
│   ├── CliCallbackHandler.java # CLI 回调实现
│   ├── HttpCallbackHandler.java # HTTP 回调实现
│   ├── NoopCallbackHandler.java # 轮询模式实现
│   └── CallbackPayload.java    # 回调数据载体
├── config/
│   └── ConfigManager.java      # 配置管理
├── daemon/
│   └── DaemonManager.java      # 守护进程管理
├── http/                       # HTTP API 层
│   ├── HttpServerManager.java  # HTTP 服务管理
│   └── dto/                    # 数据传输对象
│       ├── SendRequest.java
│       ├── SendResponse.java
│       ├── StatusResponse.java
│       ├── ListResponse.java
│       ├── HealthResponse.java
│       └── ErrorResponse.java
├── model/
│   ├── AgentRequest.java       # 请求实体
│   ├── AgentResponse.java      # 响应实体
│   ├── MessageStatus.java      # 状态枚举
│   ├── SenderType.java         # 请求方类型枚举
│   └── ProxyType.java          # Proxy 类型枚举
├── proxy/
│   ├── CommandProxy.java       # 命令代理接口
│   ├── CommandProxyFactory.java # Proxy 工厂
│   ├── CommandResult.java      # 执行结果
│   └── OpenClawProxy.java      # OpenClaw 实现
├── service/
│   └── AgentService.java       # 核心业务逻辑
└── store/
    └── SQLiteStore.java        # SQLite 存储
```

## 技术栈

| 技术 | 版本 | 用途 |
|-----|------|------|
| Picocli | 4.7.5 | CLI 框架 |
| SQLite JDBC | 3.45.1.0 | 数据库驱动 |
| Jackson | 2.16.1 | JSON 处理 |
| SLF4J + Logback | - | 日志系统 |
| Maven Shade Plugin | - | 构建打包 |

## 许可证

MIT License