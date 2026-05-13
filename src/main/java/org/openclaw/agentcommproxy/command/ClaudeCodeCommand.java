package org.openclaw.agentcommproxy.command;

import org.openclaw.agentcommproxy.config.ClaudeCodeAgentConfig;
import org.openclaw.agentcommproxy.config.ConfigManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(
    name = "claude-code",
    description = "Manage Claude Code Agents",
    subcommands = {
        ClaudeCodeCommand.Register.class,
        ClaudeCodeCommand.List.class,
        ClaudeCodeCommand.Remove.class,
        ClaudeCodeCommand.Reload.class
    }
)
public class ClaudeCodeCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println("Usage: agentproxy claude-code [register|list|remove|reload]");
        System.out.println("");
        System.out.println("Commands:");
        System.out.println("  register  Register a Claude Code Agent");
        System.out.println("  list      List all registered Claude Code Agents");
        System.out.println("  remove    Remove a Claude Code Agent");
        System.out.println("  reload    Reload Claude Code Agents config");
        System.out.println("");
        System.out.println("Examples:");
        System.out.println("  agentproxy claude-code register --agent claude-code-myproject --path /path/to/project");
        System.out.println("  agentproxy claude-code list");
        System.out.println("  agentproxy claude-code remove --agent claude-code-myproject");
        System.out.println("  agentproxy claude-code reload");
        return 0;
    }

    @Command(name = "register", description = "Register a Claude Code Agent")
    public static class Register implements Callable<Integer> {

        @Option(names = {"--agent"}, description = "Agent ID (e.g., claude-code-myproject)", required = true)
        private String agentId;

        @Option(names = {"--path"}, description = "Project path (working directory for Claude Code)", required = true)
        private String projectPath;

        @Override
        public Integer call() {
            ConfigManager configManager = new ConfigManager();
            ClaudeCodeAgentConfig agentConfig = new ClaudeCodeAgentConfig(configManager);

            // 检查路径是否存在
            java.io.File pathDir = new java.io.File(projectPath);
            if (!pathDir.exists() || !pathDir.isDirectory()) {
                System.out.println("Error: Path does not exist or is not a directory: " + projectPath);
                return 1;
            }

            agentConfig.registerAgent(agentId, projectPath);

            System.out.println("Claude Code Agent registered:");
            System.out.println("  Agent ID: " + agentId);
            System.out.println("  Project Path: " + projectPath);
            System.out.println("");
            System.out.println("Usage:");
            System.out.println("  agentproxy agent --agent " + agentId + " --sender <sender> --message <msg> --proxy claude-code --sync");
            return 0;
        }
    }

    @Command(name = "list", description = "List all registered Claude Code Agents")
    public static class List implements Callable<Integer> {

        @Override
        public Integer call() {
            ConfigManager configManager = new ConfigManager();
            ClaudeCodeAgentConfig agentConfig = new ClaudeCodeAgentConfig(configManager);

            java.util.List<ClaudeCodeAgentConfig.AgentInfo> agents = agentConfig.listAgents();

            if (agents.isEmpty()) {
                System.out.println("No Claude Code Agents registered");
                return 0;
            }

            System.out.println("Registered Claude Code Agents (" + agents.size() + "):");
            System.out.println("--------------------------------------------------------------------------------");
            for (ClaudeCodeAgentConfig.AgentInfo agent : agents) {
                System.out.println("  Agent ID:     " + agent.agentId);
                System.out.println("  Project Path: " + agent.projectPath);
                System.out.println("  Created:      " + agent.createdAt);
                System.out.println("  Updated:      " + agent.updatedAt);
                System.out.println("--------------------------------------------------------------------------------");
            }
            return 0;
        }
    }

    @Command(name = "remove", description = "Remove a Claude Code Agent")
    public static class Remove implements Callable<Integer> {

        @Option(names = {"--agent"}, description = "Agent ID to remove", required = true)
        private String agentId;

        @Override
        public Integer call() {
            ConfigManager configManager = new ConfigManager();
            ClaudeCodeAgentConfig agentConfig = new ClaudeCodeAgentConfig(configManager);

            if (!agentConfig.exists(agentId)) {
                System.out.println("Error: Agent not found: " + agentId);
                return 1;
            }

            agentConfig.removeAgent(agentId);
            System.out.println("Claude Code Agent removed: " + agentId);
            return 0;
        }
    }

    @Command(name = "reload", description = "Reload Claude Code Agents config")
    public static class Reload implements Callable<Integer> {

        @Override
        public Integer call() {
            ConfigManager configManager = new ConfigManager();
            ClaudeCodeAgentConfig agentConfig = new ClaudeCodeAgentConfig(configManager);

            // 重新加载会触发 loadConfig()
            System.out.println("Claude Code Agents config reloaded");
            System.out.println("Registered agents: " + agentConfig.listAgents().size());
            return 0;
        }
    }
}