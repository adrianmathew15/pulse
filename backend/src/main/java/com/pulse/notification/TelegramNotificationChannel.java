package com.pulse.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TelegramNotificationChannel implements NotificationChannel {
    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationChannel.class);

    private final boolean enabled;
    private final String botToken;
    private final String chatId;
    private final RestClient restClient;

    @Autowired
    public TelegramNotificationChannel(
            @Value("${pulse.notifications.telegram.enabled:false}") boolean enabled,
            @Value("${pulse.notifications.telegram.bot-token:}") String botToken,
            @Value("${pulse.notifications.telegram.chat-id:}") String chatId,
            @Value("${pulse.notifications.telegram.api-base-url:https://api.telegram.org}") String apiBaseUrl,
            @Value("${pulse.notifications.telegram.timeout-ms:5000}") long timeoutMs) {
        this(enabled, botToken, chatId, createRestClient(apiBaseUrl, timeoutMs));
    }

    TelegramNotificationChannel(boolean enabled, String botToken, String chatId,
                                RestClient restClient) {
        this.enabled = enabled;
        this.botToken = trim(botToken);
        this.chatId = trim(chatId);
        this.restClient = restClient;
    }

    @Override
    public void send(IncidentNotification notification) {
        if (!enabled) return;
        if (!isConfigured()) {
            log.warn("Telegram notifications are enabled but configuration is incomplete; "
                    + "notification for incident {} was skipped", notification.incidentId());
            return;
        }

        try {
            restClient.post()
                    .uri("/bot{botToken}/sendMessage", botToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(notification))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new TelegramDeliveryException(
                    "Telegram Bot API request failed for incident " + notification.incidentId(),
                    exception);
        }
    }

    String formatMessage(IncidentNotification notification) {
        boolean created = notification.eventType() == NotificationEventType.INCIDENT_CREATED;
        StringBuilder message = new StringBuilder(created
                ? "\uD83D\uDD34 Pulse: incident created"
                : "\uD83D\uDFE2 Pulse: incident resolved");
        append(message, "Service", notification.serviceName());
        append(message, "Status", created ? "DOWN" : "UP");
        append(message, "Incident ID", notification.incidentId().toString());
        if (created) {
            append(message, "Reason", notification.failureReason());
            if (notification.httpStatus() != null) {
                append(message, "HTTP status", notification.httpStatus().toString());
            }
        }
        append(message, "Started", formatInstant(notification.incidentStartedAt()));
        if (!created) {
            append(message, "Resolved", formatInstant(notification.incidentResolvedAt()));
        }
        return message.toString();
    }

    private Map<String, Object> requestBody(IncidentNotification notification) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", formatMessage(notification));
        return body;
    }

    private boolean isConfigured() {
        return !botToken.isBlank() && !chatId.isBlank();
    }

    private static RestClient createRestClient(String apiBaseUrl, long timeoutMs) {
        long safeTimeoutMs = timeoutMs > 0 ? timeoutMs : 5000;
        Duration timeout = Duration.ofMillis(safeTimeoutMs);
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder()
                .baseUrl(normalizeBaseUrl(apiBaseUrl))
                .requestFactory(requestFactory)
                .build();
    }

    private static String normalizeBaseUrl(String apiBaseUrl) {
        String value = trim(apiBaseUrl);
        if (value.isBlank()) return "https://api.telegram.org";
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String formatInstant(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static void append(StringBuilder message, String label, String value) {
        if (value != null && !value.isBlank()) {
            message.append('\n').append(label).append(": ").append(value);
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    static class TelegramDeliveryException extends RuntimeException {
        TelegramDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
