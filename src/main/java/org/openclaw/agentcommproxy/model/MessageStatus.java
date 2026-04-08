package org.openclaw.agentcommproxy.model;

/**
 * 消息状态枚举
 */
public enum MessageStatus {
    // 初始
    PENDING,              // 待处理

    // 执行阶段
    EXECUTING,            // 执行中
    EXECUTE_SUCCESS,      // 执行成功，待回调
    EXECUTE_FAILED,       // 执行失败，待重试
    EXECUTE_TIMEOUT,      // 执行超时，待重试

    // 回调阶段
    CALLBACKING,          // 回调中
    CALLBACK_FAILED,      // 回调失败，待重试

    // 最终状态
    DONE,                 // 完成
    ERROR                 // 最终失败（达到最大重试）
}