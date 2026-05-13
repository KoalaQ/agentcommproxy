package org.openclaw.agentcommproxy.model;

/**
 * 会话模式枚举
 */
public enum SessionMode {
    /**
     * 主会话模式
     * 使用默认的 main 会话，所有消息在同一会话中执行
     */
    MAIN("main"),

    /**
     * 独立会话模式
     * 为每个任务创建独立会话，消息在独立上下文中执行
     */
    INDEPENDENT("independent");

    private final String code;

    SessionMode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * 从字符串解析会话模式
     */
    public static SessionMode fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return MAIN;  // 默认主会话
        }
        for (SessionMode mode : values()) {
            if (mode.code.equalsIgnoreCase(code)) {
                return mode;
            }
        }
        return MAIN;  // 未知值默认主会话
    }
}