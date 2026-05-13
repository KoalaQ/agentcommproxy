package org.openclaw.agentcommproxy.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.openclaw.agentcommproxy.model.SessionMode;
import org.openclaw.agentcommproxy.proxy.CommandProxy;
import org.openclaw.agentcommproxy.proxy.CommandProxyFactory;
import org.openclaw.agentcommproxy.proxy.CommandResult;
import org.openclaw.agentcommproxy.model.ProxyType;
import org.openclaw.agentcommproxy.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * OpenClaw 会话管理器
 * 管理 sessions.json 文件，创建独立会话
 */
public class SessionManager {
    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    private static final String AGENTS_DIR = "agents";
    private static final String SESSIONS_FILE = "sessions.json";
    private static final String SESSIONS_DIR = "sessions";
    private static final String MAIN_SESSION_KEY = "main";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConfigManager configManager;
    private final String openclawDataDir;

    public SessionManager(ConfigManager configManager) {
        this.configManager = configManager;
        this.openclawDataDir = configManager.getOpenClawDataDir();
    }

    /**
     * 兼容旧构造方法（使用默认路径）
     */
    public SessionManager() {
        this.configManager = null;
        this.openclawDataDir = Paths.get(System.getProperty("user.home"), ".openclaw").toString();
    }

    /**
     * 获取或创建会话ID
     *
     * @param agentId 目标 Agent ID
     * @param taskId  业务任务ID（INDEPENDENT 模式必须提供）
     * @param mode    会话模式
     * @param clearSession 是否清空会话历史
     * @return sessionId（MAIN 模式返回 null，INDEPENDENT 模式返回新创建的 sessionId）
     */
    public String getOrCreateSessionId(String agentId, String taskId, SessionMode mode, boolean clearSession) {
        log.info("getOrCreateSessionId called: agentId={}, taskId={}, mode={}, clearSession={}", agentId, taskId, mode, clearSession);

        if (mode == SessionMode.MAIN) {
            log.info("Using MAIN session mode for agent: {}, returning null sessionId", agentId);
            return null;  // MAIN 模式不返回 sessionId，使用默认行为
        }

        if (taskId == null || taskId.isEmpty()) {
            log.warn("INDEPENDENT mode requires taskId, falling back to MAIN mode");
            return null;
        }

        log.info("Creating INDEPENDENT session for agent: {}, taskId: {}", agentId, taskId);
        log.info("OpenClaw data directory: {}", openclawDataDir);

        // 确保存在 main 会话
        ensureMainSessionExists(agentId);

        // 创建独立会话
        String sessionId = createIndependentSession(agentId, taskId, clearSession);
        log.info("Created sessionId: {}", sessionId);

        return sessionId;
    }

    /**
     * 兼容旧方法（默认不清空会话历史）
     */
    public String getOrCreateSessionId(String agentId, String taskId, SessionMode mode) {
        return getOrCreateSessionId(agentId, taskId, mode, false);
    }

    /**
     * 确保 main 会话存在
     * 如果不存在，发送一条消息触发 openclaw 创建
     */
    private void ensureMainSessionExists(String agentId) {
        Path sessionsPath = getSessionsJsonPath(agentId);
        log.info("Checking sessions.json path: {}", sessionsPath);

        if (!Files.exists(sessionsPath)) {
            log.info("No sessions.json found for agent: {}, triggering main session creation", agentId);
            triggerMainSessionCreation(agentId);

            // 再次检查
            if (!Files.exists(sessionsPath)) {
                log.warn("sessions.json still not exists after trigger attempt: {}", sessionsPath);
            } else {
                log.info("sessions.json created successfully: {}", sessionsPath);
            }
        } else {
            log.info("sessions.json exists: {}", sessionsPath);
        }
    }

