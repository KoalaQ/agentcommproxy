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

    private static final String RETRY_TYPE_EXECUTE = "EXECUTE";
    private static final String RETRY_TYPE_CALLBACK = "CALLBACK";

    private static DaemonManager instance;

    private final ConfigManager configManager;
    private final SQLiteStore store;
    private final AgentService agentService;

    private ScheduledExecutorService scheduler;
    private ScheduledExecutorService cleanupScheduler;
    private ExecutorService workerPool;
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
     */
    public void start(Integer customInterval, boolean foreground) {
        if (running.get()) {
            log.warn("Daemon is already running");
            return;
        }

        if (customInterval != null && customInterval > 0) {
            this.interval = customInterval;
        }

        workerPool = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "agentcommproxy-worker");
            t.setDaemon(true);
            return t;
        });

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "agentcommproxy-daemon");
            t.setDaemon(!foreground);
            return t;
        });

        running.set(true);
        scheduler.scheduleAtFixedRate(this::processPendingMessages, 0, interval, TimeUnit.SECONDS);

        // 启动定期清理任务（每小时执行一次）
        if (configManager.isCleanupEnabled()) {
            cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "agentcommproxy-cleanup");
                t.setDaemon(true);
                return t;
            });
            cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredMessages, 1, 60, TimeUnit.MINUTES);
            log.info("Cleanup scheduler started, cleanup days: {}, cleanup status: {}",
                configManager.getCleanupDays(), configManager.getCleanupStatus());
        }

        log.info("Daemon started, interval: {}s, pool size: {}, foreground: {}, maxRetry: {}", interval, poolSize, foreground, configManager.getAsyncRetryCount());

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

        if (cleanupScheduler != null) {
            cleanupScheduler.shutdown();
            try {
                cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                cleanupScheduler.shutdownNow();
            }
        }

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

    public boolean isRunning() {
        return running.get();
    }

    public int getInterval() {
        return interval;
    }

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
            int maxRetry = configManager.getAsyncRetryCount();

            // 1. 处理新请求 (PENDING)
            List<AgentRequest> pendingRequests = store.getPendingRequests();
            for (AgentRequest request : pendingRequests) {
                if (request.getStatus() == MessageStatus.PENDING) {
                    // 更新状态为 EXECUTING
                    store.updateRequestStatus(request.getId(), MessageStatus.EXECUTING, null, null);

                    final String requestId = request.getId();
                    workerPool.submit(() -> {
                        try {
                            store.getRequestById(requestId).ifPresent(agentService::processExecute);
                        } catch (Exception e) {
                            log.error("Error executing request {}: {}", requestId, e.getMessage());
                        }
                    });
                } else if (request.getStatus() == MessageStatus.EXECUTE_SUCCESS) {
                    // 更新状态为 CALLBACKING
                    store.updateRequestStatus(request.getId(), MessageStatus.CALLBACKING, request.getResponse(), null);

                    final String requestId = request.getId();
                    workerPool.submit(() -> {
                        try {
                            store.getRequestById(requestId).ifPresent(agentService::processCallback);
                        } catch (Exception e) {
                            log.error("Error callback request {}: {}", requestId, e.getMessage());
                        }
                    });
                }
            }

            // 2. 处理重试请求
            List<AgentRequest> retryRequests = store.getRetryRequests();
            for (AgentRequest request : retryRequests) {
                MessageStatus status = request.getStatus();

                if (status == MessageStatus.EXECUTE_FAILED || status == MessageStatus.EXECUTE_TIMEOUT) {
                    // 执行重试
                    int retryCount = request.getExecuteRetryCount();
                    store.removeFromRetryQueue(request.getId());
                    log.debug("Execute retry check: {} - retryCount={}, maxRetry={}", request.getId(), retryCount, maxRetry);

                    if (retryCount <= maxRetry) {
                        store.updateRequestStatus(request.getId(), MessageStatus.EXECUTING, null, null);

                        final String requestId = request.getId();
                        workerPool.submit(() -> {
                            try {
                                store.getRequestById(requestId).ifPresent(agentService::processExecute);
                            } catch (Exception e) {
                                log.error("Error retrying execute {}: {}", requestId, e.getMessage());
                            }
                        });
                    } else {
                        store.updateRequestStatus(request.getId(), MessageStatus.ERROR, null, "Max retries reached");
                        log.warn("Execute max retries reached: {}", request.getId());
                    }
                } else if (status == MessageStatus.CALLBACK_FAILED) {
                    // 回调重试
                    int retryCount = request.getCallbackRetryCount();
                    store.removeFromRetryQueue(request.getId());
                    log.debug("Callback retry check: {} - retryCount={}, maxRetry={}", request.getId(), retryCount, maxRetry);

                    if (retryCount <= maxRetry) {
                        store.updateRequestStatus(request.getId(), MessageStatus.CALLBACKING, request.getResponse(), null);

                        final String requestId = request.getId();
                        workerPool.submit(() -> {
                            try {
                                store.getRequestById(requestId).ifPresent(agentService::processCallback);
                            } catch (Exception e) {
                                log.error("Error retrying callback {}: {}", requestId, e.getMessage());
                            }
                        });
                    } else {
                        store.updateRequestStatus(request.getId(), MessageStatus.ERROR, request.getResponse(), "Callback max retries reached");
                        log.warn("Callback max retries reached: {}", request.getId());
                    }
                }
            }

            if (!pendingRequests.isEmpty() || !retryRequests.isEmpty()) {
                log.info("Processed {} pending, {} retry requests", pendingRequests.size(), retryRequests.size());
            }

        } catch (Exception e) {
            log.error("Error processing pending messages: {}", e.getMessage());
        }
    }

    /**
     * 清理过期消息
     */
    private void cleanupExpiredMessages() {
        if (!running.get()) {
            return;
        }

        try {
            int days = configManager.getCleanupDays();
            String statuses = configManager.getCleanupStatus();

            int count = store.clearExpired(days, statuses);
            if (count > 0) {
                log.info("Cleanup completed: {} expired messages deleted (days={}, statuses={})", count, days, statuses);
            }
        } catch (Exception e) {
            log.error("Error during cleanup: {}", e.getMessage());
        }
    }
}