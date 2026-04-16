# HTTP 接口说明

## 概述

AgentCommProxy 提供 HTTP API 入口，支持远程调用和系统集成。

- **基础 URL**: `http://localhost:8080/api/v1`
- **认证方式**: 请求头 `X-API-Key`
- **数据格式**: JSON

## 认证

所有请求接口（除健康检查外）需要携带 API Key：

```
X-API-Key: <your-api-key>
```

API Key 获取方式：
- 配置文件 `~/.agentcommproxy/config.properties` 中的 `http.api.key`
- CLI 命令 `agentproxy http status`

---

## 一、请求接口

### 1.1 发送消息

**POST /api/v1/send**

发送消息到目标 Agent。

#### 请求头

| 头部 | 必需 | 说明 |
|-----|------|-----|
| Content-Type | 是 | application/json |
| X-API-Key | 是 | API Key |

#### 请求参数

| 参数 | 必需 | 类型 | 说明 | 默认值 |
|-----|------|-----|------|-------|
| agent | 是 | String | 目标 Agent 名称 | - |
| sender | 是 | String | 发送方 Agent 名称 | - |
| message | 是 | String | 消息内容 | - |
| sync | 否 | Boolean | 是否同步模式 | false |
| timeout | 否 | Integer | 超时秒数 | 300 |
| callbackUrl | 否 | String | 回调地址（异步模式） | - |
| proxy | 否 | String | Proxy 类型 | openclaw |

#### Proxy 类型

| 类型 | 说明 |
|-----|------|
| openclaw | OpenClaw CLI 命令执行（默认） |
| http | HTTP API 调用（未来支持） |
| websocket | WebSocket 通信（未来支持） |

#### 请求示例

```json
{
  "agent": "AgentB",
  "sender": "AgentA",
  "message": "hello",
  "sync": false,
  "timeout": 300,
  "callbackUrl": "http://your-server/api/callback"
}
```

#### 响应

**异步模式 (200):**

```json
{
  "requestId": "abc-123-def-456",
  "status": "PENDING",
  "message": "已收到、稍后回复您"
}
```

**同步模式成功 (200):**

```json
{
  "requestId": "abc-123-def-456",
  "status": "DONE",
  "response": "Hello back!"
}
```

**同步模式失败 (200):**

```json
{
  "requestId": "abc-123-def-456",
  "status": "ERROR",
  "error": "Connection timeout"
}
```

#### curl 示例

```bash
# 异步发送（轮询模式）
curl -X POST http://localhost:8080/api/v1/send \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d '{"agent":"AgentB","sender":"AgentA","message":"hello"}'

# 异步发送（回调模式）
curl -X POST http://localhost:8080/api/v1/send \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d '{"agent":"AgentB","sender":"AgentA","message":"hello","callbackUrl":"http://your-server/callback"}'

# 同步发送
curl -X POST http://localhost:8080/api/v1/send \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d '{"agent":"AgentB","sender":"AgentA","message":"hello","sync":true}'
```

### 1.2 查询状态

**GET /api/v1/status/{requestId}**

查询指定消息的状态。

#### 请求头

| 头部 | 必需 | 说明 |
|-----|------|-----|
| X-API-Key | 是 | API Key |

#### 路径参数

| 参数 | 说明 |
|-----|------|
| requestId | 请求唯一标识 |

#### 响应 (200)

```json
{
  "requestId": "abc-123-def-456",
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

#### curl 示例

```bash
curl http://localhost:8080/api/v1/status/abc-123-def-456 \
  -H "X-API-Key: your-api-key"
```

### 1.3 查询列表

**GET /api/v1/list**

查询消息列表。

#### 请求头

| 头部 | 必需 | 说明 |
|-----|------|-----|
| X-API-Key | 是 | API Key |

#### 查询参数

| 参数 | 说明 | 默认值 |
|-----|------|-------|
| limit | 返回记录数量 | 10 |
| status | 状态筛选 | 全部 |

#### 响应 (200)

```json
{
  "total": 2,
  "records": [
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
  ]
}
```

#### curl 示例

```bash
# 默认查询
curl http://localhost:8080/api/v1/list -H "X-API-Key: your-api-key"

# 指定数量
curl "http://localhost:8080/api/v1/list?limit=20" -H "X-API-Key: your-api-key"

# 按状态筛选
curl "http://localhost:8080/api/v1/list?status=DONE" -H "X-API-Key: your-api-key"
```

### 1.4 健康检查

**GET /api/v1/health**

检查服务状态。无需认证。

#### 响应 (200)

```json
{
  "status": "healthy",
  "daemonRunning": true,
  "httpPort": 8080
}
```

#### curl 示例

```bash
curl http://localhost:8080/api/v1/health
```

---

## 二、回调接口

当发送消息时提供 `callbackUrl`，执行完成后系统会 POST 回调到指定地址。

### 2.1 回调请求

**POST {callbackUrl}**

系统向调用方提供的回调地址发送请求。

#### 请求头

| 头部 | 说明 |
|-----|------|
| Content-Type | application/json |

#### 回调 Payload

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

#### Payload 字段说明

| 字段 | 类型 | 说明 |
|-----|------|-----|
| requestId | String | 原请求唯一标识 |
| status | String | completed / failed |
| sender | String | 发送方 Agent 名称 |
| targetAgent | String | 目标 Agent 名称 |
| message | String | 原消息内容 |
| response | String | 执行响应内容 |
| error | String | 错误信息（失败时有值） |
| timestamp | Long | 回调时间戳（毫秒） |

### 2.2 回调响应要求

调用方回调接口应返回：

| 状态码 | 说明 |
|-------|------|
| 200-299 | 回调成功 |
| 其他 | 回调失败，将进入重试队列 |

### 2.3 回调失败重试

回调失败时，系统会自动重试：

- 重试次数：默认 3 次（配置 `async.retry.count`）
- 重试间隔：默认 5 秒（配置 `async.retry.interval`）
- 达到最大重试次数后标记为 ERROR

### 2.4 回调接口实现示例

**Python (Flask):**

```python
from flask import Flask, request, jsonify

