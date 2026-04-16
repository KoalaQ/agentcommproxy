package org.openclaw.agentcommproxy.callback;

import org.openclaw.agentcommproxy.model.AgentRequest;
import org.openclaw.agentcommproxy.model.SenderType;

/**
 * 回调处理器接口
 * 使用策略模式处理不同类型的回调
 */
public interface CallbackHandler {

    /**
     * 执行回调
     *
     * @param request 原请求对象
     * @return true 回调成功, false 回调失败
     */
    boolean doCallback(AgentRequest request);

    /**
     * 获取处理器支持的类型
     *
     * @return SenderType
     */
    SenderType getHandlerType();
}