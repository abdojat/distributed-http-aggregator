package edu.university.aggregator.model;

import java.util.Objects;
import java.util.concurrent.CancellationException;

public final class AggregationCancelled extends CancellationException {
    private final AggregationReport partialReport;

    public AggregationCancelled(AggregationReport partialReport) {
        super("Aggregation cancelled");
        this.partialReport = Objects.requireNonNull(partialReport, "partialReport");
        if (partialReport.complete()) {
            throw new IllegalArgumentException("partial report must have complete=false");
        }
    }

    public AggregationReport partialReport() {
        return partialReport;
    }
}
