package org.openclaw.agentcommproxy.model;

import java.time.Instant;

/**
 * 请求消息实体
 */
public class AgentRequest {
    private String id;                  // 请求唯一ID
    private String sender;              // 发送方 Agent
    private String targetAgent;         // 目标 Agent
    private String message;             // 消息内容
    private MessageStatus status;       // 状态
    private String response;            // 响应内容
    private String error;               // 错误信息
    private int executeRetryCount;      // 执行重试次数
    private int callbackRetryCount;     // 回调重试次数
    private boolean sync;               // 是否同步
    private int timeout;                // 超时时间（秒）
    private long createdAt;             // 创建时间戳
    private long updatedAt;             // 更新时间戳
    private SenderType senderType;      // 请求方类型（CLI/HTTP_CALLBACK/HTTP_POLL）
    private String callbackUrl;         // HTTP 回调地址
    private ProxyType proxyType;        // Proxy 类型（OPENCLAW/HTTP/WEBSOCKET/CUSTOM）

    public AgentRequest() {
        this.status = MessageStatus.PENDING;
        this.executeRetryCount = 0;
        this.callbackRetryCount = 0;
        this.sync = false;
        this.timeout = 300;
        this.createdAt = Instant.now().toEpochMilli();
        this.updatedAt = this.createdAt;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getTargetAgent() { return targetAgent; }
    public void setTargetAgent(String targetAgent) { this.targetAgent = targetAgent; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public MessageStatus getStatus() { return status; }
    public void setStatus(MessageStatus status) { this.status = status; }

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public int getExecuteRetryCount() { return executeRetryCount; }
    public void setExecuteRetryCount(int executeRetryCount) { this.executeRetryCount = executeRetryCount; }

    public int getCallbackRetryCount() { return callbackRetryCount; }
    public void setCallbackRetryCount(int callbackRetryCount) { this.callbackRetryCount = callbackRetryCount; }

    public boolean isSync() { return sync; }
    public void setSync(boolean sync) { this.sync = sync; }

    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = timeout; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public SenderType getSenderType() { return senderType; }
    public void setSenderType(SenderType senderType) { this.senderType = senderType; }

    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }

    public ProxyType getProxyType() { return proxyType; }
    public void setProxyType(ProxyType proxyType) { this.proxyType = proxyType; }
}