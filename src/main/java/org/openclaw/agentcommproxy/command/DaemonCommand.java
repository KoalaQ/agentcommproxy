package org.openclaw.agentcommproxy.command;

import org.openclaw.agentcommproxy.daemon.DaemonManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

@Command(
    name = "daemon",
    description = "Manage background daemon process",
    subcommands = {
        DaemonCommand.Start.class,
        DaemonCommand.Stop.class,
        DaemonCommand.Status.class
    }
)
public class DaemonCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println("Usage: agentproxy daemon [start|stop|status]");
        System.out.println("");
        System.out.println("Commands:");
        System.out.println("  start  Start daemon process");
        System.out.println("  stop   Stop daemon process");
        System.out.println("  status Check daemon status");
        System.out.println("");
        System.out.println("Examples:");
        System.out.println("  # 前台运行（阻塞模式，Ctrl+C 停止）");
        System.out.println("  agentproxy daemon start --foreground");
        System.out.println("");
        System.out.println("  # 后台运行（使用 nohup）");
        System.out.println("  nohup agentproxy daemon start --foreground > /dev/null 2>&1 &");
        return 0;
    }

    @Command(name = "start", description = "Start daemon process")
    public static class Start implements Callable<Integer> {

        @Parameters(index = "0", description = "Interval in seconds (default from config)", arity = "0..1")
        private Integer interval;

        @Option(names = {"-f", "--foreground"}, description = "Run in foreground mode (blocking)")
        private boolean foreground;

        @Override
        public Integer call() {
            DaemonManager daemonManager = DaemonManager.getInstance();

            if (daemonManager.isRunning()) {
                System.out.println("Daemon is already running");
                return 0;
            }

            if (foreground) {
                // 前台模式，阻塞运行
                System.out.println("Starting daemon in foreground mode...");
                System.out.println("Interval: " + (interval != null ? interval : daemonManager.getInterval()) + " seconds");
                System.out.println("Press Ctrl+C to stop");
                System.out.println("");
                daemonManager.start(interval, true);
            } else {
                // 后台模式提示
                System.out.println("To run daemon in background, use:");
                System.out.println("");
                System.out.println("  nohup agentproxy daemon start --foreground > ~/.agentcommproxy/logs/daemon.log 2>&1 &");
                System.out.println("");
                System.out.println("Or run with --foreground to block in current terminal:");
                System.out.println("");
                System.out.println("  agentproxy daemon start --foreground");
            }
            return 0;
        }
    }

    @Command(name = "stop", description = "Stop daemon process")
    public static class Stop implements Callable<Integer> {

        @Override
        public Integer call() {
            DaemonManager daemonManager = DaemonManager.getInstance();

            if (!daemonManager.isRunning()) {
                System.out.println("Daemon is not running");
                System.out.println("");
                System.out.println("To kill background daemon process:");
                System.out.println("  pkill -f 'agentproxy daemon start'");
                return 0;
            }

            daemonManager.stop();
            System.out.println("Daemon stopped");
            return 0;
        }
    }

    @Command(name = "status", description = "Check daemon status")
    public static class Status implements Callable<Integer> {

        @Override
        public Integer call() {
            DaemonManager daemonManager = DaemonManager.getInstance();

            System.out.println("Daemon status:");
            if (daemonManager.isRunning()) {
                System.out.println("  Running: YES (in current process)");
                System.out.println("  Interval: " + daemonManager.getInterval() + "s");
            } else {
                System.out.println("  Running: NO (in current process)");
            }
            System.out.println("");
            System.out.println("To check background daemon process:");
            System.out.println("  ps aux | grep 'agentproxy daemon'");
            return 0;
        }
    }
}