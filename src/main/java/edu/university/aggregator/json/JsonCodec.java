package edu.university.aggregator.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import edu.university.aggregator.model.AggregationReport;
import edu.university.aggregator.model.RunConfiguration;
import edu.university.aggregator.model.ValidationErrorReport;

public final class JsonCodec {
    private final ObjectMapper mapper;

    public JsonCodec() {
        mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public JsonNode readTree(String json) throws JsonProcessingException {
        return mapper.readTree(json);
    }

    public RunConfiguration readRunConfiguration(String json) throws JsonProcessingException {
        return mapper.readValue(json, RunConfiguration.class);
    }

    public String writeAggregationReport(AggregationReport report) throws JsonProcessingException {
        return mapper.writeValueAsString(report);
    }

    public String writeValidationErrors(ValidationErrorReport report) throws JsonProcessingException {
        return mapper.writeValueAsString(report);
    }

    public String write(Object value) throws JsonProcessingException {
        return mapper.writeValueAsString(value);
    }
}
