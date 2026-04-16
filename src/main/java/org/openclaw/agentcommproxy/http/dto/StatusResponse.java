package org.openclaw.agentcommproxy.http.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 状态查询响应 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StatusResponse {

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("sender")
    private String sender;

    @JsonProperty("targetAgent")
    private String targetAgent;

    @JsonProperty("message")
    private String message;

    @JsonProperty("response")
    private String response;

    @JsonProperty("error")
    private String error;

    @JsonProperty("executeRetryCount")
    private int executeRetryCount;

    @JsonProperty("callbackRetryCount")
    private int callbackRetryCount;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getTargetAgent() {
        return targetAgent;
    }

    public void setTargetAgent(String targetAgent) {
        this.targetAgent = targetAgent;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public int getExecuteRetryCount() {
        return executeRetryCount;
    }

    public void setExecuteRetryCount(int executeRetryCount) {
        this.executeRetryCount = executeRetryCount;
    }

    public int getCallbackRetryCount() {
        return callbackRetryCount;
    }

    public void setCallbackRetryCount(int callbackRetryCount) {
        this.callbackRetryCount = callbackRetryCount;
    }
}