package edu.university.aggregator.client;

import com.fasterxml.jackson.databind.JsonNode;
import edu.university.aggregator.json.JsonCodec;
import edu.university.aggregator.model.WorkerConfiguration;
import edu.university.aggregator.model.FailureCode;
import edu.university.aggregator.model.WorkerOutcome;
import edu.university.aggregator.model.WorkerStatus;
import java.net.URI;
import java.net.ConnectException;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class WorkerHttpClient implements AutoCloseable {
    private final HttpClient httpClient;
    private final JsonCodec codec;
    private final Set<CompletableFuture<HttpResponse<String>>> activeTransports =
            ConcurrentHashMap.newKeySet();

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

        CompletableFuture<HttpResponse<String>> transport =
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        activeTransports.add(transport);
        CompletableFuture<WorkerOutcome> result = transport
                .orTimeout(configuration.timeoutMs(), TimeUnit.MILLISECONDS)
                .thenApply(response -> toOutcome(configuration, response, started))
                .handle((outcome, failure) -> failure == null
                        ? outcome
                        : mapFailure(configuration, started, unwrap(failure)));
        result.whenComplete((outcome, failure) -> {
            activeTransports.remove(transport);
            if (result.isCancelled() || (failure instanceof CancellationException)) {
                Thread.startVirtualThread(() -> transport.cancel(true));
            }
        });
        return result;
    }

    @Override
    public void close() {
        activeTransports.forEach(transport -> transport.cancel(true));
        activeTransports.clear();
    }

    private WorkerOutcome toOutcome(
            WorkerConfiguration configuration, HttpResponse<String> response, long started) {
        if (response.statusCode() < 200 || response.statusCode() > 299) {
            return WorkerOutcome.failure(
                    configuration.workerId(), WorkerStatus.HTTP_ERROR, elapsedMs(started),
                    response.statusCode(), FailureCode.HTTP_STATUS,
                    "worker returned HTTP status " + response.statusCode());
        }
        try {
            JsonNode body = codec.readTree(response.body());
            if (!body.isObject() || !body.has("worker_id") || !body.has("data")) {
                throw new ResponseMappingException(FailureCode.INVALID_SHAPE,
                        "worker response has invalid shape");
            }
            if (!configuration.workerId().equals(body.path("worker_id").asText())) {
                throw new ResponseMappingException(FailureCode.WORKER_IDENTITY_MISMATCH,
                        "worker identity does not match request");
            }
            double durationMs = elapsedMs(started);
            return WorkerOutcome.success(
                    configuration.workerId(), durationMs, body.get("data"), response.statusCode());
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new ResponseMappingException(FailureCode.INVALID_JSON,
                    "worker response is not valid JSON");
        }
    }

    private WorkerOutcome mapFailure(
            WorkerConfiguration configuration, long started, Throwable failure) {
        Throwable cause = unwrap(failure);
        if (cause instanceof ResponseMappingException responseFailure) {
            return WorkerOutcome.failure(configuration.workerId(), WorkerStatus.INVALID_RESPONSE,
                    elapsedMs(started), null, responseFailure.code, responseFailure.getMessage());
        }
        if (cause instanceof TimeoutException || cause instanceof HttpTimeoutException) {
            return WorkerOutcome.failure(configuration.workerId(), WorkerStatus.TIMEOUT,
                    elapsedMs(started), null, FailureCode.REQUEST_TIMEOUT,
                    "worker request exceeded configured timeout");
        }
        if (cause instanceof ConnectException) {
            return WorkerOutcome.failure(configuration.workerId(), WorkerStatus.CONNECTION_FAILURE,
                    elapsedMs(started), null, FailureCode.CONNECT_ERROR,
                    "worker connection could not be established");
        }
        if (cause instanceof IOException) {
            return WorkerOutcome.failure(configuration.workerId(), WorkerStatus.CONNECTION_FAILURE,
                    elapsedMs(started), null, FailureCode.NETWORK_ERROR,
                    "worker network operation failed");
        }
        if (cause instanceof CancellationException) {
            return WorkerOutcome.failure(configuration.workerId(), WorkerStatus.CANCELLED,
                    elapsedMs(started), null, FailureCode.CANCELLED,
                    "worker request was cancelled");
        }
        throw new CompletionException(cause);
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static double elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000.0;
    }

    private static final class ResponseMappingException extends RuntimeException {
        private final FailureCode code;

        private ResponseMappingException(FailureCode code, String message) {
            super(message);
            this.code = code;
        }
    }
}
