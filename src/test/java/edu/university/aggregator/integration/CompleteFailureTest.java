package edu.university.aggregator.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.university.aggregator.json.JsonCodec;
import edu.university.aggregator.model.WorkerStatus;
import edu.university.aggregator.scenario.ScenarioCatalog;
import edu.university.aggregator.service.AggregationService;
import edu.university.aggregator.worker.WorkerFleet;
import org.junit.jupiter.api.Test;

class CompleteFailureTest {
    @Test
    void allFailProducesCompleteEmptyReportWithOneOutcomePerWorker() throws Exception {
        JsonCodec codec = new JsonCodec();
        var scenario = ScenarioCatalog.require("all-fail");
        try (WorkerFleet fleet = WorkerFleet.start(scenario.workers(), codec);
             AggregationService service = AggregationService.create(scenario.workers().size(), codec)) {
            var report = service.aggregate(fleet.runConfiguration(scenario.timeoutMs())).join();

            assertTrue(report.complete());
            assertEquals(scenario.workers().size(), report.workerOutcomes().size());
            assertEquals(0, report.aggregatedData().size());
            assertTrue(report.workerOutcomes().stream()
                    .allMatch(outcome -> outcome.status() != WorkerStatus.SUCCESS));
            assertEquals(scenario.workers().stream().map(worker -> worker.workerId()).toList(),
                    report.workerOutcomes().stream().map(outcome -> outcome.workerId()).toList());
            assertEquals(scenario.workers().size(),
                    report.workerOutcomes().stream()
                            .filter(outcome -> outcome.status() != WorkerStatus.SUCCESS)
                            .count());
            report.workerOutcomes().forEach(outcome -> {
                assertNotEquals(null, outcome.failureCode());
                assertNotEquals(null, outcome.failureReason());
            });
        }
    }
}
