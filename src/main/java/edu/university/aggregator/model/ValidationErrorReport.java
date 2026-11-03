package edu.university.aggregator.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

@JsonPropertyOrder("errors")
public record ValidationErrorReport(List<ValidationError> errors) {
    public ValidationErrorReport {
        errors = List.copyOf(errors);
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("errors must be non-empty");
        }
    }
}
