package org.openclaw.agentcommproxy.daemon;

import org.openclaw.agentcommproxy.config.ConfigManager;
import org.openclaw.agentcommproxy.model.AgentRequest;
import org.openclaw.agentcommproxy.model.MessageStatus;
import org.openclaw.agentcommproxy.service.AgentService;
import org.openclaw.agentcommproxy.store.SQLiteStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 后台守护线程管理器
 * 定时扫描待处理消息并执行
 */
public class DaemonManager {
    private static final Logger log = LoggerFactory.getLogger(DaemonManager.class);

    private static DaemonManager instance;

    private final ConfigManager configManager;
    private final SQLiteStore store;
    private final AgentService agentService;

    private ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private int interval;

    private DaemonManager() {
        this.configManager = new ConfigManager();
        this.store = new SQLiteStore(configManager);
        this.agentService = new AgentService(configManager, store);
        this.interval = configManager.getDaemonInterval();
    }

    public static synchronized DaemonManager getInstance() {
        if (instance == null) {
            instance = new DaemonManager();
        }
        return instance;
    }

    /**
     * 启动守护线程
     * @param customInterval 自定义间隔
     * @param foreground 是否前台运行（阻塞主线程）
     */
    public void start(Integer customInterval, boolean foreground) {
        if (running.get()) {
            log.warn("Daemon is already running");
            return;
        }

        if (customInterval != null && customInterval > 0) {
            this.interval = customInterval;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "agentcommproxy-daemon");
            t.setDaemon(!foreground);  // 前台运行时非守护线程
            return t;
        });

        running.set(true);
        scheduler.scheduleAtFixedRate(this::processPendingMessages, 0, interval, TimeUnit.SECONDS);

        log.info("Daemon started, interval: {} seconds, foreground: {}", interval, foreground);

        // 如果是前台模式，阻塞主线程
        if (foreground) {
            try {
                log.info("Daemon running in foreground mode, press Ctrl+C to stop");
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                log.info("Daemon interrupted, stopping...");
                stop();
            }
        }
    }

    /**
     * 启动守护线程（默认后台模式）
     */
    public void start(Integer customInterval) {
        start(customInterval, false);
    }

    /**
     * 停止守护线程
     */
    public void stop() {
        if (!running.get()) {
            log.warn("Daemon is not running");
            return;
        }

        running.set(false);
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }

        log.info("Daemon stopped");
    }

    /**
     * 是否运行中
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 获取扫描间隔
     */
    public int getInterval() {
        return interval;
    }

    /**
     * 处理待发送消息
     */
    private void processPendingMessages() {
        if (!running.get()) {
            return;
        }

        log.debug("Checking pending messages...");

        try {
            // 1. 处理待发送请求
            List<AgentRequest> pendingRequests = store.getPendingRequests();
            for (AgentRequest request : pendingRequests) {
                if (request.getStatus() == MessageStatus.PENDING) {
                    agentService.processRequest(request);
                } else if (request.getStatus() == MessageStatus.CALLBACK_PENDING) {
                    agentService.doCallback(request);
                }
            }

            // 2. 处理待重试请求
            List<AgentRequest> retryRequests = store.getRetryRequests();
            for (AgentRequest request : retryRequests) {
                agentService.processRetry(request);
            }

            if (pendingRequests.isEmpty() && retryRequests.isEmpty()) {
                log.debug("No pending messages to process");
            } else {
                log.info("Processed {} pending requests and {} retry requests",
                        pendingRequests.size(), retryRequests.size());
            }

        } catch (Exception e) {
            log.error("Error processing pending messages: {}", e.getMessage());
        }
    }
}