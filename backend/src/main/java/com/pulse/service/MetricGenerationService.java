package com.pulse.service;

import com.pulse.entity.MonitoredService;
import com.pulse.repository.ServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class MetricGenerationService {
    private static final Logger log = LoggerFactory.getLogger(MetricGenerationService.class);

    private final ServiceRepository serviceRepository;
    private final MetricService metricService;
    private final MetricValueSimulator simulator;
    private final AlertEvaluationService alertEvaluationService;
    private final Clock clock;

    @Autowired
    public MetricGenerationService(ServiceRepository serviceRepository,
                                   MetricService metricService,
                                   MetricValueSimulator simulator,
                                   AlertEvaluationService alertEvaluationService) {
        this(serviceRepository, metricService, simulator, alertEvaluationService, Clock.systemUTC());
    }

    MetricGenerationService(ServiceRepository serviceRepository,
                            MetricService metricService,
                            MetricValueSimulator simulator,
                            AlertEvaluationService alertEvaluationService,
                            Clock clock) {
        this.serviceRepository = serviceRepository;
        this.metricService = metricService;
        this.simulator = simulator;
        this.alertEvaluationService = alertEvaluationService;
        this.clock = clock;
    }

    public void generateForAllServices() {
        for (MonitoredService service : serviceRepository.findAll()) {
            try {
                MetricValueSimulator.SimulatedMetric values = simulator.next(service.getId());
                var metric = metricService.record(
                        service, values.cpuUsage(), values.memoryUsage(), clock.instant());
                alertEvaluationService.evaluate(service.getId(), metric.cpuUsage(),
                        metric.memoryUsage(), metric.timestamp());
            } catch (RuntimeException exception) {
                log.error("Metric generation failed for service {}", service.getId(), exception);
            }
        }
    }
}
