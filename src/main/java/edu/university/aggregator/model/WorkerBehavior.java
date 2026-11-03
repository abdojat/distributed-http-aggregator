package edu.university.aggregator.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum WorkerBehavior {
    SUCCESS("success"),
    HTTP_500("http_500"),
    MALFORMED("malformed"),
    UNAVAILABLE("unavailable");

    private final String value;

    WorkerBehavior(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static WorkerBehavior fromValue(String value) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown worker behavior: " + value));
    }
}
