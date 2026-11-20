package com.fraudshield.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import java.io.IOException;
import java.time.Instant;

/** The single error format returned by every API failure. */
public record ErrorBody(String timestamp, int status, String error, String message, String path, String requestId) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ErrorBody of(HttpStatus status, String message, String path) {
        return new ErrorBody(Instant.now().toString(), status.value(), status.getReasonPhrase(), message, path, MDC.get(RequestIdFilter.MDC_KEY));
    }

    /** For code running outside Spring MVC (security filters). */
    public static void write(HttpServletResponse res, HttpStatus status, String message, String path) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        MAPPER.writeValue(res.getOutputStream(), of(status, message, path));
    }
}
