package org.openclaw.agentcommproxy.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 健康检查响应 DTO
 */
public class HealthResponse {

    @JsonProperty("status")
    private String status;

    @JsonProperty("daemonRunning")
    private boolean daemonRunning;

    @JsonProperty("httpPort")
    private int httpPort;

    public HealthResponse() {
        this.status = "healthy";
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isDaemonRunning() {
        return daemonRunning;
    }

    public void setDaemonRunning(boolean daemonRunning) {
        this.daemonRunning = daemonRunning;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }
}