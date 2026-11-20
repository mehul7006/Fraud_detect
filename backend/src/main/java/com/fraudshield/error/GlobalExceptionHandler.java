package com.fraudshield.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import java.util.stream.Collectors;

/** Turns every exception into {@link ErrorBody}. Never leaks stack traces or internal messages for 5xx. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map((FieldError f) -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, msg, request);
    }

    /** Covers 400 bad JSON, 404, 405, 415 and ResponseStatusException thrown by our controllers. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        String msg = ex instanceof org.springframework.web.ErrorResponse er && er.getBody().getDetail() != null
                ? er.getBody().getDetail()
                : body instanceof ProblemDetail pd && pd.getDetail() != null ? pd.getDetail() : ex.getMessage();
        if (ex instanceof org.springframework.http.converter.HttpMessageNotReadableException) msg = "Malformed or unreadable request body";
        return build(HttpStatus.valueOf(statusCode.value()), msg, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> conflict(DataIntegrityViolationException ex, WebRequest request) {
        return build(HttpStatus.CONFLICT, "Conflicts with existing data", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled error on {}", req.getRequestURI(), ex);
        return ResponseEntity.status(500).body(ErrorBody.of(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", req.getRequestURI()));
    }

    private ResponseEntity<Object> build(HttpStatus status, String message, WebRequest request) {
        String path = request instanceof ServletWebRequest s ? s.getRequest().getRequestURI() : "";
        return ResponseEntity.status(status).body(ErrorBody.of(status, message, path));
    }
}
