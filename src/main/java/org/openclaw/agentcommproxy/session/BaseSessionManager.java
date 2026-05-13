package org.openclaw.agentcommproxy.session;

import org.openclaw.agentcommproxy.store.SQLiteStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * SessionManager 基类
 * 封装通用的 requests 表查询逻辑
 */
public abstract class BaseSessionManager implements SessionManager {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final SQLiteStore store;

    public BaseSessionManager(SQLiteStore store) {
        this.store = store;
    }

    /**
     * 通用方法：从 requests 表查询 taskId 对应的 sessionId
     * INDEPENDENT 模式使用
     */
    @Override
    public String findSessionIdByTaskId(String agentId, String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            return null;
        }

        try {
            String sql =
                "SELECT session_id FROM requests " +
                "WHERE target_agent = ? AND task_id = ? AND status = 'DONE' AND session_id IS NOT NULL " +
                "ORDER BY created_at DESC LIMIT 1";

            Connection conn = store.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, agentId);
            ps.setString(2, taskId);

            log.debug("Query session by taskId: agentId={}, taskId={}", agentId, taskId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String sessionId = rs.getString("session_id");
                log.info("Found sessionId for taskId={}: {}", taskId, sessionId);
                rs.close();
                ps.close();
                return sessionId;
            }
            log.debug("No sessionId found for taskId={}", taskId);
            rs.close();
            ps.close();
            return null;
        } catch (Exception e) {
            log.warn("Failed to find sessionId by taskId: {}", e.getMessage());
            return null;
        }
    }
}