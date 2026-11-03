package edu.university.aggregator.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import edu.university.aggregator.model.AggregatedDataItem;
import edu.university.aggregator.model.AggregationCancelled;
import edu.university.aggregator.model.AggregationReport;
import edu.university.aggregator.model.FailureCode;
import edu.university.aggregator.model.RunConfiguration;
import edu.university.aggregator.model.SimulatedWorkerConfiguration;
import edu.university.aggregator.model.ValidationError;
import edu.university.aggregator.model.ValidationErrorReport;
import edu.university.aggregator.model.WorkerBehavior;
import edu.university.aggregator.model.WorkerConfiguration;
import edu.university.aggregator.model.WorkerOutcome;
import edu.university.aggregator.model.WorkerStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelTest {
    @Test
    void enumsExposeExactlyThePublishedValues() {
        assertEquals(List.of("success", "http_500", "malformed", "unavailable"),
                List.of(WorkerBehavior.values()).stream().map(WorkerBehavior::value).toList());
        assertEquals(List.of("success", "http_error", "connection_failure", "timeout",
                        "invalid_response", "cancelled"),
                List.of(WorkerStatus.values()).stream().map(WorkerStatus::value).toList());
        assertEquals(List.of("http_status", "connect_error", "network_error",
                        "remote_protocol_error", "request_timeout", "invalid_json",
                        "invalid_shape", "worker_identity_mismatch", "cancelled"),
                List.of(FailureCode.values()).stream().map(FailureCode::value).toList());
    }

    @Test
    void workerConfigurationEnforcesLocalContract() {
        WorkerConfiguration valid = new WorkerConfiguration("worker-a", "http://127.0.0.1:8080/work", "GET", 500);
        assertEquals("worker-a", valid.workerId());
        assertThrows(IllegalArgumentException.class,
                () -> new WorkerConfiguration(" worker-a ", valid.url(), "GET", 500));
        assertThrows(IllegalArgumentException.class,
                () -> new WorkerConfiguration("", valid.url(), "GET", 500));
        assertThrows(IllegalArgumentException.class,
                () -> new WorkerConfiguration("a".repeat(65), valid.url(), "GET", 500));
        assertThrows(IllegalArgumentException.class,
                () -> new WorkerConfiguration("worker-a", "ftp://localhost/work", "GET", 500));
        assertThrows(IllegalArgumentException.class,
                () -> new WorkerConfiguration("worker-a", "http://user:secret@localhost/work", "GET", 500));
        assertThrows(IllegalArgumentException.class,
                () -> new WorkerConfiguration("worker-a", valid.url(), "POST", 500));
        assertThrows(IllegalArgumentException.class,
                () -> new WorkerConfiguration("worker-a", valid.url(), "GET", 0));
    }

    @Test
    void simulatedConfigurationAndRunAreImmutableAndValidated() {
        SimulatedWorkerConfiguration simulated = new SimulatedWorkerConfiguration(
                "worker-a", WorkerBehavior.SUCCESS, 0, JsonNodeFactory.instance.nullNode(), "127.0.0.1", 0);
        assertEquals(0, simulated.delayMs());
        assertThrows(IllegalArgumentException.class, () -> new SimulatedWorkerConfiguration(
                "worker-a", WorkerBehavior.SUCCESS, -1, JsonNodeFactory.instance.nullNode(), "127.0.0.1", 0));
        assertThrows(IllegalArgumentException.class, () -> new SimulatedWorkerConfiguration(
                "worker-a", WorkerBehavior.SUCCESS, 1, JsonNodeFactory.instance.nullNode(), "0.0.0.0", 0));
        assertThrows(IllegalArgumentException.class, () -> new SimulatedWorkerConfiguration(
                "worker-a", WorkerBehavior.SUCCESS, 1, JsonNodeFactory.instance.nullNode(), "127.0.0.1", 65536));

        List<WorkerConfiguration> mutable = new java.util.ArrayList<>(List.of(worker("a"), worker("b"), worker("c")));
        RunConfiguration run = new RunConfiguration(3, mutable);
        mutable.clear();
        assertEquals(List.of("a", "b", "c"), run.workers().stream().map(WorkerConfiguration::workerId).toList());
        assertThrows(UnsupportedOperationException.class, () -> run.workers().clear());
    }

    @Test
    void validationModelsRequireActionableContent() {
        ValidationError error = new ValidationError("workers[0].url", "invalid_url", "url must be absolute");
        ValidationErrorReport report = new ValidationErrorReport(List.of(error));
        assertEquals(error, report.errors().getFirst());
        assertThrows(IllegalArgumentException.class, () -> new ValidationError("", "required", "missing"));
        assertThrows(IllegalArgumentException.class, () -> new ValidationErrorReport(List.of()));
    }

    @Test
    void outcomeInvariantsDistinguishSuccessAndFailure() {
        WorkerOutcome success = WorkerOutcome.success("a", 1.25, JsonNodeFactory.instance.nullNode(), 200);
        assertEquals(WorkerStatus.SUCCESS, success.status());
        assertThrows(IllegalArgumentException.class,
                () -> WorkerOutcome.success("a", Double.NaN, JsonNodeFactory.instance.nullNode(), 200));
        assertThrows(IllegalArgumentException.class,
                () -> WorkerOutcome.success("a", 1, JsonNodeFactory.instance.nullNode(), 500));
        WorkerOutcome failure = WorkerOutcome.failure(
                "b", WorkerStatus.TIMEOUT, 10, null, FailureCode.REQUEST_TIMEOUT, "deadline exceeded");
        assertTrue(failure.data().isNull());
        assertThrows(IllegalArgumentException.class, () -> new WorkerOutcome(
                "b", WorkerStatus.TIMEOUT, 10, JsonNodeFactory.instance.textNode("leak"), null,
                FailureCode.REQUEST_TIMEOUT, "deadline exceeded"));
    }

    @Test
    void reportDerivationPreservesOrderAndCancellationCarriesPartialReport() {
        WorkerOutcome a = WorkerOutcome.success("a", 1, JsonNodeFactory.instance.numberNode(1), 200);
        WorkerOutcome b = WorkerOutcome.failure(
                "b", WorkerStatus.TIMEOUT, 5, null, FailureCode.REQUEST_TIMEOUT, "deadline exceeded");
        AggregationReport report = AggregationReport.fromOutcomes(true, 6, List.of(a, b));
        assertEquals(List.of(new AggregatedDataItem("a", JsonNodeFactory.instance.numberNode(1))),
                report.aggregatedData());
        assertThrows(IllegalArgumentException.class,
                () -> new AggregationReport(true, -1, List.of(a), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> AggregationReport.fromOutcomes(true, 1, List.of(a, a)));
        AggregationReport partial = AggregationReport.fromOutcomes(false, 6, List.of(a, b));
        AggregationCancelled cancelled = new AggregationCancelled(partial);
        assertEquals(partial, cancelled.partialReport());
        assertThrows(IllegalArgumentException.class, () -> new AggregationCancelled(report));
    }

    private static WorkerConfiguration worker(String id) {
        return new WorkerConfiguration(id, "http://127.0.0.1:8080/work", "GET", 500);
    }
}
