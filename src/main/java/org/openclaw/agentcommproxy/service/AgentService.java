package org.openclaw.agentcommproxy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openclaw.agentcommproxy.config.ConfigManager;
import org.openclaw.agentcommproxy.model.AgentRequest;
import org.openclaw.agentcommproxy.model.AgentResponse;
import org.openclaw.agentcommproxy.model.MessageStatus;
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

    private final ConfigManager configManager;
    private final SQLiteStore store;
    private final ObjectMapper objectMapper;
    private final CommandProxy proxy;

    public AgentService(ConfigManager configManager, SQLiteStore store) {
        this.configManager = configManager;
        this.store = store;
        this.objectMapper = new ObjectMapper();
        this.proxy = CommandProxyFactory.getDefaultProxy();
    }

    /**
     * 发送消息（同步模式）
     */
    public AgentRequest sendSync(AgentRequest request) {
        request.setSync(true);
        request.setStatus(MessageStatus.RUNNING);
        request.setTimeout(request.getTimeout() > 0 ? request.getTimeout() : configManager.getDefaultTimeout());

        // 保存请求
        store.saveRequest(request);
        log.info("Sync request saved: {}", request.getId());

        // 直接执行
        CommandResult result = proxy.execute(request.getTargetAgent(), request.getMessage(), request.getTimeout());

        // 更新状态
        if (result.isSuccess()) {
            request.setStatus(MessageStatus.SUCCESS);
            request.setResponse(result.getOutput());
        } else {
            request.setStatus(MessageStatus.FAILED);
            request.setError(result.getError());
        }
        store.updateRequestStatus(request.getId(), request.getStatus(), request.getResponse(), request.getError());

        log.info("Sync request completed: {} - {}", request.getId(), request.getStatus());
        return request;
    }

    /**
     * 发送消息（异步模式）
     * 立即返回请求ID，后台线程处理
     */
    public AgentRequest sendAsync(AgentRequest request) {
        request.setSync(false);
        request.setStatus(MessageStatus.PENDING);
        request.setTimeout(request.getTimeout() > 0 ? request.getTimeout() : configManager.getDefaultTimeout());

        // 保存请求
        store.saveRequest(request);
        log.info("Async request saved: {} - will be processed by daemon", request.getId());

        return request;
    }

    /**
     * 处理待发送请求（由后台线程调用）
     */
    public void processRequest(AgentRequest request) {
        log.info("Processing async request: {}", request.getId());

        request.setStatus(MessageStatus.RUNNING);
        store.updateRequestStatus(request.getId(), MessageStatus.RUNNING, null, null);

        CommandResult result = proxy.execute(request.getTargetAgent(), request.getMessage(), request.getTimeout());

        if (result.isSuccess()) {
            request.setStatus(MessageStatus.CALLBACK_PENDING);
            request.setResponse(result.getOutput());
            store.updateRequestStatus(request.getId(), MessageStatus.CALLBACK_PENDING, result.getOutput(), null);
            log.info("Request completed, ready for callback: {}", request.getId());
        } else if (result.isTimeout()) {
            handleFailure(request, "Timeout", true);
        } else {
            handleFailure(request, result.getError(), false);
        }
    }

    /**
     * 处理失败（重试或放弃）
     */
    private void handleFailure(AgentRequest request, String error, boolean isTimeout) {
        int maxRetry = configManager.getAsyncRetryCount();

        if (request.getRetryCount() < maxRetry) {
            // 加入重试队列
            request.setStatus(MessageStatus.FAILED);
            request.setError(error);
            store.updateRequestStatus(request.getId(), MessageStatus.FAILED, null, error);
            store.incrementRetryCount(request.getId());

            long nextRetryAt = Instant.now().toEpochMilli() + (configManager.getAsyncRetryInterval() * 1000L);
            store.addToRetryQueue(request.getId(), nextRetryAt);

            log.info("Request failed, added to retry queue: {} (retry {})", request.getId(), request.getRetryCount() + 1);
        } else {
            // 达到最大重试次数，标记为最终失败
            request.setStatus(isTimeout ? MessageStatus.TIMEOUT : MessageStatus.FAILED);
            request.setError(error + " (max retries reached)");
            store.updateRequestStatus(request.getId(), request.getStatus(), null, request.getError());
            store.removeFromRetryQueue(request.getId());

            log.warn("Request failed after max retries: {}", request.getId());
        }
    }

    /**
     * 执行回调
     * 将结果发送给请求方
     */
    public void doCallback(AgentRequest request) {
        log.info("Executing callback for request: {} to sender: {}", request.getId(), request.getSender());

        // 构建回调消息（JSON格式）
        AgentResponse response = new AgentResponse();
        response.setRequestId(request.getId());
        response.setStatus(request.getStatus().name().toLowerCase());
        response.setResponse(request.getResponse());
        response.setRequest(new AgentResponse.RequestInfo(
                request.getSender(),
                request.getTargetAgent(),
                request.getMessage()
        ));

        String callbackMessage;
        try {
            callbackMessage = objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Failed to serialize callback message: {}", e.getMessage());
            callbackMessage = request.getResponse(); // fallback to raw response
        }

        // 执行回调命令
        CommandResult result = proxy.execute(request.getSender(), callbackMessage, configManager.getDefaultTimeout());

        if (result.isSuccess()) {
            request.setStatus(MessageStatus.CALLBACK_DONE);
            store.updateRequestStatus(request.getId(), MessageStatus.CALLBACK_DONE, request.getResponse(), null);
            log.info("Callback completed: {}", request.getId());
        } else {
            log.warn("Callback failed: {} - {}", request.getId(), result.getError());
            // 回调失败也加入重试队列
            long nextRetryAt = Instant.now().toEpochMilli() + (configManager.getAsyncRetryInterval() * 1000L);
            store.addToRetryQueue(request.getId(), nextRetryAt);
        }
    }

    /**
     * 处理重试请求
     */
    public void processRetry(AgentRequest request) {
        log.info("Retrying request: {} (attempt {})", request.getId(), request.getRetryCount() + 1);
        store.removeFromRetryQueue(request.getId());
        processRequest(request);
    }
}