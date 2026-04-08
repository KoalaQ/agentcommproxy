package org.openclaw.agentcommproxy.store;

import org.openclaw.agentcommproxy.config.ConfigManager;
import org.openclaw.agentcommproxy.model.AgentRequest;
import org.openclaw.agentcommproxy.model.MessageStatus;
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
        // 加载 SQLite JDBC driver
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
                "retry_count INTEGER DEFAULT 0, " +
                "sync INTEGER DEFAULT 0, " +
                "timeout INTEGER DEFAULT 300, " +
                "created_at INTEGER, " +
                "updated_at INTEGER" +
                ")";

            String createRetryTable =
                "CREATE TABLE IF NOT EXISTS retry_queue (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "request_id TEXT NOT NULL, " +
                "next_retry_at INTEGER, " +
                "retry_count INTEGER DEFAULT 0, " +
                "FOREIGN KEY (request_id) REFERENCES requests(id)" +
                ")";

            Statement stmt = conn.createStatement();
            stmt.execute(createRequestsTable);
            stmt.execute(createRetryTable);
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
            "(id, sender, target_agent, message, status, response, error, retry_count, sync, timeout, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, request.getId());
            ps.setString(2, request.getSender());
            ps.setString(3, request.getTargetAgent());
            ps.setString(4, request.getMessage());
            ps.setString(5, request.getStatus().name());
            ps.setString(6, request.getResponse());
            ps.setString(7, request.getError());
            ps.setInt(8, request.getRetryCount());
            ps.setInt(9, request.isSync() ? 1 : 0);
            ps.setInt(10, request.getTimeout());
            ps.setLong(11, request.getCreatedAt());
            ps.setLong(12, Instant.now().toEpochMilli());

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
     * 获取待处理请求（用于后台线程处理）
     */
    public List<AgentRequest> getPendingRequests() {
        String sql = "SELECT * FROM requests WHERE status = 'PENDING' OR status = 'CALLBACK_PENDING'";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            List<AgentRequest> requests = new ArrayList<AgentRequest>();
            while (rs.next()) {
                requests.add(mapRequest(rs));
            }
            return requests;
        } catch (SQLException e) {
            log.error("Failed to get pending requests: {}", e.getMessage());
            return new ArrayList<AgentRequest>();
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
            "WHERE q.next_retry_at <= ? AND r.status = 'FAILED'";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, now);
            ResultSet rs = ps.executeQuery();

            List<AgentRequest> requests = new ArrayList<AgentRequest>();
            while (rs.next()) {
                requests.add(mapRequest(rs));
            }
            return requests;
        } catch (SQLException e) {
            log.error("Failed to get retry requests: {}", e.getMessage());
            return new ArrayList<AgentRequest>();
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
     * 增加重试计数
     */
    public void incrementRetryCount(String id) {
        String sql = "UPDATE requests SET retry_count = retry_count + 1, updated_at = ? WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, Instant.now().toEpochMilli());
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to increment retry count: {}", e.getMessage());
        }
    }

    /**
     * 添加到重试队列
     */
    public void addToRetryQueue(String requestId, long nextRetryAt) {
        String sql = "INSERT INTO retry_queue (request_id, next_retry_at, retry_count) VALUES (?, ?, 0)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, requestId);
            ps.setLong(2, nextRetryAt);
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
            List<AgentRequest> requests = new ArrayList<AgentRequest>();
            while (rs.next()) {
                requests.add(mapRequest(rs));
            }
            return requests;
        } catch (SQLException e) {
            log.error("Failed to get all requests: {}", e.getMessage());
            return new ArrayList<AgentRequest>();
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

    private AgentRequest mapRequest(ResultSet rs) throws SQLException {
        AgentRequest request = new AgentRequest();
        request.setId(rs.getString("id"));
        request.setSender(rs.getString("sender"));
        request.setTargetAgent(rs.getString("target_agent"));
        request.setMessage(rs.getString("message"));
        request.setStatus(MessageStatus.valueOf(rs.getString("status")));
        request.setResponse(rs.getString("response"));
        request.setError(rs.getString("error"));
        request.setRetryCount(rs.getInt("retry_count"));
        request.setSync(rs.getInt("sync") == 1);
        request.setTimeout(rs.getInt("timeout"));
        request.setCreatedAt(rs.getLong("created_at"));
        request.setUpdatedAt(rs.getLong("updated_at"));
        return request;
    }
}