package org.openclaw.agentcommproxy.provider;

import org.openclaw.agentcommproxy.config.ConfigManager;
import org.openclaw.agentcommproxy.model.ProxyType;
import org.openclaw.agentcommproxy.proxy.CommandProxy;
import org.openclaw.agentcommproxy.proxy.OpenClawProxy;
import org.openclaw.agentcommproxy.session.OpenClawSessionHelper;
import org.openclaw.agentcommproxy.session.OpenClawSessionManager;
import org.openclaw.agentcommproxy.session.SessionManager;
import org.openclaw.agentcommproxy.store.SQLiteStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenClaw Agent Provider
 * 封装 OpenClaw 的所有接入逻辑
 */
public class OpenClawProvider implements AgentProvider {
    private static final Logger log = LoggerFactory.getLogger(OpenClawProvider.class);

    private final OpenClawSessionManager sessionManager;
    private final OpenClawProxy proxy;
    private final OpenClawSessionHelper sessionHelper;

    public OpenClawProvider(SQLiteStore store, ConfigManager configManager) {
        this.sessionManager = new OpenClawSessionManager(store);
        this.proxy = new OpenClawProxy(configManager);
        this.sessionHelper = new OpenClawSessionHelper(configManager);
    }

    @Override
    public String getName() {
        return "openclaw";
    }

    @Override
    public ProxyType getProxyType() {
        return ProxyType.OPENCLAW;
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
    public String createIndependentSession(String agentId, String taskId, boolean clearSession) {
        log.info("Creating OpenClaw independent session: agentId={}, taskId={}", agentId, taskId);
        sessionHelper.ensureMainSessionExists(agentId);
        return sessionHelper.createIndependentSession(agentId, taskId, clearSession);
    }

    @Override
    public boolean needsPreCreateSession() {
        return true;  // OpenClaw 需要先编辑 sessions.json
    }
}