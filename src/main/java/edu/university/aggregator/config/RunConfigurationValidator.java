package edu.university.aggregator.config;

import com.fasterxml.jackson.databind.JsonNode;
import edu.university.aggregator.model.ValidationError;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class RunConfigurationValidator {
    private static final Set<String> RUN_FIELDS = Set.of("concurrency_limit", "workers");
    private static final Set<String> WORKER_FIELDS = Set.of("worker_id", "url", "method", "timeout_ms");

    public List<ValidationError> validate(JsonNode root) {
        List<ValidationError> errors = new ArrayList<>();
        if (root == null || !root.isObject()) {
            return List.of(error("$", "invalid_type", "configuration must be a JSON object"));
        }

        addUnknownFields(root, RUN_FIELDS, "", errors);
        JsonNode workers = root.get("workers");
        validateConcurrency(root.get("concurrency_limit"), workers, errors);
        validateWorkers(workers, errors);
        return List.copyOf(errors);
    }

    private static void validateConcurrency(JsonNode value, JsonNode workers, List<ValidationError> errors) {
        if (value == null || value.isNull()) {
            errors.add(error("concurrency_limit", "required", "concurrency_limit is required"));
        } else if (!value.isIntegralNumber()) {
            errors.add(error("concurrency_limit", "invalid_type", "concurrency_limit must be an integer"));
        } else {
            int limit = value.intValue();
            int workerCount = workers != null && workers.isArray() ? workers.size() : Integer.MAX_VALUE;
            if (limit < 1 || limit > workerCount) {
                errors.add(error("concurrency_limit", "out_of_range",
                        "concurrency_limit must be 1 through worker count"));
            }
        }
    }

    private static void validateWorkers(JsonNode workers, List<ValidationError> errors) {
        if (workers == null || workers.isNull()) {
            errors.add(error("workers", "required", "workers is required"));
            return;
        }
        if (!workers.isArray()) {
            errors.add(error("workers", "invalid_type", "workers must be an array"));
            return;
        }
        if (workers.size() < 3 || workers.size() > 5) {
            errors.add(error("workers", "worker_count", "workers must contain 3 through 5 entries"));
        }

        Set<String> identities = new HashSet<>();
        for (int index = 0; index < workers.size(); index++) {
            JsonNode worker = workers.get(index);
            String prefix = "workers[" + index + "]";
            if (!worker.isObject()) {
                errors.add(error(prefix, "invalid_type", "worker must be an object"));
                continue;
            }
            validateIdentity(worker.get("worker_id"), prefix + ".worker_id", identities, errors);
            validateUrl(worker.get("url"), prefix + ".url", errors);
            validateMethod(worker.get("method"), prefix + ".method", errors);
            validateTimeout(worker.get("timeout_ms"), prefix + ".timeout_ms", errors);
            addUnknownFields(worker, WORKER_FIELDS, prefix + ".", errors);
        }
    }

    private static void validateIdentity(
            JsonNode value, String field, Set<String> identities, List<ValidationError> errors) {
        if (value == null || value.isNull() || (value.isTextual() && value.textValue().isBlank())) {
            errors.add(error(field, "required", "worker_id is required"));
        } else if (!value.isTextual()) {
            errors.add(error(field, "invalid_type", "worker_id must be a string"));
        } else if (!value.textValue().equals(value.textValue().trim()) || value.textValue().length() > 64) {
            errors.add(error(field, "out_of_range", "worker_id must be trimmed and contain at most 64 characters"));
        } else if (!identities.add(value.textValue())) {
            errors.add(error(field, "duplicate", "worker_id must be unique"));
        }
    }

    private static void validateUrl(JsonNode value, String field, List<ValidationError> errors) {
        if (value == null || value.isNull()) {
            errors.add(error(field, "required", "url is required"));
            return;
        }
        if (!value.isTextual()) {
            errors.add(error(field, "invalid_type", "url must be a string"));
            return;
        }
        try {
            URI uri = URI.create(value.textValue());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            errors.add(error(field, "invalid_url", "url must be absolute credential-free HTTP(S)"));
        }
    }

    private static void validateMethod(JsonNode value, String field, List<ValidationError> errors) {
        if (value == null || value.isNull()) {
            errors.add(error(field, "required", "method is required"));
        } else if (!value.isTextual()) {
            errors.add(error(field, "invalid_type", "method must be a string"));
        } else if (!"GET".equals(value.textValue())) {
            errors.add(error(field, "unsupported_method", "method must be GET"));
        }
    }

    private static void validateTimeout(JsonNode value, String field, List<ValidationError> errors) {
        if (value == null || value.isNull()) {
            errors.add(error(field, "required", "timeout_ms is required"));
        } else if (!value.isIntegralNumber()) {
            errors.add(error(field, "invalid_type", "timeout_ms must be an integer"));
        } else if (value.longValue() <= 0) {
            errors.add(error(field, "out_of_range", "timeout_ms must be greater than 0"));
        }
    }

    private static void addUnknownFields(
            JsonNode object, Set<String> allowed, String prefix, List<ValidationError> errors) {
        Iterator<String> names = object.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!allowed.contains(name)) {
                errors.add(error(prefix + name, "unknown_field", "field is not supported"));
            }
        }
    }

    private static ValidationError error(String field, String code, String message) {
        return new ValidationError(field, code, message);
    }
}
