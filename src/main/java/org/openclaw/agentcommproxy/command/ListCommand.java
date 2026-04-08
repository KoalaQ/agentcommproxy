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

    @Option(names = {"--status"}, description = "Filter by status (PENDING/EXECUTING/EXECUTE_SUCCESS/EXECUTE_FAILED/EXECUTE_TIMEOUT/CALLBACKING/CALLBACK_FAILED/DONE/ERROR)")
    private String statusFilter;

    @Option(names = {"--full"}, description = "Show full message and response (no truncation)")
    private boolean full;

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

        for (AgentRequest r : requests) {
            System.out.println("--------------------------------------------------------------------------------");
            System.out.println("Request ID:  " + r.getId());
            System.out.println("Status:      " + r.getStatus());
            System.out.println("Sender:      " + r.getSender());
            System.out.println("Target:      " + r.getTargetAgent());
            System.out.println("Sync:        " + (r.isSync() ? "YES" : "NO"));
            System.out.println("Timeout:     " + r.getTimeout() + "s");
            System.out.println("Exec Retry:  " + r.getExecuteRetryCount());
            System.out.println("Callback Retry: " + r.getCallbackRetryCount());

            // Message
            String message = r.getMessage();
            if (message != null && !message.isEmpty()) {
                if (full) {
                    System.out.println("Message:     " + message);
                } else {
                    System.out.println("Message:     " + truncate(message, 50));
                }
            }

            // Response
            String response = r.getResponse();
            if (response != null && !response.isEmpty()) {
                if (full) {
                    System.out.println("Response:    " + response);
                } else {
                    System.out.println("Response:    " + truncate(response, 50));
                }
            }

            // Error
            String error = r.getError();
            if (error != null && !error.isEmpty()) {
                System.out.println("Error:       " + error);
            }
        }

        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("");
        System.out.println("Total: " + requests.size() + " records");
        System.out.println("Database: " + configManager.getDbPath());
        System.out.println("");
        System.out.println("Tips: Use --full to show full content, --limit N to limit records");

        return 0;
    }

    private String truncate(String str, int maxLen) {
        if (str == null) {
            return "";
        }
        // 移除换行符，单行显示
        String singleLine = str.replace("\n", " ").replace("\r", "");
        if (singleLine.length() <= maxLen) {
            return singleLine;
        }
        return singleLine.substring(0, maxLen) + "...";
    }
}