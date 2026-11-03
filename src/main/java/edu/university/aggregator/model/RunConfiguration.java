package edu.university.aggregator.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

@JsonPropertyOrder({"concurrency_limit", "workers"})
public record RunConfiguration(int concurrencyLimit, List<WorkerConfiguration> workers) {
    public RunConfiguration {
        workers = List.copyOf(workers);
        if (workers.size() < 3 || workers.size() > 5) {
            throw new IllegalArgumentException("workers must contain 3 through 5 entries");
        }
        if (concurrencyLimit < 1 || concurrencyLimit > workers.size()) {
            throw new IllegalArgumentException("concurrency_limit must be 1 through worker count");
        }
        if (workers.stream().map(WorkerConfiguration::workerId).distinct().count() != workers.size()) {
            throw new IllegalArgumentException("worker identities must be unique");
        }
    }
}
