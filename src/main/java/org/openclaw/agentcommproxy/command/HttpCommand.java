package org.openclaw.agentcommproxy.command;

import org.openclaw.agentcommproxy.config.ConfigManager;
import org.openclaw.agentcommproxy.http.HttpServerManager;
import org.openclaw.agentcommproxy.service.AgentService;
import org.openclaw.agentcommproxy.store.SQLiteStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(
    name = "http",
    description = "Manage HTTP API service",
    subcommands = {
        HttpCommand.Start.class,
        HttpCommand.Stop.class,
        HttpCommand.Status.class,
        HttpCommand.GenKey.class
    }
)
public class HttpCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println("Usage: agentproxy http [start|stop|status|gen-key]");
        System.out.println("");
        System.out.println("Commands:");
        System.out.println("  start   Start HTTP service");
        System.out.println("  stop    Stop HTTP service");
        System.out.println("  status  Check HTTP service status");
        System.out.println("  gen-key Generate new API key");
        System.out.println("");
        System.out.println("Examples:");
        System.out.println("  agentproxy http start --port 8080");
        System.out.println("  agentproxy http stop");
        System.out.println("  agentproxy http status");
        System.out.println("  agentproxy http gen-key");
        return 0;
    }

    @Command(name = "start", description = "Start HTTP service")
    public static class Start implements Callable<Integer> {

        @Option(names = {"-p", "--port"}, description = "HTTP port (default from config)")
        private Integer port;

        @Override
        public Integer call() {
            ConfigManager configManager = new ConfigManager();
            SQLiteStore store = new SQLiteStore(configManager);
            AgentService agentService = new AgentService(configManager, store);
            HttpServerManager httpManager = HttpServerManager.getInstance();

            if (httpManager.isRunning()) {
                System.out.println("HTTP service is already running on port " + httpManager.getPort());
                return 0;
            }

            int actualPort = port != null ? port : configManager.getHttpPort();

            httpManager.start(actualPort, configManager.getApiKey(), agentService, store, configManager);

            if (httpManager.isRunning()) {
                System.out.println("HTTP service started on port " + actualPort);
                System.out.println("API Key: " + configManager.getApiKey());
            } else {
                System.out.println("Failed to start HTTP service");
                return 1;
            }

            return 0;
        }
    }

    @Command(name = "stop", description = "Stop HTTP service")
    public static class Stop implements Callable<Integer> {

        @Override
        public Integer call() {
            HttpServerManager httpManager = HttpServerManager.getInstance();

            if (!httpManager.isRunning()) {
                System.out.println("HTTP service is not running");
                return 0;
            }

            httpManager.stop();
            System.out.println("HTTP service stopped");
            return 0;
        }
    }

    @Command(name = "status", description = "Check HTTP service status")
    public static class Status implements Callable<Integer> {

        @Override
        public Integer call() {
            HttpServerManager httpManager = HttpServerManager.getInstance();
            ConfigManager configManager = new ConfigManager();

            System.out.println("HTTP service status:");
            if (httpManager.isRunning()) {
                System.out.println("  Running: YES");
                System.out.println("  Port: " + httpManager.getPort());
            } else {
                System.out.println("  Running: NO");
                System.out.println("  Configured port: " + configManager.getHttpPort());
            }
            System.out.println("");
            System.out.println("API Key: " + configManager.getApiKey());
            System.out.println("HTTP Enabled: " + configManager.isHttpEnabled());
            return 0;
        }
    }

    @Command(name = "gen-key", description = "Generate new API key")
    public static class GenKey implements Callable<Integer> {

        @Override
        public Integer call() {
            ConfigManager configManager = new ConfigManager();

            String oldKey = configManager.getApiKey();
            configManager.generateNewApiKey();
            String newKey = configManager.getApiKey();

            System.out.println("New API key generated:");
            System.out.println("  Old key: " + oldKey);
            System.out.println("  New key: " + newKey);
            System.out.println("");
            System.out.println("Note: Restart HTTP service to use the new key");
            return 0;
        }
    }
}