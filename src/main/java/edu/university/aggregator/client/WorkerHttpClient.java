package edu.university.aggregator.client;

import com.fasterxml.jackson.databind.JsonNode;
import edu.university.aggregator.json.JsonCodec;
import edu.university.aggregator.model.WorkerConfiguration;
import edu.university.aggregator.model.WorkerOutcome;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class WorkerHttpClient {
    private final HttpClient httpClient;
    private final JsonCodec codec;

    public WorkerHttpClient(HttpClient httpClient, JsonCodec codec) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public CompletableFuture<WorkerOutcome> fetch(WorkerConfiguration configuration) {
        long started = System.nanoTime();
        HttpRequest request = HttpRequest.newBuilder(URI.create(configuration.url()))
                .timeout(Duration.ofMillis(configuration.timeoutMs()))
                .header("Accept", "application/json")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .orTimeout(configuration.timeoutMs(), TimeUnit.MILLISECONDS)
                .thenApply(response -> toOutcome(configuration, response, started));
    }

    private WorkerOutcome toOutcome(
            WorkerConfiguration configuration, HttpResponse<String> response, long started) {
        if (response.statusCode() < 200 || response.statusCode() > 299) {
            throw new IllegalStateException("US1 expected a successful HTTP response");
        }
        try {
            JsonNode body = codec.readTree(response.body());
            if (!body.isObject() || !body.has("worker_id") || !body.has("data")) {
                throw new IllegalArgumentException("worker response has invalid shape");
            }
            if (!configuration.workerId().equals(body.path("worker_id").asText())) {
                throw new IllegalArgumentException("worker identity mismatch");
            }
            double durationMs = (System.nanoTime() - started) / 1_000_000.0;
            return WorkerOutcome.success(
                    configuration.workerId(), durationMs, body.get("data"), response.statusCode());
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("worker response is not valid JSON", exception);
        }
    }
}
