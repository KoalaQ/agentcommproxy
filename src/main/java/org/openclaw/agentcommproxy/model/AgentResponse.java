package org.openclaw.agentcommproxy.model;

import java.time.Instant;

/**
 * 响应消息实体（用于回调）
 */
public class AgentResponse {
    private String requestId;       // 原请求ID
    private String status;          // success/failed/timeout
    private RequestInfo request;    // 原请求信息
    private String response;        // 响应内容
    private long timestamp;         // 时间戳

    public AgentResponse() {
        this.timestamp = Instant.now().toEpochMilli();
    }

    public static class RequestInfo {
        private String sender;
        private String agent;
        private String message;

        public RequestInfo(String sender, String agent, String message) {
            this.sender = sender;
            this.agent = agent;
            this.message = message;
        }

        public String getSender() { return sender; }
        public void setSender(String sender) { this.sender = sender; }

        public String getAgent() { return agent; }
        public void setAgent(String agent) { this.agent = agent; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    // Getters and Setters
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public RequestInfo getRequest() { return request; }
    public void setRequest(RequestInfo request) { this.request = request; }

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}