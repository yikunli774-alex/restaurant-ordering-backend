package io.github.yikunli774.ordering.common.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;

/**
 * Catches exceptions from any controller and renders one uniform error shape
 * (RFC 9457 "Problem Details"): status + title + detail + stable {@code code}
 * + {@code traceId}. Nothing else in the app needs to format errors.
 */
@RestControllerAdvice
public final class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Bean Validation rejected the request body: report each bad field. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        ProblemDetail problem = baseProblem(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed"
        );
        problem.setProperty("violations", violations);
        return problem;
    }

    /** A handled business error carrying its own status and code. */
    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApi(ApiException exception) {
        return baseProblem(exception.status(), exception.code(), exception.getMessage());
    }

    /** Anything unexpected: log the real cause, but never leak internals to the client. */
    /** A method-level @PreAuthorize denial reaches here; render it as 403, not 500. */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException exception) {
        return baseProblem(HttpStatus.FORBIDDEN, ApiErrorCode.PERMISSION_DENIED, "Access denied");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unhandled request failure", exception);
        return baseProblem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred"
        );
    }

    private ProblemDetail baseProblem(HttpStatus status, ApiErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(
                "https://api.restaurant-ordering.local/problems/" + code.name().toLowerCase()));
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code.name());
        problem.setProperty("traceId", MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));
        return problem;
    }
}
