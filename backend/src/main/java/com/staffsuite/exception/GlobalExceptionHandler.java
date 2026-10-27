package com.staffsuite.exception;

import java.time.Instant;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<com.staffsuite.dto.ErrorResponse> handleApi(ApiException ex, WebRequest request) {
        return build(ex.getStatus(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<com.staffsuite.dto.ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
            WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<com.staffsuite.dto.ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex,
            WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request body", request);
    }

    /** A query/path parameter with the wrong type, e.g. ?status=NOPE or ?from=not-a-date. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<com.staffsuite.dto.ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
            WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'", request);
    }

    /** Spring's own request errors (missing parameter/part, wrong method, wrong media type) keep their status. */
    @ExceptionHandler({ ServletRequestBindingException.class, MissingServletRequestPartException.class,
            HttpRequestMethodNotSupportedException.class, HttpMediaTypeNotSupportedException.class })
    public ResponseEntity<com.staffsuite.dto.ErrorResponse> handleSpringRequestErrors(Exception ex,
            WebRequest request) {
        HttpStatus status = ex instanceof ErrorResponse er ? HttpStatus.valueOf(er.getStatusCode().value())
                : HttpStatus.BAD_REQUEST;
        String message = status == HttpStatus.METHOD_NOT_ALLOWED ? "Method not allowed"
                : status == HttpStatus.UNSUPPORTED_MEDIA_TYPE ? "Unsupported content type"
                : "Missing or invalid request parameter";
        return build(status, message, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<com.staffsuite.dto.ErrorResponse> handleTooLarge(MaxUploadSizeExceededException ex,
            WebRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "File is too large (maximum 5 MB)", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<com.staffsuite.dto.ErrorResponse> handleAuthentication(AuthenticationException ex,
            WebRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<com.staffsuite.dto.ErrorResponse> handleNotFound(NoResourceFoundException ex,
            WebRequest request) {
        return build(HttpStatus.NOT_FOUND, "Resource not found", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<com.staffsuite.dto.ErrorResponse> handleAccessDenied(AccessDeniedException ex,
            WebRequest request) {
        return build(HttpStatus.FORBIDDEN, "Access denied", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<com.staffsuite.dto.ErrorResponse> handleGeneric(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<com.staffsuite.dto.ErrorResponse> build(HttpStatus status, String message,
            WebRequest request) {
        String path = request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI() : "";
        com.staffsuite.dto.ErrorResponse body = new com.staffsuite.dto.ErrorResponse(Instant.now(), status.value(),
                status.getReasonPhrase(), message, path);
        return ResponseEntity.status(status).body(body);
    }
}
