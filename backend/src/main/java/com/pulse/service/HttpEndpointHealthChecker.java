package com.pulse.service;

import com.pulse.entity.ServiceStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;

@Component
public class HttpEndpointHealthChecker implements EndpointHealthChecker {
    private final HttpClient httpClient;
    private final Duration timeout;
    private final Clock clock;

    @Autowired
    public HttpEndpointHealthChecker(@Value("${pulse.health.timeout-ms:3000}") long timeoutMs) {
        this(timeoutMs, Clock.systemUTC());
    }

    HttpEndpointHealthChecker(long timeoutMs, Clock clock) {
        if (timeoutMs < 1) throw new IllegalArgumentException("Health-check timeout must be positive");
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.clock = clock;
    }

    @Override
    public HealthCheckOutcome check(String endpoint) {
        long startedAt = System.nanoTime();
        try {
            URI uri = requireHttpUri(endpoint);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.discarding());
            long elapsed = elapsedMillis(startedAt);
            int statusCode = response.statusCode();
            boolean available = statusCode >= 200 && statusCode < 400;
            return outcome(available ? ServiceStatus.UP : ServiceStatus.DOWN,
                    statusCode, elapsed, available ? null : "HTTP " + statusCode);
        } catch (HttpTimeoutException exception) {
            return outcome(ServiceStatus.DOWN, null, elapsedMillis(startedAt),
                    "Request timed out after " + timeout.toMillis() + " ms");
        } catch (IllegalArgumentException exception) {
            return outcome(ServiceStatus.DOWN, null, elapsedMillis(startedAt),
                    "Invalid endpoint: " + safeMessage(exception));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return outcome(ServiceStatus.DOWN, null, elapsedMillis(startedAt),
                    "Health check was interrupted");
        } catch (IOException exception) {
            return outcome(ServiceStatus.DOWN, null, elapsedMillis(startedAt),
                    "Connection failed: " + safeMessage(exception));
        }
    }

    private URI requireHttpUri(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("endpoint is not configured");
        }
        URI uri = URI.create(endpoint.trim());
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null) {
            throw new IllegalArgumentException("endpoint must be an absolute HTTP or HTTPS URL");
        }
        return uri;
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0, Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
    }

    private HealthCheckOutcome outcome(ServiceStatus status, Integer httpStatus,
                                       long responseTimeMs, String failureReason) {
        return new HealthCheckOutcome(status, httpStatus, responseTimeMs,
                clock.instant(), truncate(failureReason));
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName() : message;
    }

    private String truncate(String value) {
        return value != null && value.length() > 1000 ? value.substring(0, 1000) : value;
    }
}
