package org.openclaw.agentcommproxy.provider;

import org.openclaw.agentcommproxy.config.ConfigManager;
import org.openclaw.agentcommproxy.model.ProxyType;
import org.openclaw.agentcommproxy.store.SQLiteStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent Provider 工厂
 * 注册和获取 AgentProvider
 * 新接入 Agent 只需：实现 AgentProvider → registerProvider
 */
public class AgentProviderFactory {
    private static final Logger log = LoggerFactory.getLogger(AgentProviderFactory.class);

    private static final Map<ProxyType, AgentProvider> providers = new HashMap<>();
    private static SQLiteStore store;
    private static ConfigManager configManager;

    /**
     * 初始化工厂（设置依赖）
     */
    public static void initialize(SQLiteStore store, ConfigManager configManager) {
        AgentProviderFactory.store = store;
        AgentProviderFactory.configManager = configManager;

        // 注册默认 Provider
        registerProvider(new OpenClawProvider(store, configManager));
        registerProvider(new ClaudeCodeProvider(store));

        log.info("AgentProviderFactory initialized with {} providers", providers.size());
    }

    /**
     * 注册 Provider
     */
    public static void registerProvider(AgentProvider provider) {
        providers.put(provider.getProxyType(), provider);
        log.info("Registered AgentProvider: {} -> {}", provider.getProxyType(), provider.getName());
    }

    /**
     * 根据 ProxyType 获取 Provider
     */
    public static AgentProvider getProvider(ProxyType proxyType) {
        AgentProvider provider = providers.get(proxyType);
        if (provider == null) {
            throw new IllegalArgumentException("No provider registered for: " + proxyType);
        }
        return provider;
    }

    /**
     * 获取所有已注册的 Provider
     */
    public static Map<ProxyType, AgentProvider> getAllProviders() {
        return new HashMap<>(providers);
    }
}