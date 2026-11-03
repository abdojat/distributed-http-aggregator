package edu.university.aggregator;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import edu.university.aggregator.model.SimulatedWorkerConfiguration;
import edu.university.aggregator.model.WorkerBehavior;
import edu.university.aggregator.worker.WorkerServer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class TestSupport {
    private TestSupport() {
    }

    public static List<SimulatedWorkerConfiguration> successfulWorkers(long... delaysMs) {
        List<SimulatedWorkerConfiguration> workers = new ArrayList<>();
        for (int index = 0; index < delaysMs.length; index++) {
            String id = "worker-" + (char) ('a' + index);
            JsonNode data = JsonNodeFactory.instance.objectNode().put("value", index + 1);
            workers.add(new SimulatedWorkerConfiguration(
                    id, WorkerBehavior.SUCCESS, delaysMs[index], data, "127.0.0.1", 0));
        }
        return List.copyOf(workers);
    }

    public static Duration tolerance(long longestDelayOrTimeoutMs) {
        return Duration.ofMillis(Math.max(150, Math.round(longestDelayOrTimeoutMs * 0.25)));
    }

    public static void assertStopped(WorkerServer server) {
        assertFalse(server.isRunning(), "worker server must be stopped");
    }
}
