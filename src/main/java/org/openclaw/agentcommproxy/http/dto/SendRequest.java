package org.openclaw.agentcommproxy.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 发送消息请求 DTO
 */
public class SendRequest {

    @JsonProperty("agent")
    private String agent;

    @JsonProperty("sender")
    private String sender;

    @JsonProperty("message")
    private String message;

    @JsonProperty("sync")
    private boolean sync = false;

    @JsonProperty("timeout")
    private int timeout = 300;

    @JsonProperty("callbackUrl")
    private String callbackUrl;

    @JsonProperty("proxy")
    private String proxy;

    @JsonProperty("taskId")
    private String taskId;

    @JsonProperty("sessionMode")
    private String sessionMode;

    @JsonProperty("clearSession")
    private boolean clearSession = false;

    public String getAgent() {
        return agent;
    }

    public void setAgent(String agent) {
        this.agent = agent;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isSync() {
        return sync;
    }

    public void setSync(boolean sync) {
        this.sync = sync;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public String getProxy() {
        return proxy;
    }

    public void setProxy(String proxy) {
        this.proxy = proxy;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getSessionMode() {
        return sessionMode;
    }

    public void setSessionMode(String sessionMode) {
        this.sessionMode = sessionMode;
    }

    public boolean isClearSession() {
        return clearSession;
    }

    public void setClearSession(boolean clearSession) {
        this.clearSession = clearSession;
    }
}