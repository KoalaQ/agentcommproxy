package org.openclaw.agentcommproxy.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.openclaw.agentcommproxy.config.ConfigManager;
import org.openclaw.agentcommproxy.daemon.DaemonManager;
import org.openclaw.agentcommproxy.http.dto.ErrorResponse;
import org.openclaw.agentcommproxy.http.dto.HealthResponse;
import org.openclaw.agentcommproxy.http.dto.ListResponse;
import org.openclaw.agentcommproxy.http.dto.SendRequest;
import org.openclaw.agentcommproxy.http.dto.SendResponse;
import org.openclaw.agentcommproxy.http.dto.StatusResponse;
import org.openclaw.agentcommproxy.model.AgentRequest;
import org.openclaw.agentcommproxy.model.MessageStatus;
import org.openclaw.agentcommproxy.model.ProxyType;
import org.openclaw.agentcommproxy.model.SenderType;
import org.openclaw.agentcommproxy.model.SessionMode;
import org.openclaw.agentcommproxy.service.AgentService;
import org.openclaw.agentcommproxy.store.SQLiteStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HTTP 服务管理器
 */
public class HttpServerManager {
    private static final Logger log = LoggerFactory.getLogger(HttpServerManager.class);

    private static HttpServerManager instance;

    private HttpServer server;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private int port;
    private String apiKey;
    private AgentService agentService;
    private SQLiteStore store;
    private ConfigManager configManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpServerManager() {
    }

    public static synchronized HttpServerManager getInstance() {
        if (instance == null) {
            instance = new HttpServerManager();
        }
        return instance;
    }

    /**
     * 启动 HTTP 服务
     */
    public void start(int port, String apiKey, AgentService agentService, SQLiteStore store, ConfigManager configManager) {
        if (running.get()) {
            log.warn("HTTP server is already running on port {}", this.port);
            return;
        }

        this.port = port;
        this.apiKey = apiKey;
        this.agentService = agentService;
        this.store = store;
        this.configManager = configManager;

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);

            // 注册路由
            server.createContext("/api/v1/send", this::handleSend);
            server.createContext("/api/v1/status", this::handleStatus);
            server.createContext("/api/v1/list", this::handleList);
            server.createContext("/api/v1/health", this::handleHealth);

            server.setExecutor(null); // 使用默认执行器
            server.start();

