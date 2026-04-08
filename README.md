# AgentCommProxy

Agent 通信代理 CLI 工具，支持 Agent 之间的同步/异步消息传递。

## 功能特性

- **同步消息**: 发送消息并等待响应
- **异步消息**: 发送消息后立即返回，后台处理并回调
- **消息持久化**: SQLite 存储所有请求记录
- **重试机制**: 执行失败和回调失败分别重试
- **守护进程**: 后台线程池处理异步消息
- **状态追踪**: 完整的消息状态流转

## 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                      CLI Layer (Picocli)                     │
│  AgentCommand │ ListCommand │ StatusCommand │ DaemonCommand │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
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

# 清理历史记录
agentproxy clear --days 7
```

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
```

## 异步回调消息格式

当异步消息执行完成后，会向发送方 Agent 发送回调消息：

```
Request ID: <request-id>
Sender: <sender>
Target Agent: <target-agent>
Message: <original-message>
Response: <response>
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

## 技术栈

- **CLI 框架**: Picocli 4.7.5
- **数据库**: SQLite (sqlite-jdbc 3.45.1.0)
- **JSON 处理**: Jackson 2.16.1
- **日志**: SLF4J + Logback
- **构建**: Maven Shade Plugin

## 目录结构

```
src/main/java/org/openclaw/agentcommproxy/
├── CliRunner.java              # 入口
├── command/
│   ├── AgentCommand.java       # agent 命令
│   ├── ListCommand.java        # list 命令
│   ├── StatusCommand.java      # status 命令
│   ├── ClearCommand.java       # clear 命令
│   └── DaemonCommand.java      # daemon 命令
├── config/
│   └── ConfigManager.java      # 配置管理
├── daemon/
│   └── DaemonManager.java      # 守护进程
├── model/
│   ├── AgentRequest.java       # 请求实体
│   ├── AgentResponse.java      # 响应实体
│   └── MessageStatus.java      # 状态枚举
├── proxy/
│   ├── CommandProxy.java       # 命令代理接口
│   ├── CommandProxyFactory.java
│   ├── CommandResult.java
│   └── OpenClawProxy.java      # OpenClaw 实现
├── service/
│   └── AgentService.java       # 核心业务逻辑
└── store/
    └── SQLiteStore.java        # SQLite 存储
```

## 许可证

MIT License