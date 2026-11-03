package edu.university.aggregator.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import java.util.Objects;

@JsonPropertyOrder({"worker_id", "status", "duration_ms", "data", "http_status", "failure_code", "failure_reason"})
public record WorkerOutcome(
        String workerId,
        WorkerStatus status,
        double durationMs,
        JsonNode data,
        Integer httpStatus,
        FailureCode failureCode,
        String failureReason) {
    public WorkerOutcome {
        workerId = ModelRules.workerId(workerId);
        status = Objects.requireNonNull(status, "status");
        durationMs = ModelRules.duration("duration_ms", durationMs);
        data = data == null ? NullNode.getInstance() : data;
        if (httpStatus != null && (httpStatus < 100 || httpStatus > 599)) {
            throw new IllegalArgumentException("http_status must be 100 through 599");
        }
        if (status == WorkerStatus.SUCCESS) {
            if (httpStatus == null || httpStatus < 200 || httpStatus > 299) {
                throw new IllegalArgumentException("success requires a 2xx http_status");
            }
            if (failureCode != null || failureReason != null) {
                throw new IllegalArgumentException("success must not contain failure fields");
            }
        } else {
            if (!data.isNull()) {
                throw new IllegalArgumentException("failure data must be null");
            }
            failureCode = Objects.requireNonNull(failureCode, "failureCode");
            failureReason = ModelRules.nonBlank("failure_reason", failureReason);
        }
    }

    public static WorkerOutcome success(String workerId, double durationMs, JsonNode data, int httpStatus) {
        return new WorkerOutcome(workerId, WorkerStatus.SUCCESS, durationMs,
                Objects.requireNonNull(data, "data"), httpStatus, null, null);
    }

    public static WorkerOutcome failure(
            String workerId,
            WorkerStatus status,
            double durationMs,
            Integer httpStatus,
            FailureCode failureCode,
            String failureReason) {
        if (status == WorkerStatus.SUCCESS) {
            throw new IllegalArgumentException("failure status must not be success");
        }
        return new WorkerOutcome(workerId, status, durationMs, NullNode.getInstance(),
                httpStatus, failureCode, failureReason);
    }
}
