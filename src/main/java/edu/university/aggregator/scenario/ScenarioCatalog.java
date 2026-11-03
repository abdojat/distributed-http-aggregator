package edu.university.aggregator.scenario;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import edu.university.aggregator.model.SimulatedWorkerConfiguration;
import edu.university.aggregator.model.WorkerBehavior;
import java.util.List;

public final class ScenarioCatalog {
    private static final List<ScenarioDefinition> SCENARIOS = List.of(
            new ScenarioDefinition(
                    "normal",
                    "Three successful workers with distinct delays",
                    successfulWorkers(150, 300, 450),
                    2_000),
            new ScenarioDefinition(
                    "simultaneous",
                    "Five successful workers completing at the same configured delay",
                    successfulWorkers(75, 75, 75, 75, 75),
                    1_000));

    private ScenarioCatalog() {
    }

    public static List<ScenarioDefinition> all() {
        return SCENARIOS;
    }

    public static ScenarioDefinition require(String name) {
        return SCENARIOS.stream()
                .filter(scenario -> scenario.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown scenario: " + name));
    }

    private static List<SimulatedWorkerConfiguration> successfulWorkers(long... delaysMs) {
        java.util.ArrayList<SimulatedWorkerConfiguration> workers = new java.util.ArrayList<>();
        for (int index = 0; index < delaysMs.length; index++) {
            String id = "worker-" + (char) ('a' + index);
            workers.add(new SimulatedWorkerConfiguration(
                    id,
                    WorkerBehavior.SUCCESS,
                    delaysMs[index],
                    JsonNodeFactory.instance.objectNode().put("value", index + 1),
                    "127.0.0.1",
                    0));
        }
        return List.copyOf(workers);
    }
}