app = Flask(__name__)

@app.route('/api/callback', methods=['POST'])
def callback():
    data = request.json
    
    request_id = data.get('requestId')
    status = data.get('status')
    response = data.get('response')
    
    print(f"收到回调: {request_id} - {status}")
    print(f"响应内容: {response}")
    
    # 处理回调结果
    if status == 'completed':
        # 执行成功处理
        pass
    else:
        # 执行失败处理
        pass
    
    return jsonify({'success': True}), 200

if __name__ == '__main__':
    app.run(port=3000)
```

**Node.js (Express):**

```javascript
const express = require('express');
const app = express();

app.use(express.json());

app.post('/api/callback', (req, res) => {
    const { requestId, status, response, error } = req.body;
    
    console.log(`收到回调: ${requestId} - ${status}`);
    console.log(`响应内容: ${response}`);
    
    if (status === 'completed') {
        // 执行成功处理
    } else {
        // 执行失败处理
        console.error(`错误: ${error}`);
    }
    
    res.json({ success: true });
});

app.listen(3000);
```

---

## 三、异步模式对比

| 模式 | 参数 | 回调方式 | 适用场景 |
|-----|------|---------|---------|
| 回调模式 | 提供 callbackUrl | 系统主动 POST | 服务端对接 |
| 轮询模式 | 不提供 callbackUrl | 调用方主动查询 | 客户端/前端 |

**回调模式流程：**

```
调用方 → POST /send (带 callbackUrl) → 返回 requestId
                                            ↓
系统执行 → 执行完成 → POST callbackUrl → 调用方接收结果
```

**轮询模式流程：**

```
调用方 → POST /send (无 callbackUrl) → 返回 requestId
                                            ↓
调用方 → GET /status/{id} → 状态 PENDING → 继续轮询
                                            ↓
调用方 → GET /status/{id} → 状态 DONE → 获取结果
```

---

## 四、错误处理

### 4.1 错误响应格式

```json
{
  "error": "<error_code>",
  "message": "<error_message>"
}
```

### 4.2 错误码

| 错误码 | HTTP 状态码 | 说明 |
|-------|------------|------|
| unauthorized | 401 | API Key 无效或缺失 |
| bad_request | 400 | 参数缺失或无效 |
| not_found | 404 | 资源不存在 |
| method_not_allowed | 405 | HTTP 方法不支持 |
| internal_error | 500 | 内部服务器错误 |

---

## 五、消息状态

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
| ERROR | 错误（最终失败） |

---

## 六、集成示例

### 6.1 回调模式完整示例

```python
import requests

API_URL = "http://localhost:8080/api/v1"
API_KEY = "your-api-key"

headers = {
    "Content-Type": "application/json",
    "X-API-Key": API_KEY
}

# 发送异步请求（带回调地址）
response = requests.post(
    f"{API_URL}/send",
    headers=headers,
    json={
        "agent": "AgentB",
        "sender": "AgentA",
        "message": "hello",
        "callbackUrl": "http://your-server:3000/api/callback"
    }
)

print(f"请求已发送: {response.json()['requestId']}")
# 等待回调接口接收结果...
```

### 6.2 轮询模式完整示例

```python
import requests
import time

API_URL = "http://localhost:8080/api/v1"
API_KEY = "your-api-key"

headers = {
    "Content-Type": "application/json",
    "X-API-Key": API_KEY
}

# 发送异步请求（无回调地址）
response = requests.post(
    f"{API_URL}/send",
    headers=headers,
    json={
        "agent": "AgentB",
        "sender": "AgentA",
        "message": "hello"
    }
)

request_id = response.json()['requestId']
print(f"请求已发送: {request_id}")

# 轮询状态直到完成
while True:
    status_response = requests.get(
        f"{API_URL}/status/{request_id}",
        headers=headers
    )
    
    data = status_response.json()
    status = data['status']
    
    print(f"状态: {status}")
    
    if status == 'DONE':
        print(f"响应: {data['response']}")
        break
    elif status == 'ERROR':
        print(f"错误: {data['error']}")
        break
    
    time.sleep(2)  # 2秒后再次查询
```

### 6.3 同步模式示例

```python
import requests

API_URL = "http://localhost:8080/api/v1"
API_KEY = "your-api-key"

headers = {
    "Content-Type": "application/json",
    "X-API-Key": API_KEY
}

# 发送同步请求（等待响应）
response = requests.post(
    f"{API_URL}/send",
    headers=headers,
    json={
        "agent": "AgentB",
        "sender": "AgentA",
        "message": "hello",
        "sync": True,
        "timeout": 60
    }
)

data = response.json()
if data['status'] == 'DONE':
    print(f"响应: {data['response']}")
else:
    print(f"错误: {data['error']}")
```