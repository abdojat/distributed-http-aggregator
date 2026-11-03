package edu.university.aggregator.service;

import edu.university.aggregator.client.WorkerHttpClient;
import edu.university.aggregator.json.JsonCodec;
import edu.university.aggregator.model.AggregationReport;
import edu.university.aggregator.model.RunConfiguration;
import edu.university.aggregator.model.WorkerOutcome;
import edu.university.aggregator.model.WorkerStatus;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

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
        List<CompletableFuture<WorkerOutcome>> outcomes = new ArrayList<>();
        List<CompletableFuture<AsyncPermitPool.Permit>> acquisitions = new ArrayList<>();
        for (var worker : configuration.workers()) {
            CompletableFuture<AsyncPermitPool.Permit> acquisition = permits.acquire();
            acquisitions.add(acquisition);
            outcomes.add(acquisition.thenCompose(permit ->
                    workerClient.fetch(worker).whenComplete((ignored, failure) -> permit.close())));
        }
        CompletableFuture<Void> all = CompletableFuture.allOf(outcomes.toArray(CompletableFuture[]::new));
        AggregationFuture result = new AggregationFuture(outcomes, acquisitions, configuration, started);
        all.whenComplete((ignored, failure) -> {
            if (failure == null) {
                List<WorkerOutcome> ordered = outcomes.stream().map(CompletableFuture::join).toList();
                double durationMs = elapsedMs(started);
                result.complete(AggregationReport.fromOutcomes(true, durationMs, ordered));
            } else if (!(failure instanceof CancellationException)
                    && !result.isCancelled() && !result.cancelling()) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    public Optional<AggregationReport> cancellationReport(
            CompletableFuture<AggregationReport> aggregation) {
        if (aggregation instanceof AggregationFuture cancelled) {
            return Optional.ofNullable(cancelled.partialReport());
        }
        return Optional.empty();
    }

    private static double elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000.0;
    }

    private static final class AggregationFuture extends CompletableFuture<AggregationReport> {
        private final List<CompletableFuture<WorkerOutcome>> outcomes;
        private final List<CompletableFuture<AsyncPermitPool.Permit>> acquisitions;
        private final RunConfiguration configuration;
        private final long started;
        private final AtomicReference<AggregationReport> partialReport = new AtomicReference<>();
        private final java.util.concurrent.atomic.AtomicBoolean cancelling = new java.util.concurrent.atomic.AtomicBoolean();

        private AggregationFuture(
                List<CompletableFuture<WorkerOutcome>> outcomes,
                List<CompletableFuture<AsyncPermitPool.Permit>> acquisitions,
                RunConfiguration configuration,
                long started) {
            this.outcomes = outcomes;
            this.acquisitions = acquisitions;
            this.configuration = configuration;
            this.started = started;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (isDone()) {
                return false;
            }
            cancelling.set(true);
            List<WorkerOutcome> partial = new ArrayList<>();
            for (int index = 0; index < configuration.workers().size(); index++) {
                var worker = configuration.workers().get(index);
                var outcome = outcomes.get(index);
                if (outcome.isDone() && !outcome.isCompletedExceptionally() && !outcome.isCancelled()) {
                    partial.add(outcome.join());
                } else {
                    partial.add(WorkerOutcome.failure(worker.workerId(), WorkerStatus.CANCELLED,
                            elapsedMs(started), null, edu.university.aggregator.model.FailureCode.CANCELLED,
                            "worker request was cancelled"));
                }
            }
            partialReport.set(AggregationReport.fromOutcomes(false, elapsedMs(started), partial));
            acquisitions.forEach(acquisition -> acquisition.cancel(mayInterruptIfRunning));
            outcomes.forEach(outcome -> outcome.cancel(mayInterruptIfRunning));
            return super.cancel(mayInterruptIfRunning);
        }

        AggregationReport partialReport() {
            return partialReport.get();
        }

        boolean cancelling() {
            return cancelling.get();
        }
    }

    @Override
    public void close() {
        workerClient.close();
        executor.shutdownNow();
    }
}
