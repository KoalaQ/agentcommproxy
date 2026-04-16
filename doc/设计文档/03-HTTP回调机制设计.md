# HTTP 回调机制设计

## 1. 问题背景

当前异步回调机制：
- HTTP API 发送异步请求 → 返回 request-id
- Daemon 执行完成后 → 调用 `proxy.execute(sender, callbackMessage)` 回调
- `proxy.execute()` 执行 CLI 命令方式回调

**问题**：HTTP API 调用方无法接收 CLI 方式的回调，需要支持 HTTP 回调。

## 2. 架构设计

### 2.1 SenderType 枚举

定义请求方类型：

```java
public enum SenderType {
    CLI,              // CLI 命令发起，使用 CLI 回调
    HTTP_CALLBACK,    // HTTP API 发起，提供回调地址
    HTTP_POLL         // HTTP API 发起，轮询模式（无回调地址）
}
```

### 2.2 策略模式架构

使用策略模式处理不同回调类型：

```
┌─────────────────────────────────────────────────────────────┐
│                    CallbackHandlerFactory                    │
│                  (工厂类，根据 SenderType 创建 Handler)        │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    CallbackHandler (接口)                     │
│                       doCallback(request)                    │
└─────────────────────────────────────────────────────────────┘
          │                    │                    │
          ▼                    ▼                    ▼
┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
│  CliCallbackHandler │ │HttpCallbackHandler │ │ NoopCallbackHandler │
│   (CLI 回调实现)    │ │ (HTTP 回调实现)    │ │  (轮询模式，无回调)  │
└──────────────────┘ └──────────────────┘ └──────────────────┘
```

### 2.3 目录结构

```
src/main/java/org/openclaw/agentcommproxy/
├── callback/                     # 新增回调模块
│   ├── CallbackHandler.java      # 回调处理器接口
│   ├── CallbackHandlerFactory.java # 工厂类
│   ├── CliCallbackHandler.java   # CLI 回调实现
│   ├── HttpCallbackHandler.java  # HTTP 回调实现
│   └── NoopCallbackHandler.java  # 轮询模式（无回调）
│   └── CallbackPayload.java      # 回调数据载体
├── model/
│   ├── SenderType.java           # 新增：请求方类型枚举
│   └── AgentRequest.java         # 修改：新增 senderType, callbackUrl
```

## 3. 核心类设计

### 3.1 SenderType 枚举

```java
package org.openclaw.agentcommproxy.model;

public enum SenderType {
    CLI,              // CLI 命令发起
    HTTP_CALLBACK,    // HTTP API + 回调地址
    HTTP_POLL         // HTTP API + 轮询模式
}
```

### 3.2 CallbackPayload 回调数据

```java
package org.openclaw.agentcommproxy.callback;

public class CallbackPayload {
    private String requestId;
    private String status;         // completed / failed
    private String sender;
    private String targetAgent;
    private String message;
    private String response;
    private String error;
    private long timestamp;
    
    // getters/setters
}
```

### 3.3 CallbackHandler 接口

```java
package org.openclaw.agentcommproxy.callback;

import org.openclaw.agentcommproxy.model.AgentRequest;

public interface CallbackHandler {
    /**
     * 执行回调
     * @return true 成功, false 失败
     */
    boolean doCallback(AgentRequest request);
    
    /**
     * 获取处理器类型
     */
    SenderType getHandlerType();
}
```

### 3.4 CliCallbackHandler 实现

```java
package org.openclaw.agentcommproxy.callback;

public class CliCallbackHandler implements CallbackHandler {
    private final CommandProxy proxy;
    private final ConfigManager configManager;
    
    @Override
    public boolean doCallback(AgentRequest request) {
        // 构建回调消息（原有 CLI 格式）
        String callbackMessage = buildCliCallbackMessage(request);
        
        CommandResult result = proxy.execute(
            request.getSender(), 
            callbackMessage, 
            configManager.getDefaultTimeout()
        );
        
        return result.isSuccess();
    }
    
    @Override
    public SenderType getHandlerType() {
        return SenderType.CLI;
    }
    
    private String buildCliCallbackMessage(AgentRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Request ID: ").append(request.getId()).append("\n");
        sb.append("Sender: ").append(request.getSender()).append("\n");
        sb.append("Target Agent: ").append(request.getTargetAgent()).append("\n");
        sb.append("Message: ").append(request.getMessage()).append("\n");
        if (request.getResponse() != null) {
            sb.append("Response: ").append(request.getResponse());
        }
        return sb.toString();
    }
}
```

### 3.5 HttpCallbackHandler 实现

