package org.openclaw.agentcommproxy.provider;

import org.openclaw.agentcommproxy.model.ProxyType;
import org.openclaw.agentcommproxy.proxy.ClaudeCodeProxy;
import org.openclaw.agentcommproxy.proxy.CommandProxy;
import org.openclaw.agentcommproxy.session.ClaudeCodeSessionManager;
import org.openclaw.agentcommproxy.session.SessionManager;
import org.openclaw.agentcommproxy.store.SQLiteStore;

/**
 * Claude Code Agent Provider
 * 封装 Claude Code 的所有接入逻辑
 */
public class ClaudeCodeProvider implements AgentProvider {

    private final ClaudeCodeSessionManager sessionManager;
    private final ClaudeCodeProxy proxy;

    public ClaudeCodeProvider(SQLiteStore store) {
        this.sessionManager = new ClaudeCodeSessionManager(store);
        this.proxy = new ClaudeCodeProxy();
    }

    @Override
    public String getName() {
        return "claude-code";
    }

    @Override
    public ProxyType getProxyType() {
        return ProxyType.CLAUDE_CODE;
    }

    @Override
    public SessionManager getSessionManager() {
        return sessionManager;
    }

    @Override
    public CommandProxy getProxy() {
        return proxy;
    }

    @Override
    public boolean needsPreCreateSession() {
        return false;  // Claude Code 用 --session-id 自动创建
    }
}