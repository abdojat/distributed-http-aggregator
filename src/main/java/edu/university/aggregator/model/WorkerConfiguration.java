package edu.university.aggregator.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"worker_id", "url", "method", "timeout_ms"})
public record WorkerConfiguration(String workerId, String url, String method, long timeoutMs) {
    public WorkerConfiguration {
        workerId = ModelRules.workerId(workerId);
        url = ModelRules.url(url);
        if (!"GET".equals(method)) {
            throw new IllegalArgumentException("method must be exactly GET");
        }
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeout_ms must be greater than 0");
        }
    }
}
