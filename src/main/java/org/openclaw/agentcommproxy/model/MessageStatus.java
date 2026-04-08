package org.openclaw.agentcommproxy.model;

/**
 * 消息状态枚举
 */
public enum MessageStatus {
    PENDING,      // 待处理
    RUNNING,      // 执行中
    SUCCESS,      // 成功
    FAILED,       // 失败
    TIMEOUT,      // 超时
    CALLBACK_PENDING,  // 待回调
    CALLBACK_DONE      // 回调完成
}