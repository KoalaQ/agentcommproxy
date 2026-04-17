package org.openclaw.agentcommproxy;

import picocli.CommandLine;
import picocli.CommandLine.Command;

import org.openclaw.agentcommproxy.command.AgentCommand;
import org.openclaw.agentcommproxy.command.ClearCommand;
import org.openclaw.agentcommproxy.command.DaemonCommand;
import org.openclaw.agentcommproxy.command.HttpCommand;
import org.openclaw.agentcommproxy.command.ListCommand;
import org.openclaw.agentcommproxy.command.StatusCommand;
import org.openclaw.agentcommproxy.config.ConfigManager;
import org.openclaw.agentcommproxy.daemon.DaemonManager;
import org.openclaw.agentcommproxy.http.HttpServerManager;
import org.openclaw.agentcommproxy.service.AgentService;
import org.openclaw.agentcommproxy.store.SQLiteStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(
    name = "agentproxy",
    version = "1.0.0",
    description = "Agent Communication Proxy CLI",
    subcommands = {
        AgentCommand.class,
        ClearCommand.class,
        DaemonCommand.class,
        HttpCommand.class,
        ListCommand.class,
        StatusCommand.class
    },
    mixinStandardHelpOptions = true
)
public class CliRunner implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(CliRunner.class);

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @CommandLine.Option(names = {"--http-port"}, description = "HTTP service port (override config)")
    private Integer httpPort;

    @CommandLine.Option(names = {"--no-http"}, description = "Disable HTTP service auto-start")
    private boolean noHttp;

    @CommandLine.Option(names = {"--no-daemon"}, description = "Disable daemon auto-start")
    private boolean noDaemon;

    // getter for subcommands to access global option
    public Integer getHttpPort() {
        return httpPort;
    }

    public static void main(String[] args) {
        // 初始化核心服务
        ConfigManager configManager = new ConfigManager();
        SQLiteStore store = new SQLiteStore(configManager);
        AgentService agentService = new AgentService(configManager, store);

        // 启动守护进程（除非明确禁用或正在执行 daemon/http/clear 命令）
        // daemon 命令全部跳过自动启动，由 daemon 子命令自己控制
        boolean isDaemonCommand = containsCommand(args, "daemon");
        boolean isHttpCommand = containsCommand(args, "http");
        boolean isClearCommand = containsCommand(args, "clear");
        boolean skipAutoStart = isDaemonCommand || isHttpCommand || isClearCommand;

        if (!skipAutoStart && !hasNoDaemonFlag(args) && configManager.isDaemonEnabled()) {
            DaemonManager daemon = DaemonManager.getInstance();
            if (!daemon.isRunning()) {
                daemon.start(null, false);
                log.info("Daemon started automatically");
            }
        }

        // 启动 HTTP 服务（除非明确禁用或正在执行 daemon/http/clear 命令）
        if (!skipAutoStart && !hasNoHttpFlag(args) && configManager.isHttpEnabled()) {
            HttpServerManager http = HttpServerManager.getInstance();
            if (!http.isRunning()) {
                int port = getHttpPortOverride(args, configManager.getHttpPort());
                http.start(port, configManager.getApiKey(), agentService, store, configManager);
                log.info("HTTP service started automatically on port {}", port);
            }
        }

        int exitCode = new CommandLine(new CliRunner()).execute(args);
        System.exit(exitCode);
    }

    private static boolean hasNoHttpFlag(String[] args) {
        for (String arg : args) {
            if (arg.equals("--no-http")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNoDaemonFlag(String[] args) {
        for (String arg : args) {
            if (arg.equals("--no-daemon")) {
                return true;
            }
        }
        return false;
    }

    private static int getHttpPortOverride(String[] args, int defaultPort) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--http-port")) {
                try {
                    return Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    return defaultPort;
                }
            }
        }
        return defaultPort;
    }

    /**
     * 检查参数中是否包含指定命令
     * 跳过全局选项及其参数值
     */
    private static boolean containsCommand(String[] args, String command) {
        // 需要参数值的选项
        java.util.Set<String> optionsWithArg = java.util.Set.of("--http-port");
        boolean skipNext = false;

        for (String arg : args) {
            // 如果上一个参数是需要值的选项，跳过当前参数（它是选项值）
            if (skipNext) {
                skipNext = false;
                continue;
            }
            // 跳过全局选项
            if (arg.startsWith("--")) {
                // 如果这个选项需要参数值，标记下一个要跳过
                if (optionsWithArg.contains(arg)) {
                    skipNext = true;
                }
                continue;
            }
            // 第一个非选项参数就是命令
            return arg.equals(command);
        }
        return false;
    }

    /**
     * 检查参数中是否包含指定子命令
     * 子命令是命令后的第一个参数
     */
    private static boolean containsSubCommand(String[] args, String... subCommands) {
        java.util.Set<String> optionsWithArg = java.util.Set.of("--http-port");
        boolean skipNext = false;
        boolean foundCommand = false;

        for (String arg : args) {
            if (skipNext) {
                skipNext = false;
                continue;
            }
            if (arg.startsWith("--")) {
                if (optionsWithArg.contains(arg)) {
                    skipNext = true;
                }
                continue;
            }
            if (!foundCommand) {
                // 第一个非选项参数是主命令
                foundCommand = true;
                continue;
            }
            // 第二个非选项参数是子命令
            for (String sub : subCommands) {
                if (arg.equals(sub)) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    @Override
    public void run() {
        spec.commandLine().usage(System.out);

        HttpServerManager http = HttpServerManager.getInstance();
        if (http.isRunning()) {
            System.out.println("");
            System.out.println("HTTP service is running on port " + http.getPort());
            ConfigManager config = new ConfigManager();
            System.out.println("API Key: " + config.getApiKey());
        }
    }
}