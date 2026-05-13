package org.openclaw.agentcommproxy.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Claude Code Agent 配置管理
 * 只管理 Agent 基本信息（agentId, projectPath）
 * sessionId 由 requests 表管理
 */
public class ClaudeCodeAgentConfig {
    private static final Logger log = LoggerFactory.getLogger(ClaudeCodeAgentConfig.class);

    private static final String CONFIG_FILE = "claude-code-agents.json";

    private final ObjectMapper objectMapper;
    private final Path configPath;
    private ConfigData configData;

    public ClaudeCodeAgentConfig() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        String userHome = System.getProperty("user.home");
        this.configPath = Paths.get(userHome, ".agentcommproxy", CONFIG_FILE);

        loadConfig();
    }

    public ClaudeCodeAgentConfig(ConfigManager configManager) {
        this();
    }

    private void loadConfig() {
        configData = new ConfigData();
        configData.agents = new ArrayList<>();

        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                configData = objectMapper.readValue(reader, ConfigData.class);
                if (configData.agents == null) {
                    configData.agents = new ArrayList<>();
                }
                log.info("Loaded Claude Code agents config from: {}", configPath);
            } catch (Exception e) {
                log.warn("Failed to load Claude Code agents config: {}", e.getMessage());
            }
        }
    }

    private void saveConfig() {
        try {
            Files.createDirectories(configPath.getParent());

            try (Writer writer = Files.newBufferedWriter(configPath)) {
                objectMapper.writeValue(writer, configData);
            }
            log.info("Saved Claude Code agents config to: {}", configPath);
        } catch (Exception e) {
            log.error("Failed to save Claude Code agents config: {}", e.getMessage());
        }
    }

    /**
     * 注册 Claude Code Agent
     */
    public void registerAgent(String agentId, String projectPath) {
        // 检查是否已存在
        for (AgentInfo agent : configData.agents) {
            if (agent.agentId.equals(agentId)) {
                // 更新项目路径
                agent.projectPath = projectPath;
                agent.updatedAt = Instant.now().toString();
                saveConfig();
                log.info("Updated Claude Code Agent: {}", agentId);
                return;
            }
        }

        // 新增 Agent
        AgentInfo newAgent = new AgentInfo();
        newAgent.agentId = agentId;
        newAgent.projectPath = projectPath;
        newAgent.mainSessionId = null;  // 首次对话时创建
        newAgent.createdAt = Instant.now().toString();
        newAgent.updatedAt = Instant.now().toString();

        configData.agents.add(newAgent);
        saveConfig();
        log.info("Registered Claude Code Agent: {} -> {}", agentId, projectPath);
    }

    /**
     * 获取或创建 mainSessionId（用于 MAIN 模式）
     */
    public String getOrCreateMainSessionId(String agentId) {
        for (AgentInfo agent : configData.agents) {
            if (agent.agentId.equals(agentId)) {
                if (agent.mainSessionId == null || agent.mainSessionId.isEmpty()) {
                    agent.mainSessionId = UUID.randomUUID().toString();
                    agent.updatedAt = Instant.now().toString();
                    saveConfig();
                    log.info("Created mainSessionId for Agent {}: {}", agentId, agent.mainSessionId);
                }
                return agent.mainSessionId;
            }
        }
        log.warn("Agent not found: {}", agentId);
        return null;
    }

    /**
     * 获取 mainSessionId
     */
    public String getMainSessionId(String agentId) {
        for (AgentInfo agent : configData.agents) {
            if (agent.agentId.equals(agentId)) {
                return agent.mainSessionId;
            }
        }
        return null;
    }

    /**
     * 更新 mainSessionId（用于 clearSession）
     */
    public void updateMainSessionId(String agentId, String newSessionId) {
        for (AgentInfo agent : configData.agents) {
            if (agent.agentId.equals(agentId)) {
                agent.mainSessionId = newSessionId;
                agent.updatedAt = Instant.now().toString();
                saveConfig();
                log.info("Updated mainSessionId for Agent {}: {}", agentId, newSessionId);
                return;
            }
        }
    }

    /**
     * 获取 Agent 的项目路径
     */
    public String getProjectPath(String agentId) {
        for (AgentInfo agent : configData.agents) {
            if (agent.agentId.equals(agentId)) {
                return agent.projectPath;
            }
        }
        return null;
    }

    /**
     * 删除 Agent
     */
    public void removeAgent(String agentId) {
        configData.agents.removeIf(agent -> agent.agentId.equals(agentId));
        saveConfig();
        log.info("Removed Claude Code Agent: {}", agentId);
    }

    /**
     * 列出所有 Agent
     */
    public List<AgentInfo> listAgents() {
        return new ArrayList<>(configData.agents);
    }

    /**
     * 检查 Agent 是否存在
     */
    public boolean exists(String agentId) {
        for (AgentInfo agent : configData.agents) {
            if (agent.agentId.equals(agentId)) {
                return true;
            }
        }
        return false;
    }

    /**
 * Agent 信息结构
     * 存储 Agent 基本信息 + mainSessionId（用于 MAIN 模式）
     */
    public static class AgentInfo {
        public String agentId;
        public String projectPath;
        public String mainSessionId;  // MAIN 模式的 session-id
        public String createdAt;
        public String updatedAt;
    }

    /**
     * 配置文件结构
     */
    private static class ConfigData {
        public List<AgentInfo> agents;
    }
}