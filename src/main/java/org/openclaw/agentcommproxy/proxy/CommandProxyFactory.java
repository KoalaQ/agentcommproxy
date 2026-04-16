package org.openclaw.agentcommproxy.proxy;

import org.openclaw.agentcommproxy.model.ProxyType;

import java.util.HashMap;
import java.util.Map;

/**
 * 命令代理工厂
 * 管理不同的命令代理实现
 */
public class CommandProxyFactory {
    private static final Map<String, CommandProxy> proxies = new HashMap<>();

    static {
        // 注册默认代理
        registerProxy(new OpenClawProxy());
    }

    /**
     * 注册代理
     */
    public static void registerProxy(CommandProxy proxy) {
        proxies.put(proxy.getName(), proxy);
    }

    /**
     * 根据名称获取代理
     */
    public static CommandProxy getProxy(String name) {
        CommandProxy proxy = proxies.get(name);
        if (proxy == null) {
            throw new IllegalArgumentException("Unknown proxy: " + name);
        }
        return proxy;
    }

    /**
     * 根据 ProxyType 获取代理
     */
    public static CommandProxy getProxy(ProxyType type) {
        return getProxy(type.getCode());
    }

    /**
     * 获取默认代理（openclaw）
     */
    public static CommandProxy getDefaultProxy() {
        return getProxy(ProxyType.OPENCLAW);
    }
}