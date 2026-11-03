package edu.university.aggregator.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"field", "code", "message"})
public record ValidationError(String field, String code, String message) {
    public ValidationError {
        field = ModelRules.nonBlank("field", field);
        code = ModelRules.nonBlank("code", code);
        message = ModelRules.nonBlank("message", message);
    }
}
