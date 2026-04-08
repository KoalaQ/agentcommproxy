package org.openclaw.agentcommproxy.command;

import org.openclaw.agentcommproxy.config.ConfigManager;
import org.openclaw.agentcommproxy.store.SQLiteStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(
    name = "clear",
    description = "Clear message records from database"
)
public class ClearCommand implements Callable<Integer> {

    @Option(names = {"--all"}, description = "Clear all messages")
    private boolean clearAll;

    @Option(names = {"--status"}, description = "Clear messages by status (PENDING/EXECUTING/EXECUTE_SUCCESS/EXECUTE_FAILED/EXECUTE_TIMEOUT/CALLBACKING/CALLBACK_FAILED/DONE/ERROR)")
    private String status;

    @Option(names = {"--request-id"}, description = "Clear specific message by request ID")
    private String requestId;

    @Option(names = {"-y", "--yes"}, description = "Skip confirmation prompt")
    private boolean skipConfirm;

    @Override
    public Integer call() {
        ConfigManager configManager = new ConfigManager();
        SQLiteStore store = new SQLiteStore(configManager);

        // 没有指定任何选项，显示帮助
        if (!clearAll && status == null && requestId == null) {
            System.out.println("Usage: agentproxy clear [OPTIONS]");
            System.out.println("");
            System.out.println("Options:");
            System.out.println("  --all              Clear all messages");
            System.out.println("  --status <STATUS>  Clear messages by status");
            System.out.println("  --request-id <ID>  Clear specific message");
            System.out.println("  -y, --yes          Skip confirmation prompt");
            System.out.println("");
            System.out.println("Examples:");
            System.out.println("  agentproxy clear --all");
            System.out.println("  agentproxy clear --status DONE");
            System.out.println("  agentproxy clear --status ERROR");
            System.out.println("  agentproxy clear --request-id xxx-xxx-xxx");
            System.out.println("  agentproxy clear --all -y  # skip confirmation");
            return 0;
        }

        int count = 0;

        if (clearAll) {
            int total = store.getTotalCount();
            if (!skipConfirm) {
                System.out.print("Clear all " + total + " messages? (y/N): ");
                try {
                    String input = System.console() != null ? System.console().readLine() : "n";
                    if (!input.equalsIgnoreCase("y")) {
                        System.out.println("Cancelled");
                        return 0;
                    }
                } catch (Exception e) {
                    System.out.println("Cancelled");
                    return 0;
                }
            }
            count = store.clearAll();
            if (count >= 0) {
                System.out.println("Cleared " + count + " messages");
            } else {
                System.out.println("Failed to clear messages");
                return 1;
            }
        } else if (status != null) {
            count = store.clearByStatus(status);
            if (count >= 0) {
                System.out.println("Cleared " + count + " messages with status: " + status.toUpperCase());
            } else {
                System.out.println("Failed to clear messages");
                return 1;
            }
        } else if (requestId != null) {
            boolean success = store.clearById(requestId);
            if (success) {
                System.out.println("Cleared message: " + requestId);
            } else {
                System.out.println("Message not found: " + requestId);
                return 1;
            }
        }

        return 0;
    }
}