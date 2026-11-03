package edu.university.aggregator.service;

import edu.university.aggregator.client.WorkerHttpClient;
import edu.university.aggregator.json.JsonCodec;
import edu.university.aggregator.model.AggregationReport;
import edu.university.aggregator.model.RunConfiguration;
import edu.university.aggregator.model.WorkerOutcome;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AggregationService implements AutoCloseable {
    private final ExecutorService executor;
    private final WorkerHttpClient workerClient;
    private final AsyncPermitPool permits;

    private AggregationService(int concurrencyLimit, JsonCodec codec) {
        executor = Executors.newFixedThreadPool(concurrencyLimit);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .executor(executor)
                .build();
        workerClient = new WorkerHttpClient(httpClient, codec);
        permits = new AsyncPermitPool(concurrencyLimit);
    }

    public static AggregationService create(int concurrencyLimit, JsonCodec codec) {
        return new AggregationService(concurrencyLimit, codec);
    }

    public CompletableFuture<AggregationReport> aggregate(RunConfiguration configuration) {
        long started = System.nanoTime();
        List<CompletableFuture<WorkerOutcome>> outcomes = configuration.workers().stream()
                .map(worker -> permits.acquire().thenCompose(permit ->
                        workerClient.fetch(worker).whenComplete((ignored, failure) -> permit.close())))
                .toList();
        CompletableFuture<Void> all = CompletableFuture.allOf(outcomes.toArray(CompletableFuture[]::new));
        return all.thenApply(ignored -> {
            List<WorkerOutcome> ordered = outcomes.stream().map(CompletableFuture::join).toList();
            double durationMs = (System.nanoTime() - started) / 1_000_000.0;
            return AggregationReport.fromOutcomes(true, durationMs, ordered);
        });
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