```java
package org.openclaw.agentcommproxy.callback;

public class HttpCallbackHandler implements CallbackHandler {
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public boolean doCallback(AgentRequest request) {
        String callbackUrl = request.getCallbackUrl();
        if (callbackUrl == null || callbackUrl.isEmpty()) {
            return false;
        }
        
        try {
            CallbackPayload payload = buildPayload(request);
            String body = objectMapper.writeValueAsString(payload);
            
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(callbackUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            
            HttpResponse<String> response = client.send(
                httpRequest, 
                HttpResponse.BodyHandlers.ofString()
            );
            
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            log.warn("HTTP callback failed: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public SenderType getHandlerType() {
        return SenderType.HTTP_CALLBACK;
    }
    
    private CallbackPayload buildPayload(AgentRequest request) {
        CallbackPayload payload = new CallbackPayload();
        payload.setRequestId(request.getId());
        payload.setStatus(request.getStatus() == MessageStatus.ERROR ? "failed" : "completed");
        payload.setSender(request.getSender());
        payload.setTargetAgent(request.getTargetAgent());
        payload.setMessage(request.getMessage());
        payload.setResponse(request.getResponse());
        payload.setError(request.getError());
        payload.setTimestamp(Instant.now().toEpochMilli());
        return payload;
    }
}
```

### 3.6 NoopCallbackHandler 实现（轮询模式）

```java
package org.openclaw.agentcommproxy.callback;

public class NoopCallbackHandler implements CallbackHandler {
    
    @Override
    public boolean doCallback(AgentRequest request) {
        // 轮询模式无需回调，直接返回成功
        // 调用方通过 /status/{id} 主动查询结果
        return true;
    }
    
    @Override
    public SenderType getHandlerType() {
        return SenderType.HTTP_POLL;
    }
}
```

### 3.7 CallbackHandlerFactory 工厂类

```java
package org.openclaw.agentcommproxy.callback;

public class CallbackHandlerFactory {
    private final Map<SenderType, CallbackHandler> handlers = new HashMap<>();
    
    public CallbackHandlerFactory(ConfigManager configManager) {
        // 注册所有处理器
        handlers.put(SenderType.CLI, new CliCallbackHandler(configManager));
        handlers.put(SenderType.HTTP_CALLBACK, new HttpCallbackHandler());
        handlers.put(SenderType.HTTP_POLL, new NoopCallbackHandler());
    }
    
    /**
     * 根据请求获取对应的回调处理器
     */
    public CallbackHandler getHandler(AgentRequest request) {
        SenderType senderType = request.getSenderType();
        if (senderType == null) {
            // 默认使用 CLI 回调（兼容旧数据）
            senderType = SenderType.CLI;
        }
        return handlers.get(senderType);
    }
    
    /**
     * 根据 SenderType 获取处理器
     */
    public CallbackHandler getHandler(SenderType senderType) {
        return handlers.get(senderType);
    }
}
```

### 3.8 AgentService 修改

使用策略模式替换原有 if 分支：

```java
public class AgentService {
    private final CallbackHandlerFactory callbackHandlerFactory;
    
    public AgentService(ConfigManager configManager, SQLiteStore store) {
        this.configManager = configManager;
        this.store = store;
        this.proxy = CommandProxyFactory.getDefaultProxy();
        this.callbackHandlerFactory = new CallbackHandlerFactory(configManager);
    }
    
    /**
     * 处理回调（策略模式）
     */
    public void processCallback(AgentRequest request) {
        log.info("Callback request: {} to sender: {} via {}", 
            request.getId(), request.getSender(), request.getSenderType());
        
        CallbackHandler handler = callbackHandlerFactory.getHandler(request);
        boolean success = handler.doCallback(request);
        
        if (success) {
            store.updateRequestStatus(request.getId(), MessageStatus.DONE, request.getResponse(), null);
            log.info("Callback success: {}", request.getId());
        } else {
            handleCallbackFailure(request);
        }
    }
}
```

### 3.9 AgentRequest 扩展

```java
public class AgentRequest {
    // 现有字段...
    
    private SenderType senderType;      // 新增：请求方类型
    private String callbackUrl;         // 新增：HTTP 回调地址
    
    // getters/setters
}
```

## 4. SenderType 自动判断

根据请求来源自动设置 SenderType：

| 来源 | callbackUrl | SenderType |
|-----|-------------|------------|
| CLI 命令 | - | CLI |
| HTTP API | 有值 | HTTP_CALLBACK |
| HTTP API | 无值 | HTTP_POLL |

### 4.1 CLI 发送时

```java
// AgentCommand.java
AgentRequest request = new AgentRequest();
request.setSenderType(SenderType.CLI);  // CLI 命令固定为 CLI 类型
```

### 4.2 HTTP API 发送时

```java
// HttpServerManager.java handleSend()
SendRequest sendRequest = objectMapper.readValue(is, SendRequest.class);

AgentRequest request = new AgentRequest();
request.setSender(sendRequest.getSender());
// ...

if (sendRequest.getCallbackUrl() != null && !sendRequest.getCallbackUrl().isEmpty()) {
    request.setSenderType(SenderType.HTTP_CALLBACK);
    request.setCallbackUrl(sendRequest.getCallbackUrl());
} else {
    request.setSenderType(SenderType.HTTP_POLL);
}
```

