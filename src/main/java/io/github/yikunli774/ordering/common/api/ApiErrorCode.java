package io.github.yikunli774.ordering.common.api;

/**
 * Stable, machine-readable error codes returned to clients.
 * The frontend branches on these strings, so their names must not change casually.
 */
public enum ApiErrorCode {
    VALIDATION_FAILED,
    INVALID_STATE_TRANSITION,
    RESOURCE_NOT_FOUND,
    STAFF_CREDENTIALS_INVALID,
    TABLE_CODE_INVALID,
    PARTICIPANT_FORBIDDEN,
    RESOURCE_ALREADY_EXISTS,
    PERMISSION_DENIED,
    ITEM_UNAVAILABLE,
    EMPTY_CART,
    INSUFFICIENT_STOCK,
    SESSION_IN_CHECKOUT,
    INTERNAL_ERROR
}
