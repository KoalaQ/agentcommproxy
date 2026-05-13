package org.openclaw.agentcommproxy.proxy;

import org.openclaw.agentcommproxy.config.ConfigManager;
import org.openclaw.agentcommproxy.session.OpenClawSessionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * OpenClaw 命令代理实现
 * sessionId=null：创建新会话（编辑 sessions.json）
 * sessionId有值：使用已有会话（--session-id）
 */
public class OpenClawProxy implements CommandProxy {
    private static final Logger log = LoggerFactory.getLogger(OpenClawProxy.class);

    private final ConfigManager configManager;
    private final OpenClawSessionHelper sessionHelper;

    public OpenClawProxy() {
        this.configManager = new ConfigManager();
        this.sessionHelper = new OpenClawSessionHelper(configManager);
    }

    public OpenClawProxy(ConfigManager configManager) {
        this.configManager = configManager;
        this.sessionHelper = new OpenClawSessionHelper(configManager);
    }

    @Override
    public String getName() {
        return "openclaw";
    }

    @Override
    public CommandResult execute(String agent, String message, int timeout, String sessionId) {
        String actualSessionId = sessionId;

        // sessionId=null 时需要创建新会话（INDEPENDENT模式首次调用）
        if (sessionId == null || sessionId.isEmpty()) {
            // 对于 INDEPENDENT 模式，需要 taskId 来创建独立会话
            // 但 execute 方法签名中没有 taskId，这里暂时生成一个 UUID
            // 实际使用时，AgentService 会先通过 SessionManager.findSessionIdByTaskId 查询
            // 如果查不到，需要传入 taskId 来创建
            //
            // 简化方案：MAIN 模式不创建 sessionId，直接用 --agent 参数
            // INDEPENDENT 模式的创建逻辑在 AgentService 中处理
            log.info("No sessionId provided, using MAIN mode (agent-based)");
        }

        String command = buildCommand(agent, message, timeout, actualSessionId);
        log.info("Executing command: {}", command);

        try {
            ProcessBuilder pb = new ProcessBuilder();

            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                pb.command("cmd", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }

            pb.redirectErrorStream(false);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            StringBuilder error = new StringBuilder();

            Thread outputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!isLogLevel(line)) {
                            output.append(line).append("\n");
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error reading output stream: {}", e.getMessage());
                }
            });

            Thread errorThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        error.append(line).append("\n");
                    }
                } catch (Exception e) {
                    log.warn("Error reading error stream: {}", e.getMessage());
                }
            });

            outputThread.start();
            errorThread.start();

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);

            outputThread.join(1000);
            errorThread.join(1000);

            if (!finished) {
                process.destroyForcibly();
                log.warn("Command timed out after {} seconds", timeout);
                return CommandResult.timeout();
            }

            int exitCode = process.exitValue();
            String outputStr = output.toString().trim();
            String errorStr = error.toString().trim();

            if (exitCode == 0) {
                log.info("Command succeeded with output: {}", outputStr);
                // 返回 sessionId（如果有的话）
                return CommandResult.success(outputStr, actualSessionId);
            } else {
                log.warn("Command failed with exit code {}: {}", exitCode, errorStr);
                return CommandResult.failure(errorStr, exitCode);
            }

        } catch (Exception e) {
            log.error("Failed to execute command: {}", e.getMessage());
            return CommandResult.failure(e.getMessage(), -2);
        }
    }

    /**
     * 创建独立会话（INDEPENDENT 模式）
     * 由 AgentService 调用
     */
    public String createIndependentSession(String agentId, String taskId, boolean clearSession) {
        sessionHelper.ensureMainSessionExists(agentId);
        return sessionHelper.createIndependentSession(agentId, taskId, clearSession);
    }

    @Override
    public String buildCommand(String agent, String message, int timeout, String sessionId) {
        String escapedMessage = escapeMessage(message);
        if (sessionId != null && !sessionId.isEmpty()) {
            return String.format("openclaw agent --session-id %s --message \"%s\" --timeout %d",
                    sessionId, escapedMessage, timeout);
        } else {
            return String.format("openclaw agent --agent %s --message \"%s\" --timeout %d",
                    agent, escapedMessage, timeout);
        }
    }

    private String escapeMessage(String message) {
        if (message == null) {
            return "";
        }
        return message.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean isLogLevel(String line) {
        if (line == null || line.isEmpty()) {
            return true;
        }

        String cleanLine = line.replaceAll("\\x1b\\[[0-9;]*m", "")
                               .replaceAll("\\[\\d+m", "");

        String trimmed = cleanLine.trim();

        if (trimmed.isEmpty()) {
            return true;
        }

        if (trimmed.startsWith("Config warnings")) {
            return true;
        }

        if (trimmed.contains("OpenClaw") && trimmed.contains("—")) {
            return true;
        }

        if (trimmed.matches("^\\d{2}:\\d{2}:\\d{2}.*")) {
            return true;
        }

        if (trimmed.startsWith("[plugins]")) {
            return true;
        }

        if (trimmed.contains("Registered")) {
            return true;
        }

        if (trimmed.startsWith("[INFO]")
            || trimmed.startsWith("[DEBUG]")
            || trimmed.startsWith("[WARN]")
            || trimmed.startsWith("[ERROR]")) {
            return true;
        }

        if (trimmed.equals("│") || trimmed.equals("◇") || trimmed.equals("◆")) {
            return true;
        }

        return false;
    }
}