## 5. 数据库扩展

### 5.1 表结构变更

```sql
-- requests 表新增字段
ALTER TABLE requests ADD COLUMN sender_type TEXT DEFAULT 'CLI';
ALTER TABLE requests ADD COLUMN callback_url TEXT;
```

### 5.2 SQLiteStore 扩展

```java
// 保存请求时记录 sender_type 和 callback_url
public void saveRequest(AgentRequest request) {
    String sql = "INSERT INTO requests (id, sender, target_agent, message, status, sync, timeout, sender_type, callback_url, ...) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ...)";
    // 新增 sender_type, callback_url 参数
}

// 查询时读取 sender_type 和 callback_url
public Optional<AgentRequest> getRequestById(String id) {
    // SELECT sender_type, callback_url FROM requests WHERE id = ?
    // 设置到 AgentRequest 对象
}
```

## 6. HTTP API 扩展

### 6.1 SendRequest 扩展

```json
{
  "agent": "AgentB",
  "sender": "AgentA",
  "message": "hello",
  "sync": false,
  "callbackUrl": "http://your-server/api/callback"  // 可选
}
```

### 6.2 回调 Payload（HTTP_CALLBACK 模式）

执行完成后 POST 到 callbackUrl：

```json
{
  "requestId": "abc-123-def-456",
  "status": "completed",
  "sender": "AgentA",
  "targetAgent": "AgentB",
  "message": "hello",
  "response": "Hello back!",
  "error": null,
  "timestamp": 1713812345678
}
```

## 7. 实现步骤

### Phase 1: 基础框架

1. 创建 `SenderType.java` 枚举
2. 创建 `CallbackPayload.java` 数据载体
3. 创建 `CallbackHandler.java` 接口

### Phase 2: 回调处理器实现

1. 创建 `CliCallbackHandler.java`
2. 创建 `HttpCallbackHandler.java`
3. 创建 `NoopCallbackHandler.java`
4. 创建 `CallbackHandlerFactory.java`

### Phase 3: 数据模型扩展

1. `AgentRequest.java` 新增 senderType, callbackUrl
2. `SQLiteStore.java` 新增字段处理
3. 数据库表结构变更

### Phase 4: 服务层集成

1. `AgentService.java` 使用策略模式
2. `AgentCommand.java` 设置 SenderType.CLI
3. `HttpServerManager.java` 根据参数判断 SenderType

### Phase 5: 文档更新

1. 更新 `doc/04-HTTP-API说明.md`
2. 更新 `doc/01-项目架构.md`

## 8. 文件清单

| 文件 | 操作 | 说明 |
|-----|------|-----|
| `callback/CallbackHandler.java` | 新增 | 回调处理器接口 |
| `callback/CallbackHandlerFactory.java` | 新增 | 工厂类 |
| `callback/CliCallbackHandler.java` | 新增 | CLI 回调实现 |
| `callback/HttpCallbackHandler.java` | 新增 | HTTP 回调实现 |
| `callback/NoopCallbackHandler.java` | 新增 | 轮询模式实现 |
| `callback/CallbackPayload.java` | 新增 | 回调数据载体 |
| `model/SenderType.java` | 新增 | 请求方类型枚举 |
| `model/AgentRequest.java` | 修改 | 新增 senderType, callbackUrl |
| `store/SQLiteStore.java` | 修改 | 新增字段处理 |
| `service/AgentService.java` | 修改 | 使用策略模式 |
| `command/AgentCommand.java` | 修改 | 设置 SenderType |
| `http/HttpServerManager.java` | 修改 | 判断 SenderType |
| `http/dto/SendRequest.java` | 修改 | 新增 callbackUrl 参数 |

## 9. 优势

**策略模式 vs if 分支：**

| 对比项 | if 分支 | 策略模式 |
|-------|--------|---------|
| 扩展性 | 新增类型需改 Service | 新增 Handler 类即可 |
| 可维护性 | 逻辑混杂在一处 | 各类型独立类 |
| 可测试性 | 需 mock 全部逻辑 | 单元测试各 Handler |
| 代码清晰度 | if/else 嵌套 | 工厂+接口，结构清晰 |

**扩展场景示例：**

未来新增 WebSocket 回调：
```java
// 只需新增一个类，无需修改 AgentService
public class WebSocketCallbackHandler implements CallbackHandler {
    @Override
    public boolean doCallback(AgentRequest request) {
        // WebSocket 推送逻辑
    }
    
    @Override
    public SenderType getHandlerType() {
        return SenderType.WEBSOCKET;  // 新增枚举值
    }
}

// 工厂类注册
handlers.put(SenderType.WEBSOCKET, new WebSocketCallbackHandler());
```