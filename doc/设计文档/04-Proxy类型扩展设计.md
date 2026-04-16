# Proxy 类型扩展设计

## 1. 背景

当前 CommandProxy 层只支持 OpenClaw，未来需要支持更多命令类型（如 HTTP API、WebSocket、自定义命令等）。需要设计接口支持指定调用哪种 Proxy。

## 2. 现有架构

```
CommandProxy (接口)
     │
     ▼
OpenClawProxy (唯一实现)

CommandProxyFactory (工厂)
     │
     └── getDefaultProxy() → OpenClawProxy

AgentService
     │
     └── proxy = CommandProxyFactory.getDefaultProxy()
```

**问题**：AgentService 硬编码使用默认 proxy，无法指定类型。

## 3. 扩展设计

### 3.1 ProxyType 枚举

```java
package org.openclaw.agentcommproxy.model;

/**
 * Proxy 类型枚举
 */
public enum ProxyType {
    OPENCLAW("openclaw", "OpenClaw CLI 命令"),
    HTTP("http", "HTTP API 调用"),
    WEBSOCKET("websocket", "WebSocket 通信"),
    CUSTOM("custom", "自定义命令");

    private final String code;
    private final String description;

    ProxyType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static ProxyType fromCode(String code) {
        for (ProxyType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return OPENCLAW;  // 默认
    }
}
```

### 3.2 AgentRequest 扩展

新增 `proxyType` 字段：

```java
public class AgentRequest {
    // 现有字段...
    private ProxyType proxyType;  // 新增：Proxy 类型
}
```

### 3.3 CommandProxyFactory 扩展

```java
public class CommandProxyFactory {
    private static final Map<String, CommandProxy> proxies = new HashMap<>();

    static {
        registerProxy(new OpenClawProxy());
        // 未来注册更多
        // registerProxy(new HttpProxy());
        // registerProxy(new WebSocketProxy());
    }

    /**
     * 根据类型获取代理
     */
    public static CommandProxy getProxy(ProxyType type) {
        return getProxy(type.getCode());
    }

    /**
     * 获取默认代理
     */
    public static CommandProxy getDefaultProxy() {
        return getProxy(ProxyType.OPENCLAW);
    }
}
```

### 3.4 AgentService 修改

```java
public class AgentService {
    public void processExecute(AgentRequest request) {
        // 根据 request.getProxyType() 选择 proxy
        CommandProxy proxy = CommandProxyFactory.getProxy(request.getProxyType());
        CommandResult result = proxy.execute(...);
        // ...
    }

    public void processCallback(AgentRequest request) {
        // 回调也根据 ProxyType 选择
        CommandProxy proxy = CommandProxyFactory.getProxy(request.getProxyType());
        // ...
    }
}
```

### 3.5 CLI 命令扩展

```bash
# 新增 --proxy 参数
agentproxy agent --agent AgentB --sender AgentA --message "hello" --proxy openclaw
agentproxy agent --agent AgentB --sender AgentA --message "hello" --proxy http
```

```java
@Option(names = {"--proxy"}, description = "Proxy type (openclaw/http/websocket)")
private String proxyType;
```

### 3.6 HTTP API 扩展

```json
{
  "agent": "AgentB",
  "sender": "AgentA",
  "message": "hello",
  "proxy": "openclaw"  // 新增
}
```

### 3.7 SQLiteStore 扩展

数据库新增字段：

```sql
ALTER TABLE requests ADD COLUMN proxy_type TEXT DEFAULT 'openclaw';
```

## 4. 新增 Proxy 实现示例

### 4.1 HttpProxy 示例

```java
package org.openclaw.agentcommproxy.proxy;

public class HttpProxy implements CommandProxy {
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public String getName() {
        return "http";
    }

    @Override
    public CommandResult execute(String agent, String message, int timeout) {
        // 从配置获取目标 URL
        String targetUrl = configManager.getProxyHttpUrl(agent);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(timeout))
                .POST(HttpRequest.BodyPublishers.ofString(buildPayload(agent, message)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return CommandResult.success(response.body());
            } else {
                return CommandResult.failure("HTTP error: " + response.statusCode(), response.statusCode());
            }
        } catch (Exception e) {
            return CommandResult.failure(e.getMessage(), -1);
        }
    }

    private String buildPayload(String agent, String message) {
        return "{\"agent\":\"" + agent + "\",\"message\":\"" + message + "\"}";
    }
}
```

### 4.2 配置扩展

```properties
# Proxy 配置
proxy.default=openclaw

# HTTP Proxy 配置
proxy.http.baseUrl=http://api.example.com/agents
proxy.http.timeout=30

# OpenClaw 配置
proxy.openclaw.command=openclaw
```

## 5. 实现步骤

### Phase 1: 基础框架

1. 创建 `ProxyType.java` 枚举
2. 扩展 `CommandProxyFactory` 支持 ProxyType

### Phase 2: 数据模型扩展

1. `AgentRequest` 新增 proxyType 字段
2. `SQLiteStore` 新增 proxy_type 列
3. 数据库迁移

### Phase 3: 服务层修改

1. `AgentService` 根据 proxyType 选择 proxy
2. `AgentCommand` 新增 --proxy 参数
3. `HttpServerManager` 新增 proxy 字段

### Phase 4: 配置扩展

1. `ConfigManager` 新增 proxy 配置项
2. 支持默认 proxy 类型配置

## 6. 文件清单

| 文件 | 操作 | 说明 |
|-----|------|-----|
| `model/ProxyType.java` | 新增 | Proxy 类型枚举 |
| `model/AgentRequest.java` | 修改 | 新增 proxyType |
| `proxy/CommandProxyFactory.java` | 修改 | 支持 ProxyType |
| `store/SQLiteStore.java` | 修改 | 新增 proxy_type |
| `service/AgentService.java` | 修改 | 动态选择 proxy |
| `command/AgentCommand.java` | 修改 | 新增 --proxy |
| `http/dto/SendRequest.java` | 修改 | 新增 proxy |
| `http/HttpServerManager.java` | 修改 | 处理 proxy 参数 |
| `config/ConfigManager.java` | 修改 | proxy 配置项 |

## 7. 兼容性考虑

- 默认值：`proxyType = OPENCLAW`，兼容现有数据
- 未指定时：使用 `proxy.default` 配置
- CLI 不指定 --proxy：使用默认 proxy
- HTTP API 不指定 proxy：使用默认 proxy

## 8. 配置示例

```properties
# Proxy 配置
proxy.default=openclaw

# HTTP Proxy（未来支持）
proxy.http.baseUrl=http://api.example.com/agents
proxy.http.authToken=xxx

# WebSocket Proxy（未来支持）
proxy.websocket.url=ws://ws.example.com/agents
```

## 9. 使用示例

```bash
# CLI 指定 proxy
agentproxy agent --agent AgentB --sender AgentA --message "hello" --proxy openclaw

# HTTP API 指定 proxy
curl -X POST http://localhost:8080/api/v1/send \
  -d '{"agent":"AgentB","sender":"AgentA","message":"hello","proxy":"http"}'
```

## 10. 扩展流程

未来新增 Proxy 类型只需：

1. 实现 `CommandProxy` 接口
2. 在 `CommandProxyFactory` 注册
3. 在 `ProxyType` 枚举添加类型
4. 配置相关参数

无需修改 `AgentService` 核心逻辑。