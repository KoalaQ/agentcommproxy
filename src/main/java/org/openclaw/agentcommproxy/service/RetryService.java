package org.openclaw.agentcommproxy.service;

import org.openclaw.agentcommproxy.config.ConfigManager;
import org.openclaw.agentcommproxy.model.AgentRequest;
import org.openclaw.agentcommproxy.store.SQLiteStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 重试服务
 * 管理失败请求的重试逻辑
 */
public class RetryService {
    private static final Logger log = LoggerFactory.getLogger(RetryService.class);

    private final ConfigManager configManager;
    private final SQLiteStore store;

    public RetryService(ConfigManager configManager, SQLiteStore store) {
        this.configManager = configManager;
        this.store = store;
    }

    /**
     * 获取待重试请求列表
     */
    public List<AgentRequest> getRetryRequests() {
        return store.getRetryRequests();
    }

    /**
     * 检查是否可以重试
     */
    public boolean canRetry(AgentRequest request) {
        return request.getRetryCount() < configManager.getAsyncRetryCount();
    }
}