package edu.university.aggregator.integration;

import static edu.university.aggregator.TestSupport.assertStopped;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import edu.university.aggregator.json.JsonCodec;
import edu.university.aggregator.model.SimulatedWorkerConfiguration;
import edu.university.aggregator.model.WorkerBehavior;
import edu.university.aggregator.worker.WorkerServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class WorkerContractTest {
    private final JsonCodec codec = new JsonCodec();

    @Test
    void servesHealthAndDelayedSuccessfulWorkOnAssignedLoopbackPort() throws Exception {
        WorkerServer server = new WorkerServer(new SimulatedWorkerConfiguration(
                "worker-a", WorkerBehavior.SUCCESS, 100,
                JsonNodeFactory.instance.objectNode().put("value", 42), "127.0.0.1", 0), codec);
        try {
            server.start();
            assertTrue(server.port() > 0);
            assertEquals("127.0.0.1", URI.create(server.workUrl()).getHost());
            HttpClient client = HttpClient.newHttpClient();

            JsonNode health = get(client, server.healthUrl()).body();
            assertEquals("worker-a", health.path("worker_id").asText());
            assertEquals("ready", health.path("status").asText());

            long started = System.nanoTime();
            HttpResult work = get(client, server.workUrl());
            double elapsedMs = (System.nanoTime() - started) / 1_000_000.0;
            assertEquals(200, work.status());
            assertEquals("worker-a", work.body().path("worker_id").asText());
            assertEquals(42, work.body().path("data").path("value").asInt());
            assertTrue(elapsedMs >= 80, "configured delay must be observable");
        } finally {
            server.close();
        }
        assertStopped(server);
    }

    @Test
    void successfulWorkerPreservesExplicitNullData() throws Exception {
        try (WorkerServer server = new WorkerServer(new SimulatedWorkerConfiguration(
                "worker-null", WorkerBehavior.SUCCESS, 0, JsonNodeFactory.instance.nullNode(),
                "127.0.0.1", 0), codec)) {
            server.start();
            JsonNode response = get(HttpClient.newHttpClient(), server.workUrl()).body();
            assertTrue(response.has("data"));
            assertTrue(response.get("data").isNull());
        }
    }

    @Test
    void servesHttp500AndMalformedBehaviors() throws Exception {
            try (WorkerServer error = new WorkerServer(new SimulatedWorkerConfiguration(
                    "worker-error", WorkerBehavior.HTTP_500, 0,
                    JsonNodeFactory.instance.objectNode(), "127.0.0.1", 0), codec);
                 WorkerServer malformed = new WorkerServer(new SimulatedWorkerConfiguration(
                         "worker-malformed", WorkerBehavior.MALFORMED, 0,
                         JsonNodeFactory.instance.objectNode(), "127.0.0.1", 0), codec)) {
                error.start();
                malformed.start();
                HttpClient client = HttpClient.newHttpClient();
                HttpResponse<String> errorResponse = rawGet(client, error.workUrl());
                HttpResponse<String> malformedResponse = rawGet(client, malformed.workUrl());

                assertEquals(500, errorResponse.statusCode());
                assertEquals(200, malformedResponse.statusCode());
                assertTrue(malformedResponse.body().contains("\"worker_id\":"));
        }
    }

    private HttpResult get(HttpClient client, String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(2)).GET().build();
        HttpResponse<String> response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).join();
        return new HttpResult(response.statusCode(), codec.readTree(response.body()));
    }

    private HttpResponse<String> rawGet(HttpClient client, String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(2)).GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).join();
    }

    private record HttpResult(int status, JsonNode body) {
    }
}
