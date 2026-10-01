package com.pulse.service;

import com.pulse.entity.ServiceStatus;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class HttpEndpointHealthCheckerTest {
    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.createContext("/failure", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(250);
                exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void reportsSuccessfulHttpResponseAsUp() {
        HealthCheckOutcome result = checker(1000).check(baseUrl + "/ok");

        assertThat(result.status()).isEqualTo(ServiceStatus.UP);
        assertThat(result.httpStatus()).isEqualTo(204);
        assertThat(result.responseTimeMs()).isNotNegative();
        assertThat(result.failureReason()).isNull();
    }

    @Test
    void reportsHttpFailureAsDown() {
        HealthCheckOutcome result = checker(1000).check(baseUrl + "/failure");

        assertThat(result.status()).isEqualTo(ServiceStatus.DOWN);
        assertThat(result.httpStatus()).isEqualTo(503);
        assertThat(result.failureReason()).isEqualTo("HTTP 503");
    }

    @Test
    void reportsTimeoutWithoutThrowing() {
        HealthCheckOutcome result = checker(50).check(baseUrl + "/slow");

        assertThat(result.status()).isEqualTo(ServiceStatus.DOWN);
        assertThat(result.httpStatus()).isNull();
        assertThat(result.failureReason()).contains("timed out");
    }

    @Test
    void reportsInvalidEndpointWithoutThrowing() {
        HealthCheckOutcome result = checker(1000).check("not a URL");

        assertThat(result.status()).isEqualTo(ServiceStatus.DOWN);
        assertThat(result.failureReason()).startsWith("Invalid endpoint:");
    }

    private HttpEndpointHealthChecker checker(long timeoutMs) {
        return new HttpEndpointHealthChecker(timeoutMs, Clock.systemUTC());
    }
}
