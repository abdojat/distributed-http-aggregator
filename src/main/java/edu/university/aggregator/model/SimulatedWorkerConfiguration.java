package edu.university.aggregator.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

public record SimulatedWorkerConfiguration(
        String workerId,
        WorkerBehavior behavior,
        long delayMs,
        JsonNode data,
        String listenHost,
        int listenPort) {
    public SimulatedWorkerConfiguration {
        workerId = ModelRules.workerId(workerId);
        behavior = Objects.requireNonNull(behavior, "behavior");
        if (delayMs < 0) {
            throw new IllegalArgumentException("delay_ms must be non-negative");
        }
        data = Objects.requireNonNull(data, "data");
        if (!"127.0.0.1".equals(listenHost)) {
            throw new IllegalArgumentException("listen_host must be 127.0.0.1");
        }
        if (listenPort < 0 || listenPort > 65535) {
            throw new IllegalArgumentException("listen_port must be 0 or 1 through 65535");
        }
    }
}
