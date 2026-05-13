package org.openclaw.agentcommproxy.service;

import org.openclaw.agentcommproxy.callback.CallbackHandler;
import org.openclaw.agentcommproxy.callback.CallbackHandlerFactory;
import org.openclaw.agentcommproxy.config.ConfigManager;
import org.openclaw.agentcommproxy.model.AgentRequest;
import org.openclaw.agentcommproxy.model.MessageStatus;
import org.openclaw.agentcommproxy.model.ProxyType;
import org.openclaw.agentcommproxy.model.SessionMode;
import org.openclaw.agentcommproxy.provider.AgentProvider;
import org.openclaw.agentcommproxy.provider.AgentProviderFactory;
import org.openclaw.agentcommproxy.proxy.CommandResult;
import org.openclaw.agentcommproxy.session.SessionManager;
import org.openclaw.agentcommproxy.store.SQLiteStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Agent 消息处理服务
 * 核心业务逻辑
 * 通过 AgentProvider 解耦，支持扩展新 Agent 工具
 */
public class AgentService {
    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private static final String RETRY_TYPE_EXECUTE = "EXECUTE";
    private static final String RETRY_TYPE_CALLBACK = "CALLBACK";

    private final ConfigManager configManager;
    private final SQLiteStore store;
    private final CallbackHandlerFactory callbackHandlerFactory;

    public AgentService(ConfigManager configManager, SQLiteStore store) {
        this.configManager = configManager;
        this.store = store;
        this.callbackHandlerFactory = new CallbackHandlerFactory(configManager);

        // 初始化 AgentProviderFactory
        AgentProviderFactory.initialize(store, configManager);
    }

    /**
     * 发送消息（同步模式）
     */
    public AgentRequest sendSync(AgentRequest request) {
        request.setSync(true);
        request.setStatus(MessageStatus.EXECUTING);
        request.setTimeout(request.getTimeout() > 0 ? request.getTimeout() : configManager.getDefaultTimeout());

        // 设置默认 proxyType
        if (request.getProxyType() == null) {
            request.setProxyType(configManager.getDefaultProxyType());
        }

        // 设置默认 sessionMode
        if (request.getSessionMode() == null) {
            request.setSessionMode(SessionMode.MAIN);
        }

        // 获取 AgentProvider
        AgentProvider provider = AgentProviderFactory.getProvider(request.getProxyType());
        SessionManager sessionManager = provider.getSessionManager();

        // 获取 sessionId
        String sessionId = getSessionId(sessionManager, request);

        // INDEPENDENT 模式首次调用，需要预创建会话（OpenClaw 需要）
        if (request.getSessionMode() == SessionMode.INDEPENDENT
            && provider.needsPreCreateSession()
            && (sessionId == null || sessionId.isEmpty())) {
            sessionId = provider.createIndependentSession(
                request.getTargetAgent(),
                request.getTaskId(),
                request.isClearSession()
            );
            log.info("Pre-created session for {}: {}", provider.getName(), sessionId);
        }

        log.info("Sync request: agent={}, taskId={}, sessionMode={}, sessionId={}, clearSession={}",
            request.getTargetAgent(), request.getTaskId(), request.getSessionMode(),
            sessionId, request.isClearSession());

        // 保存请求
        request.setSessionId(sessionId);
        store.saveRequest(request);
        log.info("Sync request saved: {}", request.getId());

        // 执行命令
        CommandResult result = provider.getProxy().execute(
            request.getTargetAgent(),
            request.getMessage(),
            request.getTimeout(),
            sessionId
        );

        // 处理结果
        if (result.isSuccess()) {
            request.setStatus(MessageStatus.DONE);
            request.setResponse(result.getOutput());

            // 更新 sessionId（如果 proxy 返回了新的）
            String resultSessionId = result.getSessionId();
            if (resultSessionId != null && !resultSessionId.equals(sessionId)) {
                request.setSessionId(resultSessionId);
                log.info("SessionId updated: {} -> {}", sessionId, resultSessionId);

                // MAIN 模式需要保存到配置
                if (request.getSessionMode() == SessionMode.MAIN) {
                    sessionManager.setMainSessionId(request.getTargetAgent(), resultSessionId);
                }
            }

            store.updateRequestStatusAndSessionId(
                request.getId(),
                request.getStatus(),
                request.getResponse(),
                request.getError(),
                request.getSessionId()
            );
        } else {
            request.setStatus(MessageStatus.ERROR);
            request.setError(result.getError());
            store.updateRequestStatus(request.getId(), request.getStatus(), request.getResponse(), request.getError());
        }

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

        // 设置默认 proxyType
        if (request.getProxyType() == null) {
            request.setProxyType(configManager.getDefaultProxyType());
        }

        // 设置默认 sessionMode
        if (request.getSessionMode() == null) {
            request.setSessionMode(SessionMode.MAIN);
        }

        // 获取 AgentProvider
        AgentProvider provider = AgentProviderFactory.getProvider(request.getProxyType());
        SessionManager sessionManager = provider.getSessionManager();

        // 获取 sessionId
        String sessionId = getSessionId(sessionManager, request);
        request.setSessionId(sessionId);

        store.saveRequest(request);
        log.info("Async request saved: taskId={}, sessionMode={}, sessionId={}, clearSession={}",
            request.getTaskId(), request.getSessionMode(), sessionId, request.isClearSession());

        return request;
    }

