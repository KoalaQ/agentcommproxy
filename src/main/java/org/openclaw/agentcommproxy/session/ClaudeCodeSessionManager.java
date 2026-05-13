package org.openclaw.agentcommproxy.session;

import org.openclaw.agentcommproxy.config.ClaudeCodeAgentConfig;
import org.openclaw.agentcommproxy.store.SQLiteStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Claude Code SessionManager
 * MAIN 模式：mainSessionId 存储在 JSON 配置文件
 * INDEPENDENT 模式：sessionId 从 requests 表查询
 */
public class ClaudeCodeSessionManager extends BaseSessionManager {
    private static final Logger log = LoggerFactory.getLogger(ClaudeCodeSessionManager.class);

    private final ClaudeCodeAgentConfig agentConfig;

    public ClaudeCodeSessionManager(SQLiteStore store) {
        super(store);
        this.agentConfig = new ClaudeCodeAgentConfig();
    }

    /**
     * MAIN 模式：从 JSON 配置读取 mainSessionId
     */
    @Override
    public String getMainSessionId(String agentId) {
        String sessionId = agentConfig.getMainSessionId(agentId);
        log.info("MAIN mode: mainSessionId from config for agent {}: {}", agentId, sessionId);
        return sessionId;
    }

    /**
     * MAIN 模式：写入 JSON 配置更新 mainSessionId
     */
    @Override
    public void setMainSessionId(String agentId, String sessionId) {
        agentConfig.updateMainSessionId(agentId, sessionId);
        log.info("MAIN mode: saved mainSessionId for agent {}: {}", agentId, sessionId);
    }
}