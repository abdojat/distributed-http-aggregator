package edu.university.aggregator.unit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import edu.university.aggregator.client.WorkerHttpClient;
import edu.university.aggregator.json.JsonCodec;
import edu.university.aggregator.model.WorkerConfiguration;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class DiagnosticRedactionTest {
    @Test
    void connectionDiagnosticsDoNotExposeDestinationOrResponseContent() {
        try (var executor = Executors.newSingleThreadExecutor()) {
            WorkerHttpClient client = new WorkerHttpClient(
                    HttpClient.newBuilder().connectTimeout(Duration.ofMillis(100))
                            .executor(executor).build(), new JsonCodec());
            var outcome = client.fetch(new WorkerConfiguration(
                    "secret-worker", "http://127.0.0.1:1/work", "GET", 100)).join();

            assertTrue(outcome.failureReason().contains("worker"));
            assertFalse(outcome.failureReason().contains("127.0.0.1"));
        }
    }
}
