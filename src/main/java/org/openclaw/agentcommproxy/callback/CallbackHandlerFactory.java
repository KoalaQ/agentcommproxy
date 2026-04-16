package org.openclaw.agentcommproxy.callback;

import org.openclaw.agentcommproxy.config.ConfigManager;
import org.openclaw.agentcommproxy.model.AgentRequest;
import org.openclaw.agentcommproxy.model.SenderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 回调处理器工厂
 * 根据请求类型返回对应的回调处理器
 */
public class CallbackHandlerFactory {

    private static final Logger log = LoggerFactory.getLogger(CallbackHandlerFactory.class);

    private final Map<SenderType, CallbackHandler> handlers;

    public CallbackHandlerFactory(ConfigManager configManager) {
        handlers = new HashMap<>();

        // 注册所有处理器
        handlers.put(SenderType.CLI, new CliCallbackHandler(configManager));
        handlers.put(SenderType.HTTP_CALLBACK, new HttpCallbackHandler());
        handlers.put(SenderType.HTTP_POLL, new NoopCallbackHandler());

        log.info("CallbackHandlerFactory initialized with {} handlers", handlers.size());
    }

    /**
     * 根据请求获取对应的回调处理器
     *
     * @param request 请求对象
     * @return 回调处理器
     */
    public CallbackHandler getHandler(AgentRequest request) {
        SenderType senderType = request.getSenderType();

        if (senderType == null) {
            // 兼容旧数据，默认使用 CLI 回调
            log.debug("SenderType is null for request {}, using CLI handler", request.getId());
            senderType = SenderType.CLI;
        }

        CallbackHandler handler = handlers.get(senderType);

        if (handler == null) {
            log.warn("No handler found for SenderType {}, using CLI handler", senderType);
            handler = handlers.get(SenderType.CLI);
        }

        return handler;
    }

    /**
     * 根据 SenderType 获取处理器
     *
     * @param senderType 请求方类型
     * @return 回调处理器
     */
    public CallbackHandler getHandler(SenderType senderType) {
        return handlers.get(senderType);
    }

    /**
     * 注册新的处理器（用于扩展）
     *
     * @param handler 回调处理器
     */
    public void registerHandler(CallbackHandler handler) {
        handlers.put(handler.getHandlerType(), handler);
        log.info("Registered callback handler for type: {}", handler.getHandlerType());
    }
}