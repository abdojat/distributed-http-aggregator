package edu.university.aggregator.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.HashSet;
import java.util.List;

@JsonPropertyOrder({"complete", "total_duration_ms", "worker_outcomes", "aggregated_data"})
public record AggregationReport(
        boolean complete,
        double totalDurationMs,
        List<WorkerOutcome> workerOutcomes,
        List<AggregatedDataItem> aggregatedData) {
    public AggregationReport {
        totalDurationMs = ModelRules.duration("total_duration_ms", totalDurationMs);
        workerOutcomes = List.copyOf(workerOutcomes);
        aggregatedData = List.copyOf(aggregatedData);
        HashSet<String> identities = new HashSet<>();
        if (workerOutcomes.stream().anyMatch(outcome -> !identities.add(outcome.workerId()))) {
            throw new IllegalArgumentException("worker_outcomes must contain unique identities");
        }
        List<AggregatedDataItem> expected = workerOutcomes.stream()
                .filter(outcome -> outcome.status() == WorkerStatus.SUCCESS)
                .map(outcome -> new AggregatedDataItem(outcome.workerId(), outcome.data()))
                .toList();
        if (!expected.equals(aggregatedData)) {
            throw new IllegalArgumentException("aggregated_data must derive from successful outcomes in order");
        }
    }

    public static AggregationReport fromOutcomes(
            boolean complete, double totalDurationMs, List<WorkerOutcome> workerOutcomes) {
        List<AggregatedDataItem> data = workerOutcomes.stream()
                .filter(outcome -> outcome.status() == WorkerStatus.SUCCESS)
                .map(outcome -> new AggregatedDataItem(outcome.workerId(), outcome.data()))
                .toList();
        return new AggregationReport(complete, totalDurationMs, workerOutcomes, data);
    }
}
