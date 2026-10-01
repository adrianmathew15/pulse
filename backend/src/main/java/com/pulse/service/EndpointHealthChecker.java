package com.pulse.service;

public interface EndpointHealthChecker {
    HealthCheckOutcome check(String endpoint);
}
