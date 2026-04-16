package org.openclaw.agentcommproxy.http.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 发送消息响应 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SendResponse {

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("response")
    private String response;

    @JsonProperty("error")
    private String error;

    @JsonProperty("message")
    private String message;

    public SendResponse() {
    }

    public SendResponse(String requestId, String status) {
        this.requestId = requestId;
        this.status = status;
    }

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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}