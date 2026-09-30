package com.pulse.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MetricValueSimulatorTest {
    @Test
    void producesBoundedValuesWithTemporalContinuity() {
        MetricValueSimulator simulator = new MetricValueSimulator();
        UUID serviceId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        MetricValueSimulator.SimulatedMetric previous = simulator.next(serviceId);

        for (int i = 0; i < 1_000; i++) {
            MetricValueSimulator.SimulatedMetric current = simulator.next(serviceId);
            assertThat(current.cpuUsage()).isBetween(0.0, 100.0);
            assertThat(current.memoryUsage()).isBetween(0.0, 100.0);
            assertThat(Math.abs(current.cpuUsage() - previous.cpuUsage())).isLessThanOrEqualTo(3.21);
            assertThat(Math.abs(current.memoryUsage() - previous.memoryUsage())).isLessThanOrEqualTo(2.41);
            previous = current;
        }
    }

    @Test
    void maintainsIndependentServiceState() {
        MetricValueSimulator simulator = new MetricValueSimulator();
        UUID firstId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID secondId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        for (int i = 0; i < 10; i++) simulator.next(firstId);
        var secondAfterFirstAdvanced = simulator.next(secondId);
        var secondFromFreshSimulator = new MetricValueSimulator().next(secondId);

        assertThat(secondAfterFirstAdvanced).isEqualTo(secondFromFreshSimulator);
    }
}
