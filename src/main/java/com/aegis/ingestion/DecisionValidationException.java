package com.aegis.ingestion;

/**
 * Thrown by {@link DecisionIngestionService} when an incoming
 * {@code DecisionEvent} fails validation (missing required field) or is a
 * duplicate of a decision already in the audit log.
 *
 * Unchecked so callers that don't care (e.g. the mock generator, once it's
 * confident in its own data) aren't forced to handle it, while
 * {@code DecisionController} still catches it to return a 400.
 */
public class DecisionValidationException extends RuntimeException {

    public DecisionValidationException(String message) {
        super(message);
    }
}
