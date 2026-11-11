package com.bfs.fraudcase.api;

import com.bfs.fraudcase.application.AuthorizationException;
import com.bfs.fraudcase.application.IllegalStateTransitionException;
import com.bfs.fraudcase.application.NotFoundException;
import com.bfs.fraudcase.application.ValidationException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Safe RFC 9457 problem responses: no stack traces, provider details or sensitive payloads.
 *
 * @author Venkateshwara Doijode
 */
@RestControllerAdvice(assignableTypes = CaseApiController.class)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ValidationException.class)
    ProblemDetail validation(ValidationException e) {
        ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "Validation failed", "One or more fields are invalid");
        p.setProperty("fieldErrors", e.fieldErrors());
        return p;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadable(HttpMessageNotReadableException e) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request", "Request body is malformed or contains an unknown enum value");
    }

    @ExceptionHandler(AuthorizationException.class)
    ProblemDetail forbidden(AuthorizationException e) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    ProblemDetail conflict(IllegalStateTransitionException e) {
        return problem(HttpStatus.CONFLICT, "Invalid state transition", e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    ProblemDetail unexpected(RuntimeException e) {
        log.error("Unhandled error type={}", e.getClass().getSimpleName());
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Processing failed safely",
                "The request could not be completed; the case was left in a safe state");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(title);
        p.setType(URI.create("about:blank"));
        p.setProperty("correlationId", MDC.get("correlationId"));
        return p;
    }
}
