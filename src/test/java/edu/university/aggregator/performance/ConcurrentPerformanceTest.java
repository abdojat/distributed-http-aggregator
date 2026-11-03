package edu.university.aggregator.performance;

import static edu.university.aggregator.TestSupport.successfulWorkers;
import static edu.university.aggregator.TestSupport.tolerance;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.university.aggregator.client.WorkerHttpClient;
import edu.university.aggregator.json.JsonCodec;
import edu.university.aggregator.service.AggregationService;
import edu.university.aggregator.worker.WorkerFleet;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class ConcurrentPerformanceTest {
    @Test
    void concurrentExecutionBeatsControlledSequentialBaseline() throws Exception {
        JsonCodec codec = new JsonCodec();
        try (WorkerFleet fleet = WorkerFleet.start(successfulWorkers(150, 300, 450), codec);
             AggregationService service = AggregationService.create(3, codec);
             var sequentialExecutor = Executors.newFixedThreadPool(3)) {
            double concurrentMs = service.aggregate(fleet.runConfiguration(2_000)).join().totalDurationMs();

            WorkerHttpClient sequentialClient = new WorkerHttpClient(HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(1)).executor(sequentialExecutor).build(), codec);
            long sequentialStart = System.nanoTime();
            for (var worker : fleet.runConfiguration(2_000).workers()) {
                sequentialClient.fetch(worker).join();
            }
            double sequentialMs = (System.nanoTime() - sequentialStart) / 1_000_000.0;
            double toleranceMs = tolerance(450).toMillis();

            System.out.printf("PERF_METRICS concurrent_seconds=%.6f sequential_seconds=%.6f speedup=%.3f%n",
                    concurrentMs / 1_000.0, sequentialMs / 1_000.0, sequentialMs / concurrentMs);
            assertTrue(concurrentMs <= 450 + toleranceMs);
            assertTrue(concurrentMs < 0.75 * 900);
            assertTrue(concurrentMs < 0.75 * sequentialMs);
        }
    }
}
