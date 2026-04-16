# HTTP 入口设计文档

## 1. 背景

现有 AgentCommProxy 仅支持 CLI 入口，需要增加 HTTP API 入口以支持：
- 远程调用
- 与其他系统集成
- Web 前端对接

## 2. 设计目标

- **共存模式**: CLI 和 HTTP 同时运行，一个进程同时开放两种入口
- **零依赖**: 使用 JDK 内置 `com.sun.net.httpserver.HttpServer`
- **简单认证**: API Key 请求头验证
- **复用现有服务**: 直接调用 AgentService，不重复实现业务逻辑

## 3. 架构设计

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
│   ┌─────────────────────────────────────────────────────────┐│
│   │                    AgentService                         ││
│   │         sendSync() │ sendAsync() │ processCallback()   ││
│   └─────────────────────────────────────────────────────────┘│
│                         │                                    │
│                         ▼                                    │
│   ┌─────────────────────────────────────────────────────────┐│
│   │              SQLiteStore + DaemonManager                ││
│   └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

## 4. 新增模块

### 4.1 目录结构

```
src/main/java/org/openclaw/agentcommproxy/
├── http/                       # 新增 HTTP 模块
│   ├── HttpServerManager.java  # HTTP 服务管理器
│   ├── HttpHandler.java        # 请求处理器
│   ├── ApiKeyFilter.java       # API Key 认证过滤器
│   └── dto/                    # 数据传输对象
│       ├── SendRequest.java    # 发送消息请求 DTO
│       ├── SendResponse.java   # 发送消息响应 DTO
│       └── StatusResponse.java # 状态查询响应 DTO
├── CliRunner.java              # 修改：启动 HTTP 服务
└── command/
    └── HttpCommand.java        # 新增：HTTP 相关 CLI 命令
```

### 4.2 核心类设计

#### HttpServerManager

```java
public class HttpServerManager {
    private HttpServer server;
    private int port;
    private String apiKey;
    private AgentService agentService;
    private SQLiteStore store;

    // 启动 HTTP 服务
    public void start(int port, String apiKey);

    // 停止 HTTP 服务
    public void stop();

    // 检查运行状态
    public boolean isRunning();
}
```

#### HttpHandler

路由分发处理器，处理以下端点：

| 端点 | 方法 | 功能 |
|-----|------|-----|
| `/api/v1/send` | POST | 发送消息（同步/异步） |
| `/api/v1/status/{id}` | GET | 查询消息状态 |
| `/api/v1/list` | GET | 查询消息列表 |
| `/api/v1/health` | GET | 健康检查 |

#### ApiKeyFilter

请求头认证：`X-API-Key: <api-key>`

- 验证失败返回 401 Unauthorized
- `/api/v1/health` 端点无需认证

### 4.3 DTO 设计

#### SendRequest (POST /api/v1/send)

```json
{
  "agent": "AgentB",
  "sender": "AgentA",
  "message": "hello",
  "sync": false,
  "timeout": 300
}
```

#### SendResponse (同步模式)

```json
{
  "requestId": "abc-123",
  "status": "DONE",
  "response": "Hello back!",
  "error": null
}
```

#### SendResponse (异步模式)

```json
{
  "requestId": "abc-123",
  "status": "PENDING",
  "message": "已收到、稍后回复您"
}
```

#### StatusResponse (GET /api/v1/status/{id})

```json
{
  "requestId": "abc-123",
  "status": "DONE",
  "sender": "AgentA",
  "targetAgent": "AgentB",
  "message": "hello",
  "response": "Hello back!",
  "error": null,
  "executeRetryCount": 0,
  "callbackRetryCount": 0
}
```

## 5. API 端点详细设计

### 5.1 发送消息

**POST /api/v1/send**

请求头：
- `Content-Type: application/json`
- `X-API-Key: <api-key>` (必需)

请求体：
```json
{
  "agent": "AgentB",       // 必需：目标 Agent
  "sender": "AgentA",      // 必需：发送方 Agent
  "message": "hello",      // 必需：消息内容
  "sync": false,           // 可选：是否同步，默认 false
  "timeout": 300           // 可选：超时秒数，默认 300
}
```

响应：
- 200: 成功
- 400: 参数错误
- 401: API Key 无效
- 500: 内部错误

### 5.2 查询状态

**GET /api/v1/status/{requestId}**

请求头：
- `X-API-Key: <api-key>` (必需)

响应：
- 200: 返回状态详情
- 404: 请求不存在
- 401: API Key 无效

### 5.3 查询列表

**GET /api/v1/list**

请求头：
- `X-API-Key: <api-key>` (必需)

查询参数：
- `limit`: 返回数量，默认 10
- `status`: 状态筛选
- `full`: 是否完整内容

响应：
- 200: 返回消息列表
- 401: API Key 无效

### 5.4 健康检查

**GET /api/v1/health**

无需认证，返回服务状态。

响应：
```json
{
  "status": "healthy",
  "daemonRunning": true,
  "httpPort": 8080
}
```

## 6. 配置扩展

