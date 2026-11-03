package edu.university.aggregator.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.university.aggregator.json.JsonCodec;
import edu.university.aggregator.model.WorkerStatus;
import edu.university.aggregator.scenario.ScenarioCatalog;
import edu.university.aggregator.service.AggregationService;
import edu.university.aggregator.worker.WorkerFleet;
import org.junit.jupiter.api.Test;

class FailureIsolationTest {
    @Test
    void retainsSuccessesAcrossHttpMalformedUnavailableAndTimeoutWorkers() throws Exception {
        JsonCodec codec = new JsonCodec();
        var scenario = ScenarioCatalog.require("partial-failure");
        try (WorkerFleet fleet = WorkerFleet.start(scenario.workers(), codec);
             AggregationService service = AggregationService.create(5, codec)) {
            var report = service.aggregate(fleet.runConfiguration(scenario.timeoutMs())).join();

            assertEquals(5, report.workerOutcomes().size());
            assertEquals(
                    java.util.List.of("worker-a", "worker-b", "worker-c", "worker-d", "worker-e"),
                    report.workerOutcomes().stream().map(outcome -> outcome.workerId()).toList());
            assertEquals(1, report.aggregatedData().size());
            assertEquals(WorkerStatus.SUCCESS, report.workerOutcomes().get(0).status());
            assertEquals(WorkerStatus.HTTP_ERROR, report.workerOutcomes().get(1).status());
            assertEquals(WorkerStatus.INVALID_RESPONSE, report.workerOutcomes().get(2).status());
            assertEquals(WorkerStatus.CONNECTION_FAILURE, report.workerOutcomes().get(3).status());
            assertEquals(WorkerStatus.TIMEOUT, report.workerOutcomes().get(4).status());
            assertTrue(report.complete());
        }
    }
}
