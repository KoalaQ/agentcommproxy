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

    public static void main(String[] args) {
        // 初始化核心服务
        ConfigManager configManager = new ConfigManager();
        SQLiteStore store = new SQLiteStore(configManager);
        AgentService agentService = new AgentService(configManager, store);

        // 启动守护进程（除非明确禁用或正在执行 daemon 命令）
        boolean isDaemonCommand = args.length > 0 && args[0].equals("daemon");
        if (!isDaemonCommand && !hasNoDaemonFlag(args) && configManager.isDaemonEnabled()) {
            DaemonManager daemon = DaemonManager.getInstance();
            if (!daemon.isRunning()) {
                daemon.start(null, false);
                log.info("Daemon started automatically");
            }
        }

        // 启动 HTTP 服务（除非明确禁用或正在执行 http 命令）
        boolean isHttpCommand = args.length > 0 && args[0].equals("http");
        if (!isHttpCommand && !hasNoHttpFlag(args) && configManager.isHttpEnabled()) {
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