    /**
     * 处理执行请求（由 DaemonManager 调用）
     */
    public void processExecute(AgentRequest request) {
        log.info("Executing request: {} via proxy: {}, sessionId={}",
            request.getId(), request.getProxyType(), request.getSessionId());

        AgentProvider provider = AgentProviderFactory.getProvider(request.getProxyType());
        SessionManager sessionManager = provider.getSessionManager();

        String sessionId = request.getSessionId();

        // INDEPENDENT 模式首次调用，需要预创建会话（OpenClaw 需要）
        if (request.getSessionMode() == SessionMode.INDEPENDENT
            && provider.needsPreCreateSession()
            && (sessionId == null || sessionId.isEmpty())) {
            sessionId = provider.createIndependentSession(
                request.getTargetAgent(),
                request.getTaskId(),
                request.isClearSession()
            );
            request.setSessionId(sessionId);
            log.info("Pre-created session for {}: {}", provider.getName(), sessionId);
        }

        CommandResult result = provider.getProxy().execute(
            request.getTargetAgent(),
            request.getMessage(),
            request.getTimeout(),
            sessionId
        );

        if (result.isSuccess()) {
            request.setResponse(result.getOutput());

            // 更新 sessionId（如果 proxy 返回了新的）
            String resultSessionId = result.getSessionId();
            if (resultSessionId != null && !resultSessionId.equals(sessionId)) {
                request.setSessionId(resultSessionId);

                // MAIN 模式需要保存到配置
                if (request.getSessionMode() == SessionMode.MAIN) {
                    sessionManager.setMainSessionId(request.getTargetAgent(), resultSessionId);
                }
            }

            store.updateRequestStatusAndSessionId(
                request.getId(),
                MessageStatus.EXECUTE_SUCCESS,
                result.getOutput(),
                null,
                request.getSessionId()
            );
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
     */
    public void processCallback(AgentRequest request) {
        log.info("Callback request: {} to sender: {} via {}", request.getId(), request.getSender(), request.getSenderType());

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
     * 获取 sessionId
     */
    private String getSessionId(SessionManager sessionManager, AgentRequest request) {
        if (request.getSessionMode() == SessionMode.MAIN) {
            // MAIN 模式：获取 mainSessionId（可能为 null）
            String sessionId = sessionManager.getMainSessionId(request.getTargetAgent());

            // 如果 clearSession，清空 sessionId 让 proxy 重新创建
            if (request.isClearSession() && sessionId != null) {
                log.info("ClearSession requested, clearing mainSessionId: {}", sessionId);
                sessionManager.setMainSessionId(request.getTargetAgent(), null);
                return null;
            }

            return sessionId;
        } else {
            // INDEPENDENT 模式：从 requests 表查询
            String sessionId = sessionManager.findSessionIdByTaskId(
                request.getTargetAgent(),
                request.getTaskId()
            );

            // 如果 clearSession，清空 sessionId
            if (request.isClearSession()) {
                log.info("ClearSession requested, clearing sessionId for taskId: {}", request.getTaskId());
                return null;
            }

            return sessionId;
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
        request.setExecuteRetryCount(retryCount);

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