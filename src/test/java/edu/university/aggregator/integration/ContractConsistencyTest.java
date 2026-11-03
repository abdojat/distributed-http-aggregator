package edu.university.aggregator.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import edu.university.aggregator.TestSupport;
import edu.university.aggregator.json.JsonCodec;
import edu.university.aggregator.model.AggregationReport;
import edu.university.aggregator.model.FailureCode;
import edu.university.aggregator.model.RunConfiguration;
import edu.university.aggregator.model.ValidationErrorReport;
import edu.university.aggregator.model.WorkerOutcome;
import edu.university.aggregator.model.WorkerStatus;
import edu.university.aggregator.worker.WorkerFleet;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContractConsistencyTest {
    private final JsonCodec codec = new JsonCodec();

    @Test
    void runConfigurationJsonPreservesRequiredSnakeCaseContract() throws Exception {
        String json = """
                {"concurrency_limit":2,"workers":[
                  {"worker_id":"a","url":"http://127.0.0.1:8080/work","method":"GET","timeout_ms":100},
                  {"worker_id":"b","url":"https://example.invalid/work","method":"GET","timeout_ms":200},
                  {"worker_id":"c","url":"http://127.0.0.1:8081/work","method":"GET","timeout_ms":300}
                ]}
                """;
        RunConfiguration configuration = codec.readRunConfiguration(json);
        assertEquals(2, configuration.concurrencyLimit());
        assertEquals(List.of("a", "b", "c"),
                configuration.workers().stream().map(worker -> worker.workerId()).toList());
        assertThrows(Exception.class, () -> codec.readRunConfiguration(
                json.replace("\"timeout_ms\":300", "\"timeout_ms\":300,\"extra\":true")));
    }

    @Test
    void validationEnvelopeContainsOnlyOrderedContractFields() throws Exception {
        ValidationErrorReport report = new ValidationErrorReport(List.of(
                new edu.university.aggregator.model.ValidationError(
                        "workers[0].timeout_ms", "out_of_range", "timeout_ms must be greater than 0")));
        JsonNode node = codec.readTree(codec.writeValidationErrors(report));
        assertTrue(node.has("errors"));
        assertEquals(1, node.get("errors").size());
        var fields = new java.util.ArrayList<String>();
        node.get("errors").get(0).fieldNames().forEachRemaining(fields::add);
        assertEquals(List.of("field", "code", "message"), fields);
    }

    @Test
    void successAndFailureOutcomeInvariantsRoundTripToPublishedNames() throws Exception {
        JsonNode data = JsonNodeFactory.instance.objectNode().put("value", 42);
        WorkerOutcome success = WorkerOutcome.success("worker-a", 12.5, data, 200);
        WorkerOutcome failure = WorkerOutcome.failure(
                "worker-b", WorkerStatus.HTTP_ERROR, 4.0, 500, FailureCode.HTTP_STATUS, "remote HTTP status");
        AggregationReport report = AggregationReport.fromOutcomes(true, 20.0, List.of(success, failure));
        JsonNode node = codec.readTree(codec.writeAggregationReport(report));

        var fields = new java.util.ArrayList<String>();
        node.fieldNames().forEachRemaining(fields::add);
        assertEquals(List.of("complete", "total_duration_ms", "worker_outcomes", "aggregated_data"), fields);
        assertEquals(2, node.get("worker_outcomes").size());
        assertEquals(1, node.get("aggregated_data").size());
        JsonNode successJson = node.get("worker_outcomes").get(0);
        assertEquals("worker-a", successJson.get("worker_id").asText());
        assertEquals("success", successJson.get("status").asText());
        assertEquals(200, successJson.get("http_status").asInt());
        assertTrue(successJson.get("failure_code").isNull());
        assertTrue(successJson.get("failure_reason").isNull());
        JsonNode failureJson = node.get("worker_outcomes").get(1);
        assertTrue(failureJson.get("data").isNull());
        assertEquals("http_status", failureJson.get("failure_code").asText());
        assertFalse(failureJson.get("failure_reason").asText().isBlank());
    }

    @Test
    void reportsContainOneOutcomePerConfiguredWorkerAndOrderedSuccessfulData() throws Exception {
        try (WorkerFleet fleet = WorkerFleet.start(TestSupport.successfulWorkers(10, 20, 30), codec)) {
            RunConfiguration configuration = fleet.runConfiguration(1_000);
            try (var service = edu.university.aggregator.service.AggregationService.create(3, codec)) {
                AggregationReport report = service.aggregate(configuration).join();
                assertEquals(configuration.workers().size(), report.workerOutcomes().size());
                assertEquals(configuration.workers().stream().map(worker -> worker.workerId()).toList(),
                        report.workerOutcomes().stream().map(outcome -> outcome.workerId()).toList());
                assertEquals(3, report.aggregatedData().size());
                assertTrue(report.workerOutcomes().stream().allMatch(outcome ->
                        outcome.status() == WorkerStatus.SUCCESS
                                && outcome.durationMs() >= 0
                                && outcome.httpStatus() >= 200
                                && outcome.httpStatus() <= 299
                                && outcome.data() != null
                                && outcome.failureCode() == null
                                && outcome.failureReason() == null));
                assertNotNull(report.totalDurationMs());
                assertTrue(report.totalDurationMs() >= 0);
            }
        }
    }
}
