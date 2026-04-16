package org.openclaw.agentcommproxy.store;

import org.openclaw.agentcommproxy.config.ConfigManager;
import org.openclaw.agentcommproxy.model.AgentRequest;
import org.openclaw.agentcommproxy.model.MessageStatus;
import org.openclaw.agentcommproxy.model.SenderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLite 数据存储
 */
public class SQLiteStore {
    private static final Logger log = LoggerFactory.getLogger(SQLiteStore.class);

    private final ConfigManager configManager;
    private final String dbPath;

    public SQLiteStore(ConfigManager configManager) {
        this.configManager = configManager;
        this.dbPath = configManager.getDbPath();
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            log.error("SQLite JDBC driver not found: {}", e.getMessage());
            throw new RuntimeException("SQLite JDBC driver not found", e);
        }
        initDatabase();
    }

    private void initDatabase() {
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get(dbPath).getParent());
        } catch (Exception e) {
            log.warn("Failed to create db directory: {}", e.getMessage());
        }

        try (Connection conn = getConnection()) {
            String createRequestsTable =
                "CREATE TABLE IF NOT EXISTS requests (" +
                "id TEXT PRIMARY KEY, " +
                "sender TEXT NOT NULL, " +
                "target_agent TEXT NOT NULL, " +
                "message TEXT, " +
                "status TEXT DEFAULT 'PENDING', " +
                "response TEXT, " +
                "error TEXT, " +
                "execute_retry_count INTEGER DEFAULT 0, " +
                "callback_retry_count INTEGER DEFAULT 0, " +
                "sync INTEGER DEFAULT 0, " +
                "timeout INTEGER DEFAULT 300, " +
                "created_at INTEGER, " +
                "updated_at INTEGER" +
                ")";

            String createRetryTable =
                "CREATE TABLE IF NOT EXISTS retry_queue (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "request_id TEXT NOT NULL, " +
                "retry_type TEXT NOT NULL, " +
                "next_retry_at INTEGER, " +
                "retry_count INTEGER DEFAULT 0, " +
                "FOREIGN KEY (request_id) REFERENCES requests(id)" +
                ")";

            Statement stmt = conn.createStatement();
            stmt.execute(createRequestsTable);
            stmt.execute(createRetryTable);

            // 迁移旧表结构（添加新字段）
            try {
                stmt.execute("ALTER TABLE requests ADD COLUMN execute_retry_count INTEGER DEFAULT 0");
            } catch (SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE requests ADD COLUMN callback_retry_count INTEGER DEFAULT 0");
            } catch (SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE retry_queue ADD COLUMN retry_type TEXT DEFAULT 'EXECUTE'");
            } catch (SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE requests ADD COLUMN sender_type TEXT DEFAULT 'CLI'");
            } catch (SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE requests ADD COLUMN callback_url TEXT");
            } catch (SQLException ignored) {}

            log.info("Database initialized at: {}", dbPath);
        } catch (SQLException e) {
            log.error("Failed to initialize database: {}", e.getMessage());
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    /**
     * 保存请求
     */
    public AgentRequest saveRequest(AgentRequest request) {
        if (request.getId() == null) {
            request.setId(UUID.randomUUID().toString());
        }

        String sql =
            "INSERT OR REPLACE INTO requests " +
            "(id, sender, target_agent, message, status, response, error, execute_retry_count, callback_retry_count, sync, timeout, created_at, updated_at, sender_type, callback_url) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, request.getId());
            ps.setString(2, request.getSender());
            ps.setString(3, request.getTargetAgent());
            ps.setString(4, request.getMessage());
            ps.setString(5, request.getStatus().name());
            ps.setString(6, request.getResponse());
            ps.setString(7, request.getError());
            ps.setInt(8, request.getExecuteRetryCount());
            ps.setInt(9, request.getCallbackRetryCount());
            ps.setInt(10, request.isSync() ? 1 : 0);
            ps.setInt(11, request.getTimeout());
            ps.setLong(12, request.getCreatedAt());
            ps.setLong(13, Instant.now().toEpochMilli());
            ps.setString(14, request.getSenderType() != null ? request.getSenderType().name() : SenderType.CLI.name());
            ps.setString(15, request.getCallbackUrl());

            ps.executeUpdate();
            log.debug("Saved request: {}", request.getId());
            return request;
        } catch (SQLException e) {
            log.error("Failed to save request: {}", e.getMessage());
            throw new RuntimeException("Failed to save request", e);
        }
    }

    /**
     * 根据ID获取请求
     */
    public Optional<AgentRequest> getRequestById(String id) {
        String sql = "SELECT * FROM requests WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRequest(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            log.error("Failed to get request: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 获取待处理请求
     */
    public List<AgentRequest> getPendingRequests() {
        String sql = "SELECT * FROM requests WHERE status IN ('PENDING', 'EXECUTE_SUCCESS')";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            List<AgentRequest> requests = new ArrayList<>();
            while (rs.next()) {
                requests.add(mapRequest(rs));
            }
            return requests;
        } catch (SQLException e) {
            log.error("Failed to get pending requests: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 获取待重试请求
     */
    public List<AgentRequest> getRetryRequests() {
        long now = Instant.now().toEpochMilli();
        String sql =
            "SELECT r.* FROM requests r " +
            "JOIN retry_queue q ON r.id = q.request_id " +
            "WHERE q.next_retry_at <= ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, now);
            ResultSet rs = ps.executeQuery();

            List<AgentRequest> requests = new ArrayList<>();
            while (rs.next()) {
                requests.add(mapRequest(rs));
            }
            return requests;
        } catch (SQLException e) {
            log.error("Failed to get retry requests: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 更新请求状态
     */
    public void updateRequestStatus(String id, MessageStatus status, String response, String error) {
        String sql =
            "UPDATE requests SET status = ?, response = ?, error = ?, updated_at = ? " +
            "WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status.name());
            ps.setString(2, response);
            ps.setString(3, error);
            ps.setLong(4, Instant.now().toEpochMilli());
            ps.setString(5, id);

            ps.executeUpdate();
            log.debug("Updated request {} to status {}", id, status);
        } catch (SQLException e) {
            log.error("Failed to update request status: {}", e.getMessage());
        }
    }

    /**
     * 增加执行重试计数
     */
    public void incrementExecuteRetryCount(String id) {
        String sql = "UPDATE requests SET execute_retry_count = execute_retry_count + 1, updated_at = ? WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, Instant.now().toEpochMilli());
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to increment execute retry count: {}", e.getMessage());
        }
    }

    /**
     * 增加回调重试计数
     */
    public void incrementCallbackRetryCount(String id) {
        String sql = "UPDATE requests SET callback_retry_count = callback_retry_count + 1, updated_at = ? WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, Instant.now().toEpochMilli());
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to increment callback retry count: {}", e.getMessage());
        }
    }

    /**
     * 添加到重试队列
     */
    public void addToRetryQueue(String requestId, String retryType, long nextRetryAt) {
        String sql = "INSERT INTO retry_queue (request_id, retry_type, next_retry_at, retry_count) VALUES (?, ?, ?, 0)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, requestId);
            ps.setString(2, retryType);
            ps.setLong(3, nextRetryAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to add to retry queue: {}", e.getMessage());
        }
    }

    /**
     * 从重试队列移除
     */
    public void removeFromRetryQueue(String requestId) {
        String sql = "DELETE FROM retry_queue WHERE request_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, requestId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to remove from retry queue: {}", e.getMessage());
        }
    }

    /**
     * 获取所有请求记录
     */
    public List<AgentRequest> getAllRequests(int limit, String statusFilter) {
        String sql;
        if (statusFilter != null && !statusFilter.isEmpty()) {
            sql = "SELECT * FROM requests WHERE status = ? ORDER BY created_at DESC LIMIT ?";
        } else {
            sql = "SELECT * FROM requests ORDER BY created_at DESC LIMIT ?";
        }

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (statusFilter != null && !statusFilter.isEmpty()) {
                ps.setString(1, statusFilter.toUpperCase());
                ps.setInt(2, limit);
            } else {
                ps.setInt(1, limit);
            }

            ResultSet rs = ps.executeQuery();
            List<AgentRequest> requests = new ArrayList<>();
            while (rs.next()) {
                requests.add(mapRequest(rs));
            }
            return requests;
        } catch (SQLException e) {
            log.error("Failed to get all requests: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 获取记录总数
     */
    public int getTotalCount() {
        String sql = "SELECT COUNT(*) FROM requests";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            log.error("Failed to get total count: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 清空所有消息记录
     */
    public int clearAll() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("DELETE FROM retry_queue");
            int count = stmt.executeUpdate("DELETE FROM requests");

            log.info("Cleared all messages: {} records deleted", count);
            return count;
        } catch (SQLException e) {
            log.error("Failed to clear all messages: {}", e.getMessage());
            return -1;
        }
    }

    /**
     * 清空指定状态的消息记录
     */
    public int clearByStatus(String status) {
        try (Connection conn = getConnection()) {

            String deleteRetrySql = "DELETE FROM retry_queue WHERE request_id IN " +
                "(SELECT id FROM requests WHERE status = ?)";
            try (PreparedStatement ps = conn.prepareStatement(deleteRetrySql)) {
                ps.setString(1, status.toUpperCase());
                ps.executeUpdate();
            }

            String deleteRequestsSql = "DELETE FROM requests WHERE status = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteRequestsSql)) {
                ps.setString(1, status.toUpperCase());
                int count = ps.executeUpdate();
                log.info("Cleared messages with status {}: {} records deleted", status, count);
                return count;
            }
        } catch (SQLException e) {
            log.error("Failed to clear messages by status: {}", e.getMessage());
            return -1;
        }
    }

    /**
     * 清空指定请求ID的消息
     */
    public boolean clearById(String requestId) {
        try (Connection conn = getConnection()) {

            String deleteRetrySql = "DELETE FROM retry_queue WHERE request_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteRetrySql)) {
                ps.setString(1, requestId);
                ps.executeUpdate();
            }

            String deleteRequestSql = "DELETE FROM requests WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteRequestSql)) {
                ps.setString(1, requestId);
                int count = ps.executeUpdate();
                if (count > 0) {
                    log.info("Cleared message: {}", requestId);
                    return true;
                }
                return false;
            }
        } catch (SQLException e) {
            log.error("Failed to clear message by id: {}", e.getMessage());
            return false;
        }
    }

    private AgentRequest mapRequest(ResultSet rs) throws SQLException {
        AgentRequest request = new AgentRequest();
        request.setId(rs.getString("id"));
        request.setSender(rs.getString("sender"));
        request.setTargetAgent(rs.getString("target_agent"));
        request.setMessage(rs.getString("message"));
        try {
            request.setStatus(MessageStatus.valueOf(rs.getString("status")));
        } catch (IllegalArgumentException e) {
            // 兼容旧状态
            String status = rs.getString("status");
            if ("SUCCESS".equals(status)) {
                request.setStatus(MessageStatus.EXECUTE_SUCCESS);
            } else if ("FAILED".equals(status)) {
                request.setStatus(MessageStatus.EXECUTE_FAILED);
            } else if ("TIMEOUT".equals(status)) {
                request.setStatus(MessageStatus.EXECUTE_TIMEOUT);
            } else if ("CALLBACK_PENDING".equals(status)) {
                request.setStatus(MessageStatus.CALLBACK_FAILED);
            } else if ("CALLBACK_DONE".equals(status)) {
                request.setStatus(MessageStatus.DONE);
            } else {
                request.setStatus(MessageStatus.PENDING);
            }
        }
        request.setResponse(rs.getString("response"));
        request.setError(rs.getString("error"));
        try {
            request.setExecuteRetryCount(rs.getInt("execute_retry_count"));
        } catch (SQLException e) {
            request.setExecuteRetryCount(0);
        }
        try {
            request.setCallbackRetryCount(rs.getInt("callback_retry_count"));
        } catch (SQLException e) {
            request.setCallbackRetryCount(0);
        }
        request.setSync(rs.getInt("sync") == 1);
        request.setTimeout(rs.getInt("timeout"));
        request.setCreatedAt(rs.getLong("created_at"));
        request.setUpdatedAt(rs.getLong("updated_at"));

        // 读取 sender_type 和 callback_url
        try {
            String senderTypeStr = rs.getString("sender_type");
            if (senderTypeStr != null && !senderTypeStr.isEmpty()) {
                request.setSenderType(SenderType.valueOf(senderTypeStr));
            } else {
                request.setSenderType(SenderType.CLI);  // 默认值
            }
        } catch (SQLException e) {
            request.setSenderType(SenderType.CLI);
        }
        try {
            request.setCallbackUrl(rs.getString("callback_url"));
        } catch (SQLException e) {
            request.setCallbackUrl(null);
        }

        return request;
    }
}