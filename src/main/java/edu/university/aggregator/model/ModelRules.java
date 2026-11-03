package edu.university.aggregator.model;

import java.net.URI;

final class ModelRules {
    private ModelRules() {
    }

    static String workerId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("worker_id must be non-empty");
        }
        if (!value.equals(value.trim())) {
            throw new IllegalArgumentException("worker_id must be trimmed");
        }
        if (value.length() > 64) {
            throw new IllegalArgumentException("worker_id must contain at most 64 characters");
        }
        return value;
    }

    static String nonBlank(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be non-empty");
        }
        return value;
    }

    static String url(String value) {
        nonBlank("url", value);
        try {
            URI uri = URI.create(value);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("url must be absolute credential-free HTTP(S)");
            }
            return value;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("url must be absolute credential-free HTTP(S)", exception);
        }
    }

    static double duration(String name, double value) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return value;
    }
}
