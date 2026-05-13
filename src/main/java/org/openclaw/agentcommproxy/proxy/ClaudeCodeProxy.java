package org.openclaw.agentcommproxy.proxy;

import org.openclaw.agentcommproxy.config.ClaudeCodeAgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Claude Code 命令代理实现
 * sessionId=null：创建新会话（--session-id）
 * sessionId有值：恢复已有会话（--resume）
 */
public class ClaudeCodeProxy implements CommandProxy {
    private static final Logger log = LoggerFactory.getLogger(ClaudeCodeProxy.class);

    private final ClaudeCodeAgentConfig agentConfig;

    public ClaudeCodeProxy() {
        this.agentConfig = new ClaudeCodeAgentConfig();
    }

    @Override
    public String getName() {
        return "claude-code";
    }

    @Override
    public CommandResult execute(String agent, String message, int timeout, String sessionId) {
        // 获取 Claude Code Agent 的项目路径
        String projectPath = agentConfig.getProjectPath(agent);
        if (projectPath == null) {
            log.error("Claude Code Agent not registered: {}", agent);
            return CommandResult.failure("Agent not registered: " + agent, -1);
        }

        String escapedMessage = escapeMessage(message);
        String command;
        String actualSessionId;

        if (sessionId == null || sessionId.isEmpty()) {
            // 无 sessionId：创建新会话
            actualSessionId = UUID.randomUUID().toString();
            command = String.format("claude --session-id %s -p \"%s\"", actualSessionId, escapedMessage);
            log.info("Creating new session with --session-id {}", actualSessionId);
        } else {
            // 有 sessionId：恢复已有会话
            actualSessionId = sessionId;
            command = String.format("claude --resume %s -p \"%s\"", sessionId, escapedMessage);
            log.info("Resuming existing session with --resume {}", sessionId);
        }

        log.info("Executing Claude Code command in directory: {}", projectPath);
        log.info("Command: {}", command);

        try {
            ProcessBuilder pb = new ProcessBuilder();

            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                pb.command("cmd", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }

            pb.directory(new File(projectPath));
            pb.redirectErrorStream(false);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            StringBuilder error = new StringBuilder();

            Thread outputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
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
                log.warn("Claude Code command timed out after {} seconds", timeout);
                return CommandResult.timeout();
            }

            int exitCode = process.exitValue();
            String outputStr = output.toString().trim();
            String errorStr = error.toString().trim();

            if (exitCode == 0) {
                log.info("Claude Code command succeeded");
                // 返回 sessionId（新建或复用的）
                return CommandResult.success(outputStr, actualSessionId);
            } else {
                log.warn("Claude Code command failed with exit code {}: {}", exitCode, errorStr);
                return CommandResult.failure(errorStr, exitCode);
            }

        } catch (Exception e) {
            log.error("Failed to execute Claude Code command: {}", e.getMessage());
            return CommandResult.failure(e.getMessage(), -2);
        }
    }

    @Override
    public String buildCommand(String agent, String message, int timeout, String sessionId) {
        String escapedMessage = escapeMessage(message);

        if (sessionId != null && !sessionId.isEmpty()) {
            return String.format("claude --resume %s -p \"%s\"", sessionId, escapedMessage);
        } else {
            String newSessionId = UUID.randomUUID().toString();
            return String.format("claude --session-id %s -p \"%s\"", newSessionId, escapedMessage);
        }
    }

    private String escapeMessage(String message) {
        if (message == null) {
            return "";
        }
        return message.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}