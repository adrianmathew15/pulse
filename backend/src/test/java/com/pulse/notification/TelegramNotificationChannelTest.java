package com.pulse.notification;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TelegramNotificationChannelTest {
    private HttpServer server;
    private final AtomicInteger requestCount = new AtomicInteger();
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private volatile int responseStatus;

    @BeforeEach
    void startServer() throws IOException {
        responseStatus = 200;
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::handleRequest);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void disabledChannelMakesNoApiRequest() {
        channel(false, "123:secret-token", "-1001234567890").send(createdNotification());

        assertThat(requestCount).hasValue(0);
    }

    @Test
    void usesConfiguredTelegramApiUrl() {
        channel(true, "123:secret-token", "-1001234567890").send(createdNotification());

        assertThat(requestCount).hasValue(1);
        assertThat(requestPath).hasValue("/bot123:secret-token/sendMessage");
    }

    @Test
    void usesBotTokenInSendMessageUrl() {
        channel(true, "987654:bot-token", "-1001234567890").send(createdNotification());

        assertThat(requestPath.get()).startsWith("/bot987654:bot-token/");
    }

    @Test
    void sendsConfiguredChatId() {
        channel(true, "123:secret-token", "-1001234567890").send(createdNotification());

        assertThat(requestBody.get()).contains("\"chat_id\":\"-1001234567890\"");
    }

    @Test
    void sendsTelegramSendMessagePayload() {
        channel(true, "123:secret-token", "42").send(createdNotification());

        assertThat(requestBody.get())
                .startsWith("{\"chat_id\":\"42\",\"text\":")
                .contains(
                        "Pulse: incident created",
                        "Service: Payments API",
                        "Status: DOWN",
                        "Incident ID: aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                        "Reason: HTTP 503 Service Unavailable",
                        "HTTP status: 503",
                        "Started: 2026-09-30T12:00:00Z")
                .doesNotContain("parse_mode")
                .endsWith("\"}");
    }

    @Test
    void formatsIncidentCreatedMessage() {
        String message = channel(false, "", "").formatMessage(createdNotification());

        assertThat(message).contains(
                "Pulse: incident created",
                "Service: Payments API",
                "Status: DOWN",
                "Incident ID: aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "Reason: HTTP 503 Service Unavailable",
                "HTTP status: 503",
                "Started: 2026-09-30T12:00:00Z")
                .doesNotContain("Resolved:");
    }

    @Test
    void formatsIncidentResolvedMessage() {
        String message = channel(false, "", "").formatMessage(resolvedNotification());

        assertThat(message).contains(
                "Pulse: incident resolved",
                "Service: Payments API",
                "Status: UP",
                "Incident ID: aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "Started: 2026-09-30T12:00:00Z",
                "Resolved: 2026-09-30T12:00:45Z")
                .doesNotContain("Reason:", "HTTP status:");
    }

    @Test
    void apiFailureIsIsolatedAndDoesNotStopAnotherChannel() {
        responseStatus = 500;
        TelegramNotificationChannel telegram = channel(
                true, "123:secret-token", "-1001234567890");
        AtomicInteger inAppDeliveries = new AtomicInteger();
        NotificationChannel inApp = notification -> inAppDeliveries.incrementAndGet();
        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(telegram, inApp));

        assertThatCode(() -> dispatcher.dispatch(
                new NotificationRequestedEvent(createdNotification())))
                .doesNotThrowAnyException();

        assertThat(requestCount).hasValue(1);
        assertThat(inAppDeliveries).hasValue(1);
    }

    @Test
    void missingConfigurationDoesNotPreventApplicationContextStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(TelegramNotificationChannel.class)
                .withPropertyValues("pulse.notifications.telegram.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TelegramNotificationChannel.class);
                    assertThatCode(() -> context.getBean(TelegramNotificationChannel.class)
                            .send(createdNotification())).doesNotThrowAnyException();
                });

        assertThat(requestCount).hasValue(0);
    }

    private TelegramNotificationChannel channel(boolean enabled, String token, String chatId) {
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .build();
        return new TelegramNotificationChannel(enabled, token, chatId, restClient);
    }

    private IncidentNotification createdNotification() {
        Instant startedAt = Instant.parse("2026-09-30T12:00:00Z");
        return new IncidentNotification(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                "Payments API", NotificationEventType.INCIDENT_CREATED, startedAt,
                "HTTP 503 Service Unavailable", 503, startedAt, null);
    }

    private IncidentNotification resolvedNotification() {
        Instant startedAt = Instant.parse("2026-09-30T12:00:00Z");
        Instant resolvedAt = startedAt.plusSeconds(45);
        return new IncidentNotification(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                "Payments API", NotificationEventType.INCIDENT_RESOLVED, resolvedAt,
                "HTTP 503 Service Unavailable", 503, startedAt, resolvedAt);
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        requestPath.set(exchange.getRequestURI().getPath());
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] response = responseStatus >= 400
                ? "{\"ok\":false,\"description\":\"provider unavailable\"}"
                        .getBytes(StandardCharsets.UTF_8)
                : "{\"ok\":true,\"result\":{\"message_id\":1}}"
                        .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(responseStatus, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
