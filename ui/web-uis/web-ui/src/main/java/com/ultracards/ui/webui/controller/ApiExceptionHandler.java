package com.ultracards.ui.webui.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<?> handleHttpClientError(
            HttpClientErrorException ex,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        var uri = request.getRequestURI();
        var status = ex.getStatusCode();

        if (status.equals(HttpStatus.UNAUTHORIZED)) {
            nukeAllCookies(request, response);
            if (uri.startsWith("/api/")) {
                Map<String, Object> body = new HashMap<>();
                body.put("status", "unauthorized");
                body.put("message", "Authentication required");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
            }
            return ResponseEntity.status(HttpStatus.SEE_OTHER)
                    .header(HttpHeaders.LOCATION, "/")
                    .build();
        }

        // Special handling: conflict on email send -> forward JSON (UI expects to show message inline)
        if (uri.startsWith("/api/auth/email/send") && status.equals(HttpStatus.CONFLICT)) {
            return forwardUpstreamBody(status, ex);
        }

        // For API calls, do NOT logout on client errors. Forward status and body so UI can react.
        if (uri.startsWith("/api/")) {
            return forwardUpstreamBody(status, ex);
        }

        // For non-API MVC requests, keep the session and show a soft redirect
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, "/")
                .build();
    }

    @ExceptionHandler(MissingRequestCookieException.class)
    public ResponseEntity<?> handleMissingRequestCookie(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        var uri = request.getRequestURI();
        // For API calls, report as 401 JSON without destroying cookies globally
        if (uri.startsWith("/api/")) {
            Map<String, Object> body = new HashMap<>();
            body.put("status", "unauthorized");
            body.put("message", "Authentication required");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }
        // For non-API, gentle redirect to home
        nukeAllCookies(request, response);
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, "/")
                .build();
    }

    @ExceptionHandler({ MethodArgumentNotValidException.class, BindException.class })
    public ResponseEntity<?> handleValidation(Exception ex) {
        var br = (ex instanceof MethodArgumentNotValidException manve)
                ? manve.getBindingResult()
                : ((BindException) ex).getBindingResult();

        var fieldErrors = br.getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        DefaultMessageSourceResolvable::getDefaultMessage,
                        (a, b) -> a // keep first
                ));

        // include object-level (global) errors too, if you use them
        var globalErrors = br.getGlobalErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.toList());

        var body = new HashMap<>();
        body.put("status", "error");
        body.put("errors", fieldErrors);
        if (!globalErrors.isEmpty()) body.put("globalErrors", globalErrors);

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpServerErrorException.class)
    public ResponseEntity<?> handleServerError(
            HttpServerErrorException ex,
            HttpServletRequest request
    ) {
        // Forward upstream 5xx without logging out; UI can show a toast or inline error
        return forwardUpstreamBody(ex.getStatusCode(), ex);
    }

    private ResponseEntity<?> forwardUpstreamBody(org.springframework.http.HttpStatusCode status,
                                                  RuntimeException ex) {
        try {
            String body = (ex instanceof HttpClientErrorException hce) ? hce.getResponseBodyAsString()
                    : (ex instanceof HttpServerErrorException hse) ? hse.getResponseBodyAsString()
                    : null;
            if (body != null && !body.isBlank()) {
                var trimmed = body.trim();
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                    return ResponseEntity.status(status)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body(trimmed);
                }
            }
        } catch (Exception ignore) { }
        // Fallback generic JSON structure
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "error");
        payload.put("message", "Request failed");
        return ResponseEntity.status(status).body(payload);
    }

    private void nukeAllCookies(HttpServletRequest request,
                                HttpServletResponse response) {
        // Nuke all cookies the client sent
        AuthController.nukeAllCookies(request, response);
    }
}
