package org.openclaw.agentcommproxy.session;

import org.openclaw.agentcommproxy.store.SQLiteStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenClaw SessionManager
 * MAIN 模式：无 mainSessionId概念，返回 null
 * INDEPENDENT 模式：sessionId 从 requests 表查询
 */
public class OpenClawSessionManager extends BaseSessionManager {
    private static final Logger log = LoggerFactory.getLogger(OpenClawSessionManager.class);

    public OpenClawSessionManager(SQLiteStore store) {
        super(store);
    }

    /**
     * MAIN 模式：OpenClaw 无 mainSessionId概念，返回 null
     */
    @Override
    public String getMainSessionId(String agentId) {
        log.info("MAIN mode: OpenClaw has no mainSessionId concept, returning null for agent {}", agentId);
        return null;
    }

    /**
     * MAIN 模式：OpenClaw 无 mainSessionId概念，空实现
     */
    @Override
    public void setMainSessionId(String agentId, String sessionId) {
        log.info("MAIN mode: OpenClaw has no mainSessionId concept, ignoring set for agent {}", agentId);
    }
}