    /**
     * 发送消息触发 main 会话创建
     */
    private void triggerMainSessionCreation(String agentId) {
        CommandProxy proxy = CommandProxyFactory.getProxy(ProxyType.OPENCLAW);
        CommandResult result = proxy.execute(agentId, "hi", 30, null);

        if (!result.isSuccess()) {
            log.warn("Failed to trigger main session creation: {}", result.getError());
            // 即使失败也继续，可能 openclaw 内部已创建
        }

        // 等待一下让 openclaw 完成文件写入
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 创建独立会话
     * 从 main 会话配置复制，修改必要字段
     * 如果已存在同名会话，直接返回已有 sessionId
     *
     * @param agentId 目标 Agent ID
     * @param taskId  业务任务ID
     * @param clearSession 是否清空会话历史文件
     * @return sessionId
     */
    private String createIndependentSession(String agentId, String taskId, boolean clearSession) {
        Path sessionsPath = getSessionsJsonPath(agentId);
        log.info("createIndependentSession: sessionsPath={}", sessionsPath);

        try {
            JsonNode sessions = readSessionsJson(sessionsPath);
            log.info("Read sessions.json, field count: {}", sessions.size());

            // 构建目标 session key
            String targetSessionKey = buildSessionKey(agentId, taskId);
            log.info("Target session key: {}", targetSessionKey);

            // 检查是否已存在该会话
            if (sessions.has(targetSessionKey)) {
                JsonNode existingSession = sessions.get(targetSessionKey);
                String existingSessionId = existingSession.get("sessionId").asText();
                log.info("Found existing session for key={}, sessionId={}", targetSessionKey, existingSessionId);

                // 如果需要清空历史
                if (clearSession) {
                    clearSessionFile(agentId, existingSessionId);
                    log.info("Cleared existing session file (clearSession=true)");
                } else {
                    log.info("Using existing session, history preserved (clearSession=false)");
                }

                return existingSessionId;
            }

            // 会话不存在，创建新的
            log.info("Session not found, creating new one for key={}", targetSessionKey);

            // 查找 main 会话配置
            String mainKey = findMainSessionKey(sessions, agentId);
            log.info("Found main session key: {}", mainKey);
            if (mainKey == null) {
                log.error("Cannot find main session for agent: {}", agentId);
                return null;
            }

            JsonNode mainSession = sessions.get(mainKey);
            if (mainSession == null) {
                log.error("Main session config is null for key: {}", mainKey);
                return null;
            }
            log.info("Main session has sessionId: {}", mainSession.has("sessionId") ? mainSession.get("sessionId").asText() : "N/A");

            // 生成新的 sessionId
            String newSessionId = UUID.randomUUID().toString();
            log.info("Generated new sessionId: {}", newSessionId);

            // 复制 main 配置并修改必要字段
            ObjectNode newSession = ((ObjectNode) mainSession).deepCopy();
            newSession.put("sessionId", newSessionId);
            newSession.put("updatedAt", System.currentTimeMillis());

            // 修改 sessionFile 路径
            String sessionFile = buildSessionFilePath(agentId, newSessionId);
            newSession.put("sessionFile", sessionFile);
            log.info("New sessionFile: {}", sessionFile);

            // 添加到 sessions.json
            ((ObjectNode) sessions).set(targetSessionKey, newSession);

            // 写入文件
            writeSessionsJson(sessionsPath, sessions);
            log.info("Written sessions.json");

            // 处理会话文件：仅在 clearSession=true 时清空，否则不操作（保留历史或让 openclaw 自动创建）
            if (clearSession) {
                clearSessionFile(agentId, newSessionId);
                log.info("Cleared session file (clearSession=true)");
            } else {
                log.info("Session file not modified (clearSession=false, history preserved)");
            }

            log.info("Created new independent session: key={}, sessionId={}", targetSessionKey, newSessionId);

            return newSessionId;

        } catch (Exception e) {
            log.error("Failed to create independent session: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 读取 sessions.json
     */
    private JsonNode readSessionsJson(Path path) throws IOException {
        if (!Files.exists(path)) {
            return objectMapper.createObjectNode();
        }

        String content = Files.readString(path);
        return objectMapper.readTree(content);
    }

    /**
     * 写入 sessions.json
     */
    private void writeSessionsJson(Path path, JsonNode sessions) throws IOException {
        Files.createDirectories(path.getParent());
        String content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(sessions);
        Files.writeString(path, content);
    }

    /**
     * 清空会话历史文件 (.jsonl)
     * 仅在 clearSession=true 时调用
     */
    private void clearSessionFile(String agentId, String sessionId) throws IOException {
        Path sessionsDir = Paths.get(openclawDataDir, AGENTS_DIR, agentId, SESSIONS_DIR);
        Path sessionFile = sessionsDir.resolve(sessionId + ".jsonl");

        // 清空文件内容（如果存在）
        if (Files.exists(sessionFile)) {
            Files.writeString(sessionFile, "");
            log.info("Cleared existing session file: {}", sessionFile);
        } else {
            // 文件不存在，创建空文件
            Files.createDirectories(sessionsDir);
            Files.writeString(sessionFile, "");
            log.info("Created new empty session file: {}", sessionFile);
        }
    }

    /**
     * 查找 main 会话的 key
     */
    private String findMainSessionKey(JsonNode sessions, String agentId) {
        // 先尝试精确匹配 "agent:{agentId}:main"
        String exactMainKey = buildSessionKey(agentId, MAIN_SESSION_KEY);
        if (sessions.has(exactMainKey)) {
            return exactMainKey;
        }

        // 遍历查找包含 "main" 的 key
        java.util.Iterator<String> fieldNames = sessions.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (fieldName.contains(MAIN_SESSION_KEY)) {
                return fieldName;
            }
        }

        // 返回第一个 field（假设是 main）
        if (sessions.isObject() && sessions.size() > 0) {
            return sessions.fieldNames().next();
        }

        return null;
    }

    /**
     * 构建 session key
     * 格式: agent:{agentId}:{taskId}
     */
    private String buildSessionKey(String agentId, String taskId) {
        return String.format("agent:%s:%s", agentId, taskId);
    }

    /**
     * 构建 session 文件路径
     * 格式: {openclawDataDir}/agents/{agentId}/sessions/{sessionId}.jsonl
     */
    private String buildSessionFilePath(String agentId, String sessionId) {
        return String.format("%s/%s/%s/%s/%s.jsonl",
                openclawDataDir, AGENTS_DIR, agentId, SESSIONS_DIR, sessionId);
    }

    /**
     * 获取 sessions.json 文件路径
     * 正确路径: {openclawDataDir}/agents/{agentId}/sessions/sessions.json
     */
    private Path getSessionsJsonPath(String agentId) {
        return Paths.get(openclawDataDir, AGENTS_DIR, agentId, SESSIONS_DIR, SESSIONS_FILE);
    }
}