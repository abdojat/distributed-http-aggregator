package edu.university.aggregator.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.university.aggregator.json.JsonCodec;
import edu.university.aggregator.model.WorkerStatus;
import edu.university.aggregator.scenario.ScenarioCatalog;
import edu.university.aggregator.service.AggregationService;
import edu.university.aggregator.worker.WorkerFleet;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AcceptanceScenarioTest {
    private static Stream<Arguments> scenarios() {
        return Stream.of(
                Arguments.of("normal", true, 3),
                Arguments.of("http-500", true, 2),
                Arguments.of("unavailable", true, 2),
                Arguments.of("timeout", true, 2),
                Arguments.of("simultaneous", true, 5),
                Arguments.of("partial-failure", true, 1),
                Arguments.of("all-fail", true, 0));
    }

    @ParameterizedTest(name = "{0} acceptance scenario")
    @MethodSource("scenarios")
    void executesRemoteAcceptanceScenarios(String name, boolean complete, int expectedSuccesses)
            throws Exception {
        JsonCodec codec = new JsonCodec();
        var scenario = ScenarioCatalog.require(name);
        try (WorkerFleet fleet = WorkerFleet.start(scenario.workers(), codec);
             var service = edu.university.aggregator.service.AggregationService.create(
                     scenario.workers().size(), codec)) {
            var report = service.aggregate(fleet.runConfiguration(scenario.timeoutMs())).join();
            assertEquals(complete, report.complete());
            assertEquals(scenario.workers().size(), report.workerOutcomes().size());
            assertEquals(expectedSuccesses, report.aggregatedData().size());
            assertTrue(report.workerOutcomes().stream().allMatch(outcome ->
                    outcome.durationMs() >= 0 && (outcome.status() == WorkerStatus.SUCCESS
                            ? outcome.httpStatus() != null && outcome.failureCode() == null
                            : outcome.data().isNull() && outcome.failureCode() != null
                                    && outcome.failureReason() != null)));
            if ("http-500".equals(name)) {
                assertEquals(WorkerStatus.HTTP_ERROR, report.workerOutcomes().get(0).status());
                assertEquals(500, report.workerOutcomes().get(0).httpStatus());
            } else if ("unavailable".equals(name)) {
                assertEquals(WorkerStatus.CONNECTION_FAILURE, report.workerOutcomes().get(0).status());
            } else if ("timeout".equals(name)) {
                assertEquals(WorkerStatus.TIMEOUT, report.workerOutcomes().get(0).status());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("invalidConfigurations")
    void invalidConfigurationIsRejectedBeforeAnyWorkerRequest(String json, String expectedField)
            throws Exception {
        var validator = new edu.university.aggregator.config.RunConfigurationValidator();
        var root = new JsonCodec().readTree(json);
        var errors = validator.validate(root);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(error -> error.field().equals(expectedField)));
    }

    private static Stream<Arguments> invalidConfigurations() {
        return Stream.of(
                Arguments.of("""
                        {"concurrency_limit":1,"workers":[
                        {"worker_id":"a","url":"http://127.0.0.1:1/work","method":"GET","timeout_ms":1},
                        {"worker_id":"b","url":"http://127.0.0.1:2/work","method":"GET","timeout_ms":1}]}
                        """, "workers"),
                Arguments.of("""
                        {"concurrency_limit":4,"workers":[
                        {"worker_id":"a","url":"http://127.0.0.1:1/work","method":"GET","timeout_ms":1},
                        {"worker_id":"a","url":"http://127.0.0.1:2/work","method":"GET","timeout_ms":1},
                        {"worker_id":"c","url":"http://127.0.0.1:3/work","method":"GET","timeout_ms":1}]}
                        """, "workers[1].worker_id"));
    }
}
