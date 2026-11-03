package edu.university.aggregator.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import edu.university.aggregator.client.WorkerHttpClient;
import edu.university.aggregator.json.JsonCodec;
import edu.university.aggregator.model.SimulatedWorkerConfiguration;
import edu.university.aggregator.model.WorkerBehavior;
import edu.university.aggregator.model.WorkerConfiguration;
import edu.university.aggregator.model.WorkerStatus;
import edu.university.aggregator.worker.WorkerServer;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class WorkerHttpClientTest {
    @Test
    void sendAsyncMapsMatchingSuccessfulPayloadAndMonotonicDuration() throws Exception {
        JsonCodec codec = new JsonCodec();
        try (WorkerServer server = new WorkerServer(new SimulatedWorkerConfiguration(
                     "worker-a", WorkerBehavior.SUCCESS, 25,
                     JsonNodeFactory.instance.objectNode().put("value", 7), "127.0.0.1", 0), codec);
             var executor = Executors.newFixedThreadPool(2)) {
            server.start();
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(1)).executor(executor).build();
            WorkerHttpClient client = new WorkerHttpClient(httpClient, codec);
            var outcome = client.fetch(new WorkerConfiguration(
                    "worker-a", server.workUrl(), "GET", 1_000)).join();

            assertEquals(WorkerStatus.SUCCESS, outcome.status());
            assertEquals("worker-a", outcome.workerId());
            assertEquals(7, outcome.data().path("value").asInt());
            assertEquals(200, outcome.httpStatus());
            assertTrue(outcome.durationMs() >= 0);
        }
    }

    @Test
    void mapsHttp500WithoutDiscardingWorkerIdentity() throws Exception {
            JsonCodec codec = new JsonCodec();
            try (WorkerServer server = new WorkerServer(new SimulatedWorkerConfiguration(
                    "worker-error", WorkerBehavior.HTTP_500, 0,
                    JsonNodeFactory.instance.objectNode(), "127.0.0.1", 0), codec);
                 var executor = Executors.newFixedThreadPool(2)) {
                server.start();
                WorkerHttpClient client = new WorkerHttpClient(
                        HttpClient.newBuilder().executor(executor).build(), codec);

                var outcome = client.fetch(new WorkerConfiguration(
                        "worker-error", server.workUrl(), "GET", 1_000)).join();

                assertEquals(WorkerStatus.HTTP_ERROR, outcome.status());
                assertEquals(500, outcome.httpStatus());
                assertEquals("http_status", outcome.failureCode().value());
                assertTrue(outcome.data().isNull());
            }
    }

    @Test
    void mapsMalformedJsonAndConnectionFailureAsData() throws Exception {
            JsonCodec codec = new JsonCodec();
            try (WorkerServer server = new WorkerServer(new SimulatedWorkerConfiguration(
                    "worker-malformed", WorkerBehavior.MALFORMED, 0,
                    JsonNodeFactory.instance.objectNode(), "127.0.0.1", 0), codec);
                 var executor = Executors.newFixedThreadPool(2)) {
                server.start();
                WorkerHttpClient client = new WorkerHttpClient(
                        HttpClient.newBuilder().connectTimeout(Duration.ofMillis(200))
                                .executor(executor).build(), codec);

                var malformed = client.fetch(new WorkerConfiguration(
                        "worker-malformed", server.workUrl(), "GET", 1_000)).join();
                var unavailable = client.fetch(new WorkerConfiguration(
                        "worker-unavailable", "http://127.0.0.1:1/work", "GET", 500)).join();

                assertEquals(WorkerStatus.INVALID_RESPONSE, malformed.status());
                assertEquals("invalid_json", malformed.failureCode().value());
                assertEquals(WorkerStatus.CONNECTION_FAILURE, unavailable.status());
                assertTrue(unavailable.failureReason().length() < 100);
            }
    }

    @Test
    void mapsIndependentTimeoutWithoutThrowing() throws Exception {
            JsonCodec codec = new JsonCodec();
            try (WorkerServer server = new WorkerServer(new SimulatedWorkerConfiguration(
                    "worker-slow", WorkerBehavior.SUCCESS, 400,
                    JsonNodeFactory.instance.objectNode(), "127.0.0.1", 0), codec);
                 var executor = Executors.newFixedThreadPool(2)) {
                server.start();
                WorkerHttpClient client = new WorkerHttpClient(
                        HttpClient.newBuilder().executor(executor).build(), codec);

                var outcome = client.fetch(new WorkerConfiguration(
                        "worker-slow", server.workUrl(), "GET", 50)).join();

                assertEquals(WorkerStatus.TIMEOUT, outcome.status());
                assertEquals("request_timeout", outcome.failureCode().value());
        }
        }
}
