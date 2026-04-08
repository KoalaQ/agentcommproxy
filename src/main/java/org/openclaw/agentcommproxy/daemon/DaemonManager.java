package org.openclaw.agentcommproxy.daemon;

import org.openclaw.agentcommproxy.config.ConfigManager;
import org.openclaw.agentcommproxy.model.AgentRequest;
import org.openclaw.agentcommproxy.model.MessageStatus;
import org.openclaw.agentcommproxy.service.AgentService;
import org.openclaw.agentcommproxy.store.SQLiteStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
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
    private ExecutorService workerPool;  // 工作线程池
    private final AtomicBoolean running = new AtomicBoolean(false);
    private int interval;
    private int poolSize;

    private DaemonManager() {
        this.configManager = new ConfigManager();
        this.store = new SQLiteStore(configManager);
        this.agentService = new AgentService(configManager, store);
        this.interval = configManager.getDaemonInterval();
        this.poolSize = configManager.getDaemonPoolSize();
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

        // 创建工作线程池
        workerPool = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "agentcommproxy-worker");
            t.setDaemon(true);
            return t;
        });

        // 创建调度器
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "agentcommproxy-daemon");
            t.setDaemon(!foreground);
            return t;
        });

        running.set(true);
        scheduler.scheduleAtFixedRate(this::processPendingMessages, 0, interval, TimeUnit.SECONDS);

        log.info("Daemon started, interval: {} seconds, pool size: {}, foreground: {}", interval, poolSize, foreground);

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

        // 停止调度器
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }

        // 停止工作线程池
        if (workerPool != null) {
            workerPool.shutdown();
            try {
                workerPool.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                workerPool.shutdownNow();
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
     * 获取线程池大小
     */
    public int getPoolSize() {
        return poolSize;
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
            // 1. 处理待发送请求 (PENDING)
            List<AgentRequest> pendingRequests = store.getPendingRequests();
            for (AgentRequest request : pendingRequests) {
                if (request.getStatus() == MessageStatus.PENDING) {
                    // 提交到线程池异步处理
                    workerPool.submit(() -> {
                        try {
                            agentService.processRequest(request);
                        } catch (Exception e) {
                            log.error("Error processing request {}: {}", request.getId(), e.getMessage());
                        }
                    });
                } else if (request.getStatus() == MessageStatus.CALLBACK_PENDING) {
                    // 提交到线程池异步处理
                    workerPool.submit(() -> {
                        try {
                            agentService.doCallback(request);
                        } catch (Exception e) {
                            log.error("Error callback request {}: {}", request.getId(), e.getMessage());
                        }
                    });
                }
            }

            // 2. 处理待重试请求（区分执行失败和回调失败）
            List<AgentRequest> retryRequests = store.getRetryRequests();
            for (AgentRequest request : retryRequests) {
                if (request.getStatus() == MessageStatus.FAILED) {
                    // 提交到线程池异步处理
                    workerPool.submit(() -> {
                        try {
                            agentService.processRetry(request);
                        } catch (Exception e) {
                            log.error("Error retrying request {}: {}", request.getId(), e.getMessage());
                        }
                    });
                } else if (request.getStatus() == MessageStatus.CALLBACK_PENDING) {
                    // 提交到线程池异步处理
                    workerPool.submit(() -> {
                        try {
                            store.removeFromRetryQueue(request.getId());
                            agentService.doCallback(request);
                        } catch (Exception e) {
                            log.error("Error retrying callback {}: {}", request.getId(), e.getMessage());
                        }
                    });
                }
            }

            if (pendingRequests.isEmpty() && retryRequests.isEmpty()) {
                log.debug("No pending messages to process");
            } else {
                log.info("Submitted {} pending requests and {} retry requests to thread pool",
                        pendingRequests.size(), retryRequests.size());
            }

        } catch (Exception e) {
            log.error("Error processing pending messages: {}", e.getMessage());
        }
    }
}