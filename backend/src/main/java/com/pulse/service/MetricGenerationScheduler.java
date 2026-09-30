package com.pulse.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "pulse.metrics", name = "simulation-enabled",
        havingValue = "true", matchIfMissing = true)
public class MetricGenerationScheduler {
    private final MetricGenerationService generationService;

    public MetricGenerationScheduler(MetricGenerationService generationService) {
        this.generationService = generationService;
    }

    @Scheduled(
            fixedDelayString = "${pulse.metrics.generation-interval-ms:5000}",
            initialDelayString = "${pulse.metrics.generation-interval-ms:5000}"
    )
    public void generateMetrics() {
        generationService.generateForAllServices();
    }
}
