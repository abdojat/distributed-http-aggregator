package edu.university.aggregator.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import edu.university.aggregator.json.JsonCodec;
import edu.university.aggregator.model.AggregationReport;
import edu.university.aggregator.model.FailureCode;
import edu.university.aggregator.model.ValidationError;
import edu.university.aggregator.model.ValidationErrorReport;
import edu.university.aggregator.model.WorkerOutcome;
import edu.university.aggregator.model.WorkerStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonCodecTest {
    private final JsonCodec codec = new JsonCodec();

    @Test
    void reportMatchesPublishedShapeAndConfiguredOrder() throws Exception {
        WorkerOutcome a = WorkerOutcome.success("a", 1.5, JsonNodeFactory.instance.nullNode(), 200);
        WorkerOutcome b = WorkerOutcome.failure(
                "b", WorkerStatus.TIMEOUT, 10, null, FailureCode.REQUEST_TIMEOUT, "deadline exceeded");
        JsonNode json = codec.readTree(codec.writeAggregationReport(
                AggregationReport.fromOutcomes(true, 11, List.of(a, b))));

        assertEquals(List.of("complete", "total_duration_ms", "worker_outcomes", "aggregated_data"),
                java.util.stream.StreamSupport.stream(
                        ((Iterable<String>) json::fieldNames).spliterator(), false).toList());
        assertEquals("a", json.at("/worker_outcomes/0/worker_id").asText());
        assertEquals("b", json.at("/worker_outcomes/1/worker_id").asText());
        assertEquals("success", json.at("/worker_outcomes/0/status").asText());
        assertEquals(200, json.at("/worker_outcomes/0/http_status").asInt());
        assertEquals(true, json.at("/worker_outcomes/0/data").isNull());
        assertEquals(true, json.at("/worker_outcomes/1/data").isNull());
        assertEquals("request_timeout", json.at("/worker_outcomes/1/failure_code").asText());
        assertEquals("a", json.at("/aggregated_data/0/worker_id").asText());
    }

    @Test
    void validationEnvelopeHasExactOrderedFields() throws Exception {
        String encoded = codec.writeValidationErrors(new ValidationErrorReport(List.of(
                new ValidationError("workers[1].timeout_ms", "out_of_range", "timeout_ms must be greater than 0"))));
        assertEquals("""
                {"errors":[{"field":"workers[1].timeout_ms","code":"out_of_range","message":"timeout_ms must be greater than 0"}]}""",
                encoded);
    }

    @Test
    void strictInputRejectsUnknownProperties() {
        String json = """
                {"concurrency_limit":3,"unknown":true,"workers":[
                  {"worker_id":"a","url":"http://localhost/a","method":"GET","timeout_ms":1},
                  {"worker_id":"b","url":"http://localhost/b","method":"GET","timeout_ms":1},
                  {"worker_id":"c","url":"http://localhost/c","method":"GET","timeout_ms":1}
                ]}
                """;
        assertThrows(UnrecognizedPropertyException.class, () -> codec.readRunConfiguration(json));
    }

    @Test
    void validSnakeCaseConfigurationRoundTripsIntoImmutableRecords() throws Exception {
        String json = """
                {"concurrency_limit":3,"workers":[
                  {"worker_id":"a","url":"http://localhost/a","method":"GET","timeout_ms":1},
                  {"worker_id":"b","url":"http://localhost/b","method":"GET","timeout_ms":2},
                  {"worker_id":"c","url":"http://localhost/c","method":"GET","timeout_ms":3}
                ]}
                """;
        var configuration = codec.readRunConfiguration(json);
        assertEquals(3, configuration.concurrencyLimit());
        assertEquals(List.of("a", "b", "c"),
                configuration.workers().stream().map(worker -> worker.workerId()).toList());
    }
}
