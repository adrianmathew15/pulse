package com.pulse.service;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class MetricValueSimulator {
    private final ConcurrentMap<UUID, SimulationState> states = new ConcurrentHashMap<>();

    public SimulatedMetric next(UUID serviceId) {
        SimulationState state = states.computeIfAbsent(serviceId, this::initialState);
        synchronized (state) {
            double cpuDelta = Math.sin(state.step * 0.73 + state.phase) * 3.2;
            double memoryDelta = Math.cos(state.step * 0.47 + state.phase) * 2.4;
            state.cpu = clamp(state.cpu + cpuDelta);
            state.memory = clamp(state.memory + memoryDelta);
            state.step++;
            return new SimulatedMetric(round(state.cpu), round(state.memory));
        }
    }

    private SimulationState initialState(UUID serviceId) {
        int hash = serviceId.hashCode();
        double cpu = 30 + Math.floorMod(hash, 3000) / 100.0;
        double memory = 40 + Math.floorMod(Integer.rotateLeft(hash, 13), 3000) / 100.0;
        double phase = Math.floorMod(hash, 628) / 100.0;
        return new SimulationState(cpu, memory, phase);
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record SimulatedMetric(double cpuUsage, double memoryUsage) {
    }

    private static final class SimulationState {
        private double cpu;
        private double memory;
        private final double phase;
        private long step;

        private SimulationState(double cpu, double memory, double phase) {
            this.cpu = cpu;
            this.memory = memory;
            this.phase = phase;
        }
    }
}
