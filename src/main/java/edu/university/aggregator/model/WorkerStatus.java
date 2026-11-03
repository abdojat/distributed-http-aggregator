package edu.university.aggregator.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum WorkerStatus {
    SUCCESS("success"),
    HTTP_ERROR("http_error"),
    CONNECTION_FAILURE("connection_failure"),
    TIMEOUT("timeout"),
    INVALID_RESPONSE("invalid_response"),
    CANCELLED("cancelled");

    private final String value;

    WorkerStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static WorkerStatus fromValue(String value) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown worker status: " + value));
    }
}
