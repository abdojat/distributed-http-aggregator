package edu.university.aggregator.integration;

import static edu.university.aggregator.TestSupport.successfulWorkers;
import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.university.aggregator.json.JsonCodec;
import edu.university.aggregator.model.WorkerStatus;
import edu.university.aggregator.service.AggregationService;
import edu.university.aggregator.worker.WorkerFleet;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimultaneousCompletionTest {
    @Test
    void twentyRunsPreserveEveryOutcomeAssociationAndOrder() throws Exception {
        JsonCodec codec = new JsonCodec();
        try (WorkerFleet fleet = WorkerFleet.start(successfulWorkers(50, 50, 50, 50, 50), codec);
             AggregationService service = AggregationService.create(5, codec)) {
            List<String> expectedIds = List.of("worker-a", "worker-b", "worker-c", "worker-d", "worker-e");
            for (int run = 1; run <= 20; run++) {
                var report = service.aggregate(fleet.runConfiguration(1_000)).join();
                assertEquals(5, report.workerOutcomes().size(), "run " + run);
                assertEquals(expectedIds,
                        report.workerOutcomes().stream().map(outcome -> outcome.workerId()).toList(), "run " + run);
                assertEquals(List.of(1, 2, 3, 4, 5), report.aggregatedData().stream()
                        .map(item -> item.data().path("value").asInt()).toList(), "run " + run);
                assertEquals(List.of(WorkerStatus.SUCCESS, WorkerStatus.SUCCESS, WorkerStatus.SUCCESS,
                                WorkerStatus.SUCCESS, WorkerStatus.SUCCESS),
                        report.workerOutcomes().stream().map(outcome -> outcome.status()).toList(), "run " + run);
            }
            System.out.println("RELIABILITY_METRICS runs=20 passed=20 failures=0");
        }
    }
}
