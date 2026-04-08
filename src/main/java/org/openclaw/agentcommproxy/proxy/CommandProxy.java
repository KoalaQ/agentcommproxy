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
     * 执行命令
     * @param agent 目标 Agent 名称
     * @param message 消息内容
     * @param timeout 超时时间（秒）
     * @return 执行结果
     */
    CommandResult execute(String agent, String message, int timeout);

    /**
     * 构建命令字符串
     */
    String buildCommand(String agent, String message, int timeout);
}