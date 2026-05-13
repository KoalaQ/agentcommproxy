package org.openclaw.agentcommproxy.model;

/**
 * Proxy 类型枚举
 */
public enum ProxyType {
    OPENCLAW("openclaw", "OpenClaw CLI 命令"),
    HTTP("http", "HTTP API 调用"),
    WEBSOCKET("websocket", "WebSocket 通信"),
    CUSTOM("custom", "自定义命令"),
    CLAUDE_CODE("claude-code", "Claude Code Agent");

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

    /**
     * 根据 code 获取 ProxyType
     * 默认返回 OPENCLAW
     */
    public static ProxyType fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return OPENCLAW;
        }
        for (ProxyType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return OPENCLAW;  // 默认
    }
}