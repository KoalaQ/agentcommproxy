package org.openclaw.agentcommproxy.callback;

import org.openclaw.agentcommproxy.model.AgentRequest;
import org.openclaw.agentcommproxy.model.SenderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 无操作回调处理器
 * 用于轮询模式，调用方主动查询结果
 */
public class NoopCallbackHandler implements CallbackHandler {

    private static final Logger log = LoggerFactory.getLogger(NoopCallbackHandler.class);

    @Override
    public boolean doCallback(AgentRequest request) {
        // 轮询模式无需回调
        // 调用方通过 HTTP API /status/{id} 主动查询结果
        log.info("Noop callback (poll mode) for request: {} - caller will poll status", request.getId());
        return true;
    }

    @Override
    public SenderType getHandlerType() {
        return SenderType.HTTP_POLL;
    }
}