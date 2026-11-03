package edu.university.aggregator.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.university.aggregator.json.JsonCodec;
import edu.university.aggregator.service.AggregationService;
import edu.university.aggregator.worker.WorkerFleet;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class CancellationCleanupTest {
    @Test
    void cancellationReturnsPromptlyAndStopsFleet() throws Exception {
        JsonCodec codec = new JsonCodec();
        var workers = edu.university.aggregator.TestSupport.successfulWorkers(1_000, 1_000, 1_000);
        WorkerFleet fleet = WorkerFleet.start(workers, codec);
        AggregationService service = AggregationService.create(3, codec);
        try {
            var future = service.aggregate(fleet.runConfiguration(2_000));
            Thread.sleep(30);
            long started = System.nanoTime();
            assertTrue(future.cancel(true));
            var partial = service.cancellationReport(future).orElseThrow();
            assertTrue(!partial.complete());
            assertTrue(partial.workerOutcomes().stream()
                    .allMatch(outcome -> outcome.status().name().equals("CANCELLED")));
            assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 500);
        } finally {
            service.close();
            fleet.close();
        }
        assertTrue(fleet.servers().stream().noneMatch(server -> server.isRunning()));
    }
}
