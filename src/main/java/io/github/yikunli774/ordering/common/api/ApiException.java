package io.github.yikunli774.ordering.common.api;

import org.springframework.http.HttpStatus;

/**
 * The one exception type business code throws to signal a handled error.
 * It carries the HTTP status and a stable {@link ApiErrorCode}; the global
 * handler turns it into a uniform Problem Details JSON response.
 */
public final class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ApiErrorCode code;

    public ApiException(HttpStatus status, ApiErrorCode code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public ApiErrorCode code() {
        return code;
    }
}
