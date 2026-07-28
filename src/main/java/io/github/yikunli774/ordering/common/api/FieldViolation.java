package io.github.yikunli774.ordering.common.api;

/**
 * One invalid field in a rejected request: which field and why.
 * Returned as a list inside a validation error so the client can highlight inputs.
 */
public record FieldViolation(String field, String message) {
}
