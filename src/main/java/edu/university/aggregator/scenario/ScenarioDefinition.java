package edu.university.aggregator.scenario;

import edu.university.aggregator.model.SimulatedWorkerConfiguration;
import java.util.List;

public record ScenarioDefinition(
        String name,
        String description,
        List<SimulatedWorkerConfiguration> workers,
        long timeoutMs) {
    public ScenarioDefinition {
        workers = List.copyOf(workers);
    }
}
