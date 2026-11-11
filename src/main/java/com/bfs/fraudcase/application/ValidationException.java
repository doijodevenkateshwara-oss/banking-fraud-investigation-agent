package com.bfs.fraudcase.application;

import java.util.Map;

/**
 * Field-level validation failure; messages are safe to display.
 *
 * @author Venkateshwara Doijode
 */
public class ValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public ValidationException(Map<String, String> fieldErrors) {
        super("Validation failed: " + fieldErrors.keySet());
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public ValidationException(String field, String message) {
        this(Map.of(field, message));
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }
}
