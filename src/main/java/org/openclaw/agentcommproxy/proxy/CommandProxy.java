package org.openclaw.agentcommproxy.proxy;

/**
 * 命令代理接口
 * 支持扩展不同的指令代理
 */
public interface CommandProxy {

    /**
     * 获取代理名称
     */
    String getName();

    /**
     * 执行命令（使用指定会话）
     * @param agent 目标 Agent 名称
     * @param message 消息内容
     * @param timeout 超时时间（秒）
     * @param sessionId 会话ID（可选，独立会话模式使用）
     * @return 执行结果
     */
    CommandResult execute(String agent, String message, int timeout, String sessionId);

    /**
     * 执行命令（默认会话）
     * @param agent 目标 Agent 名称
     * @param message 消息内容
     * @param timeout 超时时间（秒）
     * @return 执行结果
     */
    default CommandResult execute(String agent, String message, int timeout) {
        return execute(agent, message, timeout, null);
    }

    /**
     * 构建命令字符串（使用指定会话）
     */
    String buildCommand(String agent, String message, int timeout, String sessionId);

    /**
     * 构建命令字符串（默认会话）
     */
    default String buildCommand(String agent, String message, int timeout) {
        return buildCommand(agent, message, timeout, null);
    }
}