package edu.university.aggregator.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

@JsonPropertyOrder({"worker_id", "data"})
public record AggregatedDataItem(String workerId, JsonNode data) {
    public AggregatedDataItem {
        workerId = ModelRules.workerId(workerId);
        data = Objects.requireNonNull(data, "data");
    }
}
