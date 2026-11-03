package edu.university.aggregator.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum FailureCode {
    HTTP_STATUS("http_status"),
    CONNECT_ERROR("connect_error"),
    NETWORK_ERROR("network_error"),
    REMOTE_PROTOCOL_ERROR("remote_protocol_error"),
    REQUEST_TIMEOUT("request_timeout"),
    INVALID_JSON("invalid_json"),
    INVALID_SHAPE("invalid_shape"),
    WORKER_IDENTITY_MISMATCH("worker_identity_mismatch"),
    CANCELLED("cancelled");

    private final String value;

    FailureCode(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static FailureCode fromValue(String value) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown failure code: " + value));
    }
}
