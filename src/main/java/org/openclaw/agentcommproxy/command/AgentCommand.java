package org.openclaw.agentcommproxy.command;

import org.openclaw.agentcommproxy.config.ConfigManager;
import org.openclaw.agentcommproxy.model.AgentRequest;
import org.openclaw.agentcommproxy.model.MessageStatus;
import org.openclaw.agentcommproxy.model.SenderType;
import org.openclaw.agentcommproxy.service.AgentService;
import org.openclaw.agentcommproxy.store.SQLiteStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

@Command(
    name = "agent",
    description = "Send message to target agent"
)
public class AgentCommand implements Callable<Integer> {

    @Option(names = {"--agent"}, description = "Target agent name", required = true)
    private String targetAgent;

    @Option(names = {"--message"}, description = "Message content", required = true)
    private String message;

    @Option(names = {"--sender"}, description = "Sender agent name (for callback)", required = true)
    private String sender;

    @Option(names = {"--sync"}, description = "Sync mode (wait for response)")
    private boolean sync;

    @Option(names = {"--timeout"}, description = "Timeout in seconds (default 300)")
    private int timeout;

    @Option(names = {"--request-id"}, description = "Request ID (optional, for retry)")
    private String requestId;

    @Override
    public Integer call() {
        ConfigManager configManager = new ConfigManager();
        SQLiteStore store = new SQLiteStore(configManager);
        AgentService agentService = new AgentService(configManager, store);

        // 设置默认超时
        if (timeout <= 0) {
            timeout = configManager.getDefaultTimeout();
        }

        AgentRequest request = new AgentRequest();
        request.setSender(sender);
        request.setTargetAgent(targetAgent);
        request.setMessage(message);
        request.setSync(sync);
        request.setTimeout(timeout);
        request.setSenderType(SenderType.CLI);  // CLI 命令固定使用 CLI 回调

        // 指定请求ID（用于重试或修改）
        if (requestId != null) {
            request.setId(requestId);
        }

        if (sync) {
            // 同步模式：立即执行并返回结果
            AgentRequest result = agentService.sendSync(request);

            if (result.getStatus() == MessageStatus.DONE) {
                System.out.println(result.getResponse());
                return 0;
            } else {
                System.err.println("Error: " + result.getError());
                return 1;
            }
        } else {
            // 异步模式：保存请求并返回请求ID
            AgentRequest saved = agentService.sendAsync(request);
            System.out.println("已收到、稍后回复您，消息唯一标识: " + saved.getId());
            return 0;
        }
    }
}