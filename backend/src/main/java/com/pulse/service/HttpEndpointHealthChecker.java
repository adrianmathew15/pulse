package com.pulse.service;

import com.pulse.entity.ServiceStatus;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;

@Component
public class HttpEndpointHealthChecker implements EndpointHealthChecker {
    private final EndpointDestinationValidator destinationValidator;
    private final EndpointHttpClient httpClient;
    private final Duration timeout;
    private final Clock clock;

    @Autowired
    public HttpEndpointHealthChecker(EndpointDestinationValidator destinationValidator,
                                     EndpointHttpClient httpClient,
                                     @Value("${pulse.health.timeout-ms:3000}") long timeoutMs) {
        this(destinationValidator, httpClient, timeoutMs, Clock.systemUTC());
    }

    HttpEndpointHealthChecker(EndpointDestinationValidator destinationValidator,
                              EndpointHttpClient httpClient, long timeoutMs, Clock clock) {
        if (timeoutMs < 1) throw new IllegalArgumentException("Health-check timeout must be positive");
        this.destinationValidator = destinationValidator;
        this.httpClient = httpClient;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.clock = clock;
    }

    @Override
    public HealthCheckOutcome check(String endpoint) {
        long startedAt = System.nanoTime();
        try {
            URI uri = destinationValidator.validate(endpoint);
            EndpointHttpResponse response = httpClient.get(uri);
            int statusCode = response.statusCode();
            if (statusCode >= 300 && statusCode < 400) {
                validateRedirect(uri, response.location());
                return outcome(ServiceStatus.DOWN, statusCode, elapsedMillis(startedAt),
                        "Redirect responses are not followed");
            }
            boolean available = statusCode >= 200 && statusCode < 300;
            return outcome(available ? ServiceStatus.UP : ServiceStatus.DOWN,
                    statusCode, elapsedMillis(startedAt), available ? null : "HTTP " + statusCode);
        } catch (EndpointNotAllowedException exception) {
            throw exception;
        } catch (EndpointResolutionException exception) {
            return outcome(ServiceStatus.DOWN, null, elapsedMillis(startedAt),
                    "Connection failed: endpoint host could not be resolved");
        } catch (IllegalArgumentException exception) {
            return outcome(ServiceStatus.DOWN, null, elapsedMillis(startedAt),
                    "Invalid endpoint: " + safeMessage(exception));
        } catch (IOException exception) {
            String reason = isTimeout(exception)
                    ? "Request timed out after " + timeout.toMillis() + " ms"
                    : "Connection failed: " + safeMessage(exception);
            return outcome(ServiceStatus.DOWN, null, elapsedMillis(startedAt), reason);
        }
    }

    private void validateRedirect(URI source, String location) {
        if (location == null || location.isBlank()) return;
        destinationValidator.validate(source.resolve(location));
    }

    private boolean isTimeout(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof SocketTimeoutException
                    || current instanceof ConnectionRequestTimeoutException) return true;
        }
        return false;
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
