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
}