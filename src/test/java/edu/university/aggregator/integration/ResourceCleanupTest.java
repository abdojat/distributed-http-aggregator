package edu.university.aggregator.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.university.aggregator.TestSupport;
import edu.university.aggregator.json.JsonCodec;
import edu.university.aggregator.model.WorkerStatus;
import edu.university.aggregator.scenario.ScenarioCatalog;
import edu.university.aggregator.service.AggregationService;
import edu.university.aggregator.worker.WorkerFleet;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ResourceCleanupTest {
    @Test
    void repeatedSuccessfulFailedAndTimedOutRunsLeaveNoWorkerServersRunning() throws Exception {
        for (String scenarioName : new String[] {"normal", "partial-failure", "timeout"}) {
            JsonCodec codec = new JsonCodec();
            var scenario = ScenarioCatalog.require(scenarioName);
            WorkerFleet fleet = WorkerFleet.start(scenario.workers(), codec);
            try {
                try (var service = AggregationService.create(scenario.workers().size(), codec)) {
                    var report = service.aggregate(fleet.runConfiguration(scenario.timeoutMs())).join();
                    assertTrue(report.complete());
                    assertTrue(report.workerOutcomes().stream().allMatch(outcome ->
                            outcome.status() != null && outcome.durationMs() >= 0));
                }
            } finally {
                fleet.close();
            }
            assertTrue(fleet.servers().stream().noneMatch(server -> server.isRunning()));
        }
    }

    @Test
    void repeatedCancellationProducesOnlyTerminalCancelledOutcomesAndCleansUp() throws Exception {
        for (int run = 0; run < 3; run++) {
            JsonCodec codec = new JsonCodec();
            WorkerFleet fleet = WorkerFleet.start(TestSupport.successfulWorkers(1_000, 1_000, 1_000), codec);
            var service = AggregationService.create(3, codec);
            try {
                var future = service.aggregate(fleet.runConfiguration(2_000));
                assertTrue(future.cancel(true));
                var partial = service.cancellationReport(future).orElseThrow();
                assertFalse(partial.complete());
                assertTrue(partial.workerOutcomes().stream()
                        .allMatch(outcome -> outcome.status() == WorkerStatus.CANCELLED));
                assertTrue(future.isCancelled());
            } finally {
                service.close();
                fleet.close();
            }
            assertTrue(fleet.servers().stream().noneMatch(server -> server.isRunning()));
        }
    }
}
