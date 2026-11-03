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
                    1_000),
            new ScenarioDefinition(
                    "http-500",
                    "One HTTP 500 worker alongside successful workers",
                    mixedWorkers(WorkerBehavior.HTTP_500, 100),
                    1_000),
            new ScenarioDefinition(
                    "unavailable",
                    "One unavailable worker alongside successful workers",
                    mixedWorkers(WorkerBehavior.UNAVAILABLE, 0),
                    1_000),
            new ScenarioDefinition(
                    "timeout",
                    "One delayed worker exceeds its independent timeout",
                    mixedWorkers(WorkerBehavior.SUCCESS, 800),
                    150),
            new ScenarioDefinition(
                    "partial-failure",
                    "Successful, HTTP error, malformed, unavailable, and delayed workers",
                    List.of(
                            worker("worker-a", WorkerBehavior.SUCCESS, 20, 1),
                            worker("worker-b", WorkerBehavior.HTTP_500, 20, 2),
                            worker("worker-c", WorkerBehavior.MALFORMED, 20, 3),
                            worker("worker-d", WorkerBehavior.UNAVAILABLE, 0, 4),
                            worker("worker-e", WorkerBehavior.SUCCESS, 500, 5)),
                    400));

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
            workers.add(worker("worker-" + (char) ('a' + index),
                    WorkerBehavior.SUCCESS, delaysMs[index], index + 1));
        }
        return List.copyOf(workers);
    }

    private static List<SimulatedWorkerConfiguration> mixedWorkers(
            WorkerBehavior firstBehavior, long firstDelayMs) {
        return List.of(
                worker("worker-a", firstBehavior, firstDelayMs, 1),
                worker("worker-b", WorkerBehavior.SUCCESS, 50, 2),
                worker("worker-c", WorkerBehavior.SUCCESS, 75, 3));
    }

    private static SimulatedWorkerConfiguration worker(
            String id, WorkerBehavior behavior, long delayMs, int value) {
        return new SimulatedWorkerConfiguration(
                id, behavior, delayMs,
                JsonNodeFactory.instance.objectNode().put("value", value),
                "127.0.0.1", 0);
    }
}
