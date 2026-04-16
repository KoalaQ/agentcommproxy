package org.openclaw.agentcommproxy.callback;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openclaw.agentcommproxy.model.AgentRequest;
import org.openclaw.agentcommproxy.model.MessageStatus;
import org.openclaw.agentcommproxy.model.SenderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP 回调处理器
 * 通过 HTTP POST 方式回调发送方
 */
public class HttpCallbackHandler implements CallbackHandler {

    private static final Logger log = LoggerFactory.getLogger(HttpCallbackHandler.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    public HttpCallbackHandler() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Override
    public boolean doCallback(AgentRequest request) {
        String callbackUrl = request.getCallbackUrl();

        if (callbackUrl == null || callbackUrl.isEmpty()) {
            log.warn("HTTP callback URL is empty for request: {}", request.getId());
            return false;
        }

        log.info("HTTP callback for request: {} to URL: {}", request.getId(), callbackUrl);

        try {
            CallbackPayload payload = buildPayload(request);
            String body = objectMapper.writeValueAsString(payload);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(callbackUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = httpClient.send(
                httpRequest,
                HttpResponse.BodyHandlers.ofString()
            );

            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;

            if (success) {
                log.info("HTTP callback success: {} - status {}", request.getId(), response.statusCode());
            } else {
                log.warn("HTTP callback failed: {} - status {} - body {}", request.getId(), response.statusCode(), response.body());
            }

            return success;

        } catch (Exception e) {
            log.warn("HTTP callback error: {} - {}", request.getId(), e.getMessage());
            return false;
        }
    }

    @Override
    public SenderType getHandlerType() {
        return SenderType.HTTP_CALLBACK;
    }

    /**
     * 构建回调 Payload
     */
    private CallbackPayload buildPayload(AgentRequest request) {
        CallbackPayload payload = new CallbackPayload();
        payload.setRequestId(request.getId());
        payload.setStatus(request.getStatus() == MessageStatus.ERROR ? "failed" : "completed");
        payload.setSender(request.getSender());
        payload.setTargetAgent(request.getTargetAgent());
        payload.setMessage(request.getMessage());
        payload.setResponse(request.getResponse());
        payload.setError(request.getError());
        return payload;
    }
}