            running.set(true);
            log.info("HTTP server started on port {}", port);

        } catch (IOException e) {
            log.error("Failed to start HTTP server: {}", e.getMessage());
        }
    }

    /**
     * 停止 HTTP 服务
     */
    public void stop() {
        if (!running.get()) {
            log.warn("HTTP server is not running");
            return;
        }

        if (server != null) {
            server.stop(0);
            log.info("HTTP server stopped on port {}", port);
        }

        running.set(false);
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getPort() {
        return port;
    }

    /**
     * API Key 认证
     */
    private boolean validateApiKey(com.sun.net.httpserver.HttpExchange exchange) {
        String requestApiKey = exchange.getRequestHeaders().getFirst("X-API-Key");
        return apiKey != null && apiKey.equals(requestApiKey);
    }

    /**
     * 发送响应
     */
    private void sendResponse(com.sun.net.httpserver.HttpExchange exchange, int statusCode, Object response) throws IOException {
        String json = objectMapper.writeValueAsString(response);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, json.getBytes(StandardCharsets.UTF_8).length);
        OutputStream os = exchange.getResponseBody();
        os.write(json.getBytes(StandardCharsets.UTF_8));
        os.close();
    }

    /**
     * 处理发送消息请求
     */
    private void handleSend(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            sendResponse(exchange, 405, new ErrorResponse("method_not_allowed", "Only POST method is supported"));
            return;
        }

        if (!validateApiKey(exchange)) {
            sendResponse(exchange, 401, new ErrorResponse("unauthorized", "Invalid or missing API Key"));
            return;
        }

        try {
            InputStream is = exchange.getRequestBody();
            SendRequest request = objectMapper.readValue(is, SendRequest.class);

            // 参数校验
            if (request.getAgent() == null || request.getAgent().isEmpty()) {
                sendResponse(exchange, 400, new ErrorResponse("bad_request", "agent is required"));
                return;
            }
            if (request.getSender() == null || request.getSender().isEmpty()) {
                sendResponse(exchange, 400, new ErrorResponse("bad_request", "sender is required"));
                return;
            }
            if (request.getMessage() == null || request.getMessage().isEmpty()) {
                sendResponse(exchange, 400, new ErrorResponse("bad_request", "message is required"));
                return;
            }

            // 构建请求
            AgentRequest agentRequest = new AgentRequest();
            agentRequest.setSender(request.getSender());
            agentRequest.setTargetAgent(request.getAgent());
            agentRequest.setMessage(request.getMessage());
            agentRequest.setSync(request.isSync());
            agentRequest.setTimeout(request.getTimeout());

            // 根据 callbackUrl 判断 SenderType
            if (request.getCallbackUrl() != null && !request.getCallbackUrl().isEmpty()) {
                agentRequest.setSenderType(SenderType.HTTP_CALLBACK);
                agentRequest.setCallbackUrl(request.getCallbackUrl());
            } else {
                agentRequest.setSenderType(SenderType.HTTP_POLL);
            }

            // 设置 proxyType
            if (request.getProxy() != null && !request.getProxy().isEmpty()) {
                agentRequest.setProxyType(ProxyType.fromCode(request.getProxy()));
            }

            // 设置 taskId 和 sessionMode
            if (request.getTaskId() != null && !request.getTaskId().isEmpty()) {
                agentRequest.setTaskId(request.getTaskId());
            }
            if (request.getSessionMode() != null && !request.getSessionMode().isEmpty()) {
                agentRequest.setSessionMode(SessionMode.fromCode(request.getSessionMode()));
            } else {
                // 未指定 sessionMode，默认使用 MAIN
                agentRequest.setSessionMode(SessionMode.MAIN);
            }

            // 设置 clearSession（仅 INDEPENDENT 模式有效）
            if (request.isClearSession() && agentRequest.getSessionMode() != SessionMode.INDEPENDENT) {
                log.warn("clearSession is only supported in INDEPENDENT mode, ignoring");
                agentRequest.setClearSession(false);
            } else {
                agentRequest.setClearSession(request.isClearSession());
            }

            SendResponse response;

            if (request.isSync()) {
                // 同步模式
                AgentRequest result = agentService.sendSync(agentRequest);
                response = new SendResponse(result.getId(), result.getStatus().name());
                if (result.getStatus() == MessageStatus.DONE) {
                    response.setResponse(result.getResponse());
                } else {
                    response.setError(result.getError());
                }
            } else {
                // 异步模式
                AgentRequest result = agentService.sendAsync(agentRequest);
                response = new SendResponse(result.getId(), result.getStatus().name());
                response.setMessage("已收到、稍后回复您");
            }

            sendResponse(exchange, 200, response);

        } catch (Exception e) {
            log.error("Error handling send request: {}", e.getMessage());
            sendResponse(exchange, 500, new ErrorResponse("internal_error", e.getMessage()));
        }
    }

    /**
     * 处理状态查询请求
     */
    private void handleStatus(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            sendResponse(exchange, 405, new ErrorResponse("method_not_allowed", "Only GET method is supported"));
            return;
        }

        if (!validateApiKey(exchange)) {
            sendResponse(exchange, 401, new ErrorResponse("unauthorized", "Invalid or missing API Key"));
            return;
        }

        try {
            // 解析路径获取 request ID
            String path = exchange.getRequestURI().getPath();
            String requestId = extractRequestId(path);

            if (requestId == null || requestId.isEmpty()) {
                sendResponse(exchange, 400, new ErrorResponse("bad_request", "request ID is required"));
                return;
            }

            var requestOpt = store.getRequestById(requestId);

            if (requestOpt.isEmpty()) {
                sendResponse(exchange, 404, new ErrorResponse("not_found", "Request not found: " + requestId));
                return;
            }

            AgentRequest request = requestOpt.get();
            StatusResponse response = new StatusResponse();
            response.setRequestId(request.getId());
            response.setStatus(request.getStatus().name());
            response.setSender(request.getSender());
            response.setTargetAgent(request.getTargetAgent());
            response.setMessage(request.getMessage());
            response.setResponse(request.getResponse());
            response.setError(request.getError());
            response.setExecuteRetryCount(request.getExecuteRetryCount());
            response.setCallbackRetryCount(request.getCallbackRetryCount());

            sendResponse(exchange, 200, response);

        } catch (Exception e) {
            log.error("Error handling status request: {}", e.getMessage());
            sendResponse(exchange, 500, new ErrorResponse("internal_error", e.getMessage()));
        }
    }

    /**
     * 从路径提取 request ID
     * /api/v1/status/abc-123 -> abc-123
     */
    private String extractRequestId(String path) {
        String prefix = "/api/v1/status/";
        if (path.startsWith(prefix)) {
            return path.substring(prefix.length());
        }
        return null;
    }

    /**
     * 处理列表查询请求
     */
    private void handleList(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            sendResponse(exchange, 405, new ErrorResponse("method_not_allowed", "Only GET method is supported"));
            return;
        }

        if (!validateApiKey(exchange)) {
            sendResponse(exchange, 401, new ErrorResponse("unauthorized", "Invalid or missing API Key"));
            return;
        }

        try {
            // 解析查询参数
            String query = exchange.getRequestURI().getQuery();
            int limit = 10;
            String status = null;

            if (query != null) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    if (pair.length == 2) {
                        if ("limit".equals(pair[0])) {
                            limit = Integer.parseInt(pair[1]);
                        } else if ("status".equals(pair[0])) {
                            status = pair[1];
                        }
                    }
                }
            }

            List<AgentRequest> requests = store.getAllRequests(limit, status);

            ListResponse response = new ListResponse();
            response.setTotal(requests.size());

            List<StatusResponse> records = requests.stream().map(r -> {
                StatusResponse sr = new StatusResponse();
                sr.setRequestId(r.getId());
                sr.setStatus(r.getStatus().name());
                sr.setSender(r.getSender());
                sr.setTargetAgent(r.getTargetAgent());
                sr.setMessage(r.getMessage());
                sr.setResponse(r.getResponse());
                sr.setError(r.getError());
                sr.setExecuteRetryCount(r.getExecuteRetryCount());
                sr.setCallbackRetryCount(r.getCallbackRetryCount());
                return sr;
            }).toList();

            response.setRecords(records);
            sendResponse(exchange, 200, response);

        } catch (Exception e) {
            log.error("Error handling list request: {}", e.getMessage());
            sendResponse(exchange, 500, new ErrorResponse("internal_error", e.getMessage()));
        }
    }

    /**
     * 处理健康检查请求
     */
    private void handleHealth(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            sendResponse(exchange, 405, new ErrorResponse("method_not_allowed", "Only GET method is supported"));
            return;
        }

        // 健康检查无需认证
        HealthResponse response = new HealthResponse();
        response.setDaemonRunning(DaemonManager.getInstance().isRunning());
        response.setHttpPort(port);

        sendResponse(exchange, 200, response);
    }
}