新增配置项（`~/.agentcommproxy/config.properties`）：

```properties
# HTTP 服务配置
http.enabled=true
http.port=8080
http.api.key=your-api-key-here
```

| 配置项 | 说明 | 默认值 |
|-------|------|-------|
| http.enabled | 是否启用 HTTP | true |
| http.port | HTTP 端口 | 8080 |
| http.api.key | API Key | 自动生成 UUID |

## 7. CLI 集成

### 7.1 启动方式

**方式一：同时启动 CLI + HTTP**

```bash
# 默认启动（HTTP 和 CLI 都可用）
agentproxy

# 指定 HTTP 端口
agentproxy --http-port 9090

# 禁用 HTTP
agentproxy --no-http
```

**方式二：HTTP 服务管理命令**

```bash
# 查看 HTTP 状态
agentproxy http status

# 启动 HTTP 服务（如果已停止）
agentproxy http start --port 8080

# 停止 HTTP 服务
agentproxy http stop

# 生成新 API Key
agentproxy http gen-key
```

### 7.2 CliRunner 修改

启动时自动启动 HTTP 服务（如果 enabled）：

```java
public static void main(String[] args) {
    // 初始化服务
    ConfigManager config = new ConfigManager();
    SQLiteStore store = new SQLiteStore(config);
    AgentService service = new AgentService(config, store);
    DaemonManager daemon = DaemonManager.getInstance();

    // 启动守护进程
    if (config.isDaemonEnabled()) {
        daemon.start(null, false);
    }

    // 启动 HTTP 服务
    if (config.isHttpEnabled()) {
        HttpServerManager http = HttpServerManager.getInstance();
        http.start(config.getHttpPort(), config.getApiKey(), service, store);
    }

    // 解析 CLI 命令
    int exitCode = new CommandLine(new CliRunner()).execute(args);

    // 如果是 usage 显示，不退出
    // ...
}
```

## 8. 实现步骤

### Phase 1: HTTP 基础框架

1. 创建 `HttpServerManager.java`
2. 创建 `ApiKeyFilter.java`
3. 创建 DTO 类（`SendRequest`, `SendResponse`, `StatusResponse`）

### Phase 2: API 端点实现

1. 实现 `/api/v1/send` 端点
2. 实现 `/api/v1/status/{id}` 端点
3. 实现 `/api/v1/list` 端点
4. 实现 `/api/v1/health` 端点

### Phase 3: CLI 集成

1. 扩展 `ConfigManager` 支持 HTTP 配置
2. 修改 `CliRunner` 启动逻辑
3. 创建 `HttpCommand.java` 管理 HTTP 服务

### Phase 4: 文档更新

1. 更新 `doc/01-项目架构.md`
2. 更新 `doc/02-CLI命令说明.md`
3. 新增 `doc/03-HTTP-API说明.md`

## 9. 验证方案

### 功能测试

```bash
# 1. 启动服务
agentproxy --http-port 8080

# 2. 测试健康检查（无需认证）
curl http://localhost:8080/api/v1/health

# 3. 测试异步发送
curl -X POST http://localhost:8080/api/v1/send \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d '{"agent":"AgentB","sender":"AgentA","message":"hello"}'

# 4. 测试同步发送
curl -X POST http://localhost:8080/api/v1/send \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d '{"agent":"AgentB","sender":"AgentA","message":"hello","sync":true}'

# 5. 测试状态查询
curl http://localhost:8080/api/v1/status/abc-123 \
  -H "X-API-Key: your-api-key"

# 6. 测试认证失败
curl http://localhost:8080/api/v1/status/abc-123 \
  -H "X-API-Key: wrong-key"
# 应返回 401
```

### CLI 测试

```bash
# CLI 和 HTTP 同时可用
agentproxy agent --agent AgentB --sender AgentA --message "cli test"
agentproxy http status
agentproxy http stop
agentproxy http start --port 9090
```

## 10. 文件清单

| 文件 | 操作 | 说明 |
|-----|------|-----|
| `http/HttpServerManager.java` | 新增 | HTTP 服务管理 |
| `http/HttpHandler.java` | 新增 | 请求处理器 |
| `http/ApiKeyFilter.java` | 新增 | API Key 认证 |
| `http/dto/SendRequest.java` | 新增 | 发送请求 DTO |
| `http/dto/SendResponse.java` | 新增 | 发送响应 DTO |
| `http/dto/StatusResponse.java` | 新增 | 状态响应 DTO |
| `http/dto/ListResponse.java` | 新增 | 列表响应 DTO |
| `config/ConfigManager.java` | 修改 | 添加 HTTP 配置项 |
| `CliRunner.java` | 修改 | 启动 HTTP 服务 |
| `command/HttpCommand.java` | 新增 | HTTP 管理 CLI 命令 |
| `doc/01-项目架构.md` | 更新 | 添加 HTTP 层说明 |
| `doc/02-CLI命令说明.md` | 更新 | 添加 HTTP 命令说明 |
| `doc/03-HTTP-API说明.md` | 新增 | HTTP API 文档 |