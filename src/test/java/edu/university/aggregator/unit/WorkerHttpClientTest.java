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
}
