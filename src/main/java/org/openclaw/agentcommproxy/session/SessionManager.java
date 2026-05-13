package org.openclaw.agentcommproxy.session;

/**
 * SessionManager 接口
 * 定义会话查询方法（只查不建）
 */
public interface SessionManager {

    /**
     * 通用方法：查询 taskId 对应的 sessionId（从 requests 表）
     * INDEPENDENT 模式使用
     */
    String findSessionIdByTaskId(String agentId, String taskId);

    /**
     * 差异方法：获取 MAIN 模式的 sessionId
     * ClaudeCode：从 JSON 配置读取
     * OpenClaw：返回 null（无此概念）
     */
    String getMainSessionId(String agentId);

    /**
     * 差异方法：更新 MAIN 模式的 sessionId
     * ClaudeCode：写入 JSON 配置
     * OpenClaw：空实现
     */
    void setMainSessionId(String agentId, String sessionId);
}