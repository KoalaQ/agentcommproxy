package org.openclaw.agentcommproxy.provider;

import org.openclaw.agentcommproxy.model.ProxyType;
import org.openclaw.agentcommproxy.proxy.CommandProxy;
import org.openclaw.agentcommproxy.session.SessionManager;

/**
 * Agent 工具接入接口
 * 封装 Proxy + SessionManager + 创建逻辑
 * 新接入 Agent 工具只需实现此接口并注册到 AgentProviderFactory
 */
public interface AgentProvider {

    /**
     * 获取 Provider 名称
     */
    String getName();

    /**
     * 获取 ProxyType
     */
    ProxyType getProxyType();

    /**
     * 获取 SessionManager
     */
    SessionManager getSessionManager();

    /**
     * 获取 CommandProxy
     */
    CommandProxy getProxy();

    /**
     * 创建独立会话（INDEPENDENT 模式首次调用）
     * OpenClaw 需要编辑 sessions.json
     * Claude Code 不需要特殊处理（Proxy 自动创建）
     *
     * @param agentId 目标 Agent ID
     * @param taskId 业务任务 ID
     * @param clearSession 是否清空会话
     * @return sessionId，返回 null 表示不需要预创建
     */
    default String createIndependentSession(String agentId, String taskId, boolean clearSession) {
        return null;
    }

    /**
     * 是否需要预创建会话
     * OpenClaw: true（需要先编辑 sessions.json）
     * Claude Code: false（Proxy 用 --session-id 自动创建）
     */
    default boolean needsPreCreateSession() {
        return false;
    }
}