package org.openclaw.agentcommproxy.service;

import org.openclaw.agentcommproxy.callback.CallbackHandler;
import org.openclaw.agentcommproxy.callback.CallbackHandlerFactory;
import org.openclaw.agentcommproxy.config.ConfigManager;
import org.openclaw.agentcommproxy.model.AgentRequest;
import org.openclaw.agentcommproxy.model.MessageStatus;
import org.openclaw.agentcommproxy.model.SenderType;
import org.openclaw.agentcommproxy.proxy.CommandProxy;
import org.openclaw.agentcommproxy.proxy.CommandProxyFactory;
import org.openclaw.agentcommproxy.proxy.CommandResult;
import org.openclaw.agentcommproxy.store.SQLiteStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Agent 消息处理服务
 * 核心业务逻辑
 */
public class AgentService {
    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private static final String RETRY_TYPE_EXECUTE = "EXECUTE";
    private static final String RETRY_TYPE_CALLBACK = "CALLBACK";

    private final ConfigManager configManager;
    private final SQLiteStore store;
    private final CommandProxy proxy;
    private final CallbackHandlerFactory callbackHandlerFactory;

    public AgentService(ConfigManager configManager, SQLiteStore store) {
        this.configManager = configManager;
        this.store = store;
        this.proxy = CommandProxyFactory.getDefaultProxy();
        this.callbackHandlerFactory = new CallbackHandlerFactory(configManager);
    }

    /**
     * 发送消息（同步模式）
     */
    public AgentRequest sendSync(AgentRequest request) {
        request.setSync(true);
        request.setStatus(MessageStatus.EXECUTING);
        request.setTimeout(request.getTimeout() > 0 ? request.getTimeout() : configManager.getDefaultTimeout());

        store.saveRequest(request);
        log.info("Sync request saved: {}", request.getId());

        CommandResult result = proxy.execute(request.getTargetAgent(), request.getMessage(), request.getTimeout());

        if (result.isSuccess()) {
            request.setStatus(MessageStatus.DONE);
            request.setResponse(result.getOutput());
        } else {
            request.setStatus(MessageStatus.ERROR);
            request.setError(result.getError());
        }
        store.updateRequestStatus(request.getId(), request.getStatus(), request.getResponse(), request.getError());

        log.info("Sync request completed: {} - {}", request.getId(), request.getStatus());
        return request;
    }

    /**
     * 发送消息（异步模式）
     */
    public AgentRequest sendAsync(AgentRequest request) {
        request.setSync(false);
        request.setStatus(MessageStatus.PENDING);
        request.setTimeout(request.getTimeout() > 0 ? request.getTimeout() : configManager.getDefaultTimeout());

        store.saveRequest(request);
        log.info("Async request saved: {} - will be processed by daemon", request.getId());

        return request;
    }

    /**
     * 处理执行请求（由 DaemonManager 调用）
     */
    public void processExecute(AgentRequest request) {
        log.info("Executing request: {}", request.getId());

        CommandResult result = proxy.execute(request.getTargetAgent(), request.getMessage(), request.getTimeout());

        if (result.isSuccess()) {
            request.setResponse(result.getOutput());
            store.updateRequestStatus(request.getId(), MessageStatus.EXECUTE_SUCCESS, result.getOutput(), null);
            log.info("Execute success: {}", request.getId());

            // 立即执行回调
            processCallback(request);
        } else if (result.isTimeout()) {
            handleExecuteFailure(request, "Timeout", true);
        } else {
            handleExecuteFailure(request, result.getError(), false);
        }
    }

    /**
     * 处理回调（策略模式）
     * 由 DaemonManager 调用，根据 SenderType 选择不同的回调处理器
     */
    public void processCallback(AgentRequest request) {
        log.info("Callback request: {} to sender: {} via {}", request.getId(), request.getSender(), request.getSenderType());

        // 获取对应的回调处理器
        CallbackHandler handler = callbackHandlerFactory.getHandler(request);
        boolean success = handler.doCallback(request);

        if (success) {
            store.updateRequestStatus(request.getId(), MessageStatus.DONE, request.getResponse(), null);
            log.info("Callback success: {} via {}", request.getId(), handler.getHandlerType());
        } else {
            handleCallbackFailure(request);
        }
    }

    /**
     * 处理回调失败
     */
    private void handleCallbackFailure(AgentRequest request) {
        log.warn("Callback failed: {} - {}", request.getId(), request.getSenderType());

        store.updateRequestStatus(request.getId(), MessageStatus.CALLBACK_FAILED, request.getResponse(), null);
        store.incrementCallbackRetryCount(request.getId());

        int maxRetry = configManager.getAsyncRetryCount();
        int retryCount = request.getCallbackRetryCount() + 1;
        request.setCallbackRetryCount(retryCount);

        if (retryCount <= maxRetry) {
            long nextRetryAt = Instant.now().toEpochMilli() + (configManager.getAsyncRetryInterval() * 1000L);
            store.addToRetryQueue(request.getId(), RETRY_TYPE_CALLBACK, nextRetryAt);
            log.info("Callback added to retry queue: {} (retry {})", request.getId(), retryCount);
        } else {
            store.updateRequestStatus(request.getId(), MessageStatus.ERROR, request.getResponse(), "Callback failed after max retries");
            log.warn("Callback failed after max retries: {}", request.getId());
        }
    }

    /**
     * 处理执行失败
     */
    private void handleExecuteFailure(AgentRequest request, String error, boolean isTimeout) {
        int maxRetry = configManager.getAsyncRetryCount();

        store.updateRequestStatus(request.getId(),
            isTimeout ? MessageStatus.EXECUTE_TIMEOUT : MessageStatus.EXECUTE_FAILED,
            null, error);
        store.incrementExecuteRetryCount(request.getId());

        int retryCount = request.getExecuteRetryCount() + 1;
        request.setExecuteRetryCount(retryCount);  // 同步更新 request 对象

        if (retryCount <= maxRetry) {
            long nextRetryAt = Instant.now().toEpochMilli() + (configManager.getAsyncRetryInterval() * 1000L);
            store.addToRetryQueue(request.getId(), RETRY_TYPE_EXECUTE, nextRetryAt);
            log.info("Execute failed, added to retry queue: {} (retry {})", request.getId(), retryCount);
        } else {
            store.updateRequestStatus(request.getId(), MessageStatus.ERROR, null,
                (isTimeout ? "Timeout" : error) + " (max retries reached)");
            log.warn("Execute failed after max retries: {}", request.getId());
        }
    }
}