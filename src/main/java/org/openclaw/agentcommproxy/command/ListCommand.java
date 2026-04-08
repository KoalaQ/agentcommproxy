package org.openclaw.agentcommproxy.command;

import org.openclaw.agentcommproxy.config.ConfigManager;
import org.openclaw.agentcommproxy.model.AgentRequest;
import org.openclaw.agentcommproxy.store.SQLiteStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.concurrent.Callable;

@Command(
    name = "list",
    description = "List request records from database"
)
public class ListCommand implements Callable<Integer> {

    @Option(names = {"--limit"}, description = "Limit number of records (default 10)")
    private int limit = 10;

    @Option(names = {"--status"}, description = "Filter by status (PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT/CALLBACK_PENDING/CALLBACK_DONE)")
    private String statusFilter;

    @Override
    public Integer call() {
        ConfigManager configManager = new ConfigManager();
        SQLiteStore store = new SQLiteStore(configManager);

        List<AgentRequest> requests = store.getAllRequests(limit, statusFilter);

        if (requests.isEmpty()) {
            System.out.println("No records found in database");
            System.out.println("");
            System.out.println("Database location: " + configManager.getDbPath());
            return 0;
        }

        System.out.println("Found " + requests.size() + " records:");
        System.out.println("");
        System.out.println("------------------------------------------------------------------------------------------------------------------------");
        System.out.printf("%-36s %-10s %-15s %-15s %-15s %-8s %-5s%n",
            "Request ID", "Status", "Sender", "Target", "Message", "Sync", "Retry");
        System.out.println("------------------------------------------------------------------------------------------------------------------------");

        for (AgentRequest r : requests) {
            String messagePreview = r.getMessage() != null && r.getMessage().length() > 15
                ? r.getMessage().substring(0, 15) + "..."
                : r.getMessage();
            System.out.printf("%-36s %-10s %-15s %-15s %-15s %-8s %-5d%n",
                r.getId(),
                r.getStatus(),
                r.getSender(),
                r.getTargetAgent(),
                messagePreview,
                r.isSync() ? "YES" : "NO",
                r.getRetryCount());
        }
        System.out.println("------------------------------------------------------------------------------------------------------------------------");
        System.out.println("");
        System.out.println("Database: " + configManager.getDbPath());

        return 0;
    }
}