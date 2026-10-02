package com.pulse.service;

import com.pulse.entity.ServiceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpEndpointHealthCheckerTest {
    private EndpointDestinationValidator validator;
    private EndpointHttpClient client;
    private HttpEndpointHealthChecker checker;
    private final URI endpoint = URI.create("https://203.0.113.10/health");

    @BeforeEach
    void setUp() {
        validator = mock(EndpointDestinationValidator.class);
        client = mock(EndpointHttpClient.class);
        checker = new HttpEndpointHealthChecker(validator, client, 50, Clock.systemUTC());
    }

    @Test
    void reportsSuccessfulHttpResponseAsUp() throws Exception {
        when(validator.validate(endpoint.toString())).thenReturn(endpoint);
        when(client.get(endpoint)).thenReturn(new EndpointHttpResponse(204, null));
        HealthCheckOutcome result = checker.check(endpoint.toString());

        assertThat(result.status()).isEqualTo(ServiceStatus.UP);
        assertThat(result.httpStatus()).isEqualTo(204);
        assertThat(result.failureReason()).isNull();
    }

    @Test
    void reportsHttpFailureAsDown() throws Exception {
        when(validator.validate(endpoint.toString())).thenReturn(endpoint);
        when(client.get(endpoint)).thenReturn(new EndpointHttpResponse(503, null));
        HealthCheckOutcome result = checker.check(endpoint.toString());

        assertThat(result.status()).isEqualTo(ServiceStatus.DOWN);
        assertThat(result.httpStatus()).isEqualTo(503);
        assertThat(result.failureReason()).isEqualTo("HTTP 503");
    }

    @Test
    void reportsTimeoutWithoutThrowing() throws Exception {
        when(validator.validate(endpoint.toString())).thenReturn(endpoint);
        when(client.get(endpoint)).thenThrow(new SocketTimeoutException("timed out"));
        assertThat(checker.check(endpoint.toString()).failureReason()).contains("timed out");
    }

    @Test
    void rejectsPrivateRedirectWithoutFollowingIt() throws Exception {
        URI privateTarget = URI.create("http://127.0.0.1/admin");
        when(validator.validate(endpoint.toString())).thenReturn(endpoint);
        when(client.get(endpoint)).thenReturn(new EndpointHttpResponse(302, privateTarget.toString()));
        doThrow(new EndpointNotAllowedException("Endpoint destination is not allowed"))
                .when(validator).validate(privateTarget);

        assertThatThrownBy(() -> checker.check(endpoint.toString()))
                .isInstanceOf(EndpointNotAllowedException.class);
        verify(client).get(endpoint);
        verify(validator).validate(privateTarget);
    }

    @Test
    void rejectsLocalhostRedirectWithoutFollowingIt() throws Exception {
        URI localhost = URI.create("http://localhost/admin");
        when(validator.validate(endpoint.toString())).thenReturn(endpoint);
        when(client.get(endpoint)).thenReturn(new EndpointHttpResponse(301, localhost.toString()));
        doThrow(new EndpointNotAllowedException("Endpoint destination is not allowed"))
                .when(validator).validate(localhost);

        assertThatThrownBy(() -> checker.check(endpoint.toString()))
                .isInstanceOf(EndpointNotAllowedException.class);
        verify(client).get(endpoint);
    }
}
