package edu.university.aggregator.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import edu.university.aggregator.json.JsonCodec;
import edu.university.aggregator.model.SimulatedWorkerConfiguration;
import edu.university.aggregator.model.WorkerBehavior;
import edu.university.aggregator.model.WorkerStatus;
import edu.university.aggregator.service.AggregationService;
import edu.university.aggregator.worker.WorkerFleet;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimeoutTest {
    @Test
    void timeoutIsIndependentOfFastSibling() throws Exception {
        JsonCodec codec = new JsonCodec();
        List<SimulatedWorkerConfiguration> configurations = List.of(
                new SimulatedWorkerConfiguration("fast", WorkerBehavior.SUCCESS, 20,
                        JsonNodeFactory.instance.objectNode().put("value", 1), "127.0.0.1", 0),
                new SimulatedWorkerConfiguration("slow", WorkerBehavior.SUCCESS, 500,
                        JsonNodeFactory.instance.objectNode().put("value", 2), "127.0.0.1", 0),
                new SimulatedWorkerConfiguration("other", WorkerBehavior.SUCCESS, 30,
                        JsonNodeFactory.instance.objectNode().put("value", 3), "127.0.0.1", 0));
        try (WorkerFleet fleet = WorkerFleet.start(configurations, codec);
             AggregationService service = AggregationService.create(3, codec)) {
            long started = System.nanoTime();
            var report = service.aggregate(fleet.runConfiguration(80)).join();
            double elapsedMs = (System.nanoTime() - started) / 1_000_000.0;

            assertEquals(WorkerStatus.TIMEOUT, report.workerOutcomes().get(1).status());
            assertEquals(WorkerStatus.SUCCESS, report.workerOutcomes().get(0).status());
            assertEquals(WorkerStatus.SUCCESS, report.workerOutcomes().get(2).status());
            assertTrue(elapsedMs < 350, "run must not wait for the slow worker delay");
        }
    }
}
