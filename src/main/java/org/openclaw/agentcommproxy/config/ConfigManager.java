package org.openclaw.agentcommproxy.config;

import org.openclaw.agentcommproxy.model.ProxyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 配置管理器
 */
public class ConfigManager {
    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);

    private static final String CONFIG_DIR = ".agentcommproxy";
    private static final String CONFIG_FILE = "config.properties";
    private static final String DB_FILE = "data.db";

    // 默认配置
    private static final int DEFAULT_ASYNC_RETRY_COUNT = 3;
    private static final int DEFAULT_ASYNC_RETRY_INTERVAL = 5;
    private static final int DEFAULT_TIMEOUT = 300;
    private static final int DEFAULT_DAEMON_INTERVAL = 5;
    private static final int DEFAULT_DAEMON_POOL_SIZE = 4;
    private static final int DEFAULT_HTTP_PORT = 8080;
    private static final boolean DEFAULT_HTTP_ENABLED = true;
    private static final boolean DEFAULT_CLEANUP_ENABLED = true;
    private static final int DEFAULT_CLEANUP_DAYS = 7;
    private static final String DEFAULT_CLEANUP_STATUS = "DONE";
    private static final String DEFAULT_PROXY_TYPE = "openclaw";

    private Properties properties;
    private Path configPath;

    public ConfigManager() {
        loadConfig();
    }

    private void loadConfig() {
        String userHome = System.getProperty("user.home");
        configPath = Paths.get(userHome, CONFIG_DIR, CONFIG_FILE);

        properties = new Properties();

        // 设置默认值
        setDefaultProperties();

        // 如果配置文件存在，加载它
        if (Files.exists(configPath)) {
            try (InputStream is = Files.newInputStream(configPath)) {
                properties.load(is);
                log.info("Loaded config from: {}", configPath);
            } catch (IOException e) {
                log.warn("Failed to load config file, using defaults: {}", e.getMessage());
            }
        } else {
            // 创建默认配置文件
            saveConfig();
            log.info("Created default config at: {}", configPath);
        }
    }

    private void setDefaultProperties() {
        properties.setProperty("async.retry.count", String.valueOf(DEFAULT_ASYNC_RETRY_COUNT));
        properties.setProperty("async.retry.interval", String.valueOf(DEFAULT_ASYNC_RETRY_INTERVAL));
        properties.setProperty("default.timeout", String.valueOf(DEFAULT_TIMEOUT));
        properties.setProperty("daemon.interval", String.valueOf(DEFAULT_DAEMON_INTERVAL));
        properties.setProperty("daemon.pool.size", String.valueOf(DEFAULT_DAEMON_POOL_SIZE));
        properties.setProperty("daemon.enabled", "true");
        properties.setProperty("db.path", Paths.get(System.getProperty("user.home"), CONFIG_DIR, DB_FILE).toString());
        properties.setProperty("http.enabled", String.valueOf(DEFAULT_HTTP_ENABLED));
        properties.setProperty("http.port", String.valueOf(DEFAULT_HTTP_PORT));
        // API Key 首次启动时自动生成
        String existingApiKey = properties.getProperty("http.api.key");
        if (existingApiKey == null || existingApiKey.isEmpty()) {
            properties.setProperty("http.api.key", java.util.UUID.randomUUID().toString());
        }
        // 定期清理配置
        properties.setProperty("cleanup.enabled", String.valueOf(DEFAULT_CLEANUP_ENABLED));
        properties.setProperty("cleanup.days", String.valueOf(DEFAULT_CLEANUP_DAYS));
        properties.setProperty("cleanup.status", DEFAULT_CLEANUP_STATUS);
        // Proxy 配置
        properties.setProperty("proxy.default", DEFAULT_PROXY_TYPE);
    }

    private void saveConfig() {
        try {
            Files.createDirectories(configPath.getParent());
            try (OutputStream os = Files.newOutputStream(configPath)) {
                properties.store(os, "AgentCommProxy Configuration");
            }
        } catch (IOException e) {
            log.error("Failed to save config file: {}", e.getMessage());
        }
    }

    // 配置获取方法
    public int getAsyncRetryCount() {
        return getInt("async.retry.count", DEFAULT_ASYNC_RETRY_COUNT);
    }

    public int getAsyncRetryInterval() {
        return getInt("async.retry.interval", DEFAULT_ASYNC_RETRY_INTERVAL);
    }

    public int getDefaultTimeout() {
        return getInt("default.timeout", DEFAULT_TIMEOUT);
    }

    public int getDaemonInterval() {
        return getInt("daemon.interval", DEFAULT_DAEMON_INTERVAL);
    }

    public int getDaemonPoolSize() {
        return getInt("daemon.pool.size", DEFAULT_DAEMON_POOL_SIZE);
    }

    public boolean isDaemonEnabled() {
        return getBoolean("daemon.enabled", true);
    }

    public String getDbPath() {
        return properties.getProperty("db.path");
    }

    // HTTP 配置
    public boolean isHttpEnabled() {
        return getBoolean("http.enabled", DEFAULT_HTTP_ENABLED);
    }

    public int getHttpPort() {
        return getInt("http.port", DEFAULT_HTTP_PORT);
    }

    public String getApiKey() {
        return properties.getProperty("http.api.key");
    }

    public void setHttpPort(int port) {
        properties.setProperty("http.port", String.valueOf(port));
        saveConfig();
    }

    public void setHttpEnabled(boolean enabled) {
        properties.setProperty("http.enabled", String.valueOf(enabled));
        saveConfig();
    }

    public void generateNewApiKey() {
        properties.setProperty("http.api.key", java.util.UUID.randomUUID().toString());
        saveConfig();
    }

    // 定期清理配置
    public boolean isCleanupEnabled() {
        return getBoolean("cleanup.enabled", DEFAULT_CLEANUP_ENABLED);
    }

    public int getCleanupDays() {
        return getInt("cleanup.days", DEFAULT_CLEANUP_DAYS);
    }

    public String getCleanupStatus() {
        return properties.getProperty("cleanup.status", DEFAULT_CLEANUP_STATUS);
    }

    public void setCleanupEnabled(boolean enabled) {
        properties.setProperty("cleanup.enabled", String.valueOf(enabled));
        saveConfig();
    }

    public void setCleanupDays(int days) {
        properties.setProperty("cleanup.days", String.valueOf(days));
        saveConfig();
    }

    public void setCleanupStatus(String status) {
        properties.setProperty("cleanup.status", status);
        saveConfig();
    }

    // Proxy 配置
    public ProxyType getDefaultProxyType() {
        String proxyCode = properties.getProperty("proxy.default", DEFAULT_PROXY_TYPE);
        return ProxyType.fromCode(proxyCode);
    }

    public void setDefaultProxyType(ProxyType proxyType) {
        properties.setProperty("proxy.default", proxyType.getCode());
        saveConfig();
    }

    private int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                log.warn("Invalid config value for {}: {}", key, value);
            }
        }
        return defaultValue;
    }

    private boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            return Boolean.parseBoolean(value);
        }
        return defaultValue;
    }

    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
        saveConfig();
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }
}