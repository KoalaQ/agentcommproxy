package org.openclaw.agentcommproxy.callback;

import org.openclaw.agentcommproxy.config.ConfigManager;
import org.openclaw.agentcommproxy.model.AgentRequest;
import org.openclaw.agentcommproxy.model.SenderType;
import org.openclaw.agentcommproxy.proxy.CommandProxy;
import org.openclaw.agentcommproxy.proxy.CommandProxyFactory;
import org.openclaw.agentcommproxy.proxy.CommandResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI 回调处理器
 * 通过执行 CLI 命令方式回调发送方
 */
public class CliCallbackHandler implements CallbackHandler {

    private static final Logger log = LoggerFactory.getLogger(CliCallbackHandler.class);

    private final CommandProxy proxy;
    private final ConfigManager configManager;

    public CliCallbackHandler(ConfigManager configManager) {
        this.configManager = configManager;
        this.proxy = CommandProxyFactory.getDefaultProxy();
    }

    @Override
    public boolean doCallback(AgentRequest request) {
        log.info("CLI callback for request: {} to sender: {}", request.getId(), request.getSender());

        String callbackMessage = buildCallbackMessage(request);

        CommandResult result = proxy.execute(
            request.getSender(),
            callbackMessage,
            configManager.getDefaultTimeout()
        );

        if (result.isSuccess()) {
            log.info("CLI callback success: {}", request.getId());
            return true;
        } else {
            log.warn("CLI callback failed: {} - {}", request.getId(), result.getError());
            return false;
        }
    }

    @Override
    public SenderType getHandlerType() {
        return SenderType.CLI;
    }

    /**
     * 构建 CLI 回调消息格式
     */
    private String buildCallbackMessage(AgentRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Request ID: ").append(request.getId()).append("\n");
        sb.append("Sender: ").append(request.getSender()).append("\n");
        sb.append("Target Agent: ").append(request.getTargetAgent()).append("\n");
        sb.append("Message: ").append(request.getMessage()).append("\n");
        if (request.getResponse() != null && !request.getResponse().isEmpty()) {
            sb.append("Response: ").append(request.getResponse());
        }
        return sb.toString();
    }
}