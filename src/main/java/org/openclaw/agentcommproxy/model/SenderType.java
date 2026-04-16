package org.openclaw.agentcommproxy.model;

/**
 * 请求方类型枚举
 * 用于区分不同的回调方式
 */
public enum SenderType {
    /**
     * CLI 命令发起
     * 使用 CLI 方式回调（执行 proxy.execute）
     */
    CLI,

    /**
     * HTTP API 发起，提供回调地址
     * 使用 HTTP POST 回调到指定地址
     */
    HTTP_CALLBACK,

    /**
     * HTTP API 发起，轮询模式
     * 无回调，调用方主动轮询 /status/{id} 查询结果
     */
    HTTP_POLL
}