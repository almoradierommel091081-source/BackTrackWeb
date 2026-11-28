package org.example.backtrackweb.controller;

import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.exception.NotFoundException;
import org.example.backtrackweb.sync.SyncConflictException;
import org.example.backtrackweb.tenant.CrossTenantException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON errors for the machine API. The browser keeps its HTML error pages
 * ({@link GlobalExceptionHandler}); this advice only applies to the API
 * controllers.
 */
@RestControllerAdvice(basePackages = "org.example.backtrackweb.sync")
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> business(BusinessException ex) {
        return body(HttpStatus.BAD_REQUEST, "invalid_request", ex.getMessage());
    }

    @ExceptionHandler({NotFoundException.class, CrossTenantException.class})
    public ResponseEntity<Map<String, Object>> missing(RuntimeException ex) {
        return body(HttpStatus.NOT_FOUND, "not_found", "That record could not be found.");
    }

    /** A stale lease, a version conflict or a device asking for a held lease. */
    @ExceptionHandler(SyncConflictException.class)
    public ResponseEntity<Map<String, Object>> conflict(SyncConflictException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "conflict");
        body.put("reason", ex.getReason());
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> invalid(IllegalArgumentException ex) {
        return body(HttpStatus.BAD_REQUEST, "invalid_request", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception ex) {
        log.error("Unexpected API error", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "server_error",
                "Something went wrong while processing that request.");
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String error, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
