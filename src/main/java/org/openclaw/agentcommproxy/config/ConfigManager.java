package org.openclaw.agentcommproxy.config;

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
        properties.setProperty("db.path", Paths.get(System.getProperty("user.home"), CONFIG_DIR, DB_FILE).toString());
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

    public String getDbPath() {
        return properties.getProperty("db.path");
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

    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
        saveConfig();
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }
}