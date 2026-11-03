package edu.university.aggregator.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.aggregator.config.RunConfigurationValidator;
import edu.university.aggregator.model.ValidationError;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunConfigurationValidatorTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RunConfigurationValidator validator = new RunConfigurationValidator();

    @Test
    void validConfigurationHasNoErrors() throws Exception {
        assertEquals(List.of(), validator.validate(tree(validJson())));
    }

    @Test
    void collectsAllErrorsInStableDocumentAndFieldOrder() throws Exception {
        String json = """
                {
                  "unexpected": true,
                  "concurrency_limit": 0,
                  "workers": [
                    {"worker_id":"dup","url":"http://user:secret@localhost/work","method":"POST","timeout_ms":0,"extra":1},
                    {"worker_id":"dup","url":"ftp://localhost/work","method":"GET","timeout_ms":100},
                    {"worker_id":"   ","url":"not a url","method":null,"timeout_ms":"fast"},
                    {"worker_id":"%s","url":"http://localhost/work","method":"GET","timeout_ms":100},
                    {"worker_id":"fifth","url":"http://localhost/work","method":"GET","timeout_ms":100},
                    {"worker_id":"sixth","url":"http://localhost/work","method":"GET","timeout_ms":100}
                  ]
                }
                """.formatted("x".repeat(65));

        List<ValidationError> errors = validator.validate(tree(json));
        assertEquals(List.of(
                "unexpected:unknown_field",
                "concurrency_limit:out_of_range",
                "workers:worker_count",
                "workers[0].url:invalid_url",
                "workers[0].method:unsupported_method",
                "workers[0].timeout_ms:out_of_range",
                "workers[0].extra:unknown_field",
                "workers[1].worker_id:duplicate",
                "workers[1].url:invalid_url",
                "workers[2].worker_id:required",
                "workers[2].url:invalid_url",
                "workers[2].method:required",
                "workers[2].timeout_ms:invalid_type",
                "workers[3].worker_id:out_of_range"),
                errors.stream().map(e -> e.field() + ":" + e.code()).toList());
    }

    @Test
    void missingAndWrongTypesAreReportedWithoutThrowing() throws Exception {
        List<ValidationError> errors = validator.validate(tree("{\"concurrency_limit\":\"many\",\"workers\":{}}"));
        assertEquals(List.of("concurrency_limit:invalid_type", "workers:invalid_type"),
                errors.stream().map(e -> e.field() + ":" + e.code()).toList());
        assertFalse(validator.validate(tree("{}" )).isEmpty());
    }

    private static JsonNode tree(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    private static String validJson() {
        return """
                {"concurrency_limit":3,"workers":[
                  {"worker_id":"a","url":"http://127.0.0.1:8001/work","method":"GET","timeout_ms":500},
                  {"worker_id":"b","url":"http://127.0.0.1:8002/work","method":"GET","timeout_ms":500},
                  {"worker_id":"c","url":"http://127.0.0.1:8003/work","method":"GET","timeout_ms":500}
                ]}
                """;
    }
}
