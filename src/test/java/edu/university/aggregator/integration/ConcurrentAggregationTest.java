package edu.university.aggregator.integration;

import static edu.university.aggregator.TestSupport.successfulWorkers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.university.aggregator.json.JsonCodec;
import edu.university.aggregator.model.WorkerStatus;
import edu.university.aggregator.service.AggregationService;
import edu.university.aggregator.worker.WorkerFleet;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConcurrentAggregationTest {
    @Test
    void aggregatesThreeWorkersConcurrentlyInConfiguredOrder() throws Exception {
        JsonCodec codec = new JsonCodec();
        try (WorkerFleet fleet = WorkerFleet.start(successfulWorkers(300, 60, 180), codec);
             AggregationService service = AggregationService.create(3, codec)) {
            var report = service.aggregate(fleet.runConfiguration(2_000)).join();

            assertEquals(List.of("worker-a", "worker-b", "worker-c"),
                    report.workerOutcomes().stream().map(outcome -> outcome.workerId()).toList());
            assertTrue(report.workerOutcomes().stream().allMatch(outcome -> outcome.status() == WorkerStatus.SUCCESS));
            assertEquals(List.of(1, 2, 3), report.aggregatedData().stream()
                    .map(item -> item.data().path("value").asInt()).toList());
            assertTrue(report.totalDurationMs() < 540,
                    "concurrent duration must be well below the 540 ms delay sum");
        }
    }

    @Test
    void aggregatesFiveWorkersWithoutLosingIdentityOrOrder() throws Exception {
        JsonCodec codec = new JsonCodec();
        try (WorkerFleet fleet = WorkerFleet.start(successfulWorkers(100, 80, 60, 40, 20), codec);
             AggregationService service = AggregationService.create(5, codec)) {
            var report = service.aggregate(fleet.runConfiguration(2_000)).join();
            assertEquals(5, report.workerOutcomes().size());
            assertEquals(List.of("worker-a", "worker-b", "worker-c", "worker-d", "worker-e"),
                    report.workerOutcomes().stream().map(outcome -> outcome.workerId()).toList());
        }
    }
}
