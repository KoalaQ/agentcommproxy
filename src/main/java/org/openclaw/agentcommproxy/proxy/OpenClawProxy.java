package org.openclaw.agentcommproxy.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * OpenClaw 命令代理实现
 * 执行 openclaw agent 命令
 */
public class OpenClawProxy implements CommandProxy {
    private static final Logger log = LoggerFactory.getLogger(OpenClawProxy.class);

    @Override
    public String getName() {
        return "openclaw";
    }

    @Override
    public CommandResult execute(String agent, String message, int timeout, String sessionId) {
        String command = buildCommand(agent, message, timeout, sessionId);
        log.info("Executing command: {}", command);

        try {
            ProcessBuilder pb = new ProcessBuilder();

            // Windows 和 Unix 兼容处理
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                pb.command("cmd", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }

            pb.redirectErrorStream(false);
            Process process = pb.start();

            // 读取输出
            StringBuilder output = new StringBuilder();
            StringBuilder error = new StringBuilder();

            Thread outputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // 过滤掉日志行（以 [ 开头的）
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

            // 等待完成或超时
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
                return CommandResult.success(outputStr);
            } else {
                log.warn("Command failed with exit code {}: {}", exitCode, errorStr);
                return CommandResult.failure(errorStr, exitCode);
            }

        } catch (Exception e) {
            log.error("Failed to execute command: {}", e.getMessage());
            return CommandResult.failure(e.getMessage(), -2);
        }
    }

    @Override
    public String buildCommand(String agent, String message, int timeout, String sessionId) {
        String escapedMessage = escapeMessage(message);
        if (sessionId != null && !sessionId.isEmpty()) {
            // 使用 --session-id 参数，session 已关联 agent，无需指定 agent
            return String.format("openclaw agent --session-id %s --message \"%s\" --timeout %d",
                    sessionId, escapedMessage, timeout);
        } else {
            // 主会话模式，需要指定 agent
            return String.format("openclaw agent --agent %s --message \"%s\" --timeout %d",
                    agent, escapedMessage, timeout);
        }
    }

    /**
     * 转义消息中的特殊字符
     */
    private String escapeMessage(String message) {
        if (message == null) {
            return "";
        }
        // 转义双引号和反斜杠
        return message.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 判断是否为日志行（需要过滤）
     */
    private boolean isLogLevel(String line) {
        if (line == null || line.isEmpty()) {
            return true;  // 过滤空行
        }

        // 移除 ANSI 颜色码，获取纯文本
        String cleanLine = line.replaceAll("\\x1b\\[[0-9;]*m", "")
                               .replaceAll("\\[\\d+m", "");

        String trimmed = cleanLine.trim();

        // 过滤空内容
        if (trimmed.isEmpty()) {
            return true;
        }

        // 过滤 Config warnings
        if (trimmed.startsWith("Config warnings")) {
            return true;
        }

        // 过滤 OpenClaw 版本信息
        if (trimmed.contains("OpenClaw") && trimmed.contains("—")) {
            return true;
        }

        // 过滤时间戳开头的日志 (如 14:56:55+08:00)
        if (trimmed.matches("^\\d{2}:\\d{2}:\\d{2}.*")) {
            return true;
        }

        // 过滤以 [plugins] 开头的日志（包含颜色码的情况）
        if (trimmed.startsWith("[plugins]")) {
            return true;
        }

        // 过滤包含 Registered 的插件注册日志
        if (trimmed.contains("Registered")) {
            return true;
        }

        // 过滤标准日志级别开头
        if (trimmed.startsWith("[INFO]")
            || trimmed.startsWith("[DEBUG]")
            || trimmed.startsWith("[WARN]")
            || trimmed.startsWith("[ERROR]")) {
            return true;
        }

        // 过滤分隔符符号
        if (trimmed.equals("│") || trimmed.equals("◇") || trimmed.equals("◆")) {
            return true;
        }

        return false;
    }
}