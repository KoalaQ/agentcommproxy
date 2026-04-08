package org.openclaw.agentcommproxy.command;

import org.openclaw.agentcommproxy.config.ConfigManager;
import org.openclaw.agentcommproxy.store.SQLiteStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(
    name = "status",
    description = "Check message status by request ID"
)
public class StatusCommand implements Callable<Integer> {

    @Option(names = {"--request-id"}, description = "Request ID to check", required = true)
    private String requestId;

    @Override
    public Integer call() {
        ConfigManager configManager = new ConfigManager();
        SQLiteStore store = new SQLiteStore(configManager);

        var request = store.getRequestById(requestId);

        if (request.isEmpty()) {
            System.err.println("Request not found: " + requestId);
            return 1;
        }

        var r = request.get();
        System.out.println("Request ID: " + r.getId());
        System.out.println("Status: " + r.getStatus());
        System.out.println("Sender: " + r.getSender());
        System.out.println("Target Agent: " + r.getTargetAgent());
        System.out.println("Message: " + r.getMessage());
        System.out.println("Execute Retry: " + r.getExecuteRetryCount());
        System.out.println("Callback Retry: " + r.getCallbackRetryCount());

        if (r.getResponse() != null) {
            System.out.println("Response: " + r.getResponse());
        }
        if (r.getError() != null) {
            System.out.println("Error: " + r.getError());
        }

        return 0;